# ARGENT production readiness

Hedeflenen ürün; çok kiracılı, production kalitesinde bir internal exchange ve settlement platformudur. Emirleri ARGENT'in kendi order book'ları ve sentetik sandbox likiditesi üzerinden yürütür; harici execution venue, lisanslı piyasa verisi veya custody sağlayıcısı yoktur. Yerel health yanıtının yeşil olması, yapılandırılmış servislerin erişilebilir olduğunu doğrular; sentetik fiyatlar ve dahili bakiyeler gerçek piyasa verisi veya müşteri fonu değildir.

## Bu repository'de uygulananlar

- Kotlin multi-module gateway, matching engine, ledger, risk/portfolio mantığı ve market data feed'i; ayrıca aynı gateway API'sini kullanan Compose Desktop ve Jetpack Compose Android client'ları.
- Dahili limit/market/IOC/FOK matching, event replay, idempotent emir gönderimi, kalıcı order/trade saklama ve double-entry ledger settlement.
- Production matching sırasında domain event'lerin ikinci ve sınırsız bir in-memory kopyası tutulmaz; kalıcı event kaynağı PostgreSQL'dir. Recovery, settlement recovery için yalnızca replay sırasında üretilen fill'leri yakalar.
- Yerel Docker Compose servisleri, health/metrics endpoint'leri, Prometheus/Grafana yapılandırması ve başlatma/paketleme script'leri.
- PostgreSQL backup/restore script'leri ve izole restore doğrulaması (`scripts/backup-db.ps1`, `scripts/restore-db.ps1`, `scripts/verify-db-backup-restore.ps1`).
- Yapılandırılabilir simulated market-data adapter'ı, dahili market-making strategy ve yapılandırılmamış external deposit/withdrawal işlemlerini güvenli biçimde reddetme.
- Tenant workspace'leri, OWNER/ADMIN/TRADER/VIEWER rolleri, e-posta adresine bağlı invitation'lar, davetli kullanıcı onboarding'i, tenant'a bağlı session'lar, üyelik başına trading account'ları ve sunucu tarafında uygulanan salt okunur VIEWER yetkisi.

## Seçilen internal-exchange ürünü için kalan işler

Ürün sahibi harici bağlantısı olmayan, ücret gerektirmeyen bir platform seçti. Aşağıdaki işler ücretli sağlayıcılardan bağımsız temel ürün çalışmalarını kapsar:

1. **Tenant yönetimi**: üye listeleme, rol değiştirme, aktif emirleri iptal ederek sıralı üyelikten çıkarma ve owner workspace kapatma uygulandı. Desktop yönetim UI'ı da hazır; son çok kullanıcılı uçtan uca kabul ve eşzamanlı üyelik iptali/emir gönderimi için release ortamında kanıt gerekir.
2. **Matching ve concurrency kanıtı**: deterministik/property test'leri, aynı symbol üzerinde paralel submit/cancel kapsamı ve 100 tekrarlı cancel ile crossing fill yarış testi uygulandı. Daha uzun soak test'leri, gerçekçi yüksek hacim yükleri ve release donanımında ölçülmüş latency/throughput hâlâ gereklidir.
3. **Ledger ve settlement kanıtı**: H2 testleri post-commit hata enjeksiyonunu, idempotent replay'i, eşzamanlı hold işlemlerini ve bakiye-journal mutabakatını kapsar. `GET /api/v1/admin/ledger/reconciliation` ADMIN'e özel rapor sunar ve yerel release verifier bunu kontrol eder. 2026-10-13'te proje sahibi yerel Docker PostgreSQL üzerinde `scripts/test-postgres-integration.ps1` komutunu başarıyla çalıştırdı: izole schema'da 40 eşzamanlı hold, event delivery post-commit hatası ve recovery, reconnect sonrasında settlement replay, eski journal ownership migration ve reconciliation başarılı oldu. Ardından gateway yeniden derlendi; 38 account/asset çiftinin tamamı için mismatch olmadan yerel reconciliation geçti. Önceki izole PostgreSQL backup restore çıktısı `journal_entries,orders,trades=18274,4732,4` ve dengeli bir ledger bildirdi; bu, failover kanıtından ayrı bir backup/restore doğrulamasıdır.
4. **Market-maker kontrolleri**: admin kill switch, database'de tutulan enable state, quote freshness, quote başına notional cap, symbol başına inventory notional cap ve kullanılabilir envanterle sınırlı satış uygulandı. Otomatik testler kill/resume isteklerini yarıştırır; son kill işleminin bekleyen maker emirlerini iptal ettiğini ve bloke bakiyeyi serbest bıraktığını doğrular. Deployment düzeyinde bot/emir yük doğrulaması hâlâ gereklidir. Kill/resume kontrolü global admin'ler için desktop workspace dialog'unda bulunur.
5. **Güvenlik ve production operasyonları**: bağımsız threat/security review, MFA/account recovery, deployment için TLS ve secret management, network/least-privilege policy'leri, off-host backup, aksiyona dönüştürülebilir alert'ler ve incident prosedürleri.
6. **Release kabulü**: desteklenen kullanıcı/yük profillerini, SLO'ları, kapasite sınırlarını, fault-injection senaryolarını, migration/rollback prosedürünü ve UI accessibility/usability kabulünü tanımlayın. Proje sahibi Windows 1.0.3 MSI'ın kurulu olduğunu ve `scripts/verify-local-release.ps1` kontrolünün 2026-10-13'te geçtiğini bildirdi; UP health, gerekli bağımlılıklar, ledger reconciliation, installer varlığı ve kurulu sürüm doğrulandı. Daha uzun load/soak testleri ve UI accessibility/usability kabulü sürüyor. Android APK'nın derlenip yüklendiği bildirildi; emulator/cihaz iş akışının kabulü tamamlanmadı.

Bu gereksinimler dahili ve simüle bir exchange içindir. Ürün ileride gerçek dış emir veya fon kabul edecekse aşağıdaki ayrı sağlayıcı/hukuk çalışmaları da zorunlu hâle gelir.

## Yalnızca gerçek dış işlemler veya fonlar eklenecekse gereken ek işler

Bu maddeler ürün, sağlayıcı veya yargı bölgesi kararlarına bağlıdır ve mevcut paper-trading kapsamının dışındadır. Her madde için sorumlu, uygulama, entegrasyon kanıtı ve operasyon runbook'u tanımlanmadan gerçek müşteri fonlarını etkinleştirmeyin.

1. **Execution venue'ları**: Crypto venue'larını ve BIST broker'ını seçin; sandbox ve production erişimi alın; symbol eşlemesi/emir semantiğini netleştirin. Acknowledgement, partial fill, reject, cancel yarışı, kesinti ve yinelenen teslim durumları için reconciliation içeren venue adapter'ları geliştirin. Binance public market data bir execution adapter değildir.
2. **Piyasa verisi hakları**: İsteğe bağlı istfin adapter'ı BIST kotasyonları için bağlıdır ve `ISTFIN_API_KEY` ayarlanana kadar `NOT_CONFIGURED` döndürür. Etkinleştirmeden önce hedef deployment için sağlayıcının güncel gecikmesini, yetki kapsamını ve gösterim/yeniden dağıtım haklarını doğrulayın. Açıkça seçilen demo mode simüle veridir; sağlayıcı key'i veya data license pakete dahil değildir.
3. **Custody ve nakit hareketleri**: lisanslı custody/bank/payment sağlayıcılarını seçin; deposit onayı, withdrawal onayı, imzalama/key custody, limitler, idempotency, reconciliation, reversal ve audit trail akışlarını uygulayın. Mevcut deposit/withdrawal route'ları bilerek 503 döndürür.
4. **Müşteri yaşam döngüsü ve uyumluluk**: desteklenen ülkeleri/müşteri türlerini belirleyin ve yetkin hukuk/compliance incelemesi alın. Bu kararlara göre gerekli identity verification, sanctions/AML kontrolleri, consent, privacy retention, account recovery, support ve regulatory reporting akışlarını uygulayın.
5. **Production güvenliği**: uygulama için threat model hazırlayıp bağımsız inceleme yaptırın; ürüne uygun MFA ve account/session kontrolleri ekleyin; admin endpoint'lerini koruyun; TLS, secret manager/KMS, key rotation, dependency/image scanning, patch sorumluluğu ve security incident response yapılandırın.
6. **Production operasyonları**: yönetilen ve kimlik doğrulamalı servisler sağlayın; network policy ve least-privilege identity'leri tanımlayın; şifreli off-host backup ve retention yapılandırın; restore/disaster-recovery tatbikatı yapın; aksiyona dönüştürülebilir alert'ler, dashboard'lar, on-call sorumluluğu, kapasite hedefleri ve incident prosedürleri ekleyin.
7. **Release kanıtı**: availability/latency/data freshness hedeflerini belirleyin; load ve fault-injection çalıştırın; migration/rollback prosedürlerini gözden geçirin; venue/custody sandbox sertifikasyonunu, accessibility/usability incelemesini ve bağımsız accounting/security incelemesini tamamlayın.

## ARGENT sahibinin seçmesi veya edinmesi gereken girdiler

Mevcut yerel stack'i çalıştırmak için harici credential gerekmez. Gerçek provider adapter'ları geliştirilmeden önce aşağıdaki kararlar ve sandbox erişimi sağlanmalıdır:

- BIST broker'ı ve lisanslı market-data sağlayıcısı; sandbox dokümantasyonu ve test hesabıyla birlikte.
- Crypto execution venue'ları ve sandbox dokümantasyonu. Trade-only sandbox credential'larıyla başlayın; withdrawal yetkili key vermeyin.
- Custody/bank/payment sağlayıcısı ve sandbox hesabı; sağlayıcının imzalama modeli de net olmalıdır. Private signing key'leri KMS/HSM veya eşdeğer bir secret sisteminde tutun; sohbete yapıştırmayın veya repository'ye commit etmeyin.
- Desteklenecek müşteri ülkeleri ve hesap türleri; yetkin hukuk danışmanının doğruladığı compliance gereksinimleri.
- Production hosting, domain/TLS ve secret-management tercihleri; seçilen müşteri akışları gerektiriyorsa e-posta/SMS ve identity-verification sağlayıcıları.

Kararlar verildiğinde credential'ları seçilen secret manager'da saklayın ve yalnızca gereken environment variable'ları deployment sırasında sağlayın. `.env.example` yerel geliştirme şablonudur; örnek parolaları production secret'ı değildir.

## Doğrulama kaydı

Proje sahibi `scripts/verify-db-backup-restore.ps1` komutunun başarıyla tamamlandığını bildirdi. İzole restore ledger dengesini doğruladı. Proje sahibi Windows 1.0.3 MSI'ı oluşturup yükledi; gateway ledger migration düzeltmesiyle yeniden derlendikten sonra yerel release kontrolü geçti: health, ledger invariant, matching engine, PostgreSQL, Redis, RabbitMQ, 38 account/asset reconciliation çifti, installer varlığı ve kurulu sürüm. Desktop'ta tenant invitation kabulü, emir fill/cancel ve market-maker kill/resume akışları da denendi. Bunlar yerel mühendislik kontrolleridir; harici production sertifikasyonu değildir.

2026-10-13'te JDK 21 üzerinde `:argent-engine:test`, `:argent-ledger:test`, `:argent-gateway:test`, `:argent-market:test`, `:argent-risk:test` ve `:argent-terminal:compileKotlin` başarılı oldu. Gateway iki bloklamayan Ktor `Principal` deprecation warning'i verdi. Proje sahibi Windows/Docker üzerinde isteğe bağlı PostgreSQL testini çalıştırdı; eski journal ownership migration'ı ve reconciliation dahil test geçti. Gateway yeniden derlendikten sonra 1.0.3 yerel release kabulü de geçti. JMH 1.37 sonucu bir microbenchmark'tır; production kapasite iddiası değildir. Android emulator iş akışı kabulü hâlâ açık.

Internal/sandbox ürün için hemen ele alınması gereken güvenlik işleri threat model ve bağımsız inceleme, MFA/account recovery ve release/deployment hardening'dir. TLS, secret-manager entegrasyonu, network policy'leri, alert routing, backup retention/off-host storage, incident sorumluluğu ve production workload limitleri hedef deployment ve atanmış bir operatör gerektirir. Yerel doğrulama kaydına dayanarak gerçek müşteri fonlarını veya harici emir yürütmeyi etkinleştirmeyin.

Sonraki matching-engine değişikliği, çalışan gateway ve replay/benchmark örneklerinde gereksiz in-memory event history tutulmasını kapatır. Startup replay, izole ve kalıcı olmayan recovery engine için rollback snapshot'larını da atlar; her emrin yalnızca replay sonundaki durumunu kaydeder. 2026-10-13'te proje sahibi, mevcut database replay edilirken gateway başlangıcının yaklaşık 3 dakika 44 saniye sürdüğünü gözlemledi. Yerel verifier artık ilerleme mesajlarıyla beş dakikaya kadar bekliyor; startup replay de optimize edildi. Engine/gateway regression testleri ve desktop Kotlin compilation 2026-10-13'te geçti; buna event retention kapalıyken replay edilmiş fill recovery ve emir iptali/bloke bakiye serbest bırakımıyla eşzamanlı market-maker kill/resume da dahildir. Bu kaynak değişikliklerinden sonra yerel Docker health/release kontrolü henüz bekliyor; daha önce bildirilen 1.0.3 kabulü bu değişikliklerden önce yapıldı.
