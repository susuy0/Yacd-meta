package com.futoonpay.admin

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.futoonpay.admin.ui.theme.FutoonPalette
import com.futoonpay.admin.ui.theme.FutoonTheme
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Exercise real LaunchedEffect replacement with a blocking settings bridge, as on picker return. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w390dp-h844dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsCancellationRegressionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val host = SettingsRaceHost()
    private val revision = mutableIntStateOf(0)
    private val mounted = mutableStateOf(true)
    private val session = ChatToolsSessionState("home")
    private val gates = mutableListOf<SettingsReadGate>()
    private val cancellationMessage = "The coroutine scope left the composition"
    private val progress = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)
    private fun gate() = SettingsReadGate().also { gates += it }
    @After fun releaseBlockedReads() { gates.forEach { it.release.countDown() } }

    private fun root(fromHome: Boolean = false) {
        session.page.value = if (fromHome) "home" else "automation"
        val palette = FutoonPalette(dark = true, surface = Color(0xFF17212B), background = Color(0xFF0E1621),
            text = Color.White, secondaryText = Color(0xFF8A98A6), divider = Color(0xFF263544), accent = Color(0xFF2481CC))
        compose.setContent { FutoonTheme(palette) {
            Box(Modifier.width(390.dp).height(760.dp).testTag("settings-cancellation-root")) {
                PanelOverlayHost { if (mounted.value) ChatToolsScreen(host, false, "automation", revision.intValue, session) }
            }
        } }
        compose.waitUntil(10000) { compose.onAllNodesWithText(if (fromHome) "会话 Tag" else "新增群规则").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun reload() {
        compose.runOnIdle { revision.intValue++ }
        // Drain Robolectric's Main dispatcher as well as advancing the Compose frame.
        compose.waitForIdle()
    }
    private fun waitEntered(gate: SettingsReadGate) { compose.waitUntil(10000) { gate.entered.count == 0L } }
    private fun finishRead(gate: SettingsReadGate) {
        gate.release.countDown()
        compose.waitUntil(10000) { gate.returned.count == 0L }
        // Let the cancelled IO result return to Main and run subsequent UI frames.
        compose.mainClock.advanceTimeBy(250)
        compose.waitForIdle()
    }
    private fun assertNoCancellationError() {
        compose.onAllNodesWithText(cancellationMessage, substring = true).assertCountEquals(0)
        compose.onNodeWithText("重新读取设置").assertDoesNotExist()
    }


    @Test fun nativeGroupPickerRecreationKeepsPageUnsavedRulesAndEditorFields() {
        root(fromHome = true)
        compose.onNodeWithTag("chat-tools-list").performScrollToNode(hasText("群自动化"))
        compose.onNodeWithText("群自动化").performClick()
        compose.onNodeWithText("新增群规则").performClick()
        compose.onNode(hasSetTextAction() and hasText("指定机器人／发送者 Telegram ID")).performScrollTo().performTextReplacement("5824870757")
        compose.onNodeWithText("选择群组").performScrollTo().performClick()
        compose.runOnIdle { mounted.value = false }
        compose.waitForIdle()
        host.group = """{"dialog":"-42","title":"合成测试群","topic":"0"}"""
        compose.runOnIdle { revision.intValue++; mounted.value = true }
        compose.waitUntil(10000) { compose.onAllNodesWithText("选择群组").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction() and hasText("客户端群 ID", substring = true)).assertTextContains("-42")
        compose.onNode(hasSetTextAction() and hasText("指定机器人／发送者 Telegram ID")).assertTextContains("5824870757")
        compose.onNodeWithText("确定规则").performScrollTo().performClick()
        // A second native picker visit must also retain previously staged, unsaved rules.
        compose.onNodeWithText("新增群规则").performScrollTo().performClick()
        compose.onNodeWithText("选择群组").performScrollTo().performClick()
        compose.runOnIdle { mounted.value = false }
        compose.waitForIdle()
        host.group = """{"dialog":"-73","title":"第二个合成群","topic":"0"}"""
        compose.runOnIdle { revision.intValue++; mounted.value = true }
        compose.waitUntil(10000) { compose.onAllNodesWithText("选择群组").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction() and hasText("客户端群 ID", substring = true)).assertTextContains("-73")
        compose.onNodeWithContentDescription("关闭").performClick()
        compose.onNodeWithText("放弃修改").performClick()
        compose.onNodeWithText("保存设置").performClick()
        compose.waitUntil(10000) { host.writes.get() == 1 }
        assertEquals(-42L, ChatToolsConfig.parse(host.saved).extensions.rules.single().dialog)
        assertFalse(ChatToolsConfig.parse(host.saved).extensions.rules.single().enabled)
    }

    @Test fun severalGroupsAndBlankSenderCanBeSavedAfterNativePickerRecreation() {
        root(); compose.onNodeWithText("新增群规则").performClick()
        compose.onNode(hasSetTextAction() and hasText("消息包含文字（点击按钮时可留空）")).performScrollTo().performTextReplacement("补单")
        compose.onNodeWithText("选择群组").performScrollTo().performClick()
        compose.runOnIdle { mounted.value = false }; compose.waitForIdle()
        host.group = """{"groups":[{"dialog":"-42","title":"第一测试群","topic":"0"},{"dialog":"-73","title":"第二测试群","topic":"0"}]}"""
        compose.runOnIdle { revision.intValue++; mounted.value = true }
        compose.waitUntil(10000) { compose.onAllNodesWithText("选择群组").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("已选择 2 个群组，可再次选择增删").assertExists()
        compose.onNodeWithText("确定规则").performScrollTo().performClick()
        compose.onNodeWithText("保存设置").performClick()
        compose.waitUntil(10000) { host.writes.get() == 1 }
        val saved = ChatToolsConfig.parse(host.saved).extensions.rules.single()
        assertEquals(listOf(-42L,-73L), saved.targets().map { it.dialog }); assertEquals(0L,saved.sender)
        assertEquals("补单",saved.keyword); assertFalse(saved.enabled)
    }

    @Test fun cancellingNativePickerKeepsUnsavedRuleFieldsAfterRecreation() {
        root(fromHome = true)
        compose.onNodeWithTag("chat-tools-list").performScrollToNode(hasText("群自动化"))
        compose.onNodeWithText("群自动化").performClick()
        compose.onNodeWithText("新增群规则").performClick()
        compose.onNode(hasSetTextAction() and hasText("规则名称")).performTextReplacement("保留草稿")
        compose.onNodeWithText("选择群组").performScrollTo().performClick()
        compose.runOnIdle { mounted.value = false }
        compose.waitForIdle()
        host.group = ""
        compose.runOnIdle { revision.intValue++; mounted.value = true }
        compose.waitUntil(10000) { compose.onAllNodesWithText("选择群组").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction() and hasText("规则名称")).assertTextContains("保留草稿")
        compose.onNodeWithText("尚未选择群组").assertExists()
        assertEquals(0, host.writes.get())
    }

    @Test fun rapidPickerReturnsKeepSelectedGroupAndRuleDraftWithoutCancellationErrors() {
        val old = gate()
        host.read = { n -> if (n == 2) old.await(host.saved) else host.saved }
        root()
        compose.onNodeWithText("新增群规则").performClick()
        compose.onNode(hasSetTextAction() and hasText("指定机器人／发送者 Telegram ID")).performScrollTo().performTextReplacement("5824870757")
        compose.onNodeWithText("选择群组").performScrollTo().performClick()
        assertEquals(1, host.picks.get())
        host.group = """{"dialog":"-42","title":"合成测试群","topic":"0"}"""
        reload(); waitEntered(old)
        // A second host refresh replaces the first read before its blocking IO completes.
        reload()
        compose.waitUntil(10000) { host.reads.get() >= 3 }
        finishRead(old)
        assertNoCancellationError()
        compose.onNode(hasSetTextAction() and hasText("客户端群 ID", substring = true)).assertTextContains("-42")
        compose.onNode(hasSetTextAction() and hasText("指定机器人／发送者 Telegram ID")).assertTextContains("5824870757")
        compose.onNodeWithText("确定规则").performScrollTo().performClick()
        compose.onNodeWithText("保存设置").performClick()
        compose.waitUntil(10000) { host.writes.get() == 1 }
        val rule = ChatToolsConfig.parse(host.saved).extensions.rules.single()
        assertEquals(-42L, rule.dialog); assertEquals(5824870757L, rule.sender); assertFalse(rule.enabled)
    }

    @Test fun cancelledOldReadCannotHideTheStillPendingNewReadsProgress() {
        val old = gate(); val current = gate()
        val staleError = "已取消的旧读取失败"
        host.read = { n -> when (n) { 2 -> { old.await(host.saved); throw IOException(staleError) }; 3 -> current.await(host.saved); else -> host.saved } }
        root()
        reload(); waitEntered(old)
        reload(); waitEntered(current)
        finishRead(old)
        compose.onNode(progress).assertExists()
        compose.onNodeWithText(staleError).assertDoesNotExist()
        assertNoCancellationError()
        finishRead(current)
        compose.waitUntil(10000) { compose.onAllNodes(progress).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("新增群规则").assertIsDisplayed()
        compose.onNodeWithText(staleError).assertDoesNotExist()
        assertNoCancellationError()
    }

    @Test fun aRealReloadFailureIsShownOnceAndRetryRecoversWithoutChangingSettings() {
        val message = "合成读取失败，请重试"
        host.read = { n -> if (n == 2) throw IOException(message) else host.saved }
        root(); val original = host.saved
        reload()
        compose.waitUntil(10000) { compose.onAllNodesWithText(message).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText(message).assertCountEquals(1)
        compose.onNodeWithText("重新读取设置").performClick()
        compose.waitUntil(10000) { host.reads.get() >= 3 && compose.onAllNodesWithText(message).fetchSemanticsNodes().isEmpty() }
        assertNoCancellationError()
        assertEquals(original, host.saved); assertEquals(0, host.writes.get())
        compose.onNodeWithText("新增群规则").assertIsDisplayed()
        screenshot("40-group-automation-after-reload-recovery")
    }

    @Test fun aRealSaveFailureIsShownOnceAndTheEditedRuleCanBeSavedAgain() {
        val message = "合成保存失败，请重试"
        host.failSave = true
        root()
        compose.onNodeWithText("新增群规则").performClick()
        compose.onNode(hasSetTextAction() and hasText("客户端群 ID", substring = true)).performScrollTo().performTextReplacement("-73")
        compose.onNode(hasSetTextAction() and hasText("指定机器人／发送者 Telegram ID")).performScrollTo().performTextReplacement("5824870757")
        compose.onNodeWithText("确定规则").performScrollTo().performClick()
        compose.onNodeWithText("保存设置").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText(message).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText(message).assertCountEquals(1)
        assertTrue(ChatToolsConfig.parse(host.saved).extensions.rules.isEmpty())
        host.failSave = false
        compose.onNodeWithText("保存设置").assertIsEnabled().performClick()
        compose.waitUntil(10000) { host.writes.get() == 1 }
        compose.onAllNodesWithText(message).assertCountEquals(0)
        assertEquals(-73L, ChatToolsConfig.parse(host.saved).extensions.rules.single().dialog)
        assertEquals(2, host.saveAttempts.get())
    }

    private fun screenshot(name: String) {
        val bounds = compose.onNodeWithTag("settings-cancellation-root").fetchSemanticsNode().boundsInRoot
        compose.runOnUiThread {
            val view = compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0)
            val bitmap = Bitmap.createBitmap(bounds.width.toInt(), bounds.height.toInt(), Bitmap.Config.ARGB_8888)
            Canvas(bitmap).apply { translate(-bounds.left, -bounds.top); view.draw(this) }
            File("build/ui-screenshots/$name.png").apply { parentFile?.mkdirs() }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}

private class SettingsReadGate {
    val entered = CountDownLatch(1)
    val release = CountDownLatch(1)
    val returned = CountDownLatch(1)
    fun await(value: String): String {
        entered.countDown()
        try { check(release.await(30, TimeUnit.SECONDS)) { "测试读取未获释放" }; return value }
        finally { returned.countDown() }
    }
}

private class SettingsRaceHost : ChatToolsHostBridge {
    @Volatile var saved = ChatToolsConfig().json()
    @Volatile var group = ""
    @Volatile var failSave = false
    @Volatile var read: ((Int) -> String)? = null
    val reads = AtomicInteger(); val writes = AtomicInteger(); val saveAttempts = AtomicInteger(); val picks = AtomicInteger()
    override fun load(): String { val n = reads.incrementAndGet(); return read?.invoke(n) ?: saved }
    override fun save(json: String) { saveAttempts.incrementAndGet(); if (failSave) throw IOException("合成保存失败，请重试"); saved = json; writes.incrementAndGet() }
    override fun settingsTitle(title: String) {}
    override fun accountLabel() = "合成测试账号"
    override fun selectedTab() = ""
    override fun selectedCategory() = ""
    override fun rememberSelection(tab: String, category: String) {}
    override fun manageAt(tab: String, category: String) {}
    override fun selectReply(text: String) {}
    override fun manage() {}
    override fun originalSendOptions() {}
    override fun close() {}
    override fun pickEntryImage() {}
    override fun pickAutomationGroup() { picks.incrementAndGet() }
    override fun selectedGroup() = group
    override fun automationLog() = "暂无执行记录"
    override fun resetEntryPosition() {}
}
