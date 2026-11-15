package com.example.chargesuggest.duckduck.physics

import com.example.chargesuggest.duckduck.physics.DriveMotion.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveMotionTest {
    private fun peak(m: DriveMotion, seconds: Float = 3f): Pair<Float, Float> {
        var hi = 0f; var lo = 0f
        var t = 0f
        while (t < seconds) { val a = m.update(0.033f).ax; hi = maxOf(hi, a); lo = minOf(lo, a); t += 0.033f }
        return hi to lo
    }

    private fun quiet() = DriveMotion().also { it.auto = false }

    @Test fun 출발하면_뒤_오른쪽으로_쏠린다() {
        val m = quiet(); m.trigger(Event.ACCELERATE)
        val (hi, lo) = peak(m)
        assertTrue(hi > 5f); assertEquals(0f, lo, 0.01f)
    }

    @Test fun 브레이크는_앞_왼쪽으로_쏠린다() {
        val m = quiet(); m.trigger(Event.BRAKE)
        val (hi, lo) = peak(m)
        assertTrue(lo < -7f); assertEquals(0f, hi, 0.01f)
    }

    @Test fun 좌회전은_오른쪽_우회전은_왼쪽으로_쏠린다() {
        val l = quiet(); l.trigger(Event.TURN_LEFT)
        assertTrue(peak(l).first > 6f)
        val r = quiet(); r.trigger(Event.TURN_RIGHT)
        assertTrue(peak(r).second < -6f)
    }

    @Test fun 사건이_끝나면_가속도는_0이_된다() {
        val m = quiet(); m.trigger(Event.TURN_LEFT)
        peak(m, 3f)
        assertEquals(0f, m.update(0.033f).ax, 0.001f)
    }

    @Test fun 요철은_한_번만_튄다() {
        val m = quiet(); m.trigger(Event.BUMP)
        assertTrue(m.update(0.033f).bump >= 1.0f)
        assertEquals(0f, m.update(0.033f).bump, 0f)
    }

    @Test fun 조향각이_왼쪽이면_오른쪽으로_쏠리고_오른쪽이면_왼쪽으로_쏠린다() {
        val m = quiet()
        val sign = if (DriveMotion.LEFT_IS_NEGATIVE) -1f else 1f
        m.setSteering(sign * 90f) // 왼쪽으로 90도
        var a = 0f; repeat(60) { a = m.update(0.033f).ax }
        assertTrue("왼쪽 회전 $a", a > 3f)
        m.setSteering(-sign * 90f)
        repeat(60) { a = m.update(0.033f).ax }
        assertTrue("오른쪽 회전 $a", a < -3f)
        m.setSteering(0f)
        repeat(60) { a = m.update(0.033f).ax }
        assertEquals(0f, a, 0.2f)
    }

    @Test fun 브레이크를_밟으면_앞으로_쏠리고_떼면_살짝_되돌아온다() {
        val m = quiet()
        m.setBrake(true)
        assertTrue(peak(m, 1.2f).second < -7f)
        var a = 0f; repeat(40) { a = m.update(0.033f).ax }
        assertTrue("밟고 있는 동안 조금 쏠려 있다 $a", a < -2f)
        m.setBrake(false)
        assertTrue(peak(m, 1.5f).first > 1f)
    }

    @Test fun 자동_모드에서는_몇_초마다_사건이_일어난다() {
        val m = DriveMotion(seed = 7)
        var moved = 0
        var t = 0f
        while (t < 30f) { if (kotlin.math.abs(m.update(0.033f).ax) > 1f) moved++; t += 0.033f }
        assertTrue("움직인 프레임 $moved", moved > 100)
    }

    @Test fun 가속도는_상한을_넘지_않는다() {
        val m = quiet()
        repeat(5) { m.trigger(Event.BRAKE) }
        m.setBrake(true)
        assertTrue(peak(m, 1f).second >= -DriveMotion.MAX_AX - 0.001f)
    }

    @Test fun 오리마다_쏠림에_반응하는_정도가_다르다() {
        val world = DuckWorld(1682f, 1100f, 300f)
        world.sync((1..10).toList())
        assertTrue(world.bodies.map { it.accelScale }.toSet().size > 5)
    }

    @Test fun 방지턱은_3초_뒤에_한_번만_튄다() {
        val m = quiet()
        m.startSpeedBump(3f)
        var t = 0f
        val bumps = ArrayList<Float>()
        while (t < 5f) {
            val o = m.update(0.033f)
            if (o.bump > 0f) bumps += t
            t += 0.033f
        }
        assertEquals(1, bumps.size)
        assertTrue("튐은 3초쯤: ${bumps[0]}", bumps[0] in 2.9f..3.2f)
    }

    @Test fun 방지턱_전에는_튐이_없고_한_번만_일어난다() {
        val m = quiet()
        m.startSpeedBump(3f)
        var early = 0f
        var t = 0f
        while (t < 2.8f) { early += m.update(0.033f).bump; t += 0.033f }
        assertEquals(0f, early, 0f)
        var total = 0
        repeat(300) { if (m.update(0.033f).bump > 0f) total++ }
        assertEquals(1, total)
    }
}
