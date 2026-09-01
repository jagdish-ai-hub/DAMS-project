# DAMS — Deployment

**Live:** https://dams.jjsoftware.in

Production runs on a single VPS as two Docker containers behind the host's nginx,
which terminates TLS. Code changes deploy automatically on push to `main` via
GitHub Actions.

---

## Architecture

```
Internet ──HTTPS──▶ nginx (host)  ─ /      ─▶ 127.0.0.1:8083  frontend container (nginx + static SPA)
                                  ─ /api/  ─▶ 127.0.0.1:8082  backend container  (Spring Boot, :8080 inside)
                                                                     │
                                                                     ▼
                                                            Neon (hosted Postgres)

Attachments ─▶ Cloudflare R2
```

| Piece | Detail |
|---|---|
| Frontend image | built from `frontend/` — Vite build served by `nginx:alpine`. `VITE_API_URL` is baked in at build time. |
| Backend image | built from `backend/` — Maven build, `eclipse-temurin:21-jre-alpine` runtime. |
| Backend port | `127.0.0.1:8082` → `8080` in container |
| Frontend port | `127.0.0.1:8083` → `80` in container |
| Database | Neon Postgres — Flyway migrations run automatically on backend startup |
| Storage | Cloudflare R2 (`DAMS_STORAGE_PROVIDER=r2`) |
| TLS | Let's Encrypt via certbot, auto-renews (systemd timer) |
| JVM | capped: `-Xms128m -Xmx448m -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m`, container `mem_limit: 640m` |

---

## On the VPS

Everything lives in **`/home/server/dams/`**:

| File | Purpose |
|---|---|
| `compose.prod.yml` | production stack — pulls images from GHCR by tag. Shipped by CI on every deploy. |
| `.env` | real secrets + config (chmod 600, never committed). Keys below. |

`.env` keys:

```
IMAGE_PREFIX=ghcr.io/<owner>/<repo>           # which images compose.prod.yml pulls
SPRING_DATASOURCE_URL / _USERNAME / _PASSWORD # Neon
JWT_SECRET                                    # 64-char, generated on the server
APP_BASE_URL=https://dams.jjsoftware.in
CORS_ALLOWED_ORIGINS=https://dams.jjsoftware.in
DAMS_STORAGE_PROVIDER=r2
R2_ENDPOINT / R2_ACCESS_KEY_ID / R2_SECRET_ACCESS_KEY / R2_BUCKET
```

nginx vhost: `/etc/nginx/sites-available/dams` (symlinked into `sites-enabled/`).
Generated from `deploy/nginx-dams.conf`; certbot added the `443` block + HTTP→HTTPS redirect.

---

## CI/CD pipeline — `.github/workflows/ci.yml`

**Triggers**

| Event | What runs |
|---|---|
| Pull request → `main` | backend tests (`mvn verify`, incl. Testcontainers) + frontend (`lint`, `build`, `test`) |
| Push → `main` | tests → build & push images → deploy |
| Manual (`workflow_dispatch`) | deploy only — redeploy or roll back to any existing tag |

**Push to `main` flow**

1. **Tests** — backend and frontend, in parallel. Failure stops here; nothing ships.
2. **Build & push images** — `backend` and `frontend` built and pushed to GHCR as
   `ghcr.io/<owner>/<repo>-{backend,frontend}`, tagged `sha-<short>` **and** `latest`.
   GitHub layer cache (`type=gha`) keeps rebuilds fast.
3. **Deploy** — over SSH to the VPS:
   - ship `compose.prod.yml`
   - record the currently-running tag (rollback target)
   - `docker compose pull` the new `sha-` tag, `up -d`
   - poll `http://127.0.0.1:8082/actuator/health` and `http://127.0.0.1:8083/` for ~90 s
   - **healthy** → done, prune old images
   - **unhealthy** → dump container logs, `docker compose up -d` the previous tag,
     re-check, and fail the run. The site stays up on the previous version.

Every past `sha-` tag remains in GHCR, so any build can be re-deployed later.

---

## One-time setup

### GitHub → Settings → Secrets and variables → Actions

Secrets:

| Name | Value |
|---|---|
| `DEPLOY_USER` | `server` |
| `DEPLOY_HOST` | `66.116.244.178` |
| `DEPLOY_SSH_KEY` | private half of the dedicated deploy key (`~/.ssh/dams_deploy` on the VPS) |
| `DEPLOY_KNOWN_HOSTS` | `ssh-keyscan 66.116.244.178` output |

`DEPLOY_PATH` and `VITE_API_URL` are set directly in the workflow's top-level `env:` — no Variables needed.

### GitHub → Settings → Actions → General

Workflow permissions → **Read and write permissions** (lets the build push to GHCR).

### VPS

1. In `/home/server/dams/.env` set `IMAGE_PREFIX=ghcr.io/<owner>/<repo>` (lowercase).
2. Let the box pull the images — either:
   - make the two GHCR packages **Public** (Package settings on GitHub), or
   - `echo <PAT_with_read:packages> | docker login ghcr.io -u <owner> --password-stdin`

---

## Operations

```bash
cd /home/server/dams

# logs
docker compose -f compose.prod.yml logs -f backend
docker compose -f compose.prod.yml logs -f frontend

# restart / stop / start
docker compose -f compose.prod.yml restart
docker compose -f compose.prod.yml down
docker compose -f compose.prod.yml up -d

# deploy a specific tag by hand
DAMS_TAG=sha-abc1234 docker compose -f compose.prod.yml pull
DAMS_TAG=sha-abc1234 docker compose -f compose.prod.yml up -d

# change an env value, then re-create so it takes effect
nano .env
docker compose -f compose.prod.yml up -d
```

**Deploy new code:** `git push origin main`.

**Roll back / redeploy without a push:** GitHub → Actions → *CI / Deploy* → **Run workflow** →
`tag` = a past `sha-…` (or `latest`).

**Point at a different Neon database:** edit `SPRING_DATASOURCE_URL` (+ user/password) in
`/home/server/dams/.env`, then `docker compose -f compose.prod.yml up -d`.

**TLS:** renews automatically. Check with `sudo certbot certificates`.

---

## Current state

The stack was first brought up from images built on the VPS
(`compose.build.yml`). The first CI run switches it to GHCR images via
`compose.prod.yml` — same container names, no manual step. `compose.build.yml`
can be removed afterwards.
