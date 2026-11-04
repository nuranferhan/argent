# ADR 0005: Uygulamayı modüler monolith olarak sürdürme

## Durum

Kabul edildi

## Bağlam

ARGENT; gateway, matching engine, risk kontrolleri, ledger ve market data için ayrı Gradle modüllerine sahiptir. Bu modüller derleme zamanında sınırlar sağlar; ancak yerel uygulama bunları tek bir gateway process'i içinde çalıştırır. Her modülü ayrı servis olarak dağıtmak, mevcut bir ölçekleme gereksinimi olmadan emir kabulü ve settlement akışlarına ağ çağrıları ve dağıtık transaction kaynaklı hata durumları ekler.

## Karar

- Matching, risk, ledger ve market iş mantığını aynı process içinde çalışan modüller olarak tut.
- PostgreSQL'i hesap, emir ve olay verilerinin kalıcı kaynağı; Redis'i ortak rate limit ve kısa ömürlü durum için kullan.
- RabbitMQ'yu asenkron domain event dağıtımı ve entegrasyonlar için kullan; senkron emir doğrulamasını veya ledger yazımlarını broker arkasına taşıma.
- Yerel çalıştırma için desteklenen altyapı olarak Docker Compose'u kullan. Operasyonel ölçek gerektirmedikçe Kubernetes/Minikube dağıtımı ekleme.
- Modülleri ancak ölçülen yük, bağımsız release gereksinimi veya hata yalıtımı; ek ağ ve dağıtık tutarlılık maliyetlerini haklı çıkardığında ayrı dağıtılan servislere ayır.

## Sonuçlar

Emir ve settlement akışları gateway process'i içinde kalır; modül arayüzleri test ve ileride ayırma için sınırları korur. PostgreSQL, Redis ve RabbitMQ altyapı bağımlılıkları olmaya devam eder; bu nedenle sağlıkları ve kurtarma süreçleri izlenmelidir. Bu karar modül başına servis maliyetini önler; production ortamında altyapı bağımlılıklarının isteğe bağlı olduğu anlamına gelmez.
