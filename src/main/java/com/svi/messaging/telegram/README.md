# Telegram connector

This opt-in connector receives `POST /webhooks/telegram` and generates replies
through the existing channel-neutral `AiClient`. Existing AI provider settings
apply. No Telegram credentials are required when the connector is disabled.

## Enable locally

Supply these environment variables before starting the existing application with
the `local` profile using the checked-in Maven wrapper:

| Environment variable | Required/default |
| --- | --- |
| `TELEGRAM_ENABLED` | `true` to enable; disabled by default |
| `TELEGRAM_BOT_TOKEN` | Required when enabled; obtain through BotFather |
| `TELEGRAM_WEBHOOK_SECRET_TOKEN` | Required when enabled; choose 1–256 characters from letters, digits, underscore, and hyphen |
| `TELEGRAM_CONNECT_TIMEOUT` | `3s`; allowed 1–30 seconds |
| `TELEGRAM_READ_TIMEOUT` | `10s`; allowed 1–60 seconds |
| `TELEGRAM_WEBHOOK_MAX_REQUEST_BYTES` | `262144`; allowed 1024–1048576 bytes |
| `TELEGRAM_WORKER_COUNT` | `4`; allowed 1–32 |
| `TELEGRAM_QUEUE_CAPACITY_PER_WORKER` | `25`; allowed 1–10000 |
| `TELEGRAM_DEDUPLICATION_CAPACITY` | `10000`; allowed 1–1000000 |
| `TELEGRAM_DEDUPLICATION_TTL` | `24h`; allowed 1 second–7 days after processing finishes |
| `TELEGRAM_SHUTDOWN_TIMEOUT` | `10s`; allowed 1–60 seconds |

The existing WhatsApp configuration and application profile requirements still apply.
Spring binds the Telegram variables directly; no edits to the shared properties
files or `.env.example` are needed.

Register a public HTTPS webhook using Telegram's `setWebhook` method with
`url` pointing to this endpoint, `secret_token` matching the configured secret,
and `allowed_updates=["message"]`. Registration is an operator action and is
not performed automatically. Keep the bot token and secret outside source and logs.

## Webhook behavior

Requests require exactly one `X-Telegram-Bot-Api-Secret-Token` header. Authentication
runs before reading the body. Declared and streamed body sizes are bounded.

Synthetic request example (identifiers and text are test data):

```http
POST /webhooks/telegram
Content-Type: application/json
X-Telegram-Bot-Api-Secret-Token: <configured-secret>

{"update_id":1,"message":{"message_id":2,"from":{"is_bot":false},"chat":{"id":42,"type":"private"},"text":"Synthetic test text"}}
```

| Result | HTTP response |
| --- | --- |
| Accepted, duplicate, or unsupported update | `200`, empty body |
| Missing, invalid, or repeated secret header | `401`, safe JSON error |
| Malformed JSON or invalid supported-message fields | `400`, safe JSON error |
| Authenticated payload too large | `413`, safe JSON error |
| Dispatcher queue or deduplication storage full | `503`, empty body; Telegram can retry |

Only new private text messages from non-bot users generate replies. Group,
channel, edited, callback, media, and service updates are acknowledged without
processing. Supported private topic IDs are preserved in replies and AI
conversation IDs. Conversations use `telegram:<chat-id>[:<topic-id>]` to avoid
colliding with other channels. Replies are plain text, limited to 4096 characters,
with no implicit formatting mode. The client uses the official HTTPS Bot API
host, refuses redirects, and applies explicit connection and response timeouts.
Automatic HTTP observations are disabled for this client because the URL contains
the bot token. Connector counters remain available. Diagnostic representations of
configuration and outbound payloads redact credentials, chat IDs, and message text.

## Delivery and production

The in-memory dispatcher is registered only for `local` and `test`. It uses
bounded queues, assigns each conversation to one serial worker, and deduplicates
by `update_id`. It preserves acceptance order within each worker; it does not
reorder Telegram updates that arrive out of order. Queued and in-flight update
reservations do not expire. Completed reservations are retained for the configured
TTL, including failures, to reduce ambiguous duplicate sends. Full storage rejects
new work instead of evicting live reservations.

A `200` acknowledges ownership of the update, not successful AI processing or
delivery. Accepted work and deduplication state are process-local and lost on
restart. Asynchronous failures increment `telegram.message.processing{result=failed}`
and log only the update ID and failure category. They are not automatically retried
or replayed. Outbound API calls are made once: timeout and server failures may
have an ambiguous outcome, and Telegram `sendMessage` has no idempotency key.
The returned message ID confirms the Bot API operation; it is not a recipient
read or delivery receipt.

Enabling the connector outside `local` and `test` requires a
`TelegramInboundMessageDispatcher` bean backed by durable storage; application
startup fails without one. That implementation must persist before acceptance,
deduplicate updates, preserve conversation ordering, and own recovery and
ambiguous-send handling. No public outbound REST endpoint is introduced.

Counters: `telegram.webhook.messages` with `accepted`, `duplicate`, or
`overloaded` result tags; `telegram.webhook.events{result=ignored}`;
`telegram.message.processing{result=failed}`.

## Verification

Run from the repository root with Java 25:

```powershell
.\mvnw.cmd test "-Dtest=com.svi.messaging.telegram.**.*Tests"
.\mvnw.cmd clean verify
```

Tests use synthetic data and mocked AI/HTTP boundaries; real tokens and network
access to Telegram are not required.

Contract reference: [Telegram Bot API](https://core.telegram.org/bots/api),
including [setWebhook](https://core.telegram.org/bots/api#setwebhook) and
[sendMessage](https://core.telegram.org/bots/api#sendmessage).
