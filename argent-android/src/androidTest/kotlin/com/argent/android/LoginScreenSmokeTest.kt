package com.argent.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginScreenSmokeTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun loginScreenShowsBrandAndGuestMarketEntry() {
        compose.onNodeWithText("ARGENT").assertIsDisplayed()
        compose.onNodeWithText("Google ile devam et").assertIsDisplayed()
        compose.onNodeWithText("Misafir olarak giriş yap").assertIsDisplayed()
    }
}
