# ADR 0002 — Symbol başına sıralı ve sınırlı emir kuyruğu

## Durum
Kabul edildi

## Bağlam
Merkezi bir matching engine deterministik olmalıdır: aynı emir akışı aynı işlemleri üretmelidir. Tek bir order book üzerinde eşzamanlı değişiklik yapılması data race oluşturur. Gerçek borsalar da order book'ları birbirinden ayırmak için symbol bazında shard kullanır.

## Karar
Her symbol, kendi order book'unu ve 256 komut kapasiteli bir Kotlin `Channel` kuyruğunu yönetir. Submit, cancel, depth, restore ve replay komutları symbol başına sıralanır; farklı symbol'ler paralel ilerler. Kuyruk dolduğunda istek bekletilip bellekte biriktirilmez: `trySend` başarısız olur ve gateway isteği açık bir aşırı yük hatasıyla reddedebilir.

## Sonuçlar
Yoğun işlem gören bir symbol tasarım gereği tek tüketici üzerinde çalışır (price-time FIFO). Sınırlı kuyruk bellek kullanımına üst sınır getirir; yük altında çağıran tarafın yeniden deneme/back-pressure davranışı önem kazanır. Horizontal scale, tek bir book'a daha fazla thread eklemek yerine daha fazla symbol veya process kullanılarak sağlanır. Testler event log'u yeniden oynatıp aynı depth ve trade ID değerlerini doğrulayabilir.
