package com.futoonpay.admin

import com.futoonpay.admin.appearance.ChatNotificationPrivacy.redact
import org.junit.Assert.*
import org.junit.Test

class ChatNotificationPrivacyTest {
    @Test fun orderNumbersKeepThePrefixAndFirstAndLastFourDigits() {
        assertEquals("王倩：订单 P2611…3417 掉单了", redact("王倩：订单 P2611100093417 掉单了"))
        assertEquals("S2610…8812", redact("S2610081430158812"))
        assertEquals("2610…8812 已支付", redact("2610081430158812 已支付"))
    }
    @Test fun amountsAreReplacedInEveryUsualSpelling() {
        assertEquals("陈默：申请结算，金额已隐藏", redact("陈默：申请结算，¥128,400.00"))
        assertEquals("陈默：申请结算，金额已隐藏", redact("陈默：申请结算，金额：128400.00 元"))
        assertEquals("到账 金额已隐藏", redact("到账 12.50 USDT"))
        assertEquals("余额已隐藏 不足".replace("余额已隐藏", "金额已隐藏"), redact("余额 ￥300 不足"))
        assertEquals("金额已隐藏", redact("\$5.5"))
    }
    @Test fun ordinaryTextTimesAndShortNumbersSurvive() {
        for (s in listOf("代收-A2 成功率低于警戒线", "14:36 更新", "第 3 次回调失败", "PID 10482", "12 条新消息", "71.4%", "")) assertEquals(s, redact(s))
        assertEquals("", redact(null))
    }
    @Test fun phoneAndCardLikeRunsAreShortenedToo() {
        assertEquals("联系 1380…8000", redact("联系 13800138000"))
        assertEquals("卡号 6222…0123", redact("卡号 6222021234567890123"))
    }
    @Test fun mixedMessagesKeepTheirMeaning() {
        assertEquals("订单 P2611…3417 金额已隐藏，回调失败 3 次", redact("订单 P2611100093417 ¥12,000.00，回调失败 3 次"))
    }
}
