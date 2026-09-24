# Brevo API — Implementation priorities for mock-brevo

Prioritized list of the endpoints to mock so that Enoria (`/home/adu/git/enoria`) can run against this local server.

## Context

- **Enoria-side client:** official SDK `getbrevo/brevo-php ^4.0` (Guzzle), API key stored encrypted in `Entite.brevoApiKey` / `EntiteLiee.brevoApiKey`.
- **Real base URL:** `https://api.brevo.com/v3`
- **Auth:** header `api-key: <KEY>`. **One account = one API key.** On every incoming request:
  - If the API key exists in the database → the request is attached to the matching account.
  - Otherwise → a new account is **created automatically** (with default values: `firstName`, `lastName`, `email`, `plan[]` generated from the key) and attached to the request.
  - Only a missing/blank key must return **401**. Any other key is valid and provisions its account on the fly.
  - All data (contacts, lists, campaigns, sent emails, templates, folders, senders) is **scoped per account** — nothing leaks between API keys.
- **Webhooks:** Enoria receives callbacks on `POST /callback/brevomail/{key}` (key = `TOKEN_CALLBACK_MAIL`). The mock must be able to **emit** these webhooks to a configurable URL to simulate `delivered`, `opened`, `click`, `hard_bounce`, `soft_bounce`, `complaint`, `invalid_email`, `blocked`, `error`, `unsubscribed`.

All paths below are prefixed with `/v3`. Responses must match Brevo's JSON field names **exactly** (the SDK deserializes strictly).

---

## P0 — Blockers (without them, Enoria crashes at startup)

| # | Method | Endpoint | SDK | Called from | Notes |
|---|---------|----------|-----|---------------|-------|
| 1 | `GET` | `/account` | `AccountApi::getAccount` | `ApiBrevoService.php:75` (key validation), `:322` (dashboard) | Must return `firstName`, `lastName`, `email`, `plan[]` with `type` and `credits` for the account attached to the key. If the key is missing/blank → **401**. If this key has never been seen before, the account is created on the fly (see § Context) and then returned. |
| 2 | `POST` | `/smtp/email` | `TransactionalEmailsApi::sendTransacEmail` | `ApiBrevoService.php:224` (bulk sends from ToolsController), `:664` (donation receipts) | **Most-called endpoint.** Accept `to[]`, `sender`, `subject`, `htmlContent`, `cc`, `bcc`, `attachment`, `replyTo`, `templateId`, `params`. Return `{ "messageId": "<uuid>" }`. Persist the rendered payload for inspection. |
| 3 | `GET` | `/senders` | `AccountApi::getSenders` | `ApiBrevoService.php:348` + many dropdowns | Returns `senders[]` with `name`, `email`, `active` (bool). Enoria filters on `active=true`. |

## P1 — Contact and list management (newsletter flow)

| # | Method | Endpoint | SDK | Called from | Notes |
|---|---------|----------|-----|---------------|-------|
| 4 | `GET` | `/contacts/lists` | `ContactsApi::getLists` | `ApiBrevoService.php:296` | Return `lists[]` with `id`, `name`, `uniqueSubscribers`. Supports `limit`/`offset`. |
| 5 | `POST` | `/contacts/import` | `ContactsApi::importContacts` | `ApiBrevoService.php:371`, `CampagneCommunicationService.php:435` | Key payload fields: `fileBody` (CSV `EMAIL,PRENOM,NOM`), `listIds[]`, `updateExistingContacts=true`. Return `{ "processId": <int> }`. In the mock: parse the CSV, create the contacts synchronously, then respond OK. |
| 6 | `GET` | `/contacts/lists/{listId}/contacts` | `ContactsApi::getContactsFromList` | `ApiBrevoService.php:421` (sync loop in batches of 300) | `limit`/`offset` pagination. Response: `contacts[]` with `email`, `emailBlacklisted`. |
| 7 | `DELETE` | `/contacts/lists/{listId}/contacts` | `ContactsApi::removeContactFromList` | `ApiBrevoService.php:387` (user unsubscribe) | Body: `{ "emails": [...] }`. |
| 8 | `PUT` | `/contacts/{email}` | `ContactsApi::updateContact` | `ApiBrevoService.php:405` (blacklist toggle) | Payload: `emailBlacklisted` (bool), `listIds[]`. |

## P2 — Email campaigns (dashboard + manual sends)

| # | Method | Endpoint | SDK | Called from | Notes |
|---|---------|----------|-----|---------------|-------|
| 9 | `GET` | `/emailCampaigns` | `EmailCampaignsApi::getEmailCampaigns` | `ApiBrevoService.php:257` | Query: `type=classic`, `limit=100`, `offset=0`. Enoria reads `campaigns[].id/name/subject/status/sentDate/sender.email` and `statistics.campaignStats[].delivered`. |
| 10 | `POST` | `/emailCampaigns` | `EmailCampaignsApi::createEmailCampaign` | `ApiBrevoService.php:590`, `CampagneCommunicationService.php` | Payload: `name`, `sender{name,email}`, `templateId`, `subject`, `replyTo`, `recipients.listIds[]`, `inlineImageActivation`, `mirrorActive`, `utmCampaign`, `params` (template variables, e.g. `URL_DON`). |
| 11 | `POST` | `/emailCampaigns/{id}/sendNow` | `EmailCampaignsApi::sendEmailCampaignNow` | `ApiBrevoService.php:621` | No body. Return 204. Must (asynchronously) trigger `delivered` webhooks to Enoria if configured. |

## P3 — Templates & folders (secondary UI)

| # | Method | Endpoint | SDK | Called from | Notes |
|---|---------|----------|-----|---------------|-------|
| 12 | `GET` | `/smtp/templates` | `TransactionalEmailsApi::getSmtpTemplates` | `ApiBrevoService.php:483` | Query: `templateStatus=true`, `limit=50`. Response: `templates[]` with at least `id`, `name`. |
| 13 | `GET` | `/contacts/folders` | `ContactsApi::getFolders` | `ApiBrevoService.php:512` | Query: `limit=20`. Response: `folders[]` with `id`, `name`. |
| 14 | `POST` | `/contacts/folders` | `ContactsApi::createFolder` | `ApiBrevoService.php:527` (creates "Campagnes de communication Enoria" if missing) | Payload: `{ "name": "..." }`. Response: `{ "id": <int> }`. |

## P4 — Mock admin routes (outside the Brevo API)

These routes are **not** Brevo endpoints — they are used to inspect/drive the mock from tests or a browser. Suggested prefix: `/mock-*` or `/_mock/*` to avoid any collision with a possible future Brevo endpoint.

### `GET /mock-status` — List of provisioned accounts

Returns the list of API keys seen so far and, for each one, a summary of the associated account. No auth required (debug/dev endpoint).

Proposed response format:
```json
{
  "accounts": [
    {
      "apiKey": "xkeysib-abc...",
      "apiKeyPreview": "xkeysib-abc…1234",
      "createdAt": "2026-04-24T10:15:00Z",
      "lastSeenAt": "2026-04-24T11:02:13Z",
      "account": { "firstName": "...", "lastName": "...", "email": "..." },
      "counters": {
        "emailsSent": 42,
        "contacts": 1530,
        "lists": 3,
        "campaigns": 1,
        "templates": 2,
        "folders": 1,
        "senders": 2
      }
    }
  ]
}
```

To avoid leaking plaintext keys into logs or screenshots, expose `apiKeyPreview` (first + last characters) next to the full key, and consider a `MOCK_STATUS_REVEAL_KEYS=false` flag that hides `apiKey` in shared test environments.

### `POST /mock-webhooks/fire` — Emit a webhook to Enoria

For testing incident scoring on the Enoria side:

- Body: `{ "url": "http://enoria.local/callback/brevomail/<TOKEN>", "event": "...", "email": "...", "reason": "..." }`
- Events handled by `CallbackController.php:25`: `delivered`, `unique_opened`, `opened`, `click` (positive) · `soft_bounce`, `hard_bounce`, `complaint`, `invalid_email`, `blocked`, `error`, `unsubscribed` (negative)
- Enoria security: the `TOKEN_CALLBACK_MAIL` key is passed as a path param/query string; the mock forwards it exactly as given.

Optional: automatically fire `delivered` a few seconds after each `POST /smtp/email` if a default webhook URL is configured for the account.

### `GET /mock-status/accounts/{apiKey}/emails` — Inspect sent emails

Lists the emails persisted for a given account (useful for test assertions). Query: `limit`, `offset`. Response: full payload captured at send time + the returned `messageId`.

---

## Out of scope (for now)

Enoria **does not use**: SMS, WhatsApp, Conversations, Companies, Deals, Inbound Parsing, contact attribute scores, sub-accounts. Don't implement them until a real call shows up.

SMTP transport (`brevo+smtp://` via Symfony Mailer): Enoria supports it but currently uses a local SMTP server — **not a priority** for the mock.

## Recommended build order

1. Spring Boot skeleton + H2 file + Dockerfile
2. **`Account` model + auth filter**: resolves the request's API key, creates the account if unknown, exposes the current account to controllers (e.g. via `HandlerInterceptor` + request attribute or `@ModelAttribute`). Every domain entity (contact, list, email, campaign…) carries an `account_id` FK.
3. P0 #1 `GET /account` + `GET /mock-status` → accounts already start showing up
4. P0 #2 `POST /smtp/email` + persistence + `GET /mock-status/accounts/{apiKey}/emails` → Enoria can send, tests can assert
5. P0 #3 `GET /senders`
6. P1 (5 endpoints) → newsletter flow working
7. P2 (3 endpoints) → campaigns
8. P3 (3 endpoints) → UI polish
9. P4 `POST /mock-webhooks/fire` → end-to-end tests of incident scoring
