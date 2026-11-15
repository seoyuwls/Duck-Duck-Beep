package com.example.chargesuggest.duck.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DuckRollerTest {
    private val roller = DuckRoller()

    @Test
    fun `조건이 있는 인형은 그 상황이 아니면 나오지 않는다`() {
        val rng = Random(1)
        repeat(2000) {
            val kind = roller.roll(emptySet(), rng)
            assertNull("상황이 없는데 조건 인형이 나옴: ${kind.name}", kind.requires)
        }
    }

    @Test
    fun `해당 상황이면 조건 인형도 나올 수 있다`() {
        val rng = Random(2)
        val seen = (1..3000).map { roller.roll(setOf(DuckContext.RAINY), rng).id }.toSet()
        assertTrue("pony_umbrella" in seen)
        assertFalse("pony_plug" in seen) // 충전 상황이 아니므로
    }

    @Test
    fun `등급 비율이 설정한 확률에 가깝다`() {
        val rng = Random(3)
        val n = 20000
        val counts = (1..n).map { roller.roll(emptySet(), rng).rarity }.groupingBy { it }.eachCount()
        // 상황이 없으면 모든 등급에 후보가 있으므로 weight 비율(70/22/7/1)과 같아야 한다
        assertEquals(0.70, counts.getValue(Rarity.COMMON).toDouble() / n, 0.02)
        assertEquals(0.22, counts.getValue(Rarity.RARE).toDouble() / n, 0.02)
        assertEquals(0.07, counts.getValue(Rarity.EPIC).toDouble() / n, 0.015)
        assertEquals(0.01, counts.getValue(Rarity.LEGENDARY).toDouble() / n, 0.01)
    }

    @Test
    fun `같은 시드면 같은 결과가 나온다`() {
        val a = (1..50).map { roller.roll(emptySet(), Random(7)).id }
        val b = (1..50).map { roller.roll(emptySet(), Random(7)).id }
        assertEquals(a, b)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `조건 없는 인형이 하나도 없으면 만들 수 없다`() {
        DuckRoller(listOf(DuckKind("x", "x", Rarity.RARE, DuckContext.RAINY)))
    }
}

class NicknameFilterTest {
    private val filter = NicknameFilter()

    @Test fun `평범한 닉네임은 통과한다`() = assertEquals(NicknameCheck.OK, filter.check("히힝이 123"))
    @Test fun `빈 닉네임은 거절한다`() = assertEquals(NicknameCheck.EMPTY, filter.check("   "))
    @Test fun `너무 길면 거절한다`() = assertEquals(NicknameCheck.TOO_LONG, filter.check("가".repeat(11)))
    @Test fun `금칙어가 들어 있으면 거절한다`() = assertEquals(NicknameCheck.BANNED, filter.check("씨발포니"))

    @Test
    fun `공백이나 기호를 끼워 우회해도 막는다`() {
        assertEquals(NicknameCheck.BANNED, filter.check("씨 발"))
        assertEquals(NicknameCheck.BANNED, filter.check("병-신"))
        assertEquals(NicknameCheck.BANNED, filter.check("FUCK"))
    }
}

class GivePolicyTest {
    private val day = 86_400_000L
    private fun policy(limit: Int = 2) = GivePolicy(dailyLimit = limit, dayOf = { it / day })

    @Test
    fun `처음에는 허용된다`() = assertEquals(GiveCheck.ALLOWED, policy().check("a", 0))

    @Test
    fun `같은 상대에게는 쿨다운 안에 다시 보낼 수 없다`() {
        val p = policy()
        p.record("a", 1_000)
        assertEquals(GiveCheck.TARGET_COOLDOWN, p.check("a", 2_000))
        assertEquals(GiveCheck.ALLOWED, p.check("b", 2_000))
    }

    @Test
    fun `하루 횟수를 넘기면 막고 다음 날에는 풀린다`() {
        val p = policy(limit = 2)
        p.record("a", 1_000); p.record("b", 2_000)
        assertEquals(GiveCheck.DAILY_LIMIT, p.check("c", 3_000))
        assertEquals(GiveCheck.ALLOWED, p.check("c", day + 1))
    }

    @Test
    fun `쿨다운이 지나면 같은 상대에게 다시 보낼 수 있다`() {
        val p = GivePolicy(dailyLimit = 10, sameTargetCooldownMs = 1_000, dayOf = { 0L })
        p.record("a", 0)
        assertEquals(GiveCheck.ALLOWED, p.check("a", 1_000))
    }
}

class DuckCollectionTest {
    private val basic = DuckCatalog.all.first()
    private val crown = DuckCatalog.all.last()
    private fun duck(id: String, kind: DuckKind) = ReceivedDuck(id, "닉$id", kind, 0)

    @Test
    fun `최신 것이 앞에 오고 종류별 개수를 센다`() {
        val c = DuckCollection().add(duck("1", basic)).add(duck("2", basic)).add(duck("3", crown))
        assertEquals("3", c.items.first().id)
        assertEquals(2, c.countOf(basic.id))
        assertEquals(setOf(basic.id, crown.id), c.ownedKindIds())
    }

    @Test
    fun `도감 완성도는 받아 본 종류 비율이다`() {
        val c = DuckCollection().add(duck("1", basic)).add(duck("2", crown))
        assertEquals(2f / DuckCatalog.all.size, c.completion(), 1e-6f)
        assertEquals(0f, DuckCollection().completion(), 1e-6f)
    }

    @Test
    fun `너무 많이 쌓이면 오래된 것부터 버린다`() {
        var c = DuckCollection()
        repeat(DuckCollection.MAX_ITEMS + 10) { c = c.add(duck("$it", basic)) }
        assertEquals(DuckCollection.MAX_ITEMS, c.items.size)
    }
}

class ReactionPlannerTest {
    private val on = DuckSettings(notifyWhileDriving = true)
    private val off = DuckSettings(notifyWhileDriving = false)

    @Test
    fun `받으면 등급 색으로 조명이 켜지고 소리가 난다`() {
        val p = ReactionPlanner.onReceive(Rarity.EPIC, DriveMode.PARKED, on, naviSpeaking = false, appVisible = true)
        assertEquals(Rarity.EPIC.ambientRgb, p.ambientRgb)
        assertTrue(p.playSound)
        assertEquals(ReactionPlanner.NORMAL_VOLUME, p.soundVolume, 1e-6f)
    }

    @Test
    fun `내비 안내 중이면 효과음을 작게 한다`() {
        val p = ReactionPlanner.onReceive(Rarity.COMMON, DriveMode.DRIVING, on, naviSpeaking = true, appVisible = true)
        assertEquals(ReactionPlanner.DUCKED_VOLUME, p.soundVolume, 1e-6f)
        assertTrue(p.playSound) // 보류하지 않고 항상 실시간으로 낸다
    }

    @Test
    fun `앱이 보이지 않으면 카드는 없고 조명과 소리만 낸다`() {
        val p = ReactionPlanner.onReceive(Rarity.RARE, DriveMode.DRIVING, on, naviSpeaking = false, appVisible = false)
        assertFalse(p.showCard)
        assertNotNullColor(p)
        assertTrue(p.playSound)
    }

    @Test
    fun `주행 중 알림을 끄면 아무 반응도 하지 않는다`() {
        assertEquals(ReactionPlan.NONE, ReactionPlanner.onReceive(Rarity.LEGENDARY, DriveMode.DRIVING, off, false, true))
        assertEquals(ReactionPlan.NONE, ReactionPlanner.onGive(DriveMode.DRIVING, off, false))
    }

    @Test
    fun `알림을 꺼도 정차나 주차 중에는 반응한다`() {
        val p = ReactionPlanner.onReceive(Rarity.RARE, DriveMode.STOPPED, off, false, true)
        assertTrue(p.playSound)
    }

    @Test
    fun `도감은 주행 중에만 열 수 없다`() {
        assertFalse(ReactionPlanner.canOpenCollection(DriveMode.DRIVING))
        assertTrue(ReactionPlanner.canOpenCollection(DriveMode.STOPPED))
        assertTrue(ReactionPlanner.canOpenCollection(DriveMode.PARKED))
    }

    @Test
    fun `기어와 속도로 주행 상태를 정한다`() {
        assertEquals(DriveMode.PARKED, DriveMode.from(isParked = true, speedKmh = 0f))
        assertEquals(DriveMode.STOPPED, DriveMode.from(isParked = false, speedKmh = 0f))
        assertEquals(DriveMode.DRIVING, DriveMode.from(isParked = false, speedKmh = 30f))
    }

    @Test
    fun `밤 시간대를 한국 시간 기준으로 판단한다`() {
        val h = 3_600_000L
        // UTC 13:00 = 한국 22:00
        assertTrue(isNight(13 * h))
        // UTC 20:00 = 한국 05:00
        assertFalse(isNight(20 * h))
        // UTC 03:00 = 한국 12:00
        assertFalse(isNight(3 * h))
    }

    private fun assertNotNullColor(p: ReactionPlan) = assertTrue(p.ambientRgb != null)
}
