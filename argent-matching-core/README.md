# ARGENT Matching Core

ARGENT matching engine ve double-entry ledger üzerine kurulmuş yeniden kullanılabilir library facade.

```kotlin
val core = ArgentMatchingCore(dataSource) { event -> durableEventStore.append(event) }
val ledger = core.ledger
val matchingEngine = core.matchingEngine
```

İzole replay veya başka engine örnekleri için `newMatchingEngine(eventSink = ..., keepRecordedEvents = false)` kullanın. Library'yi yerel Maven repository'ye yayımlamak için `gradlew :argent-matching-core:publishToMavenLocal` komutunu çalıştırın.

Modül, dahili paper-trading yapıtaşlarını sunar. Bir broker'a bağlanmaz ve gerçek varlıkları saklamaz.
