# ADR 0002 — Her symbol için bir coroutine actor

## Durum
Kabul edildi

## Bağlam
Merkezi bir matching engine deterministik olmalıdır: aynı emir akışı aynı işlemleri üretmelidir. Tek bir order book üzerinde eşzamanlı değişiklik yapılması data race oluşturur. Gerçek borsalar da order book'ları birbirinden ayırmak için symbol bazında shard kullanır.

## Karar
Her symbol, bir Kotlin coroutine actor (`Channel.UNLIMITED`) ve bir `ConcurrentSkipListMap` order book sahibi olur. Submit, cancel, depth, restore ve replay komutları symbol başına sıraya alınır. Farklı symbol'ler paralel ilerler.

## Sonuçlar
Yoğun işlem gören bir symbol tasarım gereği tek thread üzerinde çalışır (price-time FIFO). Horizontal scale, tek bir book'a daha fazla thread eklemek yerine daha fazla symbol veya process kullanılarak sağlanır. Testler event log'u yeniden oynatıp aynı depth ve trade ID değerlerini doğrulayabilir.
