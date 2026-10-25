# ARGENT Matching Core

Reusable library facade over the ARGENT matching engine and double-entry ledger.

```kotlin
val core = ArgentMatchingCore(dataSource) { event -> durableEventStore.append(event) }
val ledger = core.ledger
val matchingEngine = core.matchingEngine
```

Use `newMatchingEngine(eventSink = ..., keepRecordedEvents = false)` for isolated replay or other engine instances. Publish to the local Maven repository with `gradlew :argent-matching-core:publishToMavenLocal`.

The module exposes internal paper-trading primitives. It does not connect to a broker or custody real assets.
