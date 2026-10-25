package com.argent.terminal.api

/** Small offline UI dictionary shared by the Compose Desktop and Android clients. */
object UiTranslations {
    private val english = mapOf(
        "Ayarlar" to "Settings", "AYARLAR" to "SETTINGS", "Hesap" to "Account", "HESAP" to "ACCOUNT",
        "Görünüm" to "Appearance", "GÖRÜNÜM" to "APPEARANCE", "Dil" to "Language", "Uygulama dili" to "App language",
        "Türkçe" to "Turkish", "✓ Türkçe" to "✓ Turkish", "İngilizce" to "English", "DİL" to "LANGUAGE", "Dil / Language" to "Language", "Metin boyutu" to "Text size",
        "Piyasa verileri" to "Market data", "PİYASA VERİSİ" to "MARKET DATA",
        "Finnhub kotasyonları ve haberler ekran açıkken otomatik yenilenir." to "Finnhub quotes and news refresh automatically while this screen is open.",
        "Finnhub kotasyonları ve haber akışı yükleniyor…" to "Loading Finnhub quotes and news…",
        "Genel hesap ve emir güncellemesi:" to "General account and order refresh:",
        "Genel veri kontrolü:" to "General data refresh:",
        "Emir güvenliği" to "Order safety", "EMİR GÜVENLİĞİ" to "ORDER SAFETY",
        "AI emir taslakları özet ve hesap parolasıyla onaylanır; AI kendi başına emir göndermez." to "AI order drafts require a summary review and account password; AI never submits orders on its own.",
        "Ayarları kaydet" to "Save settings", "Kaydet" to "Save", "İptal" to "Cancel", "Vazgeç" to "Back",
        "Koyu tema" to "Dark theme", "Açık tema" to "Light theme", "✓ Koyu tema" to "✓ Dark theme", "✓ Açık tema" to "✓ Light theme",
        "✓ Koyu" to "✓ Dark", "Koyu" to "Dark", "✓ Açık" to "✓ Light", "Açık" to "Light",
        "1 dk" to "1 min", "5 dk" to "5 min", "15 dk" to "15 min",
        "Hesap verilerin ve seçili piyasa bilgisi üzerinden sorabilirsin." to "Ask about your account data or the selected market.",
        "Hesap verilerin veya seçili piyasa hakkında sor." to "Ask about your account data or the selected market.",
        "Nasıl yardımcı olabilirim?" to "How can I help?",
        "ARGENT AI yanıt hazırlıyor…" to "ARGENT AI is preparing a reply…",
        "ARGENT'e sor…" to "Ask ARGENT…", "Sor" to "Ask", "Gönder" to "Send",
        "Piyasa" to "Markets", "PİYASALAR" to "MARKETS", "Portföy" to "Portfolio", "Emirler" to "Orders",
        "Çalışma alanı" to "Workspace", "Çıkış" to "Sign out", "İşlemlerim" to "My trades",
        "Açık emirler" to "Open orders", "AÇIK EMİRLER" to "OPEN ORDERS",
        "PİYASA HABERLERİ" to "MARKET NEWS", "NASDAQ · GERÇEK PİYASA REFERANSI" to "NASDAQ · LIVE MARKET REFERENCE",
        "NASDAQ · HABER · FİYAT ALARMLARI" to "NASDAQ · NEWS · PRICE ALERTS",
        "Fiyat alarmı" to "Price alert", "Üstüne çıkarsa" to "When price rises above", "Altına inerse" to "When price falls below",
        "USD eşik" to "USD threshold", "KUR" to "CREATE", "Sil" to "Delete",
        "Haber alınamadı veya sağlayıcı boş liste döndürdü." to "No news was returned by the provider.",
        "Otomatik" to "Automatic", "ARGENT piyasa işlemleri" to "ARGENT market trades",
        "BAKİYELER" to "BALANCES", "POZİSYONLAR" to "POSITIONS", "Bakiyeler" to "Balances", "Pozisyonlar" to "Positions",
        "SON PİYASA İŞLEMLERİ" to "RECENT MARKET TRADES", "ALIŞ" to "BUY", "SATIŞ" to "SELL",
        "AÇIK EMİRLER" to "OPEN ORDERS", "EMİR GEÇMİŞİ" to "ORDER HISTORY", "GERÇEKLEŞEN İŞLEMLER" to "FILLED TRADES",
        "Bekleyen emir yok. Tamamlanan ve iptal edilen emirler İşlemlerim geçmişindedir." to "No pending orders. Completed and cancelled orders are in My trades.",
        "Emir veya gerçekleşen işlem geçmişi yok." to "There is no order or trade history.",
        "Bekleyen emir yok. Tamamlanan ve iptal edilen emirler İşlemler geçmişindedir." to "No pending orders. Completed and cancelled orders are in Trade history.",
        "Emir geçmişi boş." to "Order history is empty.", "İşlem geçmişi boş." to "Trade history is empty.",
        "Nasıl yardımcı olabilirim?" to "How can I help?",
        "Hesap" to "Account", "Rol" to "Role", "Rolü değiştir" to "Change role", "Üyeler" to "Members",
        "Davet oluştur" to "Create invitation", "Davet e-postası" to "Invite email", "Üyeliği iptal et" to "Revoke membership",
        "Çalışma alanını kapat" to "Close workspace", "Çalışma alanı kapatılsın mı?" to "Close this workspace?",
        "Bu işlem oturumları sonlandırır. Diğer üyeler önce çıkarılmalı; açık emirler kapatma sırasında iptal edilir." to "This ends workspace sessions. Remove other members first; open orders are cancelled when the workspace closes.",
        "Bu işlem alanı kapatır ve oturumu sonlandırır." to "This closes the workspace and ends your session.",
        "Piyasa kapsamı" to "Market coverage", "Emir güvenliği" to "Order safety",
        "Gerçek borsaya iletilmez; dahili eşleştirme sandbox'ında çalışır." to "Orders are not sent to a real exchange; they run in ARGENT's internal matching sandbox.",
        "Limit deftere yazılır. Market/IOC kalanı iptal eder. FOK tamamen dolmazsa işlem açılmaz. Fiyata tıklayınca bilet dolar." to "Limit orders rest on the book. Market/IOC cancels any remainder. FOK places no trade unless fully filled. Select a price to fill the ticket.",
        "Yeni hesap şifresi en az 12 karakter olmalıdır." to "New account passwords must be at least 12 characters.",
        "Google ile devam et" to "Continue with Google", "Şifre" to "Password", "Emülatör için 10.0.2.2; fiziksel telefon için bilgisayarın yerel ağ IP adresini kullan." to "Use 10.0.2.2 for an emulator; use your computer's local network IP for a physical phone.",
        "Emirler dahili sandbox eşleştirmesinde işlenir." to "Orders are processed by the internal sandbox matching engine.",
        "Hesap açınca sanal bakiye tanımlanır; gerçek para kullanılmaz." to "A virtual balance is created for new accounts; no real money is used.",
        "Piyasa kapsamı" to "Market coverage", "Hesap verileri ve piyasa" to "Account data and market",
        "Son fiyat gözlemleri" to "Recent price observations", "TEKNİK GÖRÜNÜM" to "TECHNICAL VIEW",
        "Trend" to "Trend", "Veri bekleniyor" to "Waiting for data", "Yukarı" to "Up", "Aşağı" to "Down", "Karışık" to "Mixed",
        "Sembol" to "Symbol", "Miktar" to "Quantity", "Fiyat" to "Price", "Durumu sorgula" to "Check status",
        "Test emrini iptal et" to "Cancel test order", "BIST tarihsel CSV (timestamp,symbol,price[,volume24h])" to "BIST historical CSV (timestamp,symbol,price[,volume24h])",
        "Hız çarpanı" to "Speed multiplier", "BIST CSV replay başlat" to "Start BIST CSV replay",
        "Durumu yenile" to "Refresh status", "Geçiş yap" to "Switch", "Çıkar" to "Remove", "Kapat" to "Close", "KAPAT" to "CLOSE",
        "Emir geçmişi boş." to "Order history is empty.", "Bekleyen emir yok." to "No pending orders.",
        "Yükleniyor…" to "Loading…", "ONAYLI TASLAK" to "APPROVED DRAFT", "UYARILAR" to "ALERTS",
        "ARGENT AI" to "ARGENT AI", "Emri son kez kontrol et" to "Review order",
        "Hesap parolası" to "Account password", "ONAYLA VE GÖNDER" to "CONFIRM AND SUBMIT",
        "VAZGEÇ" to "CANCEL", "EMİR TASLAĞI · GÖNDERİLMEDİ" to "ORDER DRAFT · NOT SUBMITTED",
        "ÖZETİ ONAYLA VE PAROLAYLA GÖNDER" to "REVIEW SUMMARY AND CONFIRM WITH PASSWORD",
        "1" to "1",
        "Piyasa" to "Market", "Emir" to "Order", "Piyasa emirleri" to "Market orders",
        "EMİR DEFTERİ" to "ORDER BOOK", "SPREAD" to "SPREAD", "TREND" to "TREND",
        "ALIŞ EMRİ" to "BUY ORDER", "SATIŞ EMRİ" to "SELL ORDER",
        "Alış emri" to "Buy order", "Satış emri" to "Sell order",
        "Al" to "Buy", "Sat" to "Sell", "AL" to "BUY", "SAT" to "SELL",
        "LIMIT" to "LIMIT", "MARKET" to "MARKET", "IOC" to "IOC", "FOK" to "FOK",
        "E-posta" to "Email", "Giriş" to "Sign in", "Kayıt ol" to "Create account",
        "Hesap oluştur" to "Create account", "Davet kodu" to "Invitation code",
        "Davet kodu (tek sefer gösterilir):" to "Invitation code (shown once):",
        "Davet oluştur" to "Create invitation", "Davet e-postası" to "Invite email",
        "ARGENT gateway hesabınla oturum aç veya çalışma alanı davetini kabul et." to "Sign in with your ARGENT gateway account or accept a workspace invitation.",
        "Paper Trading Platform" to "Paper Trading Platform", "Paper Trading Platform · Android" to "Paper Trading Platform · Android",
        "ARGENT AI yanıt hazırlıyor…" to "ARGENT AI is preparing a reply…",
        "Portföy" to "Portfolio", "Emirler" to "Orders", "İşlemlerim" to "My trades",
        "Emir gönderilince ARGENT varlığı geçici rezerve edilir. Testnet gerçekleşmeleri ve komisyonları bakiyeye yazılır; iptal veya tamamlanmada kullanılmayan rezerv serbest kalır." to "When an order is submitted, ARGENT assets are temporarily reserved. Testnet fills and fees affect the balance; unused reserves are released after cancellation or completion.",
        "BIST: fiyat akışı simüledir. Yalnızca kullanım hakkınız olan CSV verisini yönetici replay API'siyle içe aktarın; gerçek zamanlı BIST verisi veya BIST emri değildir." to "BIST prices are simulated. Import only CSV data you are authorized to use; this is not live BIST data or a BIST order.",
        "Enstrüman verisi bekleniyor veya gateway'e ulaşılamıyor." to "Waiting for instrument data or the gateway is unavailable.",
        "Bu sembolde henüz işlem yok" to "No trades for this symbol yet",
        "Fiyat alarmı" to "Price alert", "Haber alinamadi" to "News unavailable",
        "Gerçek veri kaynağı henüz yapılandırılmadı" to "A live data provider is not configured",
        "Gerçek veri kaynağı yapılandırılmadı" to "A live data provider is not configured",
        "Haber alinamadi veya sağlayıcı boş liste döndürdü." to "No news was returned by the provider.",
        "Emir veya gerçekleşen işlem geçmişi yok." to "There is no order or trade history.",
        "Para yatırma ve çekme, doğrulanmış ödeme/saklama sağlayıcısı bağlandığında etkinleştirilir." to "Deposits and withdrawals are enabled only after a verified payment or custody provider is connected.",
        "En büyük açık pozisyonun aynı para birimindeki toplam pozisyonlara oranı. ARGENT göstergesidir; genel risk notu değildir. Spot işlem · kaldıraç/marjin kapalı." to "Largest open position as a share of total positions in the same currency. This is an ARGENT indicator, not a general risk grade. Spot trading · leverage/margin disabled.",
        "Güncel fiyatı olmayan varlıklar maliyet bazlı değerlenir; özkaynak kısmi tahmindir." to "Assets without a current price are valued at cost; equity is a partial estimate.",
        "Başlangıç bakiyelerinin maliyet temeli bilinmediği için PnL eksik olabilir." to "PnL may be incomplete because the cost basis of opening balances is unknown.",
        "Değerleme için bakiye veya pozisyon yok." to "No balances or positions to value.",
        "Açık emir yok." to "No open orders.", "Emir iptal edildi" to "Order cancelled",
        "Ayarlar" to "Settings", "Çalışma alanı" to "Workspace",
        "Sanal bakiye" to "Virtual balance", "gerçek para yok" to "no real money",
        "Tahmini notional" to "Estimated notional", "Tahmini tutar" to "Estimated amount",
        "PİYASA HABERLERİ" to "MARKET NEWS", "NASDAQ · GERÇEK PİYASA REFERANSI" to "NASDAQ · LIVE MARKET REFERENCE",
        "NASDAQ · HABER · FİYAT ALARMLARI" to "NASDAQ · NEWS · PRICE ALERTS",
        "BEKLEYEN EMİRLER" to "PENDING ORDERS", "GERÇEKLEŞEN İŞLEMLER" to "FILLED TRADES",
        "AÇIK EMİRLER" to "OPEN ORDERS", "ÇALIŞMA ALANLARI" to "WORKSPACES",
        "ÜYE YÖNETİMİ" to "MEMBER MANAGEMENT", "MARKET MAKER" to "MARKET MAKER",
        "YENİDEN BAŞLAT" to "RESTART", "GÜVENLİ ÇIKIŞ" to "SAFE SIGN OUT",
        "Piyasa İşleri" to "Market activity", "Piyasa işleri" to "Market activity",
        "Pozisyon yoğunlaşması" to "Position concentration", "Özkaynak" to "Equity",
        "Gerçekleşen PnL" to "Realized PnL", "Açık PnL" to "Unrealized PnL", "Brüt maruziyet" to "Gross exposure",
        "Bakiyeler" to "Balances", "Pozisyonlar" to "Positions", "Serbest" to "Available", "Bloke" to "Held",
        "Kullanılabilir" to "Available", "Blokeli" to "Held", "Serbest bakiye" to "Available balance",
        "Değerleme" to "Valuation", "tam" to "complete", "kısmi" to "partial",
        "Güncel fiyatlarla hesaplandı" to "Calculated using current prices",
        "Bazı varlıklarda güncel fiyat yok; tahmin kısmi" to "Some assets have no current price; estimate is partial",
        "En büyük açık pozisyonun aynı para birimindeki toplam pozisyonlara oranı. ARGENT göstergesidir; genel risk notu değildir. Spot işlem · kaldıraç/marjin kapalı." to "Largest open position as a share of total positions in the same currency. This is an ARGENT indicator, not a general risk grade. Spot trading · leverage/margin disabled.",
        "En büyük açık pozisyonun aynı para birimindeki toplam pozisyonlara oranı. Tek varlıkta %100; ARGENT göstergesidir, genel risk notu değildir." to "Largest open position as a share of total positions in the same currency. A single asset is 100%; this is an ARGENT indicator, not a general risk grade.",
        "AÇIK" to "OPEN", "KISMEN DOLDU" to "PARTIALLY FILLED", "DOLDU" to "FILLED", "İPTAL" to "CANCELLED", "REDDEDİLDİ" to "REJECTED", "SÜRESİ DOLDU" to "EXPIRED",
        "Emir" to "Order", "EMİR" to "ORDER", "Sen" to "You", "SEN" to "YOU", "Ne öğrenmek istersin?" to "What would you like to know?",
        "Hız çarpanı" to "Speed multiplier", "Testnet referans fiyatı" to "Testnet reference price", "Fiyatı yenile" to "Refresh price", "Testnet fiyatı yükleniyor…" to "Loading Testnet price…",
        "Binance Spot Testnet · gerçek para yok · gerçekleşen emirler ARGENT sanal bakiyesine yansır" to "Binance Spot Testnet · no real money · fills are reflected in your ARGENT virtual balance",
        "Emir gönderilince ARGENT varlığı geçici rezerve edilir. Testnet gerçekleşmeleri ve komisyonları bakiyeye yazılır; iptal veya tamamlanmada kullanılmayan rezerv serbest kalır." to "When submitted, ARGENT assets are temporarily reserved. Testnet fills and fees are applied to the balance; unused reserves are released after cancellation or completion.",
        "BIST: fiyat akışı simüledir. Yalnızca kullanım hakkınız olan CSV verisini yönetici replay API'siyle içe aktarın; gerçek zamanlı BIST verisi veya BIST emri değildir." to "BIST prices are simulated. Import only CSV data you are authorized to use through the admin replay API; this is not live BIST data or a BIST order.",
        "Mevcut kullanıcı için davet kodu" to "Invitation code for an existing user", "Davet e-postası" to "Invite email",
        "Bu çalışma alanını kapat" to "Close this workspace", "Davet kodu (tek sefer gösterilir):" to "Invitation code (shown once):",
        "Kripto: Binance public feed. BIST ve sağlayıcı anahtarı bulunmayan ürünler: paper simülasyonu. Veri yenileme aralığı, Finnhub kota kullanımını kontrol etmeye yardımcı olur." to "Crypto: Binance public feed. BIST and products without a provider key use paper simulation. The refresh interval helps control Finnhub quota usage.",
        "FINNHUB_API_KEY ekleyince hesabınızın kapsamındaki gerçek Nasdaq referans fiyatları ve haber akışı açılır. Demo fiyatı gösterilmiyor." to "Add FINNHUB_API_KEY to enable Nasdaq reference quotes and news available under your account. Demo prices are not shown.",
        "FINNHUB_API_KEY eklenince hesabınızın kapsamındaki gerçek Nasdaq referans fiyatları ve haberler açılır. Burada örnek fiyat kullanılmaz." to "Add FINNHUB_API_KEY to enable Nasdaq reference quotes and news available under your account. Sample prices are not used.",
        "Kotasyon alınamadı; otomatik olarak yeniden denenecek." to "Quote unavailable; it will be retried automatically.",
        "Veri bekleniyor" to "Waiting for data", "Piyasa işleri" to "Market activity", "Piyasa İşleri" to "Market activity",
        "Piyasa özeti" to "Market summary", "TRY değerlemesi" to "TRY valuation", "USDT değerlemesi" to "USDT valuation",
        "ALIŞ" to "BUY", "SATIŞ" to "SELL", "Dolan" to "Filled", "kalan" to "remaining", "piyasa" to "market",
        "İşlem geçmişi boş." to "Trade history is empty.", "Emir geçmişi boş." to "Order history is empty.",
        "Kullanım" to "Usage", "Açık emir yok." to "No open orders.", "Oturum" to "Session",
        "Testnet ayarı okunamadı." to "Could not read Testnet settings.", "Testnet kapalı. Yerel .env dosyasına yalnızca Binance Spot Testnet anahtarlarını ekleyip gateway'i yeniden başlatın." to "Testnet is disabled. Add Binance Spot Testnet keys to the local .env file and restart the gateway.",
        "Testnet anahtarları yerel gateway ortamında yapılandırılmış. Emirler yalnızca Binance test ağına gönderilir." to "Testnet keys are configured in the local gateway environment. Orders are sent only to Binance Testnet.",
        "Market maker durumu kullanılamıyor" to "Market maker status unavailable", "AÇIK" to "OPEN", "KAPALI" to "CLOSED",
        "TESTNET EMRİ GÖNDER" to "SUBMIT TESTNET ORDER", "GÖNDERİLİYOR…" to "SUBMITTING…", "Güncel Testnet fiyatını yenileyip tekrar deneyin." to "Refresh the current Testnet price and try again.",
        "Fiyat Binance Spot Testnet'in izin verdiği aralığın dışında; güncel Testnet fiyatını yenileyip tekrar deneyin. (PERCENT_PRICE_BY_SIDE)" to "Price is outside Binance Spot Testnet's allowed range; refresh the current Testnet price and try again. (PERCENT_PRICE_BY_SIDE)",
        "Emir deftere yazılır. Market/IOC kalanı iptal eder. FOK tamamen dolmazsa işlem açılmaz. Fiyata tıklayınca bilet dolar." to "Limit orders rest on the book. Market/IOC cancels any remainder. FOK places no trade unless fully filled. Select a price to fill the ticket.",
        "Testnet sonucu:" to "Testnet result:", "Davet koduyla katıl" to "Join with invitation code", "Piyasa emirleri" to "Market orders",
        "Bu sembolde henüz işlem yok" to "No trades for this symbol yet", "Enstrüman verisi bekleniyor veya gateway'e ulaşılamıyor." to "Waiting for instrument data or gateway connection.",
        "E-posta" to "Email", "Kullanıcı adı" to "Username", "Ad soyad" to "Name", "Giriş yap" to "Sign in", "Çalışma alanı daveti" to "Workspace invitation",
        "Güncel fiyatı olmayan varlıklar maliyet bazlı değerlenir; özkaynak kısmi tahmindir." to "Assets without a current price are valued at cost; equity is a partial estimate.",
        "Başlangıç bakiyelerinin maliyet temeli bilinmediği için PnL eksik olabilir." to "PnL may be incomplete because opening balances have no known cost basis.",
        "Değerleme için bakiye veya pozisyon yok." to "No balances or positions to value.", "Para yatırma ve çekme, doğrulanmış ödeme/saklama sağlayıcısı bağlandığında etkinleştirilir." to "Deposits and withdrawals are enabled after a verified payment or custody provider is connected.",
        "Piyasa veri akışı bağlantısız veya gecikmeli görünüyor" to "Market data feed appears disconnected or delayed",
        "Bu sembolde henüz işlem yok" to "No trades for this symbol yet", "KAYNAK" to "SOURCE", "VERİ" to "DATA",
        "FINNHUB_API_KEY ekleyince hesabınızın kapsamındaki Nasdaq referans fiyatları ve haber akışı açılır. Demo fiyatı gösterilmiyor." to "Add FINNHUB_API_KEY to enable Nasdaq reference quotes and news available under your account. Demo prices are not shown.",
        "Bu onay emri ARGENT dahili eşleştirme motoruna gönderir." to "This confirmation submits the order to ARGENT's internal matching engine.",
        "GEÇ" to "SKIP", "ROLÜ DEĞİŞTİR" to "CHANGE ROLE", "ÜYELİĞİ İPTAL ET" to "REVOKE MEMBERSHIP",
        "ÇALIŞMA ALANINI KAPAT" to "CLOSE WORKSPACE", "DAVETİ KABUL ET" to "ACCEPT INVITATION",
        "Yanıtlar ve emir taslakları · Emir yalnızca parola ile açık onaydan sonra gönderilir" to "Answers and order drafts · orders are submitted only after explicit password confirmation",
        "Asistan kapalı · Gateway AI anahtarını kontrol et" to "Assistant is unavailable · check the gateway AI key",
        "Asistan bağlantısı kontrol ediliyor…" to "Checking assistant connection…", "UYARILAR" to "ALERTS",
        "Ne öğrenmek istersin?" to "What would you like to know?", "Hesap verilerin ve seçili piyasa bilgisi üzerinden sorabilirsin." to "Ask about your account data or the selected market.",
        "Hesap verilerin veya seçili piyasa hakkında sor." to "Ask about your account data or the selected market.",
        "Son 7 gündeki iptal edilen emirlerim" to "My cancelled orders from the last 7 days", "Risk ve veri akışı uyarılarımı kontrol et" to "Check my risk and data-feed alerts", "Portföyümde yoğunlaşma ne durumda?" to "How concentrated is my portfolio?",
        "ARGENT AI yanıt hazırlıyor…" to "ARGENT AI is preparing a reply…", "ARGENT'e sor…" to "Ask ARGENT…", "Sor" to "Ask", "Gönder" to "Send",
        "ARGENT AI sağlayıcısına erişilemedi (HTTP 429)" to "ARGENT AI is temporarily rate-limited (HTTP 429). Try again shortly.", "ARGENT AI sağlayıcısına erişilemedi (HTTP 403)" to "ARGENT AI provider access was denied (HTTP 403). Check network access or provider permissions.", "ARGENT AI yanıtı okunamadı" to "ARGENT AI reply could not be read.",
        "Emri son kez kontrol et" to "Review the order", "Hesap parolası" to "Account password", "ONAYLA VE GÖNDER" to "CONFIRM AND SUBMIT", "VAZGEÇ" to "CANCEL",
        "EMİR TASLAĞI · GÖNDERİLMEDİ" to "ORDER DRAFT · NOT SUBMITTED", "ÖZETİ ONAYLA VE PAROLAYLA GÖNDER" to "REVIEW SUMMARY AND CONFIRM WITH PASSWORD",
        "Bugün için önceki karşılaştırma kaydı yok; olağandışı bakiye değişimi değerlendirilemiyor." to "No earlier comparison snapshot is available today; unusual balance changes cannot be assessed.",
        "Mevcut verilerde tanımlı bir uyarı eşiğini aşan durum görünmüyor." to "No current data appears to exceed a configured alert threshold."
    )

    fun translate(text: String, language: String): String {
        val normalizedError = text.lowercase()
        if (normalizedError.contains("timeout has expired") || normalizedError.contains("timed out") || normalizedError.contains("socket_timeout")) {
            return if (language == "en") "ARGENT AI is taking longer than expected. Please try again shortly." else "ARGENT AI yanıtı beklenenden uzun sürdü. Lütfen birazdan tekrar dene."
        }

        if (language != "en") return text
        english[text]?.let { return it }
        if (text.startsWith("✓ ")) return "✓ " + (english[text.removePrefix("✓ ")] ?: text.removePrefix("✓ "))
        if (text.uppercase().endsWith(" DEĞERLEMESİ")) return text.substringBefore(' ') + " valuation"
        Regex("^([A-Z0-9]+)  serbest (.+)  bloke (.+)$").matchEntire(text)?.let { m -> return "${m.groupValues[1]}  available ${m.groupValues[2]}  held ${m.groupValues[3]}" }
        Regex("^([0-9.,]+) kullanılabilir · ([0-9.,]+) blokeli$").matchEntire(text)?.let { m -> return "${m.groupValues[1]} available · ${m.groupValues[2]} held" }
        Regex("^([A-Z0-9]+)  qty (.+)  avg (.+)  mark (.+)  rPnL (.+)$").matchEntire(text)?.let { m -> return "${m.groupValues[1]}  qty ${m.groupValues[2]}  avg ${m.groupValues[3]}  mark ${m.groupValues[4]}  rPnL ${m.groupValues[5]}" }
        if (text.startsWith("Portföy · emirler · ")) return "Portfolio · orders · " + text.substringAfterLast(" · ")
        Regex("^Otomatik · (\\d+) dk$").matchEntire(text)?.let { return "Auto · ${it.groupValues[1]} min" }
        if (text.startsWith("ARGENT piyasa işlemleri · ") && text.endsWith(" kayıt")) return "ARGENT market activity · " + text.removePrefix("ARGENT piyasa işlemleri · ").removeSuffix(" kayıt") + " records"
        Regex("^AÇIK EMİRLER · (.+) bekleyen$").matchEntire(text)?.let { return "OPEN ORDERS · ${it.groupValues[1]} pending" }
        Regex("^EMİR GEÇMİŞİ · (.+) son emir$").matchEntire(text)?.let { return "ORDER HISTORY · latest ${it.groupValues[1]} orders" }
        if (text.startsWith("EMİR  ")) return "ORDER  " + text.removePrefix("EMİR  ")
        if (text.startsWith("Replay ") && text.contains(" satır")) return text.replace(" satır", " rows").replace(" işlendi", " processed")
        if (text.startsWith("Son fiyat gözlemleri · ")) return text.replace("Son fiyat gözlemleri", "Recent price observations").replace("kaynak bekleniyor", "waiting for source").replace("Yatırım tavsiyesi değildir", "Not investment advice").replace("Sabit aralıklı mum verisi değildir", "Not fixed-interval candlestick data")
        if (text.startsWith("Kaynak: ")) return "Source: " + text.removePrefix("Kaynak: ")
        if (text.startsWith("Testnet sonucu: ")) return "Testnet result: " + translate(text.removePrefix("Testnet sonucu: "), language)
        if (text.startsWith("Piyasa · ")) return "Market · " + text.removePrefix("Piyasa · ")
        if (text.startsWith("Gerçek veri alınamadı: ")) return "Live data unavailable: " + text.removePrefix("Gerçek veri alınamadı: ")
        if (text.startsWith("Piyasa veri akışı bağlantısız veya gecikmeli görünüyor: ")) return "Market data feed appears disconnected or delayed: " + text.substringAfter(": ")
        if (text.startsWith("Pozisyon yoğunlaşması yüksek: ")) return "High position concentration: " + text.removePrefix("Pozisyon yoğunlaşması yüksek: ")
        if (text.startsWith("Bugün için önceki karşılaştırma kaydı yok; ")) return "No earlier comparison snapshot is available today; " + text.substringAfter("; ")
        if (text.startsWith("Bugün için daha önceki bir portföy kaydı yok; ")) return "There is no earlier portfolio snapshot today; " + text.substringAfter("; ")
        if (text.startsWith("Piyasa veri akışı bağlantısız veya gecikmeli görünüyor: ")) return "Market data feed appears disconnected or delayed: " + text.substringAfter(": ")
        Regex("^(.+) kotasyonu (.+) saniyeden eski; fiyat güncel olmayabilir\\.$").matchEntire(text)?.let { return "${it.groupValues[1]} quote is older than ${it.groupValues[2]} seconds; the price may be stale." }
        Regex("^(.+) toplam bakiyesi ilk gün içi ölçüme göre %(.+) değişti; değişim eşiği %(.+)\\.$").matchEntire(text)?.let { return "${it.groupValues[1]} total balance changed by ${it.groupValues[2]}% versus the first intraday snapshot; the alert threshold is ${it.groupValues[3]}%." }
        Regex("^(.+) varlığında (.+) bloke bakiye bulunuyor\\.$").matchEntire(text)?.let { return "${it.groupValues[2]} of ${it.groupValues[1]} is held." }
        if (text.startsWith("Pozisyon yoğunlaşması yüksek: ")) return "High position concentration: " + text.removePrefix("Pozisyon yoğunlaşması yüksek: ")
        if (text.startsWith("Testnet fiyatı alınamadı")) return "Could not load the Testnet reference price."
        if (text.contains("güncel fiyat bulunamadı")) return "No current market price is available; no draft was created."
        if (text.contains("Piyasa fiyatı güncel değil")) return "The market quote is stale; no draft was created."
        if (text.contains("yalnızca simüle fiyat mevcut")) return "Only a simulated quote is available; no order draft was created."
        if (text.contains("Sembolü tanıyamadım")) return "I couldn't identify that symbol. Supported symbols: " + text.substringAfter("Desteklenenler: ")
        if (text.contains("Alış mı satış mı olduğunu belirtin")) return "Please specify whether this is a buy or sell order."
        if (text.contains("Tutarı para birimiyle yazın")) return "Please include the currency with the amount, for example: 'limit buy BTC for 100 USDT'."
        if (text.contains("Şimdilik yalnızca limit emir taslağı")) return "Only limit order drafts are currently supported."
        if (text.contains("Taslak bulunamadı veya süresi doldu")) return "The draft was not found or has expired; create it again."
        if (text.contains("Taslağın süresi doldu")) return "The draft has expired; create it again."
        if (text.contains("işlem başarısız")) return "The operation failed."
        Regex("^([A-Z0-9]+) kotasyonu 60 saniyeden eski; fiyat güncel olmayabilir\\.$").matchEntire(text)?.let { return "${it.groupValues[1]} quote is over 60 seconds old; its price may be stale." }
        Regex("^([A-Z0-9]+) varlığında (.+) bloke bakiye bulunuyor\\.$").matchEntire(text)?.let { return "${it.groupValues[2]} of ${it.groupValues[1]} is currently held." }
        Regex("^(.+) içinde (.+) bloke bakiye bulunuyor\\.$").matchEntire(text)?.let { return "${it.groupValues[2]} of ${it.groupValues[1]} is currently held." }
        Regex("^Market maker (AÇIK|KAPALI) · emir üst sınırı (.+) · stok sınırı (.+)$").matchEntire(text)?.let { return "Market maker ${if (it.groupValues[1] == "AÇIK") "ACTIVE" else "OFF"} · order cap ${it.groupValues[2]} · inventory cap ${it.groupValues[3]}" }
        Regex("^(.+) (BUY|SELL) (LIMIT|MARKET) (\\S+)@([^ ]+)  (AÇIK|KISMEN DOLDU|DOLDU|İPTAL|REDDEDİLDİ|SÜRESİ DOLDU)  filled (.+)$").matchEntire(text)?.let { m ->
            val status = when (m.groupValues[6]) { "AÇIK" -> "OPEN"; "KISMEN DOLDU" -> "PARTIALLY FILLED"; "DOLDU" -> "FILLED"; "İPTAL" -> "CANCELLED"; "REDDEDİLDİ" -> "REJECTED"; else -> "EXPIRED" }
            return "${m.groupValues[1]} ${m.groupValues[2]} ${m.groupValues[3]} ${m.groupValues[4]}@${m.groupValues[5]}  $status  filled ${m.groupValues[7]}"
        }
        if (text.startsWith("Rol: ")) return "Role: " + text.removePrefix("Rol: ")
        if (text.startsWith("Aktif rol: ")) return "Active role: " + text.removePrefix("Aktif rol: ")
        if (text.startsWith("Metin boyutu ")) return "Text size " + text.removePrefix("Metin boyutu ")
        if (text.startsWith("Genel veri kontrolü: ")) return "General data refresh: " + text.substringAfter(": ").replace(" sn", " sec")
        if (text.startsWith("Genel hesap ve emir güncellemesi: ")) return "General account and order refresh: " + text.substringAfter(": ").replace(" sn", " sec")
        if (text.startsWith("Gerçek veri alınamadı: ")) return "Live data unavailable: " + text.removePrefix("Gerçek veri alınamadı: ")
        if (text.startsWith("USD ")) return text
        return text
    }
}
