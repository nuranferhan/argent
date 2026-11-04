package com.argent.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginScreenSmokeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun loginScreenShowsBrandAndGuestMarketEntry() {
        compose.setContent {
            LoginScreen(api = com.argent.client.core.ArgentApi(), onLogin = {}, onGuest = {})
        }
        compose.onNodeWithText("ARGENT").assertIsDisplayed()
        compose.onNodeWithText("Misafir olarak giriş yap")
            .performScrollTo()
            .assertIsDisplayed()
    }
}
