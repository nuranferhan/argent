# ADR 0004 — Order book için sistem kaynağı event log'dur

## Durum
Kabul edildi

## Bağlam
JVM kapandığında bellekteki order book kaybolur. Bekleyen emirler crash sonrasında korunmalıdır. `OrderSubmitted` ve `OrderCancelled` olaylarını sırayla replay ederek book'u yeniden oluşturmak deterministik ve test edilebilirdir.

## Karar
Engine ve ledger ile ilgili her olgu `domain_events` tablosuna eklenir. Runtime recovery, aktif emirleri hızlı yol olarak `orders` tablosundan geri yükler ve `trades` tablosunda bulunmayan `TradeExecuted` olaylarını settlement işlemine alır (crash window). Admin replay, bir symbol'ün order book'unu event stream üzerinden yeniden kurar.

## Sonuçlar
PostgreSQL hem ledger hem event store olarak kullanılır. RabbitMQ bir fan-out bus'tır; sistemin doğruluk kaynağı değildir. Matching path, AMQP yanıtını beklemez.
