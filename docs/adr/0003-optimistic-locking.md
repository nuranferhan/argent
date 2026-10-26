# ADR 0003 — Ledger kilitleme: `SELECT FOR UPDATE` ve version column

## Durum
Kabul edildi

## Bağlam
Hold, release, deposit ve settlement işlemleri aynı `(user, asset)` satırında yarışabilir. Kayıp güncellemeler debit ve credit toplamlarının eşitliğini bozabilir.

## Karar
Bakiye değişiklikleri PostgreSQL transaction içinde yürütülür. Önce satır kilitlenir (`FOR UPDATE`), ardından `UPDATE … WHERE version = ?` çalıştırılır. Güncellenen satır sayısı sıfırsa `OptimisticLockException` oluşur; çağıran taraf yeniden dener veya işlemi güvenli biçimde reddeder (hold işlemi `false` döndürür).

## Sonuçlar
Hesap başına throughput aynı satır üzerinde sıralı işlenir. Nakit bakiyeler için doğru yaklaşım budur. Idempotency key'leri, retry sonrasında deposit/withdraw/settlement işlemlerinin iki kez uygulanmasını önler.
