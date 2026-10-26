# Güvenlik, bağımlılık ve kapasite incelemesi

İnceleme tarihi: 2026-10-14

## Bağımlılık güvenlik taraması

Gradle Kotlin compiler seçeneklerinden alınan, çözümlenmiş Windows build classpath'leri proje modüllerinde 142 Maven coordinate içeriyordu. OSV public query API, kullanılan şu sürümlerde bilinen güvenlik duyuruları bildirdi: Logback 1.5.12, RabbitMQ Java client 5.22.0, Micrometer 1.13.6, Netty 4.1.114.Final ve PostgreSQL JDBC 42.7.4. Kotlin Gradle plugin 2.0.21 sürümü de build-cache metadata'sındaki güvenli olmayan deserialization nedeniyle işaretlendi.

Bağımlılık tanımları aşağıdaki yamalı sürümlere güncellendi:

| Bileşen | Önceki | Güncel |
|---|---:|---:|
| Logback | 1.5.12 | 1.5.38 |
| Netty | 4.1.114.Final | Netty BOM üzerinden 4.1.137.Final |
| RabbitMQ Java client | 5.22.0 | 5.37.0 |
| Micrometer core ve Prometheus registry | 1.13.6 / 1.12.13 | 1.16.6 |
| PostgreSQL JDBC | 42.7.4 | 42.7.14 |
| Kotlin Gradle/serialization/Compose compiler plugin'leri | 2.0.21 | 2.4.21 |
| Compose Multiplatform Gradle plugin | 1.7.0 | 1.12.1 |

Güncelleme tarihindeki OSV exact-version sorguları, Kotlin Gradle plugin 2.4.21 dahil güncellenmiş artifact'ler için duyuru bulmadı. Kaynaklar: [OSV database](https://osv.dev/), [Logback duyurusu](https://osv.dev/vulnerability/GHSA-25qh-j22f-pwp8), [Netty duyurusu](https://osv.dev/vulnerability/GHSA-38f8-5428-x5cv), [RabbitMQ Java client duyurusu](https://osv.dev/vulnerability/GHSA-5m9f-rphj-c435), [Micrometer duyurusu](https://osv.dev/vulnerability/GHSA-g3pr-3p32-fp23), [PostgreSQL JDBC duyurusu](https://osv.dev/vulnerability/GHSA-98qh-xjc8-98pq), [Kotlin Gradle plugin duyurusu](https://osv.dev/vulnerability/GHSA-r937-wjx7-w2jp).

Tarama, çözümlenmiş uygulama/compiler classpath'lerini ve doğrudan sorgulanan Gradle plugin sürümlerini kapsar; Docker base image'larını, işletim sistemi paketlerini veya gelecekteki bağımlılık güncellemelerini kapsamaz. Kotlin 2.4.21, 8 Ekim 2026'da yayımlanmış kararlı bir patch sürümüdür; seçilen Compose Multiplatform plugin sürümü 1.12.1'dir. Kotlin ve Compose toolchain sürümleri birlikte yükseltildi. 14 Ekim 2026'da kullanıcı Windows/JDK 21 üzerinde `:argent-gateway:compileKotlin`, `:argent-ledger:compileKotlin`, `:argent-market:compileKotlin` ve `:argent-terminal:compileKotlin` görevlerini çalıştırdı; build başarıyla tamamlandı. Bloklamayan deprecation warning'leri devam etti: Kotlin Android `sourceSets`, Kotlin `Principal` ve Gradle 8.11.1 (plugin, gelecekteki Kotlin 2.5.0 sürümünün Gradle 8.14.4 veya üzerini gerektireceğini bildiriyor). Bu çalıştırmada Android Kotlin compile görevi yoktu. Docker Engine erişimi inceleme ortamında reddedildiği için Docker image taraması yapılmadı.

## Lisans ve kaynak dağıtımı incelemesi

Proje MIT lisanslıdır. Maven POM lisans metadata'sı taranan 142 coordinate'in 117'sinde mevcuttu; 21 POM'da lisans bildirimi yoktu ve dört Android/JetBrains artifact'i Maven Central dışında tutulduğu için bu metadata kontrolünde çözümlenemedi. Görülen bildirimler arasında Apache-2.0, MIT, BSD-2-Clause, EPL-2.0, ISC, Public Domain ve RabbitMQ client için birden fazla lisans bulunuyordu (Apache-2.0, GPL-2.0 ve MPL-2.0). POM metadata'sı, yeniden dağıtılan her artifact için hangi alternatif lisansın geçerli olduğunu tek başına kanıtlamaz.

Repository'de oluşturulmuş bir third-party attribution paketi bulunmadı. MSI/APK geniş ölçekte dağıtılmadan önce metadata'sı eksik 25 artifact'i upstream lisans dosyalarına göre doğrulayın ve dağıtım paketine gerekli third-party bildirimlerini ekleyin. Bu çalışma teknik envanterdir; hukuki uyumluluk görüşü değildir.

## Repository ve uygulama güvenlik kontrolleri

- Takip edilen dosya adları `.env`, private key, keystore, certificate, database dump ve heap dump açısından kontrol edildi; takip edilen dosya bulunmadı.
- Hedefli secret-pattern taraması yalnızca `.env.example`, README ve yerel script'lerde belgelenmiş placeholder/development default değerlerini buldu; takip edilen kaynakta doldurulmuş Binance key'i veya private key yoktu.
- `.gitignore`; `.env`, `.env.*` ( `.env.example` hariç), build çıktıları, installer'lar, APK/AAB paketleri, backup'lar, dump'lar, heap dump'lar ve signing key'leri hariç tutar.
- Docker Compose servis portlarını `127.0.0.1` adresine açar. Yerel geliştirmede bilerek zayıf örnek parolalar ve development JWT secret kullanılır; production yapılandırması bu varsayılanları reddeder ve benzersiz secret'lar ister. Geliştirme stack'ini güvenilmeyen bir ağa açmayın.
- Hesap parolaları BCrypt ile hash'lenir; yeni parolalar en az 12 karakter olmalıdır. Production readiness dokümanında MFA, account recovery, bağımsız threat review, TLS/secret-manager deployment, network least privilege, off-host backup, alerting ve incident sorumluluğu tamamlanmamış işler olarak listelenmiştir.
- Docker Engine erişimi bu ortamda reddedildiği için Docker image taraması yapılmadı.

## Salt okunur HTTP soak ölçümü

`scripts/load-readonly.js`, yalnızca `GET /api/v1/ticker/BTCUSDT` endpoint'ine saniyede 3 istek (toplam 1.800 planlı istek) ile 10 dakikalık constant-arrival-rate soak uygular. Yük başlamadan önce `GET /health` kontrolü yapar ve health `UP` değilse çalışmayı reddeder. Health route'un backing service'leri kontrol ettiği ve 7,5 saniyede yanıt verdiği gözlendiğinden preflight timeout 30 saniyedir. Test emir göndermez/iptal etmez ve bakiyeleri değiştirmez. İstek hızı ticker route'un dakikada 300 istek limitinin altındadır.

Yerel servisler çalışırken repository kökünden başlatın:

```powershell
k6 run --summary-export reports\ticker-soak-summary.json scripts\load-readonly.js
```

Kabul eşikleri: başarısız HTTP isteği %1'in altında, check başarısı %99'un üzerinde, p95 750 ms'nin ve p99 1.500 ms'nin altında olmalıdır. Bu yalnızca yerel read path ölçümüdür; emir gönderme kapasitesini, çok kullanıcılı ölçeği, harici venue davranışını veya production SLO'larını kanıtlamaz.

### Ölçüm sonucu

amd64/JDK 21 yerel makinesinde yapılan ilk 10 dakikalık çalıştırma 1.800 ticker isteğini saniyede ölçülen 2,97 istek hızında tamamladı. Sonuçlar: median 10,24 ms, p95 133,08 ms, p99 359,53 ms, maksimum 6,85 sn, 275 başarısız istek (%15,27) ve iki düşen iteration. Hata oranı ve check eşiklerini geçemedi. Bu çalıştırmada HTTP status dağılımı kaydedilmedi; dolayısıyla hatalar belirli bir status code'a bağlanamaz.

Aynı hedef hızdaki 30 saniyelik diagnostic çalıştırması 90 istek, sıfır hata, p95 171,86 ms, p99 1,46 sn ve maksimum 8,02 sn ile geçti. Bu kısa çalışma ilk başarısız 10 dakikalık sonucu geçersiz kılmaz. Bir tekrar, yük başlamadan önce 10 saniyelik health preflight timeout'u nedeniyle başlayamadı; preflight timeout sonradan 30 saniyeye çıkarıldı.

İkinci 10 dakikalık çalışma 14 Ekim 2026'da, health `UP` döndükten sonra yerel amd64 makinesinde tamamlandı. 1.800 read planlandı, 1.797 iteration tamamlandı (saniyede 2,978 iteration); 5 arrival düşürüldü. 1.797 HTTP isteğinin tamamı başarılı oldu ve 3.592 check'in tamamı geçti. Latency: median 8,09 ms, average 49,14 ms, p95 215,98 ms, p99 786,96 ms, maksimum 3,00 sn. Çalışma tanımlanan HTTP hata ve latency eşiklerini karşıladı. Bu, yaklaşık saniyede 3 istek hızında read-only ticker route'u için kanıttır; emir gönderme kapasitesini veya production ölçeğini kanıtlamaz. Daha önceki başarısız soak, daha güçlü availability iddialarından önce tekrarlanıp incelenmesi gereken bir güvenilirlik sinyalidir.

Sağlanan gateway log alıntısında son ticker yanıtları 1–43 ms içinde HTTP 200 dönüyordu; alıntıda 4xx/5xx yoktu. Ayrıca 7,5 saniye süren bir `/health` isteği ve sık `/metrics` scrape'leri görünüyordu. Bu alıntı önceki soak hatalarının nedenini belirlemez. Windows CIM erişimi inceleme ortamında reddedildiği için CPU ve RAM modeli alınamadı. Geçmiş JMH engine microbenchmark'ını bu gateway ölçümünün yerine kullanmayın.
