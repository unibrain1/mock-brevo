---
name: add-endpoint
description: "Add a new Brevo endpoint to mock-brevo. Use when you implement a new /v3 route."
---

# Add a Brevo endpoint

Steps to add a new Brevo endpoint: (1) add a DTO record in `brevo/dto/` with `@JsonIgnoreProperties(ignoreUnknown = true)` on request records (Brevo payloads often have optional fields we don't model); (2) make the controller method read `CurrentAccount.require()` first; (3) scope every query by account; (4) match Brevo's JSON field names exactly — the PHP SDK deserializes strictly; (5) add a `capture(...)` for it in `ApiShapeSnapshotTest` and record its snapshot with `./mvnw test -Dtest=ApiShapeSnapshotTest -Dsnapshots.update=true`. A failing snapshot means the JSON contract changed: fix the code, or regenerate only if the change is intended and review the diff in `src/test/resources/api-shapes/`.
