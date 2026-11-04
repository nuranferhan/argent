package com.argent.client.core

/** Small offline UI dictionary shared by the Compose Desktop and Android clients. */
object UiTranslations {
    private val english = LegacyEnglishUiCatalog.values

    fun translate(
        text: String,
        language: String,
    ): String {
        val normalizedError = text.lowercase()
        if (
            normalizedError.contains("timeout has expired") ||
            normalizedError.contains("timed out") ||
            normalizedError.contains("socket_timeout")
        ) {
            return if (language == "en") {
                "ARGENT AI is taking longer than expected. Please try again shortly."
            } else {
                "ARGENT AI yanıtı beklenenden uzun sürdü. Lütfen birazdan tekrar dene."
            }
        }

        if (language != "en") return text
        english[text]?.let { return it }
        if (text.startsWith("✓ ")) return "✓ " + (english[text.removePrefix("✓ ")] ?: text.removePrefix("✓ "))
        if (text.uppercase().endsWith(" DEĞERLEMESİ")) return text.substringBefore(' ') + " valuation"
        text.split("  ").takeIf {
            it.size == 3 && it[1].startsWith("serbest ") && it[2].startsWith("bloke ")
        }?.let { return "${it[0]}  available ${it[1].removePrefix("serbest ")}  held ${it[2].removePrefix("bloke ")}" }
        text.split(" kullanılabilir · ").takeIf {
            it.size == 2 && it[1].endsWith(" blokeli")
        }?.let { return "${it[0]} available · ${it[1].removeSuffix(" blokeli")} held" }
        text.split("  ").takeIf {
            it.size == 5 &&
                it[1].startsWith("qty ") &&
                it[2].startsWith("avg ") &&
                it[3].startsWith("mark ") &&
                it[4].startsWith("rPnL ")
        }?.let {
            return "${it[0]}  qty ${it[1].removePrefix(
                "qty ",
            )}  avg ${it[2].removePrefix(
                "avg ",
            )}  mark ${it[3].removePrefix("mark ")}  rPnL ${it[4].removePrefix("rPnL ")}"
        }
        if (text.startsWith("Portföy · emirler · ")) return "Portfolio · orders · " + text.substringAfterLast(" · ")
        text.takeIf {
            it.startsWith("Otomatik · ") && it.endsWith(" dk")
        }?.let {
            val minutes = it.removePrefix("Otomatik · ").removeSuffix(" dk")
            return "Auto · $minutes min"
        }
        if (text.startsWith("ARGENT piyasa işlemleri · ") && text.endsWith(" kayıt")) {
            val count = text.removePrefix("ARGENT piyasa işlemleri · ").removeSuffix(" kayıt")
            return "ARGENT market activity · $count records"
        }
        text.takeIf {
            it.startsWith("AÇIK EMİRLER · ") && it.endsWith(" bekleyen")
        }?.let { return "OPEN ORDERS · ${it.removePrefix("AÇIK EMİRLER · ").removeSuffix(" bekleyen")} pending" }
        text.takeIf {
            it.startsWith("EMİR GEÇMİŞİ · ") && it.endsWith(" son emir")
        }?.let {
            return "ORDER HISTORY · latest ${it.removePrefix(
                "EMİR GEÇMİŞİ · ",
            ).removeSuffix(" son emir")} orders"
        }
        if (text.startsWith("EMİR  ")) return "ORDER  " + text.removePrefix("EMİR  ")
        if (text.startsWith("Replay ") && text.contains(" satır")) {
            return text.replace(
                " satır",
                " rows",
            ).replace(" işlendi", " processed")
        }
        if (text.startsWith(
                "Son fiyat gözlemleri · ",
            )
        ) {
            return text.replace(
                "Son fiyat gözlemleri",
                "Recent price observations",
            ).replace(
                "kaynak bekleniyor",
                "waiting for source",
            ).replace(
                "Yatırım tavsiyesi değildir",
                "Not investment advice",
            ).replace("Sabit aralıklı mum verisi değildir", "Not fixed-interval candlestick data")
        }
        if (text.startsWith("Kaynak: ")) return "Source: " + text.removePrefix("Kaynak: ")
        if (text.startsWith(
                "Testnet sonucu: ",
            )
        ) {
            return "Testnet result: " + translate(text.removePrefix("Testnet sonucu: "), language)
        }
        if (text.startsWith("Piyasa · ")) return "Market · " + text.removePrefix("Piyasa · ")
        if (text.startsWith(
                "Gerçek veri alınamadı: ",
            )
        ) {
            return "Live data unavailable: " + text.removePrefix("Gerçek veri alınamadı: ")
        }
        if (text.startsWith(
                "Piyasa veri akışı bağlantısız veya gecikmeli görünüyor: ",
            )
        ) {
            return "Market data feed appears disconnected or delayed: " + text.substringAfter(": ")
        }
        if (text.startsWith(
                "Pozisyon yoğunlaşması yüksek: ",
            )
        ) {
            return "High position concentration: " + text.removePrefix("Pozisyon yoğunlaşması yüksek: ")
        }
        if (text.startsWith(
                "Bugün için önceki karşılaştırma kaydı yok; ",
            )
        ) {
            return "No earlier comparison snapshot is available today; " + text.substringAfter("; ")
        }
        if (text.startsWith(
                "Bugün için daha önceki bir portföy kaydı yok; ",
            )
        ) {
            return "There is no earlier portfolio snapshot today; " + text.substringAfter("; ")
        }
        if (text.startsWith(
                "Piyasa veri akışı bağlantısız veya gecikmeli görünüyor: ",
            )
        ) {
            return "Market data feed appears disconnected or delayed: " + text.substringAfter(": ")
        }
        text.takeIf { it.contains(" kotasyonu ") && it.contains(" saniyeden eski; fiyat güncel olmayabilir.") }?.let {
            val p = it.split(" kotasyonu ", limit = 2)
            return "${p[0]} quote is older than ${p[1].substringBefore(
                " saniyeden eski",
            )} seconds; the price may be stale."
        }
        text.takeIf {
            it.contains(" toplam bakiyesi ilk gün içi ölçüme göre %") && it.contains(" değişti; değişim eşiği %")
        }?.let {
            val p = it.split(" toplam bakiyesi ilk gün içi ölçüme göre %", limit = 2)
            val q =
                p[1].split(
                    " değişti; değişim eşiği %",
                    limit = 2,
                )
            val threshold = q[1].removeSuffix(".")
            return "${p[0]} total balance changed by ${q[0]}% versus the first intraday snapshot; " +
                "the alert threshold is $threshold%"
        }
        text.takeIf { it.endsWith(" bloke bakiye bulunuyor.") && it.contains(" varlığında ") }?.let {
            val p = it.split(" varlığında ", limit = 2)
            return "${p[1].removeSuffix(" bloke bakiye bulunuyor.")} of ${p[0]} is held."
        }
        if (text.startsWith(
                "Pozisyon yoğunlaşması yüksek: ",
            )
        ) {
            return "High position concentration: " + text.removePrefix("Pozisyon yoğunlaşması yüksek: ")
        }
        if (text.startsWith("Testnet fiyatı alınamadı")) return "Could not load the Testnet reference price."
        if (text.contains(
                "güncel fiyat bulunamadı",
            )
        ) {
            return "No current market price is available; no draft was created."
        }
        if (text.contains("Piyasa fiyatı güncel değil")) return "The market quote is stale; no draft was created."
        if (text.contains(
                "yalnızca simüle fiyat mevcut",
            )
        ) {
            return "Only a simulated quote is available; no order draft was created."
        }
        if (text.contains(
                "Sembolü tanıyamadım",
            )
        ) {
            return "I couldn't identify that symbol. Supported symbols: " + text.substringAfter("Desteklenenler: ")
        }
        if (text.contains(
                "Alış mı satış mı olduğunu belirtin",
            )
        ) {
            return "Please specify whether this is a buy or sell order."
        }
        if (text.contains(
                "Tutarı para birimiyle yazın",
            )
        ) {
            return "Please include the currency with the amount, for example: 'limit buy BTC for 100 USDT'."
        }
        if (text.contains(
                "Şimdilik yalnızca limit emir taslağı",
            )
        ) {
            return "Only limit order drafts are currently supported."
        }
        if (text.contains(
                "Taslak bulunamadı veya süresi doldu",
            )
        ) {
            return "The draft was not found or has expired; create it again."
        }
        if (text.contains("Taslağın süresi doldu")) return "The draft has expired; create it again."
        if (text.contains("işlem başarısız")) return "The operation failed."
        text.takeIf {
            it.endsWith(" saniyeden eski; fiyat güncel olmayabilir.") && it.contains(" kotasyonu 60")
        }?.let { return "${it.substringBefore(" kotasyonu")} quote is over 60 seconds old; its price may be stale." }
        text.takeIf { it.endsWith(" bloke bakiye bulunuyor.") && it.contains(" varlığında ") }?.let {
            val p = it.split(" varlığında ", limit = 2)
            return "${p[1].removeSuffix(" bloke bakiye bulunuyor.")} of ${p[0]} is currently held."
        }
        text.takeIf { it.endsWith(" bloke bakiye bulunuyor.") && it.contains(" içinde ") }?.let {
            val p = it.split(" içinde ", limit = 2)
            return "${p[1].removeSuffix(" bloke bakiye bulunuyor.")} of ${p[0]} is currently held."
        }
        text.takeIf {
            it.startsWith("Market maker ") && it.contains(" · emir üst sınırı ") && it.contains(" · stok sınırı ")
        }?.let {
            val p = it.removePrefix("Market maker ").split(" · emir üst sınırı ", limit = 2)
            val q = p[1].split(" · stok sınırı ", limit = 2)
            return "Market maker ${if (p[0] == "AÇIK") "ACTIVE" else "OFF"} · order cap ${q[0]} · inventory cap ${q[1]}"
        }
        text.takeIf { it.contains("  filled ") && it.contains("  ") }?.let { value ->
            val parts = value.split("  ", limit = 3)
            val head = parts.firstOrNull()?.split(' ') ?: emptyList()
            if (
                parts.size == 3 &&
                head.size == 4 &&
                head[1] in setOf("BUY", "SELL") &&
                head[2] in setOf("LIMIT", "MARKET")
            ) {
                val status =
                    when (parts[1]) {
                        "AÇIK" -> "OPEN"
                        "KISMEN DOLDU" -> "PARTIALLY FILLED"
                        "DOLDU" -> "FILLED"
                        "İPTAL" -> "CANCELLED"
                        "REDDEDİLDİ" -> "REJECTED"
                        "SÜRESİ DOLDU" -> "EXPIRED"
                        else -> return@let
                    }
                return "${head[0]} ${head[1]} ${head[2]} ${head[3]}  $status  ${parts[2]}"
            }
        }
        if (text.startsWith("Rol: ")) return "Role: " + text.removePrefix("Rol: ")
        if (text.startsWith("Aktif rol: ")) return "Active role: " + text.removePrefix("Aktif rol: ")
        if (text.startsWith("Metin boyutu ")) return "Text size " + text.removePrefix("Metin boyutu ")
        if (text.startsWith(
                "Genel veri kontrolü: ",
            )
        ) {
            return "General data refresh: " + text.substringAfter(": ").replace(" sn", " sec")
        }
        if (text.startsWith(
                "Genel hesap ve emir güncellemesi: ",
            )
        ) {
            return "General account and order refresh: " + text.substringAfter(": ").replace(" sn", " sec")
        }
        if (text.startsWith(
                "Gerçek veri alınamadı: ",
            )
        ) {
            return "Live data unavailable: " + text.removePrefix("Gerçek veri alınamadı: ")
        }
        if (text.startsWith("USD ")) return text
        return text
    }
}
