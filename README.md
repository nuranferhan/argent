# ARGENT - Paper Trading Platform

[![CI](https://github.com/nuranferhan/argent/actions/workflows/ci.yml/badge.svg)](https://github.com/nuranferhan/argent/actions/workflows/ci.yml)  [▶️ Proje videosunu izle](https://drive.google.com/file/d/1ggblq9ZRIR5iLo2keZh55Y5WGbn2pvzC/view?usp=sharing)

ARGENT, kripto ve çoklu varlık piyasalarında emir yaşam döngüsünü, portföyü ve çift girişli muhasebeyi tek uygulamada deneyimleten bir **paper trading platformudur**. Gerçek aracı kuruma emir göndermez; emirler ARGENT eşleştirme motorunda işlenir.

**Kotlin · Ktor · PostgreSQL · Redis · RabbitMQ · Compose Desktop · Android · Docker**

## Mimari ve kullanılan teknolojiler

```mermaid
flowchart TB
    subgraph Clients[İstemciler]
        direction LR
        Desktop[Terminal]
        Android[Android]
    end

    subgraph Gateway[Gateway · Ktor]
        direction LR
        API[REST · WebSocket]
        Auth[Kimlik doğrulama]
        Assistant[ARGENT AI]
        Market[Piyasa verisi]
        Trading[Emir servisi]
    end

    subgraph Core[Eşleştirme ve muhasebe]
        direction LR
        Risk[Risk]
        Matching[Matching Core]
        Engine[Eşleştirme motoru]
        Ledger[Çift girişli ledger]
    end

    subgraph Providers[Veri sağlayıcıları]
        direction LR
        Binance[Binance · Kripto]
        Finnhub[Finnhub · ABD]
        Paper[Paper · BIST]
        Groq[Groq · LLM]
    end

    subgraph Runtime[Altyapı · Docker Compose]
        direction LR
        PostgreSQL[(PostgreSQL)]
        Redis[(Redis)]
        RabbitMQ[(RabbitMQ)]
        Metrics[Prometheus · Grafana]
    end

    Desktop --> API
    Android --> API
    API --> Auth
    API --> Assistant
    API --> Market
    API --> Trading
    Assistant --> Groq
    Trading --> Risk
    Risk --> Matching
    Matching --> Engine
    Matching --> Ledger
    Binance --> Market
    Finnhub --> Market
    Paper --> Market
    Auth --> PostgreSQL
    Trading --> PostgreSQL
    Ledger --> PostgreSQL
    Market --> Redis
    Matching --> RabbitMQ
    API --> Metrics
```

| Katman | Teknoloji | Kullanım amacı |
| --- | --- | --- |
| İstemciler | Kotlin, Compose Desktop, Jetpack Compose, Ktor Client | Masaüstü ve Android arayüzleri; REST ve WebSocket üzerinden gateway iletişimi |
| Gateway | Ktor, Kotlin Coroutines, kotlinx.serialization | HTTP API, WebSocket, JSON, kimlik doğrulama ve istemci isteklerinin yönlendirilmesi |
| Eşleştirme ve muhasebe | `argent-engine`, `argent-ledger`, `argent-matching-core` | Emir defteri/eşleştirme, çift girişli muhasebe ve yeniden kullanılabilir çekirdek API'si |
| Piyasa ve risk | `argent-market`, `argent-risk` | Piyasa akışlarını birleştirme, teknik göstergeler, alarm kontrolleri ve emir öncesi risk denetimleri |
| Kalıcılık ve mesajlaşma | PostgreSQL, Exposed, HikariCP, Redis, RabbitMQ | Kalıcı hesap/emir/ledger verileri, bağlantı havuzu, önbellek ve servis olaylarının iletimi |
| Sağlayıcılar | Binance public stream, isteğe bağlı Finnhub, paper fiyat sağlayıcısı | Kripto akışı; sağlayıcı erişimi varsa ABD referans verisi/haberler; diğer desteklenmeyen enstrümanlar için simülasyon |
| Gözlemlenebilirlik | Micrometer, Prometheus, Grafana | Gateway metriklerini toplama ve panolarda izleme |
| Paketleme ve otomasyon | Docker Compose, Gradle, GitHub Actions | Yerel servis ortamı, çok modüllü derleme ve test/derleme CI iş akışı |

**Emir akışı:** İstemci isteği Ktor Gateway'e gelir; risk kontrollerinden sonra ARGENT eşleştirme motoruna iletilir. Eşleşme ve bakiye kayıtları ledger'a yazılır, kalıcı olaylar PostgreSQL'de saklanır ve ilgili olaylar RabbitMQ üzerinden yayınlanır. Bu akış paper trading içindir; gerçek aracı kuruma emir iletmez.

**Veri akışı:** Kripto kotasyonları Binance public akışından alınır. Finnhub yapılandırılmış ve erişilebilir olduğunda Nasdaq referans kotasyonları, haberler ve fiyat alarmı kontrolleri kullanılır; bunlar ARGENT emir defterinin fiyatı değildir. BIST, fonlar ve sağlayıcısı bulunmayan diğer enstrümanlar paper/simüle fiyatla açıkça etiketlenir. Sağlayıcı kotası, erişimi ve gösterim lisansı sağlayıcı koşullarına bağlıdır.

**AI ve güvenlik:** Groq uyumlu sağlayıcı yapılandırılırsa AI, gateway üzerinden portföy/emir/piyasa bağlamını açıklamak için çağrılır. AI tek başına emir göndermez veya bakiye değiştirmez; emir akışı kullanıcı onayı ve gateway kontrolleriyle yürür. Hesap erişimi JWT ve parola tabanlı oturum açmayı destekler; Google OAuth istemci kimlikleri ayrıca yapılandırılır.


## Öne çıkanlar

- Limit/piyasa emirleri, kısmi dolum, iptal ve emir geçmişi
- Olay tabanlı eşleştirme motoru ve çift girişli ledger
- Kripto kotasyonları için Binance public feed; BIST, fonlar, ABD hisseleri ve örnek opsiyon/VİOP kontratları için paper fiyat akışı
- Finnhub yapılandırıldığında harici ABD kotasyonları, haberler ve kullanıcı fiyat alarmları
- ARGENT AI ile portföy, emir ve piyasa açıklamaları; emir taslağını kullanıcı onayıyla gönderme
- Masaüstü ve Android istemcilerinde hesap/çalışma alanı yönetimi ve görünüm ayarları

## Piyasa verisi

`MARKET_DATA_MODE=BINANCE_PUBLIC` kripto için Binance public akışını kullanır; diğer varlıklar eksik sağlayıcı bağlantısında simüle edilir. `SIMULATED` tüm enstrümanları paper fiyat akışıyla çalıştırır. `ISTFIN_BIST` anahtarı ve sağlayıcı hesabı ayarlandığında BIST akışını dener, bağlantı kullanılamazsa BIST’i simüle eder. Finnhub verisi harici referans kotasyonu/haber/uyarı içindir; emir defteri ve eşleştirme fiyatı olarak kullanılmaz. Opsiyon ve VİOP enstrümanları şu an fiyatı simüle edilen basitleştirilmiş paper kontratlardır.

BIST gerçek zamanlı verisi için borsa/veri sağlayıcısı yetkilendirmesi ve dağıtım koşulları geçerlidir. ABD veri sağlayıcılarının ücretsiz planları istek kotası ve kullanım lisansıyla sınırlıdır; bu sınırlar sağlayıcıya göre değişir.

## Başlatma

Gereksinimler: Docker Desktop, JDK 21 ve Android derlemesi için Android SDK/JDK.

```powershell
Copy-Item .env.example .env
# .env içinde JWT_SECRET ve ilk admin hesabını ayarla
.\scripts\start-stack.ps1
```

Masaüstü: `.\gradlew.bat :argent-terminal:run`

Android APK: `.\scripts\package-android.ps1`

Gateway yerel adresi `http://127.0.0.1:8080`; Android emülatörü `http://10.0.2.2:8080` kullanır.

`PUBLIC_SIGNUP_ENABLED=true` ile herkes e-posta/şifre üzerinden TRADER hesabı açabilir. Yeni hesaplara bir kez 25.000 USDT ve 500.000 TRY sanal nakit verilir; gerçek para veya çekim yoktur. Google girişi için Google Cloud OAuth istemci kimliklerini `.env` içindeki `GOOGLE_WEB_CLIENT_ID` (Android) ve `GOOGLE_DESKTOP_CLIENT_ID` (masaüstü) alanlarına girin. Admin davet akışı ayrıca korunur. Sunucuyu dış kullanıcılara açmak için `.env` içinde `ARGENT_PUBLISH_HOST=0.0.0.0` ayarlanmalı ve gateway HTTPS kullanan bir ters vekil arkasında yayınlanmalıdır; varsayılan ayar yalnızca yerel bilgisayara erişim verir.

## Eşleştirme + ledger çekirdeği

Çekirdek `argent-matching-core` modülünde yeniden kullanılabilir kütüphane olarak sunulur. PostgreSQL `DataSource` ve kalıcı olay sink'i vererek `ArgentMatchingCore` oluşturun; servis `ledger` ve `matchingEngine` sağlar. Gradle modül yayını:

```powershell
.\gradlew.bat :argent-common:publishToMavenLocal :argent-engine:publishToMavenLocal :argent-ledger:publishToMavenLocal :argent-matching-core:publishToMavenLocal
```

API yüzeyi ve bağımlılık koordinatları `argent-matching-core` Gradle modülünde tanımlıdır. HTTP API uygulamanın Gateway modülünde çalışır.

## Ayarlar ve gizli bilgiler

Terminal ve Android Ayarlar bölümünde hesap özeti, açık/koyu tema, metin boyutu ve yenileme aralığı bulunur. Örnek değişkenler `.env.example` içindedir.
