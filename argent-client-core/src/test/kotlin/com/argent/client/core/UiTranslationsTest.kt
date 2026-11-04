package com.argent.client.core

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class UiTranslationsTest : FunSpec({
    test("typed Turkish and English catalogs have identical keys") {
        StringsTr.values.keys shouldBe StringsEn.values.keys
        StringsTr.values.keys shouldBe UiTextKey.entries.toSet()
        UiStrings.text(UiTextKey.LANGUAGE, "tr") shouldBe "Dil"
        UiStrings.text(UiTextKey.LANGUAGE, "en") shouldBe "Language"
        UiStrings.text(UiTextKey.TECHNICAL_VIEW, "tr") shouldBe "TEKNİK GÖRÜNÜM"
        UiStrings.text(UiTextKey.TECHNICAL_VIEW, "en") shouldBe "TECHNICAL VIEW"
        UiStrings.formatted(UiTextKey.MARKET_BID_ASK, "en", "10", "11") shouldBe "Bid 10 · Ask 11"
        UiStrings.formatted(UiTextKey.GENERAL_REFRESH, "en", 5) shouldBe "General data refresh: 5 sec"
    }

    test("shared navigation and alert labels translate in both supported languages") {
        UiTranslations.translate("Portföy", "tr") shouldBe "Portföy"
        UiTranslations.translate("Portföy", "en") shouldBe "Portfolio"
        UiTranslations.translate("Altına inerse", "tr") shouldBe "Altına inerse"
        UiTranslations.translate("Altına inerse", "en") shouldBe "When price falls below"
    }

    test("assistant prompts and guidance use the shared typed catalogs") {
        UiStrings.formatted(UiTextKey.ASSISTANT_CONTEXT, "en", "THYAO") shouldBe
            "Portfolio · orders · THYAO"
        UiStrings.text(UiTextKey.ASSISTANT_ENABLED, "en") shouldBe
            "Answers and order drafts · orders are submitted only after explicit password confirmation"
        AssistantPromptCatalog.suggestions("en") shouldBe
            listOf(
                "My cancelled orders from the last 7 days",
                "Check my risk and data-feed alerts",
                "How concentrated is my portfolio?",
            )
        AssistantPromptCatalog.suggestions("tr") shouldBe
            listOf(
                "Son 7 gündeki iptal edilen emirlerim",
                "Risk ve veri akışı uyarılarımı kontrol et",
                "Portföyümde yoğunlaşma ne durumda?",
            )
    }

    test("assistant view model holds the same screen state model for both clients") {
        val viewModel = AssistantViewModel()
        viewModel.prompt.value = "Check my portfolio"
        viewModel.busy.value = true
        viewModel.messages.add(AssistantUiMessage(fromUser = true, text = "Check my portfolio"))

        viewModel.prompt.value shouldBe "Check my portfolio"
        viewModel.busy.value shouldBe true
        viewModel.messages.single().fromUser shouldBe true
    }

    test("dynamic order history status is translated without changing order data") {
        UiTranslations.translate("THYAO BUY LIMIT 1@310  KISMEN DOLDU  filled 0.5", "en") shouldBe
            "THYAO BUY LIMIT 1@310  PARTIALLY FILLED  filled 0.5"
    }

    test("English timeout messages stay English") {
        UiTranslations.translate("Request timeout has expired", "en") shouldBe
            "ARGENT AI is taking longer than expected. Please try again shortly."
    }

    test("shared UI number formatting caps long decimal values and preserves other text") {
        UiNumberFormatting.limitFractionDigits("BTC qty 0.00233029123456789, price 12,12345678") shouldBe
            "BTC qty 0.00233, price 12.123457"
        UiNumberFormatting.limitFractionDigits("date 2026-10-31; amount 1.234567") shouldBe
            "date 2026-10-31; amount 1.234567"
    }
})
