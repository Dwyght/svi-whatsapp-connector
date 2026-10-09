# SVI WhatsApp AI Messaging Connector

This Spring Boot service connects inbound customer-initiated WhatsApp conversations to a replaceable AI provider. It also exposes a guarded local API for testing application-initiated, approved-template messages. Both directions send through the official WhatsApp Cloud API.

The current feature is a WhatsApp integration component, not a chatbot platform. The root package is channel-neutral so additional messaging connectors can be added later without coupling them to WhatsApp. Media processing, campaigns, voice calls, persistence, multi-tenancy, a frontend, and cloud deployment are outside this version's scope.

## Architecture

The application uses feature-based packages directly under `com.svi.messaging`:

```text
com.svi.messaging/
├── MessagingApplication.java
├── whatsapp/
│   ├── controller/    Webhook and local outbound endpoints
│   ├── service/       Processing, dispatch, ordering, and idempotency
│   ├── client/        Meta WhatsApp Cloud API adapter
│   ├── dto/           Webhook, template, and provider contracts
│   ├── mapper/        Meta payload conversion
│   ├── model/         WhatsApp ingestion models
│   ├── security/      Webhook and local outbound authentication
│   ├── config/        WhatsApp properties and HTTP configuration
│   └── exception/     WhatsApp endpoint and provider failures
├── ai/
│   ├── client/        Replaceable AI boundary and adapters
│   ├── config/        AI provider properties and client construction
│   ├── dto/           Channel-neutral AI request/response contracts
│   └── exception/     AI provider failures
└── common/
    └── exception/     Shared external-failure classification
```

Dependency direction is `whatsapp -> ai -> common`, with WhatsApp also using `common` directly. The AI and common packages have no WhatsApp dependency. `AiClient` is the reusable AI boundary for future channels, while `InboundMessageDispatcher` separates the WhatsApp webhook from long-running AI and Meta calls.

## Message Flow

1. Meta sends the exact webhook body to `POST /webhooks/whatsapp`.
2. The connector checks the body size and validates `X-Hub-Signature-256` with the Meta App Secret.
3. Valid text messages are mapped to an internal message; statuses and unsupported types are ignored.
4. The local dispatcher deduplicates the WhatsApp message ID and queues it on a conversation-ordered worker lane.
5. `MessageProcessingService` calls the selected `AiClient` and sends a nonblank response through `WhatsAppApiClient`.
6. Operational metrics and redacted logs record outcomes without logging tokens, phone numbers, or message text.

The initial AI interaction is stateless. No conversation history is stored or sent to the AI provider.

### Application-Initiated Flow

1. A developer submits one template request to `POST /api/v1/whatsapp/messages` using Postman.
2. The local-only endpoint validates its bearer token, request fields, recipient/template allowlists, and idempotency key.
3. `OutboundMessageService` reserves the idempotency key and delegates to `WhatsAppApiClient`.
4. The client sends an official `type: template` payload to Meta's Messages API.
5. A `202 Accepted` response means Meta supplied a provider message ID. It does **not** prove delivery; delivery state arrives later through Meta webhooks.
6. If the recipient replies, the existing webhook-to-AI-to-text-reply flow handles that inbound message independently.

WhatsApp business-initiated conversations generally require an approved template outside the customer-service window. This API deliberately does not expose arbitrary outbound free-form text.

## Prerequisites

- Java 25
- A Meta Developer app with a WhatsApp test number
- The Maven wrapper included in this repository
- [ngrok](https://ngrok.com/) or another HTTPS tunnel for local webhook testing
- OpenAI credentials only if testing the optional OpenAI adapter

## Configuration

Copy `.env.example` as a private reference, but do not commit populated secrets. Spring Boot does **not** automatically load `.env`; add these values to the IntelliJ run configuration or export them in the shell.

Required variables:

| Variable | Purpose |
| --- | --- |
| `META_GRAPH_API_VERSION` | Version shown in the Meta Developer test/API environment, such as the version used by its generated request |
| `WHATSAPP_PHONE_NUMBER_ID` | Test or registered WhatsApp phone-number ID |
| `WHATSAPP_ACCESS_TOKEN` | Meta access token with messaging permission |
| `META_APP_SECRET` | App secret used to authenticate webhook bodies |
| `WHATSAPP_VERIFY_TOKEN` | Private value you choose and also enter in Meta's webhook configuration |
| `OUTBOUND_LOCAL_AUTH_TOKEN` | Private bearer token protecting the local outbound endpoint |
| `OUTBOUND_ALLOWED_RECIPIENTS` | Comma-separated Meta-verified test numbers, digits only and without `+` |
| `OUTBOUND_ALLOWED_TEMPLATES` | Comma-separated approved template names; defaults to `hello_world` |

`META_GRAPH_BASE_URL` defaults to `https://graph.facebook.com`. Do not add `v` or a version to that base URL; the configured `META_GRAPH_API_VERSION` is added separately.

## Run Locally with Mock AI

Set the Meta variables and the outbound local token/recipient allowlist, then start the explicit local profile:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

The local profile defaults `AI_PROVIDER` to `mock`. Its deterministic response is `Mock AI response: <customer text>`. Health information is available at `GET /actuator/health`; only `health` and `info` Actuator endpoints are exposed.

## Test an Outbound Template with Postman

Use only a recipient that you explicitly added and verified in Meta's test environment. Set `OUTBOUND_ALLOWED_RECIPIENTS` to that number in international digits-only form, restart the local profile, and create this Postman request:

```http
POST http://localhost:8080/api/v1/whatsapp/messages
Authorization: Bearer <value of OUTBOUND_LOCAL_AUTH_TOKEN>
Idempotency-Key: hello-world-test-001
Content-Type: application/json
```

```json
{
  "recipient": "639123456789",
  "templateName": "hello_world",
  "languageCode": "en_US"
}
```

The number above is a placeholder; replace it with the verified test recipient. The pre-approved `hello_world` template takes no parameters. For another allowlisted and approved template with body placeholders, supply ordered text values:

```json
{
  "recipient": "639123456789",
  "templateName": "order_update",
  "languageCode": "en_US",
  "parameters": ["Dwyght", "A-123"]
}
```

An accepted submission returns HTTP `202`:

```json
{
  "providerMessageId": "wamid...",
  "submissionStatus": "ACCEPTED_BY_PROVIDER",
  "idempotentReplay": false
}
```

Repeat the exact request with the same `Idempotency-Key` to receive the stored response with `idempotentReplay: true` and no second Meta call. Reusing a key for different content returns `409 Conflict`. After a timeout or other ambiguous Meta failure, the same key is blocked because the original message might have been accepted.

In the Meta Developer Dashboard, confirm that the recipient is registered as a test recipient and that `hello_world` is available for the configured WhatsApp Business Account. A local `202` proves provider acceptance only. Confirm receipt on the actual device or inspect later delivery-status webhooks before claiming delivery.

## Configure Meta and ngrok

1. Start the application on port `8080`.
2. Run `ngrok http 8080` and copy the generated HTTPS URL.
3. In the Meta Developer Dashboard, open the WhatsApp product's **Configuration** page.
4. Set the callback URL to `https://<ngrok-host>/webhooks/whatsapp`.
5. Enter the same value used for `WHATSAPP_VERIFY_TOKEN` and verify the callback.
6. Subscribe the WhatsApp Business Account webhook to the `messages` field.
7. Confirm the configured phone-number ID, access token, App Secret, and Graph API version match the same Meta app/test environment.
8. In Meta's test setup, add and verify the customer phone number if Meta requires it as a test recipient.

Send a text from the verified customer phone to Meta's test WhatsApp number. A successful local flow is logged as accepted and should return the mock response through WhatsApp. Delivery-status callbacks are acknowledged but do not invoke AI. This repository has not performed or claimed a live Meta round trip because credentials are not stored here.

## Optional OpenAI Integration

Set the following values and restart the local profile:

```text
AI_PROVIDER=openai
OPENAI_API_KEY=<your externally supplied key>
OPENAI_MODEL=<model available to your OpenAI project>
```

The adapter uses OpenAI's official Java SDK and Responses API with a 30-second default timeout and bounded retries. No OpenAI key is needed for mock mode. To integrate SVI's future AI service, implement `AiClient` as a new conditional adapter and add a provider configuration value; webhook, dispatch, and Meta client code should remain unchanged.

## Automated Tests

```powershell
.\mvnw.cmd test
.\mvnw.cmd clean verify
```

Tests use mocked/stubbed external boundaries and require no Meta or OpenAI credentials. Fixtures under `src/test/resources/fixtures/whatsapp/` cover official-format text, status, unsupported, and batched webhook events.

## Development Limitations

The `local` and `test` profiles use a bounded process-local queue and TTL deduplication map. A `202` from the webhook endpoint means the inbound event was accepted into memory, not durably stored. Restart, crash, or asynchronous provider failure can lose work. Deduplication is process-local and does not provide exactly-once delivery. Hash-based worker lanes preserve practical ordering within one process but are not distributed ordering.

Outbound idempotency is also bounded, TTL-based, and process-local. It prevents common duplicate Postman submissions within one running instance but is lost on restart and cannot coordinate multiple instances. The local bearer token and allowlists are development safeguards, not production service-to-service security or consent enforcement.

There is deliberately no production dispatcher bean. Starting without `local` or `test` fails rather than silently using nondurable infrastructure.

Before production deployment, add durable event storage or a message broker, transactional deduplication and an outbound outbox, controlled retries/dead-letter handling, distributed conversation ordering, service-to-service authentication and authorization, recipient consent/policy enforcement, audit records, production secret management, TLS/network controls, persistent observability, alerts, and deployment/runbook automation. Live credential, rate-limit, failover, and end-to-end Meta tests must also be completed.
