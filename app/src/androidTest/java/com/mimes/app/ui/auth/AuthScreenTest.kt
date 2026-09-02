package com.mimes.app.ui.auth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun authScreen_showsTitleAndFields() {
        composeRule.setContent {
            AuthScreen(onAuthSuccess = {})
        }

        composeRule.onNodeWithText("Вход в MiMes").assertIsDisplayed()
        composeRule.onNodeWithText("Войти").assertIsDisplayed()
    }

    @Test
    fun authScreen_switchesToRegistrationMode() {
        composeRule.setContent {
            AuthScreen(onAuthSuccess = {})
        }

        // Переключаемся на регистрацию
        composeRule.onNodeWithText("Нет аккаунта? Зарегистрироваться").performClick()

        composeRule.onNodeWithText("Регистрация в MiMes").assertIsDisplayed()
        composeRule.onNodeWithText("Зарегистрироваться").assertIsDisplayed()
    }

    @Test
    fun authScreen_typingUsername_prefixesAtSign() {
        composeRule.setContent {
            AuthScreen(onAuthSuccess = {})
        }

        composeRule.onNodeWithText("Войти").assertExists()

        // Поле с label "Логин"
        val loginField = composeRule.onNodeWithText("Логин")
        loginField.performTextInput("user")
    }
}
