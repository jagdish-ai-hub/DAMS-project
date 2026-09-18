#!/usr/bin/env bash
#
# Keep only the newest KEEP images per DAMS repo on this VPS; delete the rest.
#
# Safe by design:
#   - Never uses `docker rmi -f`. If an image is still in use by any container
#     (running or stopped), Docker refuses to remove it on its own and this
#     script just logs that and moves on — nothing is ever force-removed.
#   - Every image removed here still has a permanent copy in ghcr.io. DAMS's
#     own deploy/rollback step (ci.yml `roll()`) re-pulls by tag from the
#     registry whenever a tag isn't already cached locally, so deleting old
#     local copies cannot break a rollback.
#   - Only ever touches images whose repository matches the DAMS project
#     (the two REPOS below) — never a blanket `docker image prune` that could
#     catch some other app's images on the same VPS.
#
# Usage:
#   bash cleanup_old_dams_images.sh             # actually remove
#   bash cleanup_old_dams_images.sh --dry-run   # show what would be removed
#
# This file is versioned here for reference, but DAMS's deploy workflow only
# ever scp's compose.prod.yml to the VPS — it does NOT sync this script. If
# you edit this file, copy it to the VPS by hand:
#   scp deploy/cleanup_old_dams_images.sh server@66.116.244.178:/home/server/dams/deploy/

set -uo pipefail   # no -e: one skipped/failed rmi should not stop the rest

KEEP=2
REPOS=(
  "ghcr.io/jagdish-ai-hub/dams-project-backend"
  "ghcr.io/jagdish-ai-hub/dams-project-frontend"
)

DRY_RUN=false
[ "${1:-}" = "--dry-run" ] && DRY_RUN=true

echo "=== DAMS old image cleanup: $(date '+%Y-%m-%d %H:%M:%S') ==="
[ "$DRY_RUN" = true ] && echo "(dry run — nothing will actually be removed)"

for repo in "${REPOS[@]}"; do
  echo "--- $repo (keeping newest $KEEP) ---"

  mapfile -t old_ids < <(
    for id in $(docker images "$repo" -q | sort -u); do
      docker inspect -f '{{.Created}} {{.Id}}' "$id"
    done | sort -r | awk -v keep="$KEEP" 'NR>keep {print $2}'
  )

  if [ "${#old_ids[@]}" -eq 0 ]; then
    echo "  nothing older than the newest $KEEP — nothing to remove"
    continue
  fi

  for id in "${old_ids[@]}"; do
    if [ "$DRY_RUN" = true ]; then
      echo "  would remove $id"
    else
      docker rmi "$id" 2>&1 | sed 's/^/  /'
    fi
  done
done

echo "=== done ==="
