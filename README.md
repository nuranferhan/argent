# ARGENT — Multi-Asset Trading & Settlement Platform

![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&logoColor=white) ![JDK 21](https://img.shields.io/badge/JDK-21-007396?logo=openjdk&logoColor=white) ![Ktor](https://img.shields.io/badge/Ktor-087CFA) ![PostgreSQL](https://img.shields.io/badge/PostgreSQL-4169E1?logo=postgresql&logoColor=white) ![Redis](https://img.shields.io/badge/Redis-DC382D?logo=redis&logoColor=white) ![RabbitMQ](https://img.shields.io/badge/RabbitMQ-FF6600?logo=rabbitmq&logoColor=white) ![Docker](https://img.shields.io/badge/Docker-2496ED?logo=docker&logoColor=white) ![Compose](https://img.shields.io/badge/Compose-Desktop%20%2B%20Android-4285F4?logo=jetpackcompose&logoColor=white)

BIST hisseleri ve kripto paritelerini tek çatı altında işleyen; emir eşleştirme, canlı veri, portföy/risk ve çift girişli muhasebeyi olay tabanlı bir mimaride birleştiren mini borsa + aracı kurum.

**JDK 21 · Kotlin · Ktor · PostgreSQL · Redis · RabbitMQ · Compose Desktop + Android**

Varsayılan yerel çalışma kipinde ARGENT ücretsiz Binance public WebSocket'inden kripto kotasyonlarını alır; API anahtarı gerekmez, internet bağlantısı gerekir. BIST fiyatları simüledir. İnternetsiz geliştirme için `MARKET_DATA_MODE=SIMULATED` seçilebilir. Emirler ARGENT'in dahili eşleştirme motorunda işlenir; Binance public veri kipinde dışarı emir gönderilmez. Admin tarafından ayrıca yapılandırıldığında **Binance Spot Testnet** emir gönderme, sorgulama ve iptal akışını test eder. Testnet fill'leri ARGENT'in dahili eşleştirme motoruna girmez; gerçekleşen miktarlar, komisyonlar ve ayrılan/kalan rezerv ARGENT'in sanal ledger'ına işlenir. Bu sanal başlangıç bakiyesi Binance Testnet bakiyesinin içe aktarımı değildir; gerçek para değildir. Yerel CSV replay yalnızca kullanıcının kullanım hakkı olan verilerle çalışır. Bu proje gerçek borsa erişimi, lisanslı canlı BIST verisi veya müşteri fonu saklama hizmeti sunmaz.

## Çalıştırmadan önce (Windows 11)

| Ne | Gerekli mi | Not |
|---|---|---|
| Docker Desktop | Evet | PostgreSQL, Redis, RabbitMQ, Prometheus, Grafana |
| JDK 21 | Evet | `java -version` → 21 |
| `JWT_SECRET` | Yerelde varsayılan var | En az 32 karakter. Üretim/staging'de zorunlu ve gizli yönetilmeli |
| `BOOTSTRAP_ADMIN_EMAIL` / `BOOTSTRAP_ADMIN_PASSWORD` | Evet | İlk veritabanı kurulumunda admin oluşturulur; mevcut veritabanında mevcut admin bilgileriyle giriş yapılır |
| Dış veri API anahtarı | **Hayır** | Kriptoda varsayılan ücretsiz Binance public market-data akışı; internet gerekir. BIST simüledir |
| Binance emir API anahtarı | **Hayır** | Yalnızca ayrıca etkinleştirilen Spot Testnet için isteğe bağlı; production URL/kodu yok |
| `.env` içine kart / özel anahtar | **Hayır** | |

PowerShell'de kendi `JWT_SECRET`, `BOOTSTRAP_ADMIN_EMAIL` ve `BOOTSTRAP_ADMIN_PASSWORD` değerlerinizi başlatma betiğinden önce `$env:` değişkenleri olarak tanımlayın veya `.env.example` dosyasını `.env` olarak kopyalayıp doldurun. `scripts\start-stack.ps1` ve `scripts\start.ps1`, admin bilgileri verilmemişse e-postayı sorar ve parolayı gizli giriş olarak alır; parolayı proje dosyasına yazmaz. Admin hesabı yalnızca ilk veritabanı kurulumunda oluşturulur; mevcut hesabın parolası ortam değişkenini değiştirerek sıfırlanmaz. Tekrar başlatırken aynı hesabın bilgilerini kullanın.

Yerel geliştirme bağımlılıklarının varsayılanları (yalnızca localhost, gerçek ortamda kullanmayın):

```
# Yalnızca localhost geliştirmesi içindir; üretim sırları kullanmayın.
JWT_SECRET=argent-dev-secret-change-me-in-prod-32chars
RABBITMQ_URL=amqp://argent:argent@127.0.0.1:5672
```

Kendi sırlarınızı kullanacaksanız PowerShell’de `start.ps1` öncesi:

```powershell
$env:JWT_SECRET = "en-az-32-karakter-uzunlugunda-gizli-anahtar!"
$env:BOOTSTRAP_ADMIN_EMAIL = "siz@sirket.com"
$env:BOOTSTRAP_ADMIN_PASSWORD = "GucluSifre1!"
```

Docker Compose içindeki Postgres/Redis/Rabbit/Grafana şifreleri **yalnızca localhost** içindir (`argent` / `argent`). Bunları internete açmayın.

## Tek komutla ayağa kaldırma

Depoyu klonlayın ve PowerShell'i depo kök dizininde açın:

```powershell
.\scripts\start-stack.ps1
```

İsteğe bağlı olarak yalnızca Windows masaüstü terminali için MSI kurulum paketi üretin:

```powershell
.\scripts\package-terminal.ps1
```

Paketleme tam JDK 21 (`jpackage.exe`) ve WiX 3 gerektirir; Gradle WiX'i ilk kullanımda internetten indirir. Betik Java ayarını işlem süresince JDK 21'e alır ve sonrasında geri yükler. Çıktı `argent-terminal\build\compose\binaries` altındadır. Bu paket terminali içerir; PostgreSQL/Redis/RabbitMQ ve gateway ayrıca `start-stack.ps1` ile çalıştırılır.

### Android uygulaması

Android istemcisi Kotlin ve Jetpack Compose ile aynı gateway API'sini kullanır. Giriş/kayıt/davet, piyasa sembolleri ve canlı kotasyonlar, grafik ve emir defteri, LIMIT/MARKET/IOC/FOK emirleri, portföy, açık emir iptali, işlemler, tenant geçişi/üye daveti-yönetimi ve market maker kill switch akışları telefona uyarlanmıştır. Android Studio/SDK, JDK 17+ gerekir; proje `compileSdk 36` kullanır.

```powershell
# APK oluştur
.\scripts\package-android.ps1

# USB hata ayıklama açık, bağlı Android telefona yükle ve gateway için adb reverse kur
.\scripts\install-android.ps1
```

APK: `argent-android\build\outputs\apk\debug\argent-android-debug.apk`. Emülatörde gateway URL `http://10.0.2.2:8080`; USB ile bağlı telefonda yükleme betiği `adb reverse tcp:8080 tcp:8080` kurar ve uygulamada `http://127.0.0.1:8080` kullanılabilir. Debug yapısı yerel HTTP gateway'e izin verir; release manifesti açık metin trafiğini kapatır, dolayısıyla release/staging sunucusu HTTPS kullanmalıdır. Debug APK yalnızca yerel sandbox içindir, imzalı yayın paketi değildir.

### ARGENT AI Asistanı

Terminal ve Android'deki ARGENT AI sekmesi varsayılan olarak kapalıdır. Groq API kullanımı hesap planına, seçilen modele ve sağlayıcının güncel fiyatlandırma ile kullanım limitlerine tabidir; ücretsiz erişim ve kotalar garanti edilmez ve değişebilir. Groq Console'dan API anahtarı oluşturup `.env` dosyasındaki `ARGENT_AI_API_KEY` değerine ekleyin; varsayılan URL ve model Groq uyumludur. Gateway'i yeniden oluşturun (`docker compose --profile app up -d --build gateway`). Asistan portföy, emir/işlem geçmişi ve seçili piyasa verisini yanıt üretmek için Groq'a iletir. Sağlayıcının belgelerine göre çıkarım verileri varsayılan olarak saklanmaz; güvenilirlik/abuse incelemesi için istisnai durumlarda 30 güne kadar geçici kayıt olabilir. Gerçek kişisel/finansal verileri dış servise göndermeyin; ARGENT demo hesap verileriyle kullanın. Anahtar boşken hesap verisi sağlayıcıya gönderilmez. Asistan yalnızca okur ve açıklar; emir gönderemez, iptal edemez veya bakiye değiştiremez. Sohbette kaynak, piyasa modu ve veri zamanı gibi teknik bilgiler gösterilmez. Günlük karşılaştırma veya sıra dışı bakiye değişimi gibi geçmiş veri gerektiren sorularda ilgili tarihsel veri yoksa asistan bunu belirtmelidir.

Bu komut Docker Desktop'ı, JDK 21'i ve internet bağlantısını gerektirir. Alternatif olarak gateway'i Gradle ile çalıştırmak için altyapıyı ayrı başlatın:

```powershell
docker compose up -d
.\scripts\start.ps1
```

İlk kurulumda Compose gateway imajını oluşturur ve Gradle bağımlılıklarını indirir; bu nedenle ilk açılış daha uzun sürebilir. Servisler yalnızca yerel bilgisayara (`127.0.0.1`) port açar. Durum ve loglar: `docker compose --profile app ps` ve `docker compose --profile app logs -f gateway`.

Gradle wrapper JAR yoksa (ilk kez):

```powershell
gradle wrapper --gradle-version 8.11.1
```

veya makinede `gradle` yoksa [Gradle 8.11.1](https://gradle.org/releases/) kurup aynı komutu çalıştırın.

## Çalışma kipleri ve sınırlar

- Varsayılan yerel açılış `MARKET_DATA_MODE=BINANCE_PUBLIC` ile tanımlı kripto paritelerine Binance public WebSocket kotasyonu, BIST sembollerine sentetik sandbox fiyatı sağlar; Binance API anahtarı gerekmez, internet gerekir.
- İnternetsiz çalışma için `MARKET_DATA_MODE=SIMULATED` tüm tanımlı kripto ve BIST sembollerine sentetik sandbox fiyatı üretir.
- Simülasyon kipi sabit tohumlu, sembol başına stokastik fiyat yolu kullanır; bu üretilmiş fiyatlar gerçek piyasa kotasyonu değildir ve gerçek emir yürütme/uyum yerine geçmez.
- `MARKET_DATA_MODE=BINANCE_PUBLIC`, kripto piyasa verisini Binance public WebSocket'ten alır ve BIST sembolleri için sentetik sandbox verisi kullanır; emirler dışarı iletilmez.
- Binance Spot Testnet emirleri yalnızca `BINANCE_SPOT_TESTNET_API_KEY` ve `BINANCE_SPOT_TESTNET_SECRET` yerel ortam değişkenleri birlikte ayarlanırsa açılır. Bu anahtarlar sadece Testnet API izinleriyle oluşturulmalı ve `.env` içinde tutulmalıdır; `.env` Git tarafından ignore edilir. API endpoint'i production host'a gönderemez. Testnet uçları yalnızca global ADMIN'e açıktır; Binance Testnet'te gerçekleşen fill'ler ledger'da tekilleştirilerek işlenir. Testnet hesabında da sanal fon bulunması gerekir.
- Desktop `Çalışma alanı` penceresindeki Binance Spot Testnet bölümü etkinlik durumunu gösterir ve test emri gönderme, durum sorgulama, iptal işlemlerini sunar. LIMIT emirlerde GTC/IOC/FOK; ayrıca MARKET emri desteklenir. Emir gönderiminde ARGENT'te gereken varlık geçici rezerve edilir; fill'ler ve komisyonlar ledger'a, iptal/tamamlanmada kullanılmayan rezerv kullanılabilir bakiyeye döner. Gateway açıkken açık emirler periyodik olarak sorgulanır; yansıtmada bir sorun olursa rezerv otomatik çözülmez ve durum inceleme için işaretlenir. ARGENT'in demo başlangıç tutarları Binance Testnet bakiyesini otomatik kopyalamaz.
- BIST varsayılanı `SIMULATED` kalır. Admin'in `POST /api/v1/admin/market-data/bist/replay` çağrısı, `timestamp,symbol,price[,volume24h]` başlıklı kullanıcı CSV'sini zaman sıralı olarak ticker akışına oynatır; timestamp Unix milisaniye veya ISO-8601 olabilir. Yalnızca tanımlı EQUITY sembolleri kabul edilir (azami 50.000 satır/2 MiB); `speed` alanı zaman aralıklarını böler (varsayılan 1000), replay sürerken BIST simülatörü duraklar. Durum: `GET /api/v1/admin/market-data/bist/replay`. CSV örneği ve komut [docs/market-data.md](docs/market-data.md) içinde. Replay özelliği veri lisansı sağlamaz; veri kaynağının kullanım/yeniden dağıtım koşullarını kullanıcı doğrular.
- Aynı kullanıcının alış/satış emirlerinin çapraz defter oluşturmasını önlemek için kendi bekleyen likiditesiyle kesişen yeni emir taker seviyesinde iptal edilir.
- Para yatırma/çekme API uçları, harici sağlayıcı ve mutabakat entegrasyonu olmadığı için `503 Service Unavailable` döndürür; ledger bakiyesini tek başına değiştirmez.
- Yerel geliştirme başlatıcısı paper bakiye ve dahili likidite sağlayıcısını etkinleştirir; bu bakiyeler gerçek para değildir. `ARGENT_ENV` dev dışında ise bu tohumlama güvenlik nedeniyle reddedilir.
- Health göstergesi altyapı sağlığını, matching engine hazır durumunu, yürütme kipini ve veri kaynağı durumunu ayrı gösterir. `INTERNAL_MATCHING`, dış borsa emri anlamına gelmez.
- Eşleştirme durumunu değiştiren bir işlem hata verirse order book değişikliği geri alınır ve engine yeni emir/iptalleri durdurur. Belirsiz emrin hold'u korunur; gateway yeniden açılırken kalıcı olaylardan kurtarma yapılır, olaysız kalan `PENDING` emir reddedilip hold'u bırakılır.
- Genel API sınırı IP başına dakikada 120 istektir; login 10, kayıt 5, token yenileme 20 istek/dakika ile ayrıca sınırlandırılır. Limit sayacı Redis'te atomik güncellenir; Redis yoksa API güvenli biçimde isteği reddeder.

Bu depo şu anda profesyonelce çalıştırılabilir bir **iç eşleştirme motoru ve terminal** iskeletidir; canlı finansal aracı kurum/kripto borsası değildir. Üretimde gerçek varlıkla işlem için yetkili borsa/broker emir adaptörü, lisanslı BIST veri sağlayıcısı, saklama/ödeme ve mutabakat sağlayıcısı, kullanıcı doğrulama/uyum süreçleri, operasyonel alarm ve felaket kurtarma tatbikatları bağlanıp ayrıca denetlenmelidir. Bu entegrasyonlar için sağlayıcı ve sözleşme bilgileri olmadan gerçek emir veya para hareketi güvenle tamamlanamaz.

## Arayüz (Compose Desktop)

Terminal: koyu trading desk — piyasalar, canlı grafik, emir defteri, LIMIT/MARKET/IOC/FOK, çoklu para birimi değerlemesi, PnL ve risk göstergeleri. Fiyatı güncel olmayan varlıklar için değerleme kısmi olarak işaretlenir. Sistem yalnızca tam teminatlı spot işlem sunar; kaldıraç, marjin ve otomatik tasfiye etkin değildir. Emir gönderimi lot/tick doğrulaması yapar ve belirsiz ağ hatalarında aynı idempotency anahtarıyla yeniden denemeyi destekler. Para hareketi kontrolleri harici sağlayıcı bağlanana dek kapalıdır.

1. Gateway: `http://127.0.0.1:8080`
2. Giriş: `BOOTSTRAP_ADMIN_EMAIL` / `BOOTSTRAP_ADMIN_PASSWORD` olarak belirlediğiniz ilk kurulum hesabı
3. Emirler yalnızca ARGENT iç defterine gönderilir; karşı emir yoksa gerçekleşmez.

Grafana: `http://127.0.0.1:3000` → `argent` / `.env` içindeki `GRAFANA_ADMIN_PASSWORD`  
RabbitMQ: `http://127.0.0.1:15672` → `argent` / `.env` içindeki `RABBITMQ_PASSWORD`  
Prometheus: `http://127.0.0.1:9090`

## Mimari

```mermaid
flowchart LR
  T[Compose Terminal] -->|REST + WS JWT| G[argent-gateway]
  G --> R[argent-risk]
  G --> L[argent-ledger]
  G --> E[argent-engine]
  G --> M[argent-market]
  M -->|public market data| B[Binance]
  M -. dev only .-> BIST[BIST sim feed]
  L --> PG[(PostgreSQL)]
  G --> RD[(Redis rate-limit)]
  G --> AMQP[RabbitMQ fan-out]
  G --> Prom[Prometheus / Grafana]
```

Uçtan uca:

1. Emir → risk (lot/tick, güncel piyasa işareti, kullanılabilir bakiye)
2. Ledger hold
3. Sembol actor eşleştirir → `TradeExecuted`
4. Ledger çift giriş settlement (atomik, idempotent `tradeId`)
5. Portföy PnL + WS ile terminal

| Modül | Görev |
|---|---|
| `argent-engine` | `ConcurrentSkipListMap` kitap, fiyat-zaman FIFO, LIMIT/MARKET/IOC/FOK, sembol başı coroutine actor, event replay |
| `argent-market` | Sentetik sandbox veya isteğe bağlı Binance public WS feed adaptörü, Ktor WS, RabbitMQ event fan-out |
| `argent-risk` | Quote varlığı bazında özkaynak/PnL, fiyat tamlığı ve yoğunlaşma skoru; kaldıraç/marjin devre dışı |
| `argent-ledger` | Double-entry, cüzdan, hold/settle, optimistic lock |
| `argent-gateway` | REST + WS, JWT, Redis rate-limit, recovery |
| `argent-terminal` | Compose Desktop trading terminal |
| `argent-android` | Kotlin/Jetpack Compose Android trading client; same gateway, mobile workflows |

## API (özet)

```powershell
$email = Read-Host "ARGENT admin email"
$securePassword = Read-Host "ARGENT admin password" -AsSecureString
$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
try { $password = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer) }
finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer) }
$body = @{ email = $email; password = $password } | ConvertTo-Json -Compress
$login = Invoke-RestMethod http://127.0.0.1:8080/api/v1/auth/login -Method POST -ContentType "application/json" -Body $body
$token = $login.accessToken
$h = @{ Authorization = "Bearer $token"; "Idempotency-Key" = "ord-1" }
Invoke-RestMethod http://127.0.0.1:8080/api/v1/orders -Method POST -Headers $h -ContentType "application/json" -Body '{"symbol":"THYAO","side":"BUY","type":"LIMIT","price":"310","quantity":"10"}'
# ADMIN journal reconciliation report (ok=true and mismatches=[] is the passing result)
Invoke-RestMethod http://127.0.0.1:8080/api/v1/admin/ledger/reconciliation -Headers @{ Authorization = "Bearer $token" }
```

- `POST /api/v1/auth/register|login|refresh`; public registration is dev-only, while production users join through `POST /api/v1/auth/register/invitation` with an email-bound invitation.
- `GET /api/v1/tenants`, `POST /api/v1/tenants/switch`, tenant admin `GET /api/v1/tenants/members`, `PATCH /api/v1/tenants/members/{userId}/role`, `DELETE /api/v1/tenants/members/{userId}`, owner `DELETE /api/v1/tenants/current`, `POST /api/v1/tenants/invitations`, `POST /api/v1/tenants/invitations/accept`
- `GET /api/v1/instruments|orderbook/{symbol}|ticker/{symbol}|trades`
- `POST /api/v1/orders`  (`Idempotency-Key` veya `clientOrderId`)
- `DELETE /api/v1/orders/{id}?symbol=`
- `GET /api/v1/portfolio|balances|positions|orders|fills`
- `VIEWER` tenant membership is read-only; each user/tenant membership is mapped to its own isolated trading account. The shared book does not expose another tenant's account records.
- `POST /api/v1/withdraw` (şimdilik kapalı; harici sağlayıcı entegrasyonu gerekli)
- `POST /api/v1/admin/deposit` (şimdilik kapalı; doğrulanmış yatırma entegrasyonu gerekli)
- `POST /api/v1/admin/replay/{symbol}` (ADMIN, event log'u izole kitapta doğrular; canlı kitabı değiştirmez)
- `GET /api/v1/admin/ledger/reconciliation` (ADMIN, kullanıcı hesabı/varlık bazında journal neti ile available+held toplamını karşılaştırır; uyuşmazlık varsa hesap kimliği ve fark değerlerini döndürür)
- `GET /api/v1/admin/binance-spot-testnet` (ADMIN; yalnızca yapılandırılmış/yapılandırılmamış bilgisini verir, anahtarları döndürmez)
- `POST /api/v1/admin/binance-spot-testnet/orders`, `GET|DELETE /api/v1/admin/binance-spot-testnet/orders/{orderId}?symbol=BTCUSDT` (ADMIN; emir Binance Spot Testnet'e gönderilir, fill'ler ARGENT ledger'ına yansıtılır)
- `POST|GET /api/v1/admin/market-data/bist/replay` (ADMIN, yerel CSV historical replay başlatma/durum)
- `GET /api/v1/admin/market-maker`, `POST /api/v1/admin/market-maker/kill-switch|resume` (global ADMIN; kill switch bot emirlerini iptal eder)
- Market maker stale quotes are cancelled after 10 seconds; `MM_MAX_QUOTE_NOTIONAL` caps each quote and `MM_MAX_INVENTORY_NOTIONAL` caps bot-held base inventory value per symbol. Sell quotes are limited to available ledger inventory.
- `GET /health`  `GET /metrics`
- `WS /ws/ticker`  `/ws/orderbook/{symbol}`  `/ws/portfolio` (`Authorization: Bearer <access-token>`)

## Testler ve benchmark

```powershell
.\gradlew.bat test --no-daemon
.\scripts\bench.ps1
```

- Eşleştirme: aynı emir akışı = aynı trade id / depth (property-based)
- Yarış koşulu: aynı sembolde 100 tur iptal ile karşıt emir/fill yarışı; her emir yalnızca tek bir sıralı sonuç alır
- Replay: `OrderSubmitted` + `OrderCancelled` sırası kitabı yeniden kurar
- Ledger: her asset için `sum(debit) == sum(credit)`; eşzamanlı hold limiti, idempotent deposit/settlement ve hesap bakiyesi-journal mutabakatı drift algılama testi
- Chaos: gateway’i öldürüp `scripts\start.ps1` ile açın; `/health.invariantOk` true kalır, açık emirler Postgres’ten restore edilir (`scripts\chaos-recovery.ps1`)

### Matching benchmark (JMH)

`scripts/bench.ps1`, JMH 1.x tabanlı Gradle benchmarkını çalıştırır. Rapor hem konsola
yazılır hem de `argent-engine/build/reports/jmh/results.json` konumuna kaydedilir.
Benchmark, eşleştirme çekirdeğini ağ ve veritabanı maliyetlerinden ayrı ölçer;
sonuçlar JDK, CPU ve işletim sistemine göre değişir. Her iterasyonda deterministik
100 seviyeli kitap kurulur ve çapraz emirle eşleştirme veya deftere pasif limit emir
ekleme ölçülür. Bu mikro benchmark uçtan uca sistem kapasitesi iddiası değildir.
Yerel JDK 21.0.12 çalıştırmasında (JMH 1.37, 3×2 s warmup, 5×2 s ölçüm)
100 fiyat seviyesini tüketen eşleştirme yaklaşık 17,0 bin işlem/s, pasif limit
emir ekleme yaklaşık 746 bin işlem/s ölçülmüştür. Özellikle pasif ekleme ölçümünün
güven aralığı geniştir; bu değerler yalnızca o makine ve o çalıştırma için örnektir.

## ADR

- [0001 BigDecimal](docs/adr/0001-bigdecimal.md)
- [0002 Sembol actor](docs/adr/0002-symbol-actor.md)
- [0003 Ledger locking](docs/adr/0003-optimistic-locking.md)
- [0004 Event sourcing](docs/adr/0004-event-sourcing.md)

## Observability

Gateway `/metrics` (Micrometer/Prometheus). Compose Grafana panosu `ops/grafana/dashboards/argent.json` otomatik yüklenir.

Windows yerel stack ve teslim paketini kontrol etmek için servisler açıkken `.\scripts\verify-local-release.ps1` çalıştırın. Script yöneticiyle oturum açıp ledger mutabakatını doğrular ve geçici refresh token'ı sonunda iptal eder; işlem veya bakiye değiştirmez. PostgreSQL üzerinde gerçek servis testi için `.\scripts\test-postgres-integration.ps1` kullanın. Başlatma, durdurma, yedekleme ve arıza adımları [operasyon runbook'unda](docs/operations-runbook.md) yer alır.

## Veritabanı yedekleme ve geri yükleme

Yerel PostgreSQL veritabanını özel biçimli, zaman damgalı arşive yedekleyin:

```powershell
.\scripts\backup-db.ps1
# Farklı klasör:
.\scripts\backup-db.ps1 -OutputDirectory D:\argent-backups
# Yedeği ayrı, geçici PostgreSQL veritabanına geri yükleyip doğrula:
.\scripts\verify-db-backup-restore.ps1
```

Geri yüklemeden önce gateway'i durdurun ve yedeğin doğru olduğunu kontrol edin. Geri yükleme veritabanındaki mevcut nesneleri değiştirir; script yalnızca `RESTORE ARGENT` onayını yazınca ilerler:

```powershell
docker compose --profile app stop gateway
.\scripts\restore-db.ps1 .\backups\argent-postgres-YYYYMMDD-HHMMSS.dump
```

Geri yükleme tamamlandıktan sonra `docker compose --profile app up -d gateway` ile gateway'i başlatın ve `/health` durumunu, bakiyeleri ve açık emirleri doğrulayın. Yedekleri proje klasörü dışında, erişimi kısıtlı ve şifreli bir yerde saklayın; bu komutlar otomatik bulut kopyası veya felaket kurtarma garantisi sağlamaz.

## Ürünleştirme kapıları

Bu kod gerçek para veya canlı borsa emirleriyle kullanılmamalıdır. Gerçek ürüne geçişten önce seçilecek BIST broker/veri sağlayıcısı, kripto venue ve custody sağlayıcısı için adapter ve sandbox entegrasyon testleri; para yatırma/çekmede çift taraflı mutabakat; kayıt/hesap doğrulama ve yetki süreçleri; güvenlik denetimi; sırların secret manager'da tutulması; veritabanı yedekleme/geri yükleme testleri; izleme, alarm, incident ve destek süreçleri tamamlanmalıdır. Bu gereksinimler kod içinde taklit edilmemiştir.

Kalan ürünleştirme kapıları ve hangi sağlayıcı bilgilerinin daha sonra gerekeceği için [üretime hazırlık listesini](docs/production-readiness.md) inceleyin.

## Lisans

MIT
