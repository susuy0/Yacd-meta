package com.futoonpay.admin

import com.futoonpay.admin.appearance.ChatAvatarRule.label
import com.futoonpay.admin.appearance.ChatAvatarRule.tint
import org.junit.Assert.*
import org.junit.Test

class ChatAvatarRuleTest {
    @Test fun merchantGroupsLoseTheirSuffixAndKeepTwoHanCharacters() {
        assertEquals("星河", label("星河电商 对接群"));assertEquals("鲸落", label("鲸落游戏 结算群"));assertEquals("青禾", label("青禾科技 对接群"))
        assertEquals("云杉", label("云杉商城"));assertEquals("北辰", label("北辰数据 对接群"))
    }
    @Test fun emojiAreIgnored() {
        assertEquals("鲸落", label("🎮 鲸落游戏 结算群 🌊"));assertEquals("星河", label("⭐星河电商 对接群"))
    }
    @Test fun channelGroupsUseTheirCodeAndTicketsUseHash() {
        assertEquals("A2", label("代收-A2 通道群"));assertEquals("B1", label("代付 - b1"));assertEquals("#", label("工单 4821 回调未达"));assertEquals("#", label("#4821"))
    }
    @Test fun latinNamesTakeTwoUpperCaseLettersAndEmptyNamesFallBackToHash() {
        assertEquals("AC", label("acme pay"));assertEquals("#", label(""));assertEquals("#", label("🎮"));assertEquals("#", label(null))
    }
    @Test fun tintIsStableAcrossFiveColoursAndNeutralWithoutAnId() {
        assertEquals(tint(10482, false), tint(10482, false));assertEquals(tint(5, false), tint(10, false))
        assertEquals(5, (0L..9L).map { tint(it + 1, false) }.toSet().size)
        assertNotEquals(tint(7, false), tint(7, true));assertNotEquals(tint(0, false), tint(1, false))
        assertEquals(tint(-12, false), tint(12, false))
    }
}
