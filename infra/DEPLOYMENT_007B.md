# FactoryFlow — Vercel + Oracle VM deployment checklist (not deployed)

## Registered domain and DNS

The user showed the management page for **factoryflow.kro.kr**. A record is selected there but its IP field is EMPTY. Do not save any invented IP.

1. In the Vercel FactoryFlow project go to **Settings > Domains > Add factoryflow.kro.kr**.
2. Read the **project-specific apex A record** recommended by Vercel; in 내도메인.한국 → 고급설정(DNS), put that IPv4 in the ROOT A record (leave the name prefix blank).
3. After acquiring an Oracle VM, add an A record with the prefix **api** and the real Oracle public IPv4. No placeholder IP.
4. Do not enable 웹포워딩 or 단일페이지 HTML. Follow any TXT verification that Vercel explicitly requests.
5. Verify DNS propagation and HTTPS. Domain management screenshot alone does NOT prove successful DNS propagation.

## Vercel

- Link GitHub repository `jeon950523/factoryflow`.
- **Root Directory = frontend**, Framework Preset = Vite, build `npm run build`, output `dist`.
- Keep `VITE_API_BASE_URL` blank; the SPA uses relative paths with same-origin credentials.
- `frontend/vercel.json` rewrites `/api/:path*` to `https://api.factoryflow.kro.kr/api/:path*` and unknown SPA routes to index.html.
- API HTTPS must work **before** publishing the frontend domain.

## Oracle VM (not executed)

- Install Docker Compose and host Caddy; restrict VM/NSG firewalls to public 80/443, SSH 22 from trusted administrator IP only.
- MySQL 3306 is private Docker network; API 8080 binds to host loopback only.
- Copy `infra/.env.prod.example` to `infra/.env.prod` on the VM, assign unique strong secrets, `chmod 600 infra/.env.prod`, never commit.
- Compose configuration check: `docker compose --env-file infra/.env.prod -f infra/compose.prod.yml config --quiet`.
- Start (after actual VM and DNS readiness): `docker compose --env-file infra/.env.prod -f infra/compose.prod.yml up -d --build`.
- Install `infra/Caddyfile` on the VM host and validate TLS; Caddy automatic certificate needs working api DNS and public 80/443.

## Backup / recovery

- Run `bash infra/scripts/backup-db.sh` on the VM; file is created under gitignored `infra/backups/` with private permissions.
- **SQL file is not encrypted**. Encrypt and copy it OFF the VM, rotate/expire safely, and test restore in an isolated database.
- Backup script success is not proof of recovery. No automated production restore is provided because restore is destructive.

## Release blocking tests

- Verify factoryflow.kro.kr HTTPS and same-origin API /api/v1/auth/csrf.
- Anonymous GET → 401; unauthenticated write without CSRF → 403; authenticated ADMIN write succeeds; VIEWER write → 403.
- Verify JSESSIONID cookie Secure / HttpOnly / SameSite=Lax and logout revocation using browser devtools.
- TASK-007A rate limiter currently uses getRemoteAddr: behind Caddy+Docker, users may share the same gateway IP. **Edge throttling / trusted proxy identity must be addressed before exposing login publicly.**
- Validate database and backend ports cannot be reached from outside, verify backup restore, VM restart, memory sizing, billing/usage alerts and admin recovery.
