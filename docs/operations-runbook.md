# ARGENT yerel operasyon kılavuzu

Bu kılavuz Windows 11 üzerinde çalışan yerel internal-exchange kurulumu içindir. Emirler, bakiyeler ve fiyatlar dahili sandbox verileridir. Servisleri public internet'e açmayın ve bakiyeleri müşteri fonu olarak değerlendirmeyin.

## Başlatma ve doğrulama

1. Docker Desktop'ı başlatın ve engine'in hazır olmasını bekleyin.
2. PowerShell'i `C:\Users\Lenovo\Projects\argent` klasöründe açın.
3. Yerel stack'i `.\scripts\start-stack.ps1` ile başlatın veya altyapıyı `docker compose up -d` ile başlatıp host gateway için `.\scripts\start.ps1` çalıştırın. Gateway'in çalıştığı foreground penceresini açık tutun.
4. Güncel desktop client'ı derlemek için başka bir PowerShell penceresinde `.\scripts\package-terminal.ps1` çalıştırın; ardından `argent-terminal\build\compose\binaries\main\msi\Argent-1.0.3.msi` paketini yükleyin. Derleme/yükleme sırasında gateway penceresini açık tutun.
5. `.\scripts\verify-local-release.ps1` çalıştırın. Başarılı kontrol; API health, ledger invariant, PostgreSQL, Redis, RabbitMQ, admin ledger reconciliation, 1.0.3 installer ve yüklenmiş executable durumunu raporlar. Geçici bir refresh token oluşturup iptal eder; emir göndermez veya bakiyeyi değiştirmez.
6. PostgreSQL çalışırken `.\scripts\test-postgres-integration.ps1` çalıştırın. Test geçici bir schema oluşturur; eşzamanlı reservation işlemlerini ve yeni bir database connection üzerinden settlement replay'i sınar, ardından schema'yı siler.
7. ARGENT'i Windows Start menüsünden açın. Login rate limit'e takılırsa yapılandırılmış sürenin dolmasını bekleyin veya Redis'ten yalnızca login rate-limit key'lerini temizleyin; Redis'i tamamen boşaltmayın ve database'i silmeyin.

## Android sandbox client

- Android Studio'yu Android SDK Platform 36, Build Tools 36.0.0 ve Platform Tools ile kurun; AGP için JDK 17 veya üzeri gerekir.
- Yerel gateway çalışırken `scripts/package-android.ps1` debug APK'sini derler. `scripts/install-android.ps1`, APK'yi derler, yetkili USB cihazına/emulator'a bağlanır, `adb reverse` ile 8080 portunu yönlendirir ve APK'yi yükler.
- Android emulator'da gateway URL'si `http://10.0.2.2:8080`; USB reverse kullanılan fiziksel cihazda `http://127.0.0.1:8080` olur.
- Debug variant bu sandbox için yerel HTTP bağlantısına izin verir. Release variant cleartext traffic'i kapatır; remote gateway kullanmadan önce HTTPS yapılandırın. Debug APK imzalı dağıtım sürümü değildir.
- Android özelliklerinin kabulü gerçek emulator/cihazda hâlâ yapılmalıdır: login ve invitation, kotasyon/order book güncellemeleri, emir/rejection/cancel, portfolio, workspace membership/role değişiklikleri ve market-maker kill/resume. Gerçek para kullanmayın; gateway internal matching ve simüle BIST fiyatları kullanır.

## Durdurma

Host gateway'i `start.ps1` çalıştıran pencerede Ctrl+C ile durdurun. App profile ile başlatılmış Compose servislerini `docker compose --profile app down`, temel stack ile başlatılanları `docker compose down` ile durdurun. Database volume'larını sildiği için `-v` eklemeyin.

## Düzenli backup ve restore

- Backup oluşturmak için `.\scripts\backup-db.ps1` çalıştırın.
- Gerçek bir disaster-recovery planı için oluşan dump'ı makine dışında saklayın. Yerel script tek başına off-host encryption, retention veya immutable storage sağlamaz.
- Bir dump'ı production verisinin üzerine yazmadan doğrulamak için `.\scripts\verify-db-backup-restore.ps1` çalıştırın. Script geçici bir database oluşturur, dump'ı buraya yükler, örnek tablo sayılarını ve ledger bakiyesini kontrol eder; ardından geçici database'i kaldırır.
- Seçilen bir backup'ı geri yüklemek için önce gateway'i durdurun ve `.\scripts\restore-db.ps1 <dump-path>` çalıştırın. Mevcut database içeriğini değiştirdiği için script açıkça `RESTORE ARGENT` yazmanızı ister. İşleme başlamadan önce dump yolunu doğrulayın ve ayrı bir kopyasını saklayın.

Proje sahibi 2026-10-12 tarihinde izole bir restore işleminin başarıyla tamamlandığını bildirdi: 18.274 journal entry, 4.732 emir, 4 trade ve `ledger invariant=balanced`. Schema veya backup script'lerinde önemli değişikliklerden sonra bu doğrulamayı tekrarlayın.

## Health ve olay inceleme

| Sinyal | İlk adım |
|---|---|
| `/health` erişilemiyor | Docker Desktop'ı ve `docker compose --profile app ps` çıktısını kontrol edin. Host gateway kullanıyorsanız `start.ps1` penceresinin hâlâ çalıştığını doğrulayın. |
| PostgreSQL veya Redis değeri false | `docker compose logs --tail 100 postgres redis` çıktısını inceleyin; ilk adım olarak volume'ları sıfırlamayın. |
| RabbitMQ değeri false | `docker compose logs --tail 100 rabbitmq` çıktısını inceleyin; `/health` yanıtı `UP` olmadan stack'i sağlıklı kabul etmeyin. |
| `invariantOk` false veya engine sağlıksız | Yeni emir göndermeyi durdurun, log'ları ve database volume'unu koruyun; yeniden başlatmadan önce nedeni araştırın. Ledger satırlarını elle düzenlemeyin. |
| Login rate limit'e takıldı | Yapılandırılmış rate window süresini bekleyin. Operatör bir key'i temizleyecekse Redis'te yalnızca `rl:auth:login:*` key'lerini silin; `FLUSHALL` kullanmayın. |
| Market-maker sorunu şüphesi | Önce desktop admin kill switch'i kullanın ve devre dışı kaldığını doğrulayın. Yeniden başlatmadan önce log'ları koruyup bot emirlerini/stokunu inceleyin. |
| Secret açığa çıkmış olabilir | `JWT_SECRET` ve bootstrap/admin password değerlerini deployment secret kaynağından değiştirin; değişiklikten sonra gateway'i yeniden başlatarak session'ları geçersiz kılın. Yeni değerleri sohbete yapıştırmayın veya commit etmeyin. |

Timestamp, etkilenen service, görülen health JSON, son gateway/container log'ları, operatör işlemleri ve sonucu kaydedin. Onarıma başlamadan önce incident öncesi database kopyasını saklayın.

## Güvenlik ve release sınırları

- Bu yerel profile'da database, Redis, RabbitMQ, Prometheus ve Grafana portlarını localhost'a bağlı tutun. Bu portları güvenilmeyen bir ağa açmayın.
- Paylaşılan/staging deployment için benzersiz secret'lar kullanın. `.env.example` içindeki kimlik bilgileri ve geliştirme JWT değeri yalnızca yerel kullanım içindir.
- Mevcut health check'ler ve Grafana dashboard'ları yerel observability sağlar. Paging, alert routing, on-call sorumluluğu, şifreli off-host backup, TLS termination, secret-manager entegrasyonu, MFA/recovery, bağımsız inceleme ve disaster-recovery hedefleri deployment'a özel kurulum ve kabul gerektirir.
- Bu build internal matching ve simüle fiyatlar kullanır. Gerçek exchange emri, lisanslı BIST feed'i, deposit, withdrawal, custody veya payment hizmeti sunmaz.
