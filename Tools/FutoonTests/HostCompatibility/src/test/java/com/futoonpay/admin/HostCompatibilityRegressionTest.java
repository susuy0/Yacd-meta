package com.futoonpay.admin;

import org.junit.Test;
import org.telegram.messenger.futoon.FutoonCoreTest;
import org.telegram.messenger.futoon.FutoonExtendedTest;
import org.telegram.messenger.futoon.FutoonModuleTest;
import org.telegram.messenger.futoon.FutoonPanelLayoutTest;

/** Real host contracts run on the JDK with loopback fixtures, outside Android's bootclasspath. */
public final class HostCompatibilityRegressionTest {
    @Test public void coreContract() throws Exception { FutoonCoreTest.main(new String[0]); }
    @Test public void extendedContract() throws Exception { FutoonExtendedTest.main(new String[0]); }
    @Test public void moduleContract() throws Exception { FutoonModuleTest.main(new String[0]); }
    @Test public void panelLayout() { FutoonPanelLayoutTest.main(new String[0]); }
}
