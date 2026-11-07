package com.futoonpay.admin

import android.app.Application
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class ChannelSessionStatusTest {
    @org.junit.Before fun freshMerchantCache(){ChannelShortcutModel.clearMerchantCache()}
    private class Session:PanelSession {
        var raw="[]";var active=true
        override fun isActive()=active
        override fun address()="https://fixture.example/admin/"
        override fun loadCookies()=raw
        override fun saveCookies(json:String){raw=json}
        override fun clearCookies(){raw="[]"}
    }
    private fun token(domain:String="fixture.example",expiry:Long=-1,path:String="/")=JSONArray().put(JSONObject().put("name","admin_token").put("value","synthetic-cookie").put("domain",domain).put("path",path).put("expiry",expiry)).toString()
    @Test fun sessionStatusReadsFreshCookiesAfterLoginAndLogout(){
        val session=Session();assertFalse(ChannelSessionStatus.hasSession(session));session.raw=token();assertTrue(ChannelSessionStatus.hasSession(session));session.clearCookies();assertFalse(ChannelSessionStatus.hasSession(session))
    }
    @Test fun renamedAuthCookieOnTheNewBackendStillCountsButTheCaptchaSessionDoesNot(){
        val session=Session()
        fun cookie(name:String)=JSONArray().put(JSONObject().put("name",name).put("value","synthetic-cookie").put("domain","fixture.example").put("path","/").put("expiry",-1)).toString()
        session.raw=cookie("PHPSESSID");assertFalse(ChannelSessionStatus.hasSession(session))
        session.raw=cookie("phpsessid");assertFalse(ChannelSessionStatus.hasSession(session))
        for(name in listOf("admin_token","svc_session","token")){session.raw=cookie(name);assertTrue(name,ChannelSessionStatus.hasSession(session))}
    }
    @Test fun expiredOtherHostAndOtherPathCookiesDoNotShowTheStrip(){
        val session=Session()
        for(raw in listOf(token(expiry=0),token(domain="other.example"),token(path="/other/"),"invalid")){session.raw=raw;assertFalse(ChannelSessionStatus.hasSession(session))}
        session.raw=token();session.active=false;assertFalse(ChannelSessionStatus.hasSession(session))
    }
    @Test fun realCookieJarUpdatesAndClearsNotifyAnAlreadyOpenConversation(){
        val context=androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
        val session=Session()
        val bridge=object:FixtureBridge(){override fun address()=session.address();override fun session():PanelSession=session;override fun hasSession()=ChannelSessionStatus.hasSession(session)}
        val model=ChannelShortcutModel(context,bridge);model.store.prefs.edit().clear().commit();model.bind("测试群",-100,1);model.resume();assertFalse(model.state.value.visible)
        var notifications=0
        val base=session.address().toHttpUrl();val jar=PanelCookieJar(session,base){notifications++;model.reloadSettings()}
        jar.saveFromResponse(base,listOf(Cookie.Builder().name("admin_token").value("synthetic-cookie").hostOnlyDomain(base.host).path("/").secure().build()))
        assertEquals(1,notifications);assertTrue(model.state.value.visible)
        jar.clear();assertEquals(2,notifications);assertFalse(model.state.value.visible);model.close()
    }
    @Test fun rememberedChatOffersLoginWithoutEnablingBusinessActions() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
        val session = Session()
        val bridge = object : FixtureBridge() {
            override fun address() = session.address()
            override fun session(): PanelSession = session
            override fun hasSession() = ChannelSessionStatus.hasSession(session)
        }
        val model = ChannelShortcutModel(context, bridge)
        model.store.prefs.edit().clear().commit()
        model.bind("测试群", -100, 1)
        model.resume()
        val scope = requireNotNull(model.state.value.scope)
        assertFalse(model.state.value.loginRequired)
        model.store.replaceChannels(scope, listOf(ChannelLink("11", "测试通道")))
        model.reloadSettings()
        assertTrue(model.state.value.loginRequired)
        assertFalse(model.state.value.visible)
        model.store.hide(scope, true)
        model.reloadSettings()
        assertFalse(model.state.value.loginRequired)
        model.close()
    }

    @Test fun inactiveSessionCannotSaveCookiesOrEmitAChange(){
        val session=Session();var notifications=0;val base=session.address().toHttpUrl();val jar=PanelCookieJar(session,base){notifications++};session.active=false
        try{jar.saveFromResponse(base,emptyList());fail()}catch(_:java.io.IOException){}
        assertEquals(0,notifications);assertEquals("[]",session.raw)
    }
}
