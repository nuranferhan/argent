package com.argent.client.core

/** Stable identifiers for settings UI strings shared by desktop and Android. */
enum class UiTextKey {
    SETTINGS,
    ACCOUNT,
    ROLE,
    LANGUAGE,
    APPEARANCE,
    DARK,
    LIGHT,
    TEXT_SIZE,
    MARKET_DATA,
    FINNHUB_AUTO_REFRESH,
    GENERAL_REFRESH,
    ORDER_SAFETY,
    AI_ORDER_CONFIRMATION,
    WORKSPACE_SETTINGS,
    SAVE_SETTINGS,
    MARKET_COVERAGE,
    MARKET_COVERAGE_DESCRIPTION,
    SECURE_SIGN_OUT,
    MINUTE_1,
    MINUTES_5,
    MINUTES_15,
    TREND,
    UPWARD,
    DOWNWARD,
    MIXED,
    WAITING_FOR_DATA,
    MARKET_BID_ASK,
    RECENT_PRICE_OBSERVATIONS,
    NOT_INVESTMENT_ADVICE,
    NO_PUBLIC_LEVELS,
    PRICE,
    QUANTITY,
    TECHNICAL_VIEW,
    TWENTY_SAMPLE_CHANGE,
    LOW_HIGH_30,
    SAMPLE_COUNT,
    INSTRUMENT_DETAILS_LOADING,
    QUANTITY_POSITIVE,
    QUANTITY_LOT_STEP,
    MARKET_PRICE_WAITING,
    PRICE_POSITIVE,
    PRICE_TICK_STEP,
    SECTION_SIGN_IN_REQUIRED,
    GUEST_MARKET_ACCESS,
    SIGN_IN,
    ALERTS_SIGN_IN_REQUIRED,
    PAPER_TRADING,
    GUEST_TRADE_ACCESS,
    SIGN_IN_TO_TRADE,
    ORDER_RESULT,
    ORDER_FAILED,
    INTERNAL_PAPER_ENGINE,
    ALERT_WAITING,
    ALERT_TRIGGERED,
    ALERT_ACTIVE_STATE,
    ALERT_TRIGGERED_STATE,
    ASSISTANT_CONTEXT,
    ASSISTANT_ENABLED,
    ASSISTANT_DISABLED,
    ASSISTANT_CONNECTING,
    ASSISTANT_GREETING,
    ASSISTANT_HELP,
    ASSISTANT_SUGGESTION_CANCELLED_ORDERS,
    ASSISTANT_SUGGESTION_RISK_ALERTS,
    ASSISTANT_SUGGESTION_CONCENTRATION,
    ASSISTANT_THINKING,
    APPROVED,
}

interface Strings {
    fun text(
        key: UiTextKey,
        language: String,
    ): String

    fun formatted(
        key: UiTextKey,
        language: String,
        vararg args: Any,
    ): String
}

object UiStrings : Strings {
    override fun text(
        key: UiTextKey,
        language: String,
    ): String {
        val catalog = if (language == "en") StringsEn.values else StringsTr.values
        return checkNotNull(catalog[key]) { "Missing UI string: $key" }
    }

    override fun formatted(
        key: UiTextKey,
        language: String,
        vararg args: Any,
    ): String = text(key, language).format(*args)
}

/** Shared examples shown when a new assistant conversation is empty. */
object AssistantPromptCatalog {
    fun suggestions(language: String): List<String> =
        listOf(
            UiTextKey.ASSISTANT_SUGGESTION_CANCELLED_ORDERS,
            UiTextKey.ASSISTANT_SUGGESTION_RISK_ALERTS,
            UiTextKey.ASSISTANT_SUGGESTION_CONCENTRATION,
        ).map { UiStrings.text(it, language) }
}
