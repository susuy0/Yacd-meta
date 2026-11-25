package com.futoonpay.admin;

import org.junit.Before;
import org.junit.Test;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.futoon.FutoonBusinessApi;
import org.telegram.messenger.futoon.FutoonNativeBridge;
import org.telegram.messenger.futoon.FutoonStore;

import static org.junit.Assert.*;

/** Exercises the real bridge against deterministic account/store boundaries. */
public final class BridgeSessionSnapshotTest {
    private static final String A = "https://a.fixture.example/admin/";
    private static final String B = "https://b.fixture.example/admin/";

    @Before public void resetAccount() {
        UserConfig.getInstance(0).owner = 42;
        UserConfig.getInstance(0).active = true;
    }

    @Test public void defaultSessionCapturesExactlyOneAddressAndPinsItsCookieScope() {
        FutoonStore store = new FutoonStore(A);
        store.sessions.put(A, "cookies-a");
        store.sessions.put(B, "cookies-b");
        store.afterNextAddressRead = B;
        PanelSession session = FutoonNativeBridge.session(0, store);
        assertEquals(1, store.addressReads);
        assertEquals(A, session.address());
        assertEquals(A, session.address());
        assertEquals(1, store.addressReads);
        assertFalse(session.isActive());
        assertEquals("cookies-a", session.loadCookies());
        session.saveCookies("updated-a");
        assertEquals("updated-a", store.sessions.get(A));
        assertEquals("cookies-b", store.sessions.get(B));
        session.clearCookies();
        assertFalse(store.sessions.containsKey(A));
        assertEquals("cookies-b", store.sessions.get(B));
    }

    @Test public void explicitSessionUsesTheCallerSnapshotEvenWhenTheStoreHasChanged() {
        FutoonStore store = new FutoonStore(B);
        PanelSession session = FutoonNativeBridge.session(0, store, A);
        assertEquals(0, store.addressReads);
        assertEquals(A, session.address());
        assertFalse(session.isActive());
        store.address(A);
        assertTrue(session.isActive());
        UserConfig.getInstance(0).owner = 99;
        assertFalse(session.isActive());
        UserConfig.getInstance(0).owner = 42;
        UserConfig.getInstance(0).active = false;
        assertFalse(session.isActive());
    }

    @Test public void defaultEngineConstructionReadsTheStoreOnlyOnce() {
        FutoonStore store = new FutoonStore(A);
        store.afterNextAddressRead = B;
        FutoonBusinessApi.Engine engine = FutoonNativeBridge.create(0, store);
        assertEquals(1, store.addressReads);
        assertTrue(engine.supports("module.editor"));
        assertTrue(engine.supports("form.save"));
    }

    @Test public void explicitEngineConstructionNeverRereadsTheTargetAddress() {
        FutoonStore store = new FutoonStore(B);
        FutoonBusinessApi.Engine engine = FutoonNativeBridge.create(0, store, A);
        assertEquals(0, store.addressReads);
        assertTrue(engine.supports("batch.options"));
        assertTrue(engine.supports("batch.run"));
    }
}
