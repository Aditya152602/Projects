# DevTrack — Secure Developer Project & Task Management Backend

Java 17 · Spring Boot 3 · Spring Security + JWT · REST · MySQL · JPA/Hibernate · Docker · AWS EC2

## Run

**Docker (recommended)**
```bash
cp .env.example .env            # edit DB_PASSWORD, JWT_SECRET (openssl rand -base64 48), admin creds
docker compose up --build
```
**Local (needs MySQL on :3306, JDK 17, Maven)**
```bash
mvn spring-boot:run             # defaults: root/root, db "devtrack" auto-created
```
Swagger UI: http://localhost:8080/swagger-ui.html (click **Authorize**, paste JWT)

## Endpoints
| Method | Path | Notes |
|---|---|---|
| POST | `/api/auth/register` | public, always role USER |
| POST | `/api/auth/login` | returns JWT |
| GET | `/api/users/me` | current user |
| POST/GET | `/api/projects` | list = own + member projects, paged |
| GET/PUT/DELETE | `/api/projects/{id}` | PUT/DELETE owner or admin |
| POST | `/api/projects/{id}/members` | body `{"email": "..."}` owner/admin |
| DELETE | `/api/projects/{id}/members/{userId}` | owner/admin |
| POST/GET | `/api/projects/{id}/tasks` | GET filters: `status`, `priority`, `page`, `size`, `sort` |
| GET | `/api/tasks` | tasks assigned to me |
| GET/PUT/DELETE | `/api/tasks/{id}` | DELETE owner/admin |
| GET | `/api/admin/users` | ADMIN only |

## Quick test
```bash
curl -X POST localhost:8080/api/auth/register -H 'Content-Type: application/json' \
  -d '{"name":"Aditya","email":"a@x.com","password":"password123"}'

TOKEN=$(curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"a@x.com","password":"password123"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["accessToken"])')

curl -X POST localhost:8080/api/projects -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"name":"First","description":"demo"}'

curl -X POST localhost:8080/api/projects/1/tasks -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"title":"Write docs","priority":"HIGH"}'

curl "localhost:8080/api/projects/1/tasks?status=TODO&sort=createdAt,desc&page=0&size=5" \
  -H "Authorization: Bearer $TOKEN"
```

## Postman
Import `postman/DevTrack.postman_collection.json`. Variables: `baseUrl` (default `http://localhost:8080`), `email`, `password`.
Run folders in order: **1 Auth → 2 Projects → 3 Tasks → 4 Cleanup**.
- *Login* saves `{{token}}`; *Create project/task* save `{{projectId}}`/`{{taskId}}` automatically. Every request has test assertions (status codes), so Collection Runner shows pass/fail.
- Member requests: register a second user, set `memberEmail` and `memberUserId` collection variables first.
- Admin request: log in with `ADMIN_EMAIL`/`ADMIN_PASSWORD` from `.env`, then run it (normal user gets 403).

## Tests
```bash
mvn test        # 52 tests. Needs DOCKER RUNNING: integration tests start a real MySQL 8.4 via Testcontainers
```
| Class | Type | Covers |
|---|---|---|
| `JwtServiceTest` | unit | token round-trip, forged signature, expired, garbage, weak secret |
| `AuthIntegrationTest` | MockMvc | register/login, 409 duplicate, 400 field errors, 401 bad creds/token, role escalation ignored |
| `ProjectIntegrationTest` | MockMvc | owner/member/stranger access (403), paging, member add/remove, cascade delete, task unassign |
| `TaskIntegrationTest` | MockMvc | defaults, validation, status/priority filters, pagination, sorting, assignee rule, permissions |
| `AdminIntegrationTest` | MockMvc | `/api/admin/**` 401/403/200, admin override on projects |
| `PersistenceIntegrationTest` | repository | DB-level unique email, `member of` JPQL, null-filter query, bulk unassign on real MySQL |

Integration tests boot the full app (real security filter chain, services, JPA) against a **real MySQL 8.4 container** (same image as `docker-compose.yml`). One container is shared by all test classes and removed when the JVM exits. First run pulls the image (slow); later runs are fast. Tests are intentionally **not** `@Transactional`, so lazy-loading and transaction bugs surface. Isolation = unique emails per test.

**Docker troubleshooting:** error `client version 1.32 is too old` means Testcontainers is too old for Docker Engine 29+. `pom.xml` pins `testcontainers.version` to 1.21.3 for this; if it persists, bump the property to the latest 1.21.x/2.x, or (temporary) set `"min-api-version": "1.32"` in the Docker daemon config.

## Deploy on AWS EC2 (v1)
1. Launch **t3.small** (Amazon Linux 2023). Security group: 22 from *your IP only*, 8080 from anywhere. **Never open 3306.**
2. Install Docker + Compose:
```bash
sudo dnf install -y docker git
sudo systemctl enable --now docker
sudo usermod -aG docker ec2-user      # log out/in after this
sudo mkdir -p /usr/local/lib/docker/cli-plugins
sudo curl -SL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
```
3. `git clone <your repo> && cd devtrack && cp .env.example .env` → edit secrets → `docker compose up -d --build`
4. Open `http://<EC2-public-IP>:8080/swagger-ui.html`.

v2 upgrade path: RDS MySQL instead of the DB container, ALB + HTTPS (ACM), CloudWatch logs, GitHub Actions CI/CD, Flyway migrations.

## Known limits (be honest in interviews)
- `ddl-auto: update` is dev-convenience; production should use Flyway migrations.
- No refresh tokens / logout (JWT is stateless, expires in 1h).
- Integration tests need Docker; no unit-only fast profile yet (`JwtServiceTest` is the only Docker-free test).
