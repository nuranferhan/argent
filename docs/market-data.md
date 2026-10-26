# Piyasa verileri ve Binance Spot Testnet

ARGENT, piyasa verisi ve emir yürütme için ayrı akışlar kullanır: Binance public crypto kotasyonları, canlı feed olmadığında paper-simulated BIST ve ek enstrüman fiyatları, isteğe bağlı sağlayıcı kaynaklı BIST kotasyonları, kullanıcı tarafından sağlanan BIST geçmiş verisi replay'i, dahili matching ve Binance Spot Testnet'e gönderilen emirler. Piyasa verisi, gerçek parayla işlem yapıldığı anlamına gelmez.

## Public crypto fiyatları

Varsayılan ayar `MARKET_DATA_MODE=BINANCE_PUBLIC` değeridir: gerçek crypto ticker'ları Binance public WebSocket üzerinden API key olmadan alınır. BIST fiyatları varsayılan olarak başlangıç değerleri belirlenmiş, paper etiketi taşıyan ve küçük bir random walk kullanan simülasyonla üretilir. `ISTFIN_BIST` seçildiğinde canlı kotasyon eksik veya eskiyse bu fallback çalışmaya devam eder; güncel sağlayıcı kotasyonları önceliklidir. Borsa İstanbul piyasa verisini yetkili veri dağıtım şirketleri üzerinden sunar. Public bir web sayfası veya resmi olmayan scraper, ARGENT'te feed gösterme izninin yerine geçmez. İsteğe bağlı istfin adapter'ı API key ve sağlayıcı yetkisi gerektirir; etkinleştirmeden önce güncel fiyatı, gecikmeyi, yetki kapsamını ve gösterim haklarını doğrulayın. Sağlayıcı key'ini yok sayılan yerel `.env` dosyasında veya deployment secret store içinde tutun; commit etmeyin. Normal ARGENT emirleri BIST'e değil dahili matching engine'e gider. `MARKET_DATA_MODE=SIMULATED`, her enstrüman için paper fiyatlarını seçer. Nasdaq referans kotasyonları ve haber başlıkları Finnhub API key ile etkinleştirilebilir; sağlayıcı limitleri ve gösterim lisansı geçerlidir. Binance Spot Testnet emir yürütmesi ayrı yapılandırılır ve yalnızca Testnet'e ait kimlik bilgilerini kullanır.

Yerel geliştirme için key'i yok sayılan kök `.env` dosyasına ekleyip gateway'i yeniden derleyin. Key'i sohbete yapıştırmayın veya commit etmeyin:

```dotenv
MARKET_DATA_MODE=ISTFIN_BIST
ISTFIN_API_KEY=your_provider_key
```

Gateway, yapılandırılmış tüm BIST sembollerini tek istekte sorgular; `429` retry ipuçlarına uyar ve sağlayıcı hatalarında bekleme süresini artırır. Kotasyonlarda sağlayıcının `asOf` zamanı bulunur. Sağlayıcı bid/ask döndürmüyorsa sentetik bid/ask üretilmez. API key tek başına piyasa verisini yeniden dağıtma veya gösterme hakkı vermez; ARGENT'te planladığınız gösterim biçimi için gerekli izni alın.

## Binance Spot Testnet

Binance Spot Testnet için özel kimlik bilgileri oluştururken Binance'in [resmî Spot Testnet şartlarını ve dokümantasyonunu](https://developers.binance.com/en/docs/products/spot/testnet/TESTNET-TERMS-OF-USE) izleyin. Production key kullanmayın. Her iki değeri de repository kökündeki yok sayılan yerel `.env` dosyasında tutun:

```dotenv
BINANCE_SPOT_TESTNET_API_KEY=your_testnet_key
BINANCE_SPOT_TESTNET_SECRET=your_testnet_secret
```

`.env` dosyasını commit etmeyin, kimlik bilgilerini sohbete yapıştırmayın ve hiçbir API key için withdrawals özelliğini açmayın. Değerleri ekledikten sonra yerel gateway'i yeniden başlatın/derleyin:

```powershell
docker compose --profile app up -d --build gateway
```

Gateway sağlıklı duruma geldikten sonra masaüstü uygulamasında global `ADMIN` hesabıyla oturum açıp **Çalışma alanı** bölümünü açın. Spot Testnet paneli kimlik bilgilerinin ayarlı olup olmadığını gösterir. LIMIT (GTC/IOC/FOK) ve MARKET emirleri, durum sorgulama ve iptal işlemleri yalnızca `https://testnet.binance.vision` adresine gider.

Yeni bir Testnet emri gönderildiğinde ARGENT, client/order eşlemesini yerel olarak kaydeder ve isteği göndermeden önce gerekli simüle varlığı rezerve eder. Her fill yalnızca bir kez ARGENT ledger'ına işlenir; bunun için Binance'in emir başına trade execution kayıtları alınır. Base/quote commission ve ilgili varlıkta yeterli simüle bakiye varsa üçüncü varlık commission'ları da hesaba katılır. Partial fill durumunda bakiyeler güncellenirken açık emir miktarı rezerve kalır. İptal veya terminal durumdaki tamamlanma, kullanılmayan rezervi serbest bırakır. Gateway çalışırken background poller aktif emirlerin durumunu eşitler ve restart sonrasında izlenen aktif emirleri yeniden takibe alır. Fill geçmişi eksikse veya commission yerel olarak settlement edilemiyorsa güvenlik amacıyla emir `SETTLEMENT_REVIEW` durumuna alınır ve bakiye blokesi korunur.

ARGENT'in başlangıçta verdiği geliştirme bakiyeleri **Binance Testnet'ten alınmaz**; bu nedenle Testnet cüzdanının başlangıç bakiyesiyle aynı olmayabilir. İşlemlerden sonra ARGENT, fill farklarını ve commission'ları kendi simüle bakiyelerine yansıtır. Binance ise Testnet cüzdanındaki fonları ayrıca kontrol eder. Testnet bakiyeleri ve gerçek emirler Binance sandbox'ında kalır; gerçek para kullanılmaz.

Gateway tek bir yerel Testnet key pair kullanır; bu nedenle erişim yalnızca ADMIN rolüne açıktır. Bu özellik paylaşılan/public deployment için değil, yerel geliştirme içindir. Key ve secret environment variable'lardan okunur; status endpoint'inden döndürülmez ve bilerek log'lara yazılmaz. Testnet host'u kod içinde sabittir; production endpoint yapılandırması yoktur.

Admin API:

- `GET /api/v1/admin/binance-spot-testnet` — iki kimlik bilgisinin ayarlı olup olmadığını bildirir.
- `POST /api/v1/admin/binance-spot-testnet/orders` — JSON: `{"symbol":"BTCUSDT","side":"BUY","type":"LIMIT","quantity":"0.001","price":"30000","timeInForce":"GTC","clientOrderId":"argent-demo-1"}`. `type`, `LIMIT` veya `MARKET`; LIMIT için `timeInForce`, `GTC`, `IOC` veya `FOK) olabilir.
- `GET /api/v1/admin/binance-spot-testnet/orders/{orderId}?symbol=BTCUSDT` — emir durumunu sorgular.
- `DELETE /api/v1/admin/binance-spot-testnet/orders/{orderId}?symbol=BTCUSDT` — emri iptal eder.

Tüm endpoint'ler ARGENT admin Bearer token'ı gerektirir. Exchange tarafından reddedilen emirler (örneğin geçersiz symbol filter veya yetersiz Testnet bakiyesi) hata olarak bildirilir; adapter emri sessizce dahili matching'e yönlendirmez.

## BIST geçmiş verisi replay'i

Canlı BIST geçmiş verisi replay'i isteğe bağlıdır; varsayılan BIST paper-price simulator bundan bağımsız çalışır. Replay API, yerel admin'in sağladığı CSV dosyasını kabul eder. ARGENT BIST verisini indirmez, yeniden dağıtmaz veya bu verinin kullanım hakkını sağlamaz. Dosyaları içe aktarmadan ya da paylaşmadan önce veri sağlayıcısının şartlarını kontrol edin. Lisanslı veri setlerini veya özel verileri GitHub'a eklemeyin.

Kabul edilen CSV sütunları ve sıraları:

```csv
timestamp,symbol,price,volume24h
2026-01-02T10:00:00Z,THYAO,300.25,1000
2026-01-02T10:01:00Z,THYAO,300.50,1250
```

`volume24h` isteğe bağlıdır. Timestamp değerleri Unix milliseconds veya ISO-8601 olabilir; satırlar zaman sırasına göre olmalıdır. Semboller yapılandırılmış equity enstrümanlarından biri olmalıdır (`THYAO`, `GARAN`, `ASELS`, `EREGL`, `SISE`). Limitler: 50.000 satır ve 2 MiB. `speed` değeri timestamp aralıklarını böler; varsayılan `1000`, bir dakikayı yaklaşık 60 ms'ye sıkıştırır. Uzun aralıklar beş saniyeyle sınırlandırılır. Replay sırasında equity simulator duraklar; crypto simülasyonu/public data akışı devam eder.

ARGENT admin token'ı aldıktan sonra PowerShell ile yerel CSV'yi gönderebilirsiniz:

```powershell
$csv = Get-Content .\bist-history.csv -Raw
$payload = @{ csv = $csv; speed = 1000 } | ConvertTo-Json -Compress
Invoke-RestMethod http://127.0.0.1:8080/api/v1/admin/market-data/bist/replay `
  -Method Post -Headers @{ Authorization = "Bearer $token" } `
  -ContentType "application/json" -Body $payload

Invoke-RestMethod http://127.0.0.1:8080/api/v1/admin/market-data/bist/replay `
  -Headers @{ Authorization = "Bearer $token" }
```

Yanıt replay ID'si, state, satır sayısı ve işlenen satır sayısını içerir. Ticker güncellemeleri normal ticker stream'ine `bist-csv-replay` kaynağıyla gönderilir. Bu, geçmiş verinin oynatılmasıdır; lisanslı canlı BIST feed'i veya BIST işlem yeri değildir.

Binance Spot Testnet imzalı istekleri, yerel saat sapmasına tolerans göstermek için sabit Testnet server-time endpoint'iyle timestamp eşlemesi yapar. Kimlik bilgileri gateway üzerinde kalır ve desktop client'a gönderilmez.
