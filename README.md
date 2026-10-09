# SVI WhatsApp AI Messaging Connector

This Spring Boot service connects inbound customer-initiated WhatsApp conversations to a replaceable AI provider. It authenticates Meta webhooks, accepts text messages, generates a reply with the configured AI client, and sends the reply through the official WhatsApp Cloud API.

The project is a WhatsApp integration component, not a chatbot platform. Media processing, campaigns, voice calls, persistence, multi-tenancy, a frontend, and cloud deployment are outside this version's scope.

## Architecture

The application uses a traditional layered architecture under `com.svi.whatsapp_connector`:

```text
controller/       Webhook HTTP endpoints
service/          Ingestion, dispatch, and message orchestration
client/           Meta Cloud API and AI provider adapters
dto/request/      Meta webhook and outbound request contracts
dto/response/     External API response contracts
mapper/           Meta payload to internal model conversion
model/            Provider-neutral immutable messages
config/           Validated properties and HTTP clients
security/         HMAC verification and request-size enforcement
exception/        Failure categories and safe HTTP errors
```

`AiClient` is the replaceable AI boundary. `InboundMessageDispatcher` separates the webhook from long-running AI and Meta calls.

## Message Flow

1. Meta sends the exact webhook body to `POST /webhooks/whatsapp`.
2. The connector checks the body size and validates `X-Hub-Signature-256` with the Meta App Secret.
3. Valid text messages are mapped to an internal message; statuses and unsupported types are ignored.
4. The local dispatcher deduplicates the WhatsApp message ID and queues it on a conversation-ordered worker lane.
5. `MessageProcessingService` calls the selected `AiClient` and sends a nonblank response through `WhatsAppApiClient`.
6. Operational metrics and redacted logs record outcomes without logging tokens, phone numbers, or message text.

The initial AI interaction is stateless. No conversation history is stored or sent to the AI provider.

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

`META_GRAPH_BASE_URL` defaults to `https://graph.facebook.com`. Do not add `v` or a version to that base URL; the configured `META_GRAPH_API_VERSION` is added separately.

## Run Locally with Mock AI

Set the five required Meta variables, then start the explicit local profile:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

The local profile defaults `AI_PROVIDER` to `mock`. Its deterministic response is `Mock AI response: <customer text>`. Health information is available at `GET /actuator/health`; only `health` and `info` Actuator endpoints are exposed.

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

The `local` and `test` profiles use a bounded process-local queue and TTL deduplication map. A `202` means the event was accepted into memory, not durably stored. Restart, crash, or asynchronous provider failure can lose work. Deduplication is process-local and does not provide exactly-once delivery. Hash-based worker lanes preserve practical ordering within one process but are not distributed ordering.

There is deliberately no production dispatcher bean. Starting without `local` or `test` fails rather than silently using nondurable infrastructure.

Before production deployment, add durable event storage or a message broker, transactional deduplication and an outbound outbox, controlled retries/dead-letter handling, distributed conversation ordering, production secret management, TLS/network controls, persistent observability, alerts, and deployment/runbook automation. Live credential, rate-limit, failover, and end-to-end Meta tests must also be completed.
