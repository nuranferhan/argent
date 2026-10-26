# ADR 0001 — Parasal tutarlarda IEEE-754 yerine `BigDecimal`

## Durum
Kabul edildi

## Bağlam
Eşleştirme, marjin ve çift taraflı settlement işlemleri kesin sonuç vermelidir. `Double`/`Float`, 0.1 değerini tam temsil edemez ve dolumlar boyunca yuvarlama hatası biriktirir.

## Karar
Tüm fiyatlar, miktarlar, işlem tutarları (notional), bakiyeler ve PnL değerleri `java.math.BigDecimal` kullanır. Wire format ondalık metinlerden oluşur. Scale yalnızca enstrümanın tick/lot sınırlarında uygulanır (`HALF_UP`).

## Sonuçlar
CPU maliyeti `long` kullanımına göre daha yüksektir. Buna rağmen bir laptop üzerinde actor başına saniyede on binlerce emir işlenebilir. Broker ledger'ında doğruluk temel değişmezdir.
