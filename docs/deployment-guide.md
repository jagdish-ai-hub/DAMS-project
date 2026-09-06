# DAMS Deployment Guide

Two ways to deploy, both landing on the same running stack:

| | **Method 1 — Automated** | **Method 2 — Manual (SFTP)** |
|---|---|---|
| Trigger | `git push` to `main` | you run commands |
| Builds | GitHub Actions | your laptop |
| Ships | Docker images → GHCR → VPS pulls | files copied over SFTP |
| Rollback | automatic on failed health-check | manual |
| Best for | day-to-day | first bring-up, or if you don't want CI |

**Everything runs the same `compose.prod.yml` on the VPS.** The one-time VPS setup (Part 1) is
required for both. Then do Part 2 *or* Part 3.

---

## Architecture

```
                 ┌─────────────── VPS (Ubuntu) ───────────────┐
Browser ──HTTPS──▶ nginx + certbot                             │
                 │   /       → 127.0.0.1:8083  frontend (nginx SPA, container)
                 │   /api/   → 127.0.0.1:8082  backend  (Spring Boot, container)
                 └───────────────┬───────────────┬────────────┘
                                 │               │
                          Neon (Postgres)   Cloudflare R2 (attachments)
```

- **GHCR** = GitHub Container Registry — where built images are stored
  (`ghcr.io/<owner>/<repo>-backend`, `-frontend`). The VPS pulls from it.
- The frontend's API origin (`VITE_API_URL`) is **baked into the image at build time**, not
  read at runtime. Method 1 passes it from a repo variable; in Method 2 you pass it yourself.

---

## Prerequisites (both methods)

1. A VPS running **Ubuntu 22.04 / 24.04**, with root or sudo.
2. Your domain's **DNS A record** pointed at the VPS IP (so `certbot` can validate).
3. These values ready:
   - Neon: `SPRING_DATASOURCE_URL` / `USERNAME` / `PASSWORD` (use a prod Neon role, not local dev)
   - R2: `R2_ENDPOINT` / `R2_ACCESS_KEY_ID` / `R2_SECRET_ACCESS_KEY` / `R2_BUCKET`
   - A **fresh** `JWT_SECRET` for prod — `openssl rand -base64 48`
4. Docker images must exist somewhere the VPS can reach:
   - Method 1 & 2A: GHCR (built by CI, or pushed from your laptop — see 2A)
   - 2C: loaded from a tarball, no registry

---

## Part 1 — One-time VPS setup (required for both methods)

SSH in as root: `ssh root@<VPS_IP>`

```bash
# 1. get the repo (only for the deploy/ scripts)
apt-get update -qq && apt-get install -y git
git clone https://github.com/<YOU>/<REPO>.git /tmp/dams
cd /tmp/dams

# 2. run the bootstrap: installs Docker, creates the 'deploy' user (in the docker group),
#    installs nginx + certbot, drops the nginx server block for <DOMAIN>
bash deploy/bootstrap-vps.sh <DOMAIN> deploy

# 3. authorize the deploy key — paste the ONE line from dams_deploy.pub (see Part 2 step 1;
#    for a pure-manual setup use any key whose private half you hold)
nano /home/deploy/.ssh/authorized_keys

# 4. app config
cp deploy/dams.env.example /opt/dams/.env
nano /opt/dams/.env                 # fill EVERY value — see the table below
chown deploy:deploy /opt/dams/.env && chmod 600 /opt/dams/.env

# 5. let Docker pull private images from GHCR
#    make a GitHub token with the read:packages scope, then:
echo <GHCR_READ_TOKEN> | sudo -u deploy docker login ghcr.io -u <YOU> --password-stdin

# 6. TLS certificate
certbot --nginx -d <DOMAIN>
```

### `/opt/dams/.env`

| Key | Value |
|---|---|
| `IMAGE_PREFIX` | `ghcr.io/<YOU>/<REPO>` (lowercase) — for 2B/2C use local names instead |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://<host>.neon.tech/dams?sslmode=require` |
| `SPRING_DATASOURCE_USERNAME` / `_PASSWORD` | Neon prod role |
| `JWT_SECRET` | fresh 32+ char secret |
| `APP_BASE_URL` | `https://<DOMAIN>` |
| `CORS_ALLOWED_ORIGINS` | `https://<DOMAIN>` |
| `DAMS_STORAGE_PROVIDER` | `r2` |
| `R2_ENDPOINT` / `R2_ACCESS_KEY_ID` / `R2_SECRET_ACCESS_KEY` / `R2_BUCKET` | from Cloudflare |

---

## Part 2 — Method 1: Automated (GitHub Actions)

### Step 1 — repo on GitHub + deploy key

```bash
# on your laptop, in the project folder
git remote add origin https://github.com/<YOU>/<REPO>.git
git add -A && git commit -m "Deploy pipeline"
git branch -M main && git push -u origin main

# dedicated deploy keypair (NOT your personal key)
ssh-keygen -t ed25519 -f dams_deploy -N "" -C dams-deploy
ssh-keyscan -H <VPS_IP>          # copy the whole output for DEPLOY_KNOWN_HOSTS
```

Put `dams_deploy.pub`'s single line into `/home/deploy/.ssh/authorized_keys` on the VPS
(Part 1 step 3).

### Step 2 — GitHub secrets & variables

Repo → **Settings → Secrets and variables → Actions**

**Secrets**

| Name | Value |
|---|---|
| `DEPLOY_SSH_KEY` | full contents of the private file `dams_deploy` |
| `DEPLOY_HOST` | `<VPS_IP>` |
| `DEPLOY_USER` | `deploy` |
| `DEPLOY_KNOWN_HOSTS` | the `ssh-keyscan -H <VPS_IP>` output |

**Variables**

| Name | Value |
|---|---|
| `VITE_API_URL` | `https://<DOMAIN>` — site root, **no** `/api` |
| `DEPLOY_PATH` | `/opt/dams` |
| `DEPLOY_PORT` | `22` (only if your SSH port differs) |

### Step 3 — deploy

```bash
git push            # any push to main; or: git commit --allow-empty -m "deploy" && git push
```

Watch the **deploy** job in the repo's **Actions** tab. What it does:

```
push to main
  ├─ backend   : mvn verify
  ├─ frontend  : npm lint / build / test
  ├─ images    : docker build → push  ghcr.io/…-backend:sha-XXXX + :latest   (same for frontend)
  └─ deploy    : scp compose.prod.yml → ssh:
                   DAMS_TAG=sha-XXXX docker compose pull && up -d
                   poll /actuator/health + frontend / for ~90s
                     healthy   → prune old images, done
                     unhealthy → relaunch the previously-running sha- tag, fail the run
```

---

## Part 3 — Method 2: Manual via SFTP

SFTP only moves files; you still SSH in once to restart. Pick 2A (keep Docker — least work) or
2B (no Docker).

### SFTP / SCP quick reference

From Windows use **WinSCP** (GUI) or, in PowerShell / Git Bash (OpenSSH is built in):

```bash
# single file
scp -i dams_deploy compose.prod.yml deploy@<VPS_IP>:/opt/dams/

# a folder
scp -i dams_deploy -r frontend/dist deploy@<VPS_IP>:/var/www/dams

# interactive
sftp -i dams_deploy deploy@<VPS_IP>
```

### 2A — SFTP the config, keep Docker images from GHCR  *(recommended manual path)*

Images still need to be built and pushed. Either let CI's `images` job do it (push to a branch
is enough — you just skip the `deploy` job), **or** push from your laptop:

```bash
# laptop — one-time login
echo <GHCR_TOKEN> | docker login ghcr.io -u <YOU> --password-stdin

# build & push (repeat per release)
docker build -t ghcr.io/<YOU>/<REPO>-backend:manual ./backend
docker build --build-arg VITE_API_URL=https://<DOMAIN> -t ghcr.io/<YOU>/<REPO>-frontend:manual ./frontend
docker push ghcr.io/<YOU>/<REPO>-backend:manual
docker push ghcr.io/<YOU>/<REPO>-frontend:manual
```

Then:

```bash
# SFTP these to /opt/dams/ :   compose.prod.yml   +   your filled .env
scp -i dams_deploy compose.prod.yml deploy@<VPS_IP>:/opt/dams/
scp -i dams_deploy prod.env         deploy@<VPS_IP>:/opt/dams/.env

# SSH in and start / update
ssh -i dams_deploy deploy@<VPS_IP>
cd /opt/dams
DAMS_TAG=manual docker compose -f compose.prod.yml pull
DAMS_TAG=manual docker compose -f compose.prod.yml up -d
docker compose -f compose.prod.yml ps
```

`nginx` from Part 1 already proxies to the containers — nothing else to change.

### 2B — SFTP the build artifacts, no Docker

Build on your laptop:

```bash
# backend jar (needs JDK 21 + Maven locally)
cd backend && mvn -q package -DskipTests           # -> target/dams-*.jar
# frontend static bundle — VITE_API_URL is baked in HERE
cd ../frontend && VITE_API_URL=https://<DOMAIN> npm run build   # -> dist/
```

SFTP:

```bash
scp -i dams_deploy backend/target/dams-*.jar deploy@<VPS_IP>:/opt/dams/app.jar
scp -i dams_deploy -r frontend/dist          deploy@<VPS_IP>:/var/www/dams
```

On the VPS (as root), one-time:

```bash
# 1. Java 21 runtime
apt-get update -qq && apt-get install -y openjdk-21-jre-headless

# 2. env file for systemd (KEY=value, no quotes, no 'export')
cat > /opt/dams/app.env <<'EOF'
SPRING_PROFILES_ACTIVE=prod
SPRING_DATASOURCE_URL=jdbc:postgresql://<host>.neon.tech/dams?sslmode=require
SPRING_DATASOURCE_USERNAME=dams_owner
SPRING_DATASOURCE_PASSWORD=...
JWT_SECRET=...
APP_BASE_URL=https://<DOMAIN>
CORS_ALLOWED_ORIGINS=https://<DOMAIN>
DAMS_STORAGE_PROVIDER=r2
R2_ENDPOINT=https://<acct>.r2.cloudflarestorage.com
R2_ACCESS_KEY_ID=...
R2_SECRET_ACCESS_KEY=...
R2_BUCKET=dams
EOF
chmod 600 /opt/dams/app.env

# 3. systemd service
cat > /etc/systemd/system/dams.service <<'EOF'
[Unit]
Description=DAMS backend
After=network-online.target
Wants=network-online.target

[Service]
User=deploy
WorkingDirectory=/opt/dams
EnvironmentFile=/opt/dams/app.env
ExecStart=/usr/bin/java -jar /opt/dams/app.jar
SuccessExitStatus=143
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable --now dams
```

Replace the nginx server block with the **static + proxy** variant (edit
`/etc/nginx/sites-available/dams`):

```nginx
server {
    listen 80;
    server_name <DOMAIN>;
    client_max_body_size 15m;

    root /var/www/dams;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;      # SPA fallback
    }
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 60s;
    }
}
```

```bash
nginx -t && systemctl reload nginx
certbot --nginx -d <DOMAIN>        # if not already done
```

Each subsequent release: rebuild locally → SFTP the new jar + `dist/` → `sudo systemctl restart dams`.
**Keep the previous jar** (`app.jar.bak`) so you can swap it back.

### 2C — Registry-free Docker (no GitHub at all)

```bash
# laptop
docker build -t dams-backend:local ./backend
docker build --build-arg VITE_API_URL=https://<DOMAIN> -t dams-frontend:local ./frontend
docker save dams-backend:local dams-frontend:local | gzip > dams-images.tar.gz

# SFTP: dams-images.tar.gz  +  compose.prod.yml (change the two image: lines to
#       dams-backend:local / dams-frontend:local and delete the ${IMAGE_PREFIX} bits)  +  .env

# VPS
gunzip -c dams-images.tar.gz | docker load
cd /opt/dams && docker compose -f compose.prod.yml up -d
```

Fully offline from GitHub, but every deploy is a ~300 MB upload and there's no rollback unless
you keep old tarballs.

---

## Rollback

| Method | How |
|---|---|
| **1 (auto)** | Happens by itself when the new image fails its health-check — the deploy job relaunches the previously-running `sha-` tag and marks the run red. |
| **1 / 2A (manual)** | On the VPS: `cd /opt/dams && DAMS_TAG=sha-<oldcommit> docker compose -f compose.prod.yml pull && DAMS_TAG=sha-<oldcommit> docker compose -f compose.prod.yml up -d`. Tags: repo → **Packages**. |
| **2B** | `cp /opt/dams/app.jar.bak /opt/dams/app.jar && systemctl restart dams` (and re-SFTP the old `dist/` if the frontend changed). |
| **2C** | `docker load` an older `dams-images.tar.gz`, `docker compose up -d`. |

---

## Operations

```bash
cd /opt/dams
docker compose -f compose.prod.yml ps
docker compose -f compose.prod.yml logs -f backend
docker compose -f compose.prod.yml logs -f frontend
# 2B:
journalctl -u dams -f
```

- **Change config:** edit `/opt/dams/.env` (or `app.env`), then `docker compose ... up -d`
  (or `systemctl restart dams`).
- **DB migrations:** Flyway runs on backend startup, so a deploy that changes schema applies it
  automatically. **Snapshot the Neon branch before a risky migration.**
- **Disk:** successful Method-1 deploys run `docker image prune -f`. Also set a GHCR retention
  policy (repo → Packages → package → Settings) to keep e.g. the last 20 versions.
- `deploy/bootstrap-vps.sh` needs its exec bit in git:
  `git update-index --chmod=+x deploy/bootstrap-vps.sh`.
- Attachment size: `client_max_body_size 15m` in nginx pairs with the 10 MB/file app limit —
  change both together.
