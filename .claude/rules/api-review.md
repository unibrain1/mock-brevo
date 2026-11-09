# Review checks for mock-brevo

Use these checks when you review a change in this repo. The CI review and
`@claude` read this file. The severity names are the review headings. The
admin UI checks are in `admin-ui.md`.

Skip `package-lock.json`, `node_modules/**` and `mvnw.cmd`. CI runs the
build, the tests and the linters. Do not report what the compiler,
SpotBugs or a linter reports.

## Brevo API contract

- Blocking: a JSON field name, presence or type differs from the Brevo
  API. The Brevo PHP SDK deserializes strictly.
- Blocking: a file under `src/test/resources/api-shapes/` changes, and the
  PR does not say why. A shape change is a change to the JSON contract.
- Important: a request DTO record has no
  `@JsonIgnoreProperties(ignoreUnknown = true)`.
- Important: a new `/v3/**` endpoint has no `capture(...)` in
  `ApiShapeSnapshotTest`.

## Tenant isolation

- Blocking: a `/v3/**` controller method does not call
  `CurrentAccount.require()` first, or a query is not scoped by account.
  A lookup by id that does not check the account leaks data from another
  api-key.
- Blocking: a response other than 401 for a missing or blank `api-key`,
  or a 401 for any other key. Any other key provisions an account.

## Persistence

- Blocking: a method that walks lazy JPA relations uses
  `jakarta.transaction.Transactional`. Use
  `org.springframework.transaction.annotation.Transactional`
  (`open-in-view` is off).
- Blocking: the H2 URL gets `AUTO_SERVER=TRUE`.
- Blocking: a schema or entity change cannot open an H2 file from the
  last release (`ddl-auto: update`).

## Security and safety

- Blocking: a raw api-key goes into a log. The request log masks it.
- Blocking: a change sends real email. The only outbound mail path is the
  opt-in `SmtpForwarder` (off by default), for a local catcher.

## Tests

- Important: new logic (an endpoint, account scoping, parsing, UI
  behavior) has no test: MockMvc in `BrevoApiTest`, a shape snapshot, or
  Playwright in `tests/ui/`.
