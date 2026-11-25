package org.telegram.messenger.futoon;

import com.futoonpay.admin.PanelBusinessEngine;
import com.futoonpay.admin.PanelSession;
import com.futoonpay.admin.data.SessionExpiredException;
import org.json.JSONObject;
import org.telegram.messenger.UserConfig;

/** Adapts the original business repository to the existing chat panel; starts no Activities. */
public final class FutoonNativeBridge {
    private FutoonNativeBridge() {}
    public static PanelSession session(int account,FutoonStore store) {
        return session(account,store,store.address());
    }
    public static PanelSession session(int account,FutoonStore store,String address) {
        final long owner=UserConfig.getInstance(account).getClientUserId();
        final String snapshot=address;
        return new PanelSession(){
            public boolean isActive(){return UserConfig.getInstance(account).isClientActivated()&&UserConfig.getInstance(account).getClientUserId()==owner&&snapshot.equals(store.address());}
            public String address(){return snapshot;}
            public String loadCookies(){try{return store.session(snapshot).load();}catch(Exception e){throw new IllegalStateException(e);}}
            public void saveCookies(String json){try{store.session(snapshot).save(json);}catch(Exception e){throw new IllegalStateException(e);}}
            public void clearCookies(){try{store.session(snapshot).clear();}catch(Exception e){throw new IllegalStateException(e);}}
        };
    }
    public static FutoonBusinessApi.Engine create(int account,FutoonStore store) {
        return create(account,store,store.address());
    }
    public static FutoonBusinessApi.Engine create(int account,FutoonStore store,String address) {
        PanelBusinessEngine core=new PanelBusinessEngine(session(account,store,address));
        return new FutoonBusinessApi.Engine(){
            public boolean supports(String op){return core.supports(op);}
            public JSONObject call(String op,JSONObject p)throws Exception {
                try{return core.execute(op,p);}catch(SessionExpiredException e){throw new FutoonApi.SessionExpired();}
            }
        };
    }
}
