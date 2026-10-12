# DevTrack — Concept Walkthrough

## 1. Layers (request → response)
```
HTTP → JwtAuthFilter → Controller → Service → Repository → MySQL
                          │            │
                       DTO in/out   business rules + @Transactional
```
- **Controller**: HTTP only. Validates DTO (`@Valid`), calls service, returns DTO.
- **Service**: all rules (who may do what), transaction boundary.
- **Repository**: DB access. Spring Data generates SQL from method names / `@Query`.
- **Entity ≠ DTO**: entity mirrors table, DTO mirrors API. Keeps password hash out of JSON, lets DB and API change separately.

## 2. JWT flow
1. `register` → BCrypt hash stored.
2. `login` → `AuthenticationManager` compares password with hash → `JwtService` signs token (subject = email, exp = 1h).
3. Client sends `Authorization: Bearer <token>`.
4. `JwtAuthFilter` verifies signature + expiry, loads user from DB, puts it in `SecurityContext`.
5. Controllers read user via `@AuthenticationPrincipal`.
6. Bad/missing token → anonymous → protected route returns **401**. Wrong role → **403**.

Why STATELESS + CSRF off: no session cookie exists, so cross-site cookie forgery does not apply.

## 3. Authorization (two levels)
- **URL level** (`SecurityConfig`): `/api/admin/**` needs `ROLE_ADMIN`.
- **Data level** (`ProjectService.findProjectForUser`): even logged-in user only touches projects they own / belong to. Skipping this = IDOR bug (user A reads user B's project by guessing id).

| Action | Owner | Member | Admin |
|---|---|---|---|
| View project / tasks | ✔ | ✔ | ✔ |
| Create / edit task | ✔ | ✔ | ✔ |
| Edit / delete project, manage members, delete task | ✔ | ✘ | ✔ |

## 4. Data model
```
User 1──* Project (owner)       Project *──* User (members, join table project_members)
Project 1──* Task               Task *──1 User (assignee, nullable)
```
- `@ManyToOne(LAZY)`: load related row only when touched. Fast, but accessing it **outside a transaction** throws `LazyInitializationException`. That is why DTO mapping happens inside `@Transactional` service methods and `open-in-view: false`.
- `cascade=ALL + orphanRemoval` on `Project.tasks`: delete project → tasks gone.
- `@Transactional` update without `save()`: managed entity changes are flushed at commit (dirty checking).

## 5. Pagination / filter / sort
`Pageable` is built from `?page=&size=&sort=field,dir`. Optional filters use `(:status is null or t.status = :status)`. `max-page-size: 50` blocks abusive page sizes. Bad sort field → handler returns 400.

## 6. Errors
Services throw `ApiException(status, msg)`. `GlobalExceptionHandler` (`@RestControllerAdvice`) converts to one JSON shape: `{timestamp, status, message, fieldErrors}`.

## 7. Docker + AWS
- Multi-stage Dockerfile: Maven builds jar, slim JRE runs it as non-root.
- Compose: `db` healthcheck → `app` starts only after MySQL answers. DB port not published.
- Secrets via `.env` / env vars, never in code.
- EC2 v1 = one box running compose. v2 = RDS + ALB + CloudWatch.

## 8. Resume honesty checklist
Only claim what you ran. Before adding bullets: run it on EC2, hit it from Swagger, add at least a few tests, keep README links live. Be ready to explain: BCrypt, why stateless, LAZY vs EAGER, DTO vs entity, IDOR check.

## 9. Testing approach
- **Unit** (`JwtServiceTest`): plain JUnit, no Spring. Fast. Tests one class in isolation.
- **Integration** (`*IntegrationTest`): `@SpringBootTest` + `MockMvc` sends fake HTTP requests through the *real* filter chain, controller, service, JPA, real MySQL. Closest thing to production without a network.
- **What to assert**: status code + JSON shape + side effect (e.g. after remove-member, task assignee is null).
- **Security tests matter most**: stranger gets 403, no token 401, member cannot delete, signup cannot self-promote to ADMIN.
- **Why no `@Transactional` on tests**: it wraps the whole test in one transaction, hiding `LazyInitializationException` and commit-time bugs. Real commits per request = real behavior.
- **Testcontainers**: library starts a throwaway Docker container from test code. We run real MySQL 8.4 (same as `docker-compose.yml`), so SQL dialect, unique constraints, join tables, and JPQL behave like production. In-memory H2 is faster but can pass tests that MySQL would fail.
- **Singleton container**: `static` container + `static {}` start in `IntegrationTestBase`, so MySQL boots once per JVM, not once per test class. `@DynamicPropertySource` feeds the random port into `spring.datasource.*` at runtime.
- **Cost**: needs Docker running; first run downloads the image. Worth it for backend correctness.
