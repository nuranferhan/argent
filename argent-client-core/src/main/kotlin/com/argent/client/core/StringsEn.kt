package com.argent.client.core

/** English strings keyed by stable identifiers; keep keys in parity with StringsTr. */
object StringsEn {
    val values =
        mapOf(
            UiTextKey.SETTINGS to "Settings",
            UiTextKey.ACCOUNT to "Account",
            UiTextKey.ROLE to "Role",
            UiTextKey.LANGUAGE to "Language",
            UiTextKey.APPEARANCE to "Appearance",
            UiTextKey.DARK to "Dark",
            UiTextKey.LIGHT to "Light",
            UiTextKey.TEXT_SIZE to "Text size %s%%",
            UiTextKey.MARKET_DATA to "Market data",
            UiTextKey.FINNHUB_AUTO_REFRESH to
                "Finnhub quotes and news refresh automatically while this screen is open.",
            UiTextKey.GENERAL_REFRESH to "General data refresh: %s sec",
            UiTextKey.ORDER_SAFETY to "Order safety",
            UiTextKey.AI_ORDER_CONFIRMATION to
                "AI order drafts require a summary review and account password; " +
                "AI never submits orders on its own.",
            UiTextKey.WORKSPACE_SETTINGS to "Workspace settings",
            UiTextKey.SAVE_SETTINGS to "Save settings",
            UiTextKey.MARKET_COVERAGE to "Market coverage",
            UiTextKey.MARKET_COVERAGE_DESCRIPTION to
                "Crypto: Binance public feed. BIST and products without a provider key use paper " +
                "simulation. The refresh interval helps control Finnhub quota usage.",
            UiTextKey.SECURE_SIGN_OUT to "SECURE SIGN OUT",
            UiTextKey.MINUTE_1 to "1 min",
            UiTextKey.MINUTES_5 to "5 min",
            UiTextKey.MINUTES_15 to "15 min",
            UiTextKey.TREND to "Trend",
            UiTextKey.UPWARD to "Upward",
            UiTextKey.DOWNWARD to "Downward",
            UiTextKey.MIXED to "Mixed",
            UiTextKey.WAITING_FOR_DATA to "Waiting for data",
            UiTextKey.MARKET_BID_ASK to "Bid %s · Ask %s",
            UiTextKey.RECENT_PRICE_OBSERVATIONS to
                "Recent price observations · %s · %s. Not fixed-interval candlestick data; " +
                "not investment advice.",
            UiTextKey.NOT_INVESTMENT_ADVICE to "Not investment advice",
            UiTextKey.NO_PUBLIC_LEVELS to "No public levels",
            UiTextKey.PRICE to "Price",
            UiTextKey.QUANTITY to "Quantity",
            UiTextKey.TECHNICAL_VIEW to "TECHNICAL VIEW",
            UiTextKey.TWENTY_SAMPLE_CHANGE to "20-sample change",
            UiTextKey.LOW_HIGH_30 to "30-period low / high",
            UiTextKey.SAMPLE_COUNT to "Samples",
            UiTextKey.INSTRUMENT_DETAILS_LOADING to "Loading instrument details.",
            UiTextKey.QUANTITY_POSITIVE to "Quantity must be greater than zero.",
            UiTextKey.QUANTITY_LOT_STEP to "Quantity must match the lot size (%s).",
            UiTextKey.MARKET_PRICE_WAITING to "Waiting for a market price.",
            UiTextKey.PRICE_POSITIVE to "Price must be greater than zero.",
            UiTextKey.PRICE_TICK_STEP to "Price must match the tick size (%s).",
            UiTextKey.SECTION_SIGN_IN_REQUIRED to "%s requires sign-in",
            UiTextKey.GUEST_MARKET_ACCESS to
                "Market prices, charts and public order-book data remain available to guests.",
            UiTextKey.SIGN_IN to "SIGN IN",
            UiTextKey.ALERTS_SIGN_IN_REQUIRED to "Please sign in to manage personal price alerts.",
            UiTextKey.PAPER_TRADING to "PAPER TRADING",
            UiTextKey.GUEST_TRADE_ACCESS to
                "Guests can view market data, charts, the order book and trades. Sign in to place an order.",
            UiTextKey.SIGN_IN_TO_TRADE to "SIGN IN TO TRADE",
            UiTextKey.ORDER_RESULT to "Order status: %s · filled quantity %s.",
            UiTextKey.ORDER_FAILED to "Order could not be placed. Check the details and try again.",
            UiTextKey.INTERNAL_PAPER_ENGINE to
                "Orders are matched in ARGENT’s paper-trading engine; they are not sent to a real exchange.",
            UiTextKey.ALERT_WAITING to "waiting",
            UiTextKey.ALERT_TRIGGERED to "triggered @ USD %s",
            UiTextKey.ALERT_ACTIVE_STATE to "waiting",
            UiTextKey.ALERT_TRIGGERED_STATE to "triggered @ USD %s",
            UiTextKey.ASSISTANT_CONTEXT to "Portfolio · orders · %s",
            UiTextKey.ASSISTANT_ENABLED to
                "Answers and order drafts · orders are submitted only after explicit password confirmation",
            UiTextKey.ASSISTANT_DISABLED to "Assistant is unavailable · check the gateway AI configuration",
            UiTextKey.ASSISTANT_CONNECTING to "Checking assistant connection…",
            UiTextKey.ASSISTANT_GREETING to "What would you like to know?",
            UiTextKey.ASSISTANT_HELP to "Ask about your account data or the selected market.",
            UiTextKey.ASSISTANT_SUGGESTION_CANCELLED_ORDERS to "My cancelled orders from the last 7 days",
            UiTextKey.ASSISTANT_SUGGESTION_RISK_ALERTS to "Check my risk and data-feed alerts",
            UiTextKey.ASSISTANT_SUGGESTION_CONCENTRATION to "How concentrated is my portfolio?",
            UiTextKey.ASSISTANT_THINKING to "ARGENT AI is preparing a response…",
            UiTextKey.APPROVED to "APPROVED",
        )
}
