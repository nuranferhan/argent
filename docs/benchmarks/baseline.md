# Eşleştirme motoru ölçüm özeti

Order book undo log değişikliğinden sonra alınan keşif amaçlı yerel ölçüm (1 Kasım 2026'da tekrarlandı). Bu sonuç önce/sonra karşılaştırması değildir; aynı makinede değişiklik öncesi ölçüm bulunmuyor. Ölçülen işlem, sabit bir order book'a karşılıklı eşleşmeyecek bir limit emri ekleyip ardından geri almaktır.

## Sonuçlar

| Başlangıç order book derinliği | Ortalama | p50 | p90 | p95 | p99 | Örnek sayısı |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Bekleyen 100 emir | 0.458 µs/op | 0.399 µs/op | 0.600 µs/op | 0.800 µs/op | 2.000 µs/op | 75,827 |
| Bekleyen 10.000 emir | 0.456 µs/op | 0.399 µs/op | 0.600 µs/op | 0.701 µs/op | 1.700 µs/op | 77,500 |

Bu çalışmada başlangıç derinliğinin 100 kat artması, ölçülen ortalamayı yaklaşık %0,4; p99 değerini ise 0,3 µs azalttı. Bu sonuç, yalnızca söz konusu işlem için beklenen sabit maliyet davranışını destekler; tekrarlanan ve kontrollü benchmark'ların yerini tutmaz, eşleşen emir dolumlarını, iptali veya uçtan uca gateway gecikmesini kapsamaz. Kısa çalışmada p99 üzerindeki uç yüzdeliklerde nadir JVM duraklamaları görülüyor (772 µs'ye kadar).

## Ortam ve tekrarlama

- JMH 1.37; Temurin 21.0.12.1; bir fork, bir thread; compiler blackhole modu.
- İki adet 500 ms ısınma ve beş adet 1 saniyelik sample-time ölçümü.
- Benchmark durumu her deneme başında bir kez oluşturulur; ölçülen her ekleme geri alınarak derinlik sabit tutulur.

```powershell
./gradlew :argent-engine:jmhJar --no-daemon --max-workers=1
java -jar argent-engine/build/libs/argent-engine-1.0.0-jmh.jar `
  -wi 2 -i 5 -w 500ms -r 1s -bm sample -f 1 -t 1 `
  '.*addLimitOrderToBookOfConfiguredSize.*'
```

Gelecekteki çalıştırmaları yalnızca aynı donanım, güç profili, JDK, JVM seçenekleri, JMH sürümü ve ayarlarla karşılaştırın. Bu microbenchmark'tan production gecikme veya throughput hedefi çıkarmayın.
