package net.obscura.vpnclientapp.ui.compose

import net.obscura.vpnclientapp.client.AccountInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountBadgeTest {
    private val subEnd = 1_796_920_442L // 2026-12-10
    private val canceledStripe =
        AccountInfo.SubscriptionInfo(active = true, cancelAtPeriodEnd = true, currentPeriodEnd = subEnd)

    @Test
    fun topUpThatOutlivesACanceledSubscriptionShowsTheAccountEnd() {
        val badge =
            resolveBadge(
                AccountInfo(
                    id = "x",
                    active = true,
                    stripeSubscription = canceledStripe,
                    currentExpiry = 1_823_790_845L,
                ),
            )
        assertEquals("Account Active", badge.title)
    }

    @Test
    fun aCanceledSubscriptionAloneStillSaysCanceled() {
        val badge =
            resolveBadge(
                AccountInfo(id = "x", active = true, stripeSubscription = canceledStripe, currentExpiry = subEnd),
            )
        assertEquals("Subscription Canceled", badge.title)
    }

    @Test
    fun noAccountInfoYetIsChecking() {
        assertEquals("Checking status…", resolveBadge(null).title)
    }
}

class AccountTimeTest {
    private val now = 1_795_768_499L // 2026-11-27

    @Test
    fun daysLeftCountsWholeDaysAndStopsAtZero() {
        assertEquals(0L, daysLeft(now + 60, now))
        assertEquals(1L, daysLeft(now + 86_400, now))
        assertEquals(324L, daysLeft(1_823_790_845L, now))
        assertEquals(0L, daysLeft(now - 1, now))
    }

    @Test
    fun topUpCreditIsListedNextToACanceledSubscription() {
        val account =
            AccountInfo(
                id = "x",
                active = true,
                stripeSubscription =
                    AccountInfo.SubscriptionInfo(
                        active = true,
                        cancelAtPeriodEnd = true,
                        currentPeriodEnd = 1_796_920_442L,
                    ),
                topUp = AccountInfo.TopUpInfo(active = true, creditExpiresAt = 1_823_790_845L),
                currentExpiry = 1_823_790_845L,
            )
        val rows = timeRows(account, now)
        assertEquals(listOf("Subscription ends", "Top-up credit until"), rows.map { it.first })
        val badge = resolveBadge(account)
        assertEquals(1_823_790_845L, badge.endSec)
        assertEquals(324L, daysLeft(badge.endSec!!, now))
        assertEquals(false, badge.subtitle.contains("days"))
    }
}
