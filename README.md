# mock-brevo

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Java 25](https://img.shields.io/badge/Java-25-orange.svg)](https://adoptium.net/)
[![Spring Boot 4.1](https://img.shields.io/badge/Spring%20Boot-4.1-green.svg)](https://spring.io/projects/spring-boot)

> **English fork.** This repository is a fork of [c0boleis/mock-brevo](https://github.com/c0boleis/mock-brevo), maintained at [unibrain1/mock-brevo](https://github.com/unibrain1/mock-brevo) to provide an English admin UI (with a FR/EN toggle), English sample data and English docs. The translation has been offered upstream in [c0boleis/mock-brevo#10](https://github.com/c0boleis/mock-brevo/pull/10). Since its features have diverged from upstream, the fork uses its own [semantic version](#fork-versions) from 1.1.0 on, and each release records which upstream version it's based on. Current release: `ghcr.io/unibrain1/mock-brevo:1.1.0` (also `latest`). For the original project, issues about the mock itself, and the upstream Docker Hub image, see the upstream repository.

Local mock of the [Brevo](https://developers.brevo.com/) (ex-Sendinblue) transactional API, designed to stand in for `api.brevo.com` during development and integration testing. Clients swap the base URL; payloads stay identical.

> ⚠️ **Disclaimer.** This project is **not affiliated with, endorsed by, or sponsored by Brevo SAS**. "Brevo" and "Sendinblue" are trademarks of Brevo SAS. This is an independent community tool for local development and integration testing. No Brevo source code is redistributed — every endpoint is re-implemented from the publicly documented API at <https://developers.brevo.com/>.

## What it does

- Accepts Brevo-shaped HTTP requests on `/v3/**` and returns responses matching the real API contract (verified against the official [`getbrevo/brevo-php`](https://github.com/getbrevo/brevo-php) SDK).
- **Auto-provisions an account per API key** — any non-empty `api-key` header works, each gets its own isolated data (contacts, lists, campaigns, sent emails, …).
- **Persists every sent email** (`POST /v3/smtp/email`) in file-based H2 so you can assert on payloads or inspect them after a test run.
- **Optional SMTP forward** — relays captured emails to a real SMTP catcher (Mailpit, MailHog, …) for visual inspection.
- **Admin web UI** at `http://localhost:8080/` with:
  - live list of accounts + counters
  - last 500 REST calls (request/response headers + body, Monaco editor with JSON folding, copy-to-clipboard)
  - light/dark theme toggle
  - French/English toggle (see [Languages](#languages))
  - inline form to create faker campaigns per account
  - direct links to the matching Brevo documentation page for each logged endpoint
- **Webhook simulation** — outbound `delivered`, `hard_bounce`, `opened`, `click`, etc. to a client-controlled URL. See [Webhook simulation](#webhook-simulation).
- **Transactional event report** (`GET /v3/smtp/statistics/events`) — every send (`requests`) and every simulated webhook event, with Brevo's filters (`startDate`/`endDate` or `days`, `email`, `event`, `tags`, `messageId`, `templateId`), paging and `sort`.

See [`ENDPOINTS.md`](ENDPOINTS.md) for the full endpoint coverage matrix.

## Quick start

### Docker (pre-built image)

```bash
docker run --rm -p 8080:8080 ghcr.io/unibrain1/mock-brevo:latest
```

Then point your Brevo client at mock-brevo instead of `api.brevo.com`. An SDK client uses `http://localhost:8080/v3` as its host. A raw HTTP client replaces `https://api.brevo.com` with `http://localhost:8080`. Any `api-key` value works — the first use provisions the account on the fly.

### Docker Compose

```yaml
services:
  mock-brevo:
    image: ghcr.io/unibrain1/mock-brevo:latest
    ports: ["8080:8080"]
    volumes: ["brevo-data:/app/data"]
    environment:
      MOCK_SMTP_ENABLED: "true"
      MOCK_SMTP_HOST: mailpit   # name of your SMTP catcher service
      MOCK_SMTP_PORT: "1025"

volumes:
  brevo-data:
```

### From source

Requires JDK 25. Maven is wrapped (`./mvnw`) — no system install needed.

```bash
./mvnw spring-boot:run                    # dev profile, SMTP forward ON (localhost:1025)
./mvnw clean package -DskipTests          # → target/mock-brevo-<version>.jar
java -jar target/mock-brevo-*.jar         # run packaged jar (default profile, SMTP OFF)
```

## Pointing a client at mock-brevo

| Client | Setting |
|---|---|
| `getbrevo/brevo-php` SDK | `Configuration->setHost('http://localhost:8080/v3')` |
| Symfony Mailer | use `brevo+api://KEY@localhost:8080` (custom host requires patching the bridge) or route via SMTP with `MOCK_SMTP_ENABLED=true` |
| `curl` / Postman | replace `https://api.brevo.com` with `http://localhost:8080` |

### Calling a host-running dev server from a Docker container

When mock-brevo runs directly on the host (`./mvnw spring-boot:run` listening on `localhost:8080`) but the client app runs in Docker, the client container can't resolve the host's `localhost`. Use the Docker-managed alias `host.docker.internal` instead.

The client container needs the alias mapped to the host gateway. Add this to your `docker-compose.yml`:

```yaml
services:
  yourapp:
    extra_hosts:
      - "host.docker.internal:host-gateway"
```

Then point the backend at `http://host.docker.internal:8080/v3`. This hostname works only **from inside Docker**. The browser on the host still uses `http://localhost:8080`.

If you run mock-brevo as a service in the same Docker Compose project (the more common setup), the backend uses the service name and internal port instead: `http://mock-brevo:8080/v3`. Publish a port (or route it through a reverse proxy) for browser access.

So an app that runs in Docker usually needs two base URLs:

- **API base URL** for backend calls: `http://host.docker.internal:8080/v3` or `http://mock-brevo:8080/v3`. This is the SDK host form. If your client adds `/v3` to its paths, drop `/v3` here.
- **Web UI base URL** for links that a browser opens: `http://localhost:8080` (or the published port).

If your app links to Brevo's web UI, point that link base at mock-brevo's UI. mock-brevo serves Brevo's deep-link paths `/marketing-campaign/edit/{id}` and `/contact/list/id/{id}`, and shows the matching campaign or list.

## Configuration

All settings are environment variables, sensible defaults for dev:

| Variable | Default | Description |
|---|---|---|
| `MOCK_BREVO_DB_PATH` | `./data/brevo` (host) / `/app/data/brevo` (Docker) | H2 file location |
| `MOCK_STATUS_REVEAL_KEYS` | `true` | Expose raw API keys in `/mock-status` (disable in shared environments) |
| `MOCK_DEFAULT_WEBHOOK_URL` | — | URL to fire outbound webhooks at |
| `MOCK_AUTO_FIRE_DELIVERED` | `false` | Auto-fire `delivered` webhook after each `POST /v3/smtp/email` |
| `MOCK_WEBHOOK_TOKEN` | — | Default bearer token sent as `Authorization: Bearer <token>` on outbound webhooks |
| `MOCK_SMTP_ENABLED` | `false` | Relay captured emails to an SMTP server |
| `MOCK_SMTP_HOST` | `localhost` (dev profile) / `mailcatcher` (Docker) | SMTP host |
| `MOCK_SMTP_PORT` | `1025` | SMTP port (Mailpit default) |
| `MOCK_SMTP_USERNAME` / `MOCK_SMTP_PASSWORD` | — | Optional auth |
| `MOCK_SMTP_STARTTLS` | `false` | STARTTLS toggle |
| `SERVER_PORT` | `8080` | HTTP port |

## Webhook simulation

`POST /mock-webhooks/fire` sends one Brevo-style webhook to `url`:

```bash
curl -X POST http://localhost:8080/mock-webhooks/fire -H 'Content-Type: application/json' -d '{
  "url": "http://host.docker.internal:8000/webhooks/brevo",
  "event": "hard_bounce",
  "email": "to@example.com",
  "messageId": "<…@mock-brevo.local>",
  "token": "my-webhook-token"
}'
```

| Field | Required | Effect |
|---|---|---|
| `url`, `event`, `email` | yes | Target, Brevo event name (`delivered`, `hard_bounce`, `soft_bounce`, `spam`, `invalid_email`, `opened`, `click`, `unsubscribed`, …) and recipient |
| `messageId` | no | A `messageId` from `POST /v3/smtp/email`. The webhook then copies that email's `tags`, `subject`, `sender_email`, `template_id` and `X-Mailin-custom` header, and the event is recorded under its account. Without it, the webhook gets a generated `message-id`. |
| `token` | no | Sent as `Authorization: Bearer <token>`. Default: `MOCK_WEBHOOK_TOKEN`. Never logged. |
| `tags` | no | Replaces the tags copied from the email (`tag` is the first one) |
| `apiKey` | no | Account to record the event under when there is no known `messageId`. Like `/v3`, an unknown key provisions a new account. If `messageId` belongs to another account, the response is `400`. |
| `reason`, `link` | no | Bounce reason (a realistic default per event), clicked URL for `click` |

The response is `202` with `"recorded": true` if the event was stored for the event report and block list.

The payload follows real Brevo deliveries. Every event has `event`, `email`, `id`, `date` (`YYYY-MM-DD HH:MM:SS`, UTC in the mock), `ts`, `ts_event`, `ts_epoch` (ms), `message-id`, `subject`, `tags` and `template_id`. Per event:

- `delivered`, `hard_bounce`, `soft_bounce` and others: add `tag`, `sender_email`, `uuid`, `reason`, `sending_ip` and, if the email set it, `X-Mailin-custom`.
- `spam`: the same, without `reason`, `sending_ip` and `template_id`.
- `invalid_email`: only the common fields.
- `opened`, `unique_opened`, `click`, `unsubscribed`, `proxy_open`: the `delivered` set plus `contact_id`, `device_used`, `user_agent`, `mirror_link`, and `link` for `click`.

Webhooks go out as HTTP/1.1 with a `Content-Length` and `User-Agent: Brevo-webhook/2.0 (mock-brevo)`. Delivery is best-effort: a failed POST is logged, not retried.

## Admin routes (not part of the Brevo API)

| Method | Path | Description |
|---|---|---|
| `GET` | `/` | Web UI |
| `GET` | `/mock-status` | All provisioned accounts + counters |
| `GET` | `/mock-status/version` | Build version and upstream base (shown in the UI header) |
| `GET` | `/mock-status/requests` | Last N REST calls (metadata) |
| `GET` | `/mock-status/requests/{id}` | Full call detail (headers + body) |
| `GET` | `/mock-status/accounts/{apiKey}/emails` | Captured emails for a tenant |
| `POST` | `/mock-status/accounts/{apiKey}/campaigns` | Create a faker campaign |
| `POST` | `/mock-webhooks/fire` | Trigger an outbound Brevo webhook |

## Languages

The admin web UI is available in **French** and **English**. The initial language follows the browser (`navigator.language` starting with `fr` → French, anything else → English). The `FR` / `EN` button in the header switches languages, and the choice is saved in `localStorage` (`mock-brevo-lang`). All UI strings live in [`src/main/resources/static/js/i18n.js`](src/main/resources/static/js/i18n.js).

> **AI-assisted translation.** The English translation (UI strings, faker sample data, and [`ENDPOINTS.md`](ENDPOINTS.md); the French original is kept as [`ENDPOINTS.fr.md`](ENDPOINTS.fr.md)) was done with [Claude](https://www.anthropic.com/claude), Anthropic's AI assistant. Corrections from native speakers of either language are welcome: open an issue or a PR against `i18n.js`.

## Fork versions

This fork has its own [Semantic Versioning](https://semver.org/): new features bump the minor version, fixes the patch version, breaking API changes the major version. Its version numbers are independent of upstream's, so fork `1.1.0` is **not** upstream `1.1.0`. Each release records the upstream version it's based on in the [CHANGELOG](CHANGELOG.md) and in the image label `io.github.unibrain1.upstream-version`.

Images are published as `ghcr.io/unibrain1/mock-brevo:<version>`, plus `<major>.<minor>`, `<major>` and `latest`. Pin a full version in anything automated.

| Fork version | Based on upstream | Notes |
|---|---|---|
| `1.0.0-en.1` | 1.0.0 | English UI and sample data (older `<upstream>-en.<n>` scheme) |
| `1.0.0-en.2` | 1.0.0 | Spring Boot 4.1, Java 25 |
| `1.0.0-en.3` | 1.0.0 | First release tagged `latest` |
| `1.1.0` | 1.0.0 | First fork SemVer release. Version in the UI header, FR/EN toggle fix after upgrades (no stale cached JS) |
| `1.2.0` *(unreleased)* | 1.0.0 | Planned: ElanRegistry compatibility (event report, blocked contacts, realistic webhooks) |

## Contributing

- Open an issue describing the endpoint shape you need — bonus points for a link to the Brevo docs page and a sample SDK call.
- PRs welcome. Keep the mock behavior as close as possible to the real API response contract (field names, types, HTTP codes).

## Licence

[MIT](LICENSE) — use freely, attribute if you wish.
