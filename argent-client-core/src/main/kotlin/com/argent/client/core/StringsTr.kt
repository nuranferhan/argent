package com.argent.client.core

/** Turkish strings keyed by stable identifiers; keep keys in parity with StringsEn. */
object StringsTr {
    val values =
        mapOf(
            UiTextKey.SETTINGS to "Ayarlar",
            UiTextKey.ACCOUNT to "Hesap",
            UiTextKey.ROLE to "Rol",
            UiTextKey.LANGUAGE to "Dil",
            UiTextKey.APPEARANCE to "Görünüm",
            UiTextKey.DARK to "Koyu",
            UiTextKey.LIGHT to "Açık",
            UiTextKey.TEXT_SIZE to "Metin boyutu %s%%",
            UiTextKey.MARKET_DATA to "Piyasa verileri",
            UiTextKey.FINNHUB_AUTO_REFRESH to "Finnhub kotasyonları ve haberler ekran açıkken otomatik yenilenir.",
            UiTextKey.GENERAL_REFRESH to "Genel veri kontrolü: %s sn",
            UiTextKey.ORDER_SAFETY to "Emir güvenliği",
            UiTextKey.AI_ORDER_CONFIRMATION to
                "AI emir taslakları özet ve hesap parolasıyla onaylanır; " +
                "AI kendi başına emir göndermez.",
            UiTextKey.WORKSPACE_SETTINGS to "Çalışma alanı ayarları",
            UiTextKey.SAVE_SETTINGS to "Ayarları kaydet",
            UiTextKey.MARKET_COVERAGE to "Piyasa kapsamı",
            UiTextKey.MARKET_COVERAGE_DESCRIPTION to
                "Kripto: Binance public feed. BIST ve sağlayıcı anahtarı bulunmayan ürünler: " +
                "paper simülasyonu. Veri yenileme aralığı, Finnhub kota kullanımını kontrol etmeye yardımcı olur.",
            UiTextKey.SECURE_SIGN_OUT to "GÜVENLİ ÇIKIŞ",
            UiTextKey.MINUTE_1 to "1 dk",
            UiTextKey.MINUTES_5 to "5 dk",
            UiTextKey.MINUTES_15 to "15 dk",
            UiTextKey.TREND to "Trend",
            UiTextKey.UPWARD to "Yukarı",
            UiTextKey.DOWNWARD to "Aşağı",
            UiTextKey.MIXED to "Karışık",
            UiTextKey.WAITING_FOR_DATA to "Veri bekleniyor",
            UiTextKey.MARKET_BID_ASK to "Alış %s · Satış %s",
            UiTextKey.RECENT_PRICE_OBSERVATIONS to
                "Son fiyat gözlemleri · %s · %s. Sabit aralıklı mum verisi değildir; " +
                "yatırım tavsiyesi değildir.",
            UiTextKey.NOT_INVESTMENT_ADVICE to "Yatırım tavsiyesi değildir",
            UiTextKey.NO_PUBLIC_LEVELS to "Açık seviye yok",
            UiTextKey.PRICE to "Fiyat",
            UiTextKey.QUANTITY to "Miktar",
            UiTextKey.TECHNICAL_VIEW to "TEKNİK GÖRÜNÜM",
            UiTextKey.TWENTY_SAMPLE_CHANGE to "20 örnek değişim",
            UiTextKey.LOW_HIGH_30 to "Son 30 dip / tepe",
            UiTextKey.SAMPLE_COUNT to "Örnek",
            UiTextKey.INSTRUMENT_DETAILS_LOADING to "Enstrüman bilgisi yükleniyor.",
            UiTextKey.QUANTITY_POSITIVE to "Miktar sıfırdan büyük olmalı.",
            UiTextKey.QUANTITY_LOT_STEP to "Miktar lot adımına uygun değil (%s).",
            UiTextKey.MARKET_PRICE_WAITING to "Piyasa fiyatı henüz alınmadı.",
            UiTextKey.PRICE_POSITIVE to "Fiyat sıfırdan büyük olmalı.",
            UiTextKey.PRICE_TICK_STEP to "Fiyat tick adımına uygun değil (%s).",
            UiTextKey.SECTION_SIGN_IN_REQUIRED to "%s için giriş yapmanız gerekiyor",
            UiTextKey.GUEST_MARKET_ACCESS to
                "Piyasa fiyatları, grafikler ve herkese açık emir defteri misafirlere açıktır.",
            UiTextKey.SIGN_IN to "GİRİŞ YAP",
            UiTextKey.ALERTS_SIGN_IN_REQUIRED to "Kişisel fiyat alarmlarını yönetmek için lütfen giriş yapın.",
            UiTextKey.PAPER_TRADING to "SANAL İŞLEM",
            UiTextKey.GUEST_TRADE_ACCESS to
                "Misafirler piyasa verilerini, grafiği, emir defterini ve işlemleri görebilir. " +
                "Emir vermek için giriş yapın.",
            UiTextKey.SIGN_IN_TO_TRADE to "EMİR İÇİN GİRİŞ YAPIN",
            UiTextKey.ORDER_RESULT to "Emir durumu: %s · gerçekleşen miktar %s.",
            UiTextKey.ORDER_FAILED to "Emir verilemedi. Bilgileri kontrol edip tekrar deneyin.",
            UiTextKey.INTERNAL_PAPER_ENGINE to
                "Emirler ARGENT paper-trading motorunda eşleştirilir; gerçek borsaya gönderilmez.",
            UiTextKey.ALERT_WAITING to "bekliyor",
            UiTextKey.ALERT_TRIGGERED to "tetiklendi @ USD %s",
            UiTextKey.ALERT_ACTIVE_STATE to "bekliyor",
            UiTextKey.ALERT_TRIGGERED_STATE to "tetiklendi @ USD %s",
            UiTextKey.ASSISTANT_CONTEXT to "Portföy · emirler · %s",
            UiTextKey.ASSISTANT_ENABLED to
                "Yanıtlar ve emir taslakları · Emir yalnızca parola ile açık onaydan sonra gönderilir",
            UiTextKey.ASSISTANT_DISABLED to "Asistan kullanılamıyor · gateway AI yapılandırmasını kontrol edin",
            UiTextKey.ASSISTANT_CONNECTING to "Asistan bağlantısı kontrol ediliyor…",
            UiTextKey.ASSISTANT_GREETING to "Ne öğrenmek istersin?",
            UiTextKey.ASSISTANT_HELP to "Hesap verilerin veya seçili piyasa hakkında sor.",
            UiTextKey.ASSISTANT_SUGGESTION_CANCELLED_ORDERS to "Son 7 gündeki iptal edilen emirlerim",
            UiTextKey.ASSISTANT_SUGGESTION_RISK_ALERTS to "Risk ve veri akışı uyarılarımı kontrol et",
            UiTextKey.ASSISTANT_SUGGESTION_CONCENTRATION to "Portföyümde yoğunlaşma ne durumda?",
            UiTextKey.ASSISTANT_THINKING to "ARGENT AI yanıt hazırlıyor…",
            UiTextKey.APPROVED to "ONAYLI",
        )
}
