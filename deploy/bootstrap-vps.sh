#!/usr/bin/env bash
# One-time DAMS VPS setup. Run as root on a fresh Ubuntu 22.04 / 24.04 box:
#
#   sudo bash bootstrap-vps.sh dams.example.com deploy
#
# Idempotent — safe to re-run. It does NOT put secrets anywhere; you finish two steps by hand
# at the end (authorized_keys + /opt/dams/.env).
set -euo pipefail

DOMAIN="${1:?usage: bootstrap-vps.sh <domain> [deploy-user]}"
DEPLOY_USER="${2:-deploy}"
APP_DIR="/opt/dams"

echo "==> Docker"
if ! command -v docker >/dev/null 2>&1; then
  curl -fsSL https://get.docker.com | sh
fi
systemctl enable --now docker

echo "==> deploy user: $DEPLOY_USER"
if ! id "$DEPLOY_USER" >/dev/null 2>&1; then
  adduser --disabled-password --gecos "" "$DEPLOY_USER"
fi
usermod -aG docker "$DEPLOY_USER"
install -o "$DEPLOY_USER" -g "$DEPLOY_USER" -m 700 -d "/home/$DEPLOY_USER/.ssh"
touch "/home/$DEPLOY_USER/.ssh/authorized_keys"
chown "$DEPLOY_USER":"$DEPLOY_USER" "/home/$DEPLOY_USER/.ssh/authorized_keys"
chmod 600 "/home/$DEPLOY_USER/.ssh/authorized_keys"

echo "==> app dir: $APP_DIR"
install -o "$DEPLOY_USER" -g "$DEPLOY_USER" -m 700 -d "$APP_DIR"

echo "==> nginx + certbot"
apt-get update -qq
apt-get install -y nginx certbot python3-certbot-nginx
sed "s/dams.example.com/${DOMAIN}/g" "$(dirname "$0")/nginx-dams.conf" > /etc/nginx/sites-available/dams
ln -sf /etc/nginx/sites-available/dams /etc/nginx/sites-enabled/dams
rm -f /etc/nginx/sites-enabled/default
nginx -t && systemctl reload nginx

echo "==> firewall (if ufw is present)"
if command -v ufw >/dev/null 2>&1; then
  ufw allow OpenSSH || true
  ufw allow 'Nginx Full' || true
fi

cat <<EOF

────────────────────────────────────────────────────────────────────────
Bootstrap done. Two manual steps remain:

1. Add the deploy key's PUBLIC half:
     echo 'ssh-ed25519 AAAA... dams-deploy' >> /home/$DEPLOY_USER/.ssh/authorized_keys

2. Create $APP_DIR/.env from deploy/dams.env.example, then:
     chown $DEPLOY_USER:$DEPLOY_USER $APP_DIR/.env && chmod 600 $APP_DIR/.env

3. Log Docker in to GHCR (private images) as $DEPLOY_USER:
     echo <GHCR_READ_PAT> | sudo -u $DEPLOY_USER docker login ghcr.io -u <github-user> --password-stdin

4. Get the cert:
     certbot --nginx -d $DOMAIN

Then push to main — the deploy job takes it from there.
────────────────────────────────────────────────────────────────────────
EOF
