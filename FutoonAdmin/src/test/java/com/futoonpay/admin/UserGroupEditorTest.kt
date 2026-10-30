package com.futoonpay.admin

import android.app.Application
import com.futoonpay.admin.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicReference

internal const val GROUP_EDITOR_HTML = """<div class="modal" aria-hidden="true"><form id="form-store">
<input type="hidden" name="action"><input type="hidden" name="gid"><input name="name"><input name="settings">
<select name="settle_open"><option value="0">跟随系统</option><option value="1">开启结算</option></select>
<select name="settle_type"><option value="0">跟随系统</option><option value="1">允许手动结算</option></select><input name="settle_rate">
<table><tr><th>方式</th><th>通道</th></tr><tr><td>支付宝</td><td><select name="info[1][channel]"><option value="0" type="0">随机可用通道</option><option value="9" type="1" rate="92.30">示例轮询组</option><option value="17" type="0" rate="98.50">示例独立通道</option></select></td><td><input name="info[1][rate]"><input type="hidden" name="info[1][type]" value="0"><select name="info[1][complaint_action]"><option value="0">默认</option><option value="2">举证</option></select></td></tr></table>
</form></div><table><tr><th>GID</th><th>用户组名称</th><th>操作</th></tr><tr><td>0</td><td>Fixture group</td><td><button onclick="editframe(0)">编辑</button></td></tr></table>
<script>function editframe(gid){$.ajax({type:'GET',url:'ajax_user?act=getGroup&gid='+gid});} function save(){$.ajax({type:'POST',url:'ajax_user?act=saveGroup',data:$('#form-store').serialize()});}</script>"""
internal fun groupEditorResponse(name: String = "Fixture group") = JSONObject().put("code",0).put("gid",0).put("name",name).put("settings","memo:说明").put("settle_open",1).put("settle_type",1).put("settle_rate","")
    .put("info",JSONObject().put("1",JSONObject().put("channel",9).put("type",1).put("rate","93.10").put("complaint_action",2)))

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class UserGroupEditorTest {
    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun teardown() { Dispatchers.resetMain() }
    private fun repo(handler: (Request) -> String): FutoonRepository {
        val transport = OkHttpClient.Builder().addInterceptor { chain -> Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
            .body(handler(chain.request()).toResponseBody("application/json".toMediaType())).build() }.build()
        return FutoonRepository(FutoonApiClient(TestCookies(), "https://fixture.example/svc_aabbcc/".toHttpUrl(), transport = transport))
    }
    private suspend fun ready(vm: NativeModuleViewModel) { withTimeout(5000) { while(vm.state.value.loading) delay(10) }; assertNull(vm.state.value.loadError) }
    @Test fun groupJsonPopulatesTheRealTemplateAndPreservesOriginalRoutingAndSettlementValues() = runBlocking {
        val requests = mutableListOf<Request>(); val repository = repo { requests += it; groupEditorResponse().toString() }
        val document = NativeDocumentParser.parse(GROUP_EDITOR_HTML,"glist")
        val form = repository.userGroupEditor("glist",document,"0")
        assertEquals("getGroup",requests.single().url.queryParameter("act")); assertEquals("0",requests.single().url.queryParameter("gid")); assertEquals("GET",requests.single().method)
        val values = form.fields.associate { it.name to it.value }
        assertEquals("edit",values["action"]); assertEquals("0",values["gid"]); assertEquals("Fixture group",values["name"])
        assertEquals("93.10",values["info[1][rate]"]); assertEquals("1",values["info[1][type]"]); assertEquals("2",values["info[1][complaint_action]"])
        assertEquals("memo:说明",values["settings"]); assertEquals("1",values["settle_open"])
        assertEquals("支付宝 · 通道",form.fields.single { it.name=="info[1][channel]" }.label)
    }
    @Test fun changingRouteUpdatesItsKindWithoutReplacingAnExplicitShare() {
        val template=NativeDocumentParser.parse(GROUP_EDITOR_HTML,"glist").forms.single(); val form=userGroupForm(template,"0",groupEditorResponse())
        val values=form.fields.associate { it.name to it.value }.toMutableMap()
        updateGroupRoute(form,values,"info[1][channel]","17")
        assertEquals("0",values["info[1][type]"]); assertEquals("93.10",values["info[1][rate]"])
        values["info[1][rate]"]=""; updateGroupRoute(form,values,"info[1][channel]","9")
        assertEquals("1",values["info[1][type]"]); assertEquals("92.30",values["info[1][rate]"])
    }
    @Test fun editingAndSavingReturnsToFreshListInsteadOfParsingJsonAsAnEmptyPage() = runBlocking {
        val name=AtomicReference("Fixture group"); val submissions=mutableListOf<Map<String,String>>()
        val repository=repo { r -> when(r.url.queryParameter("act")) {
            "getGroup" -> groupEditorResponse(name.get()).toString()
            "saveGroup" -> { val b=r.body as FormBody; val f=(0 until b.size).associate { b.name(it) to b.value(it) }; submissions+=f; name.set(f.getValue("name")); """{"code":0,"msg":"保存成功"}""" }
            else -> GROUP_EDITOR_HTML.replace("Fixture group",name.get())
        } }
        val vm=NativeModuleViewModel(repository,ModuleCatalog.find("user_groups")); ready(vm)
        vm.openAction(vm.state.value.document!!.tables.single().rowActions.single().single()); ready(vm)
        val form=vm.state.value.document!!.forms.single(); assertEquals("修改用户组",form.title); assertEquals(1,vm.state.value.documentDepth); assertTrue(submissions.isEmpty())
        vm.submit(form,form.fields.associate { it.name to it.value }+("name" to "Renamed group")).join(); ready(vm)
        assertEquals("0",submissions.single()["gid"]); assertEquals("1",submissions.single()["info[1][type]"]); assertEquals("93.10",submissions.single()["info[1][rate]"])
        assertNull(vm.state.value.groupEditorGid); assertEquals(0,vm.state.value.documentDepth); assertEquals("Renamed group",vm.state.value.document!!.tables.single().rows.single()[1])
    }
    @Test fun addAndCancelNeverReadOrSubmitAnotherGroupAndEmptyNameCannotSave() = runBlocking {
        val requests=mutableListOf<Request>(); val repository=repo { requests+=it; GROUP_EDITOR_HTML }
        val vm=NativeModuleViewModel(repository,ModuleCatalog.find("user_groups")); ready(vm); vm.openUserGroup(); ready(vm)
        val form=vm.state.value.document!!.forms.single(); val values=form.fields.associate { it.name to it.value }
        assertEquals("add",values["action"]); assertEquals("",values["gid"]); assertEquals("",values["name"])
        assertFalse(repository.submitNativeForm("glist",form,values).success); assertEquals(1,requests.size)
        assertTrue(vm.popDocument()); assertNull(vm.state.value.groupEditorGid); assertEquals(1,requests.size)
    }
    @Test fun rejectedOrMismatchedGroupResponseCannotBecomeAnEditableDefaultForm() {
        val form=NativeDocumentParser.parse(GROUP_EDITOR_HTML,"glist").forms.single()
        for (data in listOf(JSONObject("""{"code":-1,"msg":"没有权限"}"""), groupEditorResponse().put("gid",9))) {
            try { userGroupForm(form,"0",data); fail() } catch (_:IllegalStateException) { }
        }
    }
}
