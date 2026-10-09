---
name: springboot-production-engineering
description: Design, implement, refactor, review, and test production-grade Java Spring Boot backends. Use for Spring Boot architecture, feature work, integrations, security, reliability, maintainability, and verification; do not use for generic Java or non-Spring systems.
---

# Spring Boot Production Engineering

Engineer Spring Boot changes that fit the repository, remain operable in production, and are verified with evidence. Preserve the user's scope: a review or diagnosis does not authorize implementation, deployment, or unrelated cleanup.

## Establish the local contract

Before changing code:

1. Read repository instructions and the files directly relevant to the request.
2. Inspect the build descriptor and wrapper, configured Java and Spring Boot versions, package/module layout, representative neighboring code, tests, and relevant configuration.
3. Identify the established architecture and dependency direction. Preserve layered, modular, hexagonal, or other deliberate structures unless the task explicitly calls for changing them.
4. State a proportionate implementation plan and important assumptions. Resolve requirements from available evidence before asking questions.

Avoid broad repository or dependency scans when targeted inspection is enough. Do not silently upgrade frameworks, add dependencies, or introduce a new architectural style.

## Make design decisions pragmatically

- Keep responsibilities cohesive and dependencies directed inward toward business behavior.
- Apply SOLID, DRY, KISS, and YAGNI as decision aids, not quotas. Prefer clear duplication over a premature abstraction, then extract only when a stable concept is evident.
- Introduce interfaces at meaningful boundaries such as external systems, variable policies, or independently testable domain ports. Do not create an interface for every service by convention.
- Prefer small, coherent changes that match existing naming, packaging, formatting, exception handling, and API conventions.
- Keep domain and use-case decisions independent of HTTP, persistence, and vendor payload details where the project's architecture supports that separation.
- Record consequential tradeoffs and operational assumptions in the appropriate project documentation or configuration notes.

## Implement with Spring Boot conventions

- Use APIs and conventions supported by the project's actual Java and Spring Boot versions.
- Prefer constructor injection. Make required dependencies explicit and immutable.
- Bind structured settings with `@ConfigurationProperties`; validate required values and ranges. Externalize environment-specific values and secrets.
- Use records for genuinely immutable DTOs and value carriers when framework compatibility and project conventions permit. Do not force records onto mutable persistence models.
- Put explicit Bean Validation constraints at trust boundaries and use validation groups or custom validators only when simpler constraints cannot express the rule.
- Keep controllers focused on transport concerns: request parsing, validation, status codes, and delegation. Keep business rules in application/service code.
- Define stable error responses. Map expected failures deliberately and do not leak stack traces, internal exception messages, queries, or sensitive identifiers.
- Place transactions at explicit use-case or persistence boundaries. Keep them as short as practical, define read-only behavior where useful, and avoid holding database transactions open across remote calls.
- Review query count, paging, locking, uniqueness, and migration implications when persistence behavior changes. Do not treat in-memory state as durable storage.
- Choose an HTTP client appropriate to the existing stack and framework version. Configure connection and response timeouts, resource limits, authentication, serialization, and error mapping; do not introduce a reactive programming model solely to make one outbound call.

## Isolate external systems

- Put each third-party API behind a dedicated client, gateway, or port with application-owned inputs, outputs, and failure semantics. Keep vendor DTOs and authentication mechanics at that boundary.
- Categorize remote failures: invalid request, authentication/authorization, throttling, transient dependency failure, timeout, malformed response, and permanent failure. Map each intentionally.
- Retry only failures likely to recover. Bound attempts and elapsed time, use backoff and jitter where appropriate, honor server retry guidance, and avoid multiplying retries across layers.
- Confirm idempotency before retrying side effects. Use idempotency keys, deduplication, or durable state when the operation requires it.
- For webhooks, authenticate before processing. Preserve the exact raw request bytes when the provider's signature scheme requires them, use constant-time comparison where applicable, enforce freshness/replay defenses, and acknowledge only according to the provider contract.
- Never log credentials, authorization headers, signature material, full tokens, or sensitive customer payloads. Apply deliberate redaction and data minimization.

## Secure trust boundaries

- Use least privilege, deny-by-default rules where appropriate, and secure production defaults.
- Validate all untrusted input, including headers, identifiers, filenames, URLs, serialized payloads, and webhook metadata. Configure request and upload size limits appropriate to the endpoint.
- Apply authentication and authorization at the correct boundary; test object-level and action-level access, not only route access.
- Use transport encryption and suitable encryption or hashing for protected data. Rely on established libraries and platform facilities rather than custom cryptography.
- Consider injection, unsafe deserialization, SSRF, path traversal, mass assignment, excessive data exposure, and abuse/rate limits when relevant to the change.
- Keep secrets out of source, fixtures, logs, exception responses, and committed configuration. Document only required variable names and acquisition/rotation assumptions.

## Design for operation and failure

- Use SLF4J with parameterized or structured key-value logging. Choose levels deliberately and keep messages useful without duplicating stack traces.
- Accept or create a correlation identifier at the system boundary, validate its shape, include it in logs and safe error responses, and propagate it to asynchronous and outbound work.
- Distinguish recoverable, retryable, degraded, and terminal failures. Preserve causes internally while returning safe public errors.
- Expose health information suitable for the runtime. Separate liveness from readiness, and avoid expensive or misleading dependency checks.
- Add metrics or traces around important latency, throughput, retry, queue, and failure behavior when the project already has observability infrastructure or the risk justifies it.
- Use asynchronous processing only for a clear latency, throughput, isolation, or delivery requirement. Define ownership, backpressure, ordering, delivery semantics, retries, poison-message handling, shutdown behavior, and observability.
- Describe in-memory queues and caches accurately: they are process-local and lossy across restart unless backed by a durable system. Never present them as durable persistence.

## Test behavior at the right level

- Unit-test business decisions, edge cases, and failure classification without booting Spring when framework integration is not needed.
- Use focused controller tests for validation, serialization, authorization, status codes, and error contracts.
- Use integration tests for wiring, configuration binding, security filters, persistence semantics, transactions, migrations, and infrastructure behavior that mocks cannot prove.
- Test outbound clients against a controllable HTTP stub or equivalent boundary. Cover timeouts, malformed responses, authentication failures, throttling, retry limits, and idempotency-sensitive behavior.
- Mock external systems and true boundaries. Avoid mocking so much application logic that tests merely restate implementation calls.
- Include malformed and oversized inputs, missing or invalid credentials/signatures, access denial, concurrency or duplication where relevant, and both recoverable and nonrecoverable failures.
- Keep tests deterministic. Control time, randomness, and asynchronous completion explicitly; do not hide flakiness with unbounded waits or blanket retries.

## Verify and report

After implementation:

1. Review the diff for scope, dependency direction, data exposure, secret handling, transaction and retry safety, compatibility, and unnecessary complexity.
2. Run the narrowest relevant tests first, then compilation and the repository's broader test or verification task in proportion to the change. Use the checked-in build wrapper.
3. Run configured formatting, static analysis, coverage, architecture, or dependency checks when relevant.
4. Diagnose failures, fix issues within scope, and rerun every affected check. Distinguish product failures from environment or pre-existing failures with evidence.
5. Never claim a check passed unless it completed successfully in this session. If a check was not run or could not run, say exactly why.

For reviews, lead with concrete findings ordered by severity, cite the affected file and location, explain impact and a practical correction, then note test gaps and residual risks. Do not manufacture findings when the evidence does not support them.

For completed changes, report:

- implemented behavior and important design choices;
- configuration, migration, security, and operational implications;
- commands run and their actual outcomes;
- remaining limitations, assumptions, or follow-up work.
