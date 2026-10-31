# ADR 0005: Keep the application as a modular monolith

## Status

Accepted

## Context

ARGENT has separate Gradle modules for the gateway, matching engine, risk checks,
ledger, and market data. These modules provide compile-time boundaries, but the
local application runs them inside one gateway process. Deploying each module as
a separate service would add network calls and distributed transaction failure
modes to order acceptance and settlement without a current scaling requirement.

## Decision

- Keep matching, risk, ledger, and market business logic as in-process modules.
- Keep PostgreSQL as the durable source for account/order/event data and Redis
  for shared rate limiting and short-lived state.
- Keep RabbitMQ for asynchronous domain-event delivery and integrations; do not
  put synchronous order validation or ledger writes behind the broker.
- Use Docker Compose as the supported local runtime. Do not introduce a
  Kubernetes/Minikube deployment until operational scale requires it.
- Extract modules into separately deployed services only when measured load,
  independent release needs, or fault isolation justify the extra network and
  distributed consistency costs.

## Consequences

Order and settlement paths stay local to the gateway process, while module
interfaces retain test and future extraction boundaries. PostgreSQL, Redis, and
RabbitMQ remain infrastructure dependencies, so their health and recovery still
need monitoring. This decision avoids service-per-module overhead; it does not
claim that the infrastructure dependencies are optional in production.
