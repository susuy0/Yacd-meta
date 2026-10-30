package com.futoonpay.admin

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.futoonpay.admin.data.*
import com.futoonpay.admin.ui.*
import com.futoonpay.admin.ui.theme.FutoonTheme
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w390dp-h844dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UserGroupEditorUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val writes=AtomicInteger()
    private val name=AtomicReference("Fixture group")
    private var timeoutWrite = false
    private fun repository(): FutoonRepository {
        val transport=OkHttpClient.Builder().addInterceptor { chain ->
            val r=chain.request(); val body=when(r.url.queryParameter("act")) {
                "getGroup" -> groupEditorResponse(name.get()).toString()
                "saveGroup" -> { writes.incrementAndGet(); if (timeoutWrite) throw java.io.IOException("fixture timeout"); val form=r.body as FormBody; name.set((0 until form.size).first { form.name(it)=="name" }.let { form.value(it) }); """{"code":0,"msg":"保存成功"}""" }
                else -> GROUP_EDITOR_HTML.replace("Fixture group",name.get())
            }
            Response.Builder().request(r).protocol(Protocol.HTTP_1_1).code(200).message("fixture").body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        return FutoonRepository(FutoonApiClient(TestCookies(),"https://fixture.example/svc_aabbcc/".toHttpUrl(),transport=transport))
    }
    private fun open(): NativeModuleViewModel {
        val vm=NativeModuleViewModel(repository(),ModuleCatalog.find("user_groups"))
        compose.setContent { FutoonTheme { Box(Modifier.width(390.dp).height(650.dp)) { PanelOverlayHost { NativeModuleRoot(vm,{}) } } } }
        compose.waitUntil(5000) { compose.onAllNodes(hasText("编辑") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
        return vm
    }
    private fun edit() {
        compose.onNode(hasText("编辑") and hasClickAction()).performClick()
        compose.waitUntil(5000) { compose.onAllNodes(hasText("Fixture group") and hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun screenshot(name: String) = compose.runOnUiThread {
        val view=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0)
        val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888);view.draw(Canvas(bitmap))
        File("build/ui-screenshots/$name.png").also { it.parentFile?.mkdirs() }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    @Test fun editLoadsOriginalValuesInCollapsedSectionsAndCancelNeverWrites() {
        val vm=open();edit()
        compose.onNodeWithText("此页面暂时没有可显示的数据或表单").assertDoesNotExist()
        compose.onNodeWithText("保存用户组").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("93.10").assertDoesNotExist()
        compose.onNodeWithContentDescription("展开支付宝").performClick()
        compose.onNode(hasText("93.10") and hasSetTextAction()).assertExists()
        assertEquals(0,writes.get());screenshot("50-group-editor-original-values")
        compose.onNodeWithText("取消编辑").performClick();assertNull(vm.state.value.groupEditorGid);assertEquals(0,writes.get())
    }
    @Test fun saveNeedsExplicitTargetConfirmationAndReturnsToUpdatedList() {
        val vm=open();edit()
        compose.onNode(hasText("Fixture group") and hasSetTextAction()).performTextReplacement("Renamed group")
        compose.onNodeWithText("保存用户组").performClick()
        compose.onNodeWithText("确认保存用户组").assertExists();assertEquals(0,writes.get())
        compose.onNodeWithText("用户组 #0").assertExists();compose.onNodeWithText("取消").performClick();assertEquals(0,writes.get())
        compose.onNodeWithText("保存用户组").performClick();compose.onNodeWithText("确认保存").performClick()
        compose.waitUntil(5000) { writes.get()==1 && compose.onAllNodesWithText("Renamed group").fetchSemanticsNodes().isNotEmpty() }
        assertNull(vm.state.value.groupEditorGid);assertEquals(1,writes.get());screenshot("51-group-editor-saved-list")
    }
    @Test fun addStartsBlankAndCannotSaveUntilNamed() {
        val vm=open();compose.onNodeWithContentDescription("新增用户组").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("保存用户组").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("保存用户组").assertIsNotEnabled();assertEquals(0,writes.get())
        compose.onNodeWithText("取消编辑").performClick();assertNull(vm.state.value.groupEditorGid);assertEquals(0,writes.get())
    }
    @Test fun financeStatisticsHideZeroDetailsByDefaultButAllowThemToBeInspected() {
        compose.setContent { FutoonTheme { Box(Modifier.width(390.dp)) { NativeTableRow(listOf("商户号","总计","有交易通道","零交易通道"),listOf("18","100.00","100.00","0.00"),summaryFields=2,foldZeroDetails=true) } } }
        compose.onNodeWithText("有交易通道").assertDoesNotExist();compose.onNodeWithText("零交易通道").assertDoesNotExist()
        compose.onNodeWithContentDescription("展开详情").performClick();compose.onNodeWithText("有交易通道").assertIsDisplayed()
        compose.onNodeWithText("零交易通道").assertDoesNotExist();compose.onNodeWithText("显示零交易项").performClick()
        compose.onNodeWithText("零交易通道").assertIsDisplayed();screenshot("52-finance-statistics-details")
    }
    @Test fun merchantNavigationRemainsAvailableInSettlementMenuWithoutDuplicatedCardButton() {
        val nav=PanelNavigator();val vm=AppViewModel(TestEnvironment(repository()),loadOnStart=false)
        val row=SettlementSummary("42","18","Fixture","5","2","fixture-address","335.30","47.56","","","","",7,true,false,"")
        compose.setContent { FutoonTheme { CompositionLocalProvider(LocalPanelNavigation provides nav) { Box(Modifier.width(390.dp).height(650.dp)) { PanelOverlayHost { SettlementsScreen(AppUiState(settlements=listOf(row),settlementTotal=1,settlementsLoading=false),vm,showSearchEntry=false) } } } } }
        compose.onNodeWithText("查看关联商户").assertDoesNotExist();compose.onNodeWithText("服务器查询",substring=true).assertDoesNotExist()
        compose.onNodeWithContentDescription("结算操作").performClick();compose.onNodeWithText("查看关联商户").performClick()
        assertEquals("merchants",nav.current.kind);assertEquals("18",nav.current.merchantId)
    }
    @Test fun uncertainEditRequiresFreshReadAndRestoresServerValuesEvenWhenFormIdentityMatches() {
        timeoutWrite=true;val vm=open();edit()
        compose.onNode(hasText("Fixture group") and hasSetTextAction()).performTextReplacement("Pending draft")
        compose.onNodeWithText("保存用户组").performClick();compose.onNodeWithText("确认保存").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("等待核对").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("等待核对").assertIsNotEnabled();assertTrue(vm.state.value.writeUncertain);assertEquals(1,writes.get())
        compose.onNodeWithContentDescription("刷新").performClick()
        compose.waitUntil(5000) { compose.onAllNodes(hasText("Fixture group") and hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Pending draft").assertDoesNotExist();assertFalse(vm.state.value.writeUncertain);assertEquals(1,writes.get())
    }
    @Test fun uncertainCreateCannotBeReplayedByRefreshingAnUnidentifiedNewRecord() {
        timeoutWrite=true;val vm=open();compose.onNodeWithContentDescription("新增用户组").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("保存用户组").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction()).performTextReplacement("Pending creation")
        compose.onNodeWithText("保存用户组").performClick();compose.onNodeWithText("确认保存").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("等待核对").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("刷新").assertDoesNotExist();compose.onNodeWithText("等待核对").assertIsNotEnabled();assertEquals(1,writes.get())
        compose.runOnIdle { vm.load() }
        compose.waitUntil(5000) { compose.onAllNodesWithText("等待核对").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(vm.state.value.writeUncertain);assertEquals(1,writes.get())
        compose.onNodeWithText("取消编辑").performClick();assertNull(vm.state.value.groupEditorGid);assertEquals(1,writes.get())
    }

    @Test fun draftSurvivesClosingThePanelAndIsDiscardedOnlyAfterExplicitCancel() {
        val vm=NativeModuleViewModel(repository(),ModuleCatalog.find("user_groups"));val shown=androidx.compose.runtime.mutableStateOf(true)
        compose.setContent { FutoonTheme { Box(Modifier.width(390.dp).height(650.dp)) { if (shown.value) PanelOverlayHost { NativeModuleRoot(vm,{}) } } } }
        compose.waitUntil(5000) { compose.onAllNodes(hasText("编辑") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() };edit()
        compose.onNode(hasText("Fixture group") and hasSetTextAction()).performTextReplacement("Unsubmitted draft")
        compose.runOnIdle { shown.value=false };compose.onNodeWithText("Unsubmitted draft").assertDoesNotExist()
        compose.runOnIdle { shown.value=true }
        compose.onNode(hasText("Unsubmitted draft") and hasSetTextAction()).assertExists();assertEquals(0,writes.get())
        compose.onNodeWithText("取消编辑").performClick();compose.onNodeWithContentDescription("新增用户组").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("保存用户组").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Unsubmitted draft").assertDoesNotExist();compose.onNodeWithText("保存用户组").assertIsNotEnabled();assertEquals(0,writes.get())
    }

}
