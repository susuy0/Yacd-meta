package com.futoonpay.admin

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.futoonpay.admin.data.MerchantSummary
import com.futoonpay.admin.ui.MerchantAction
import com.futoonpay.admin.ui.MerchantActionDialog
import com.futoonpay.admin.ui.theme.FutoonTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MerchantBalanceUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val merchant = MerchantSummary("1011", "测试商户", "普通商户", "100.00", "0", "", "", "", 1, 1, 1)

    @Test fun increaseShowsPositiveDirectionAndRejectsNegativeInput() {
        var sent = ""
        compose.setContent { FutoonTheme { MerchantActionDialog(MerchantAction("增加余额", "add", merchant), {}) { amount, _, _ -> sent = amount } } }
        compose.onNodeWithText("确认增加").assertIsNotEnabled()
        compose.onNodeWithText("金额（正数）").performTextInput("-5")
        compose.onNodeWithText("确认增加").assertIsNotEnabled()
        compose.onNodeWithText("金额（正数）").performTextReplacement("5.50")
        compose.onNodeWithText("本次增加 +¥5.50").assertIsDisplayed()
        compose.onNodeWithText("确认增加").performClick()
        assertEquals("5.50", sent)
    }

    @Test fun deductionShowsNegativeDirectionButSubmitsPositiveMagnitude() {
        var sent = ""
        compose.setContent { FutoonTheme { MerchantActionDialog(MerchantAction("扣除余额", "reduce", merchant), {}) { amount, _, _ -> sent = amount } } }
        compose.onNodeWithText("金额（正数）").performTextInput("2.50")
        compose.onNodeWithText("本次扣除 −¥2.50").assertIsDisplayed()
        compose.onNodeWithText("确认扣除").performClick()
        assertEquals("2.50", sent)
    }

    @Test fun submittingPreventsSecondTapAndDismissal() {
        var calls = 0; var dismissed = false; val busy = mutableStateOf(false)
        compose.setContent { FutoonTheme { MerchantActionDialog(MerchantAction("增加余额", "add", merchant), { dismissed = true }, busy.value) { _, _, _ -> calls++; busy.value = true } } }
        compose.onNodeWithText("金额（正数）").performTextInput("5")
        compose.onNodeWithText("确认增加").performClick()
        compose.onNodeWithText("提交中…").assertIsNotEnabled()
        compose.onNodeWithText("取消").assertIsNotEnabled()
        compose.onNodeWithText("提交中…").performClick()
        assertEquals(1, calls); assertFalse(dismissed)
    }

    @Test fun failedRequestRetainsAmountAndDisplaysReason() {
        val error = mutableStateOf<String?>(null)
        compose.setContent { FutoonTheme { MerchantActionDialog(MerchantAction("增加余额", "add", merchant), {}, error = error.value) { _, _, _ -> error.value = "权限不足" } } }
        compose.onNodeWithText("金额（正数）").performTextInput("5.50")
        compose.onNodeWithText("确认增加").performClick()
        compose.onNodeWithText("权限不足").assertIsDisplayed()
        compose.onNodeWithText("5.50").assertExists()
        compose.onNodeWithText("确认增加").assertIsEnabled()
    }

    @Test fun switchingOperationOrMerchantClearsThePreviousAmount() {
        val action = mutableStateOf(MerchantAction("增加余额", "add", merchant))
        compose.setContent { FutoonTheme { MerchantActionDialog(action.value, {}) { _, _, _ -> } } }
        compose.onNodeWithText("金额（正数）").performTextInput("5.50")
        compose.runOnIdle { action.value = MerchantAction("扣除余额", "reduce", merchant) }
        compose.onNodeWithText("确认扣除").assertIsNotEnabled()
        compose.onNodeWithText("金额（正数）").performTextInput("2.50")
        compose.runOnIdle { action.value = MerchantAction("扣除余额", "reduce", merchant.copy(uid = "1012")) }
        compose.onNodeWithText("确认扣除").assertIsNotEnabled()
        compose.onNodeWithText("商户 UID 1012").assertIsDisplayed()
    }
}
