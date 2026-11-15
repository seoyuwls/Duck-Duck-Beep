package com.example.chargesuggest.duckduck.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class DuckPhysicsTest {
    private val COUNT = com.example.chargesuggest.duckduck.domain.DuckConfig.BIN_CAPACITY
    private val w = 1682f
    private val h = 1100f
    private val size = DuckWorld.duckSizeFor(w, h, COUNT)

    private fun settled(count: Int = COUNT, seed: Int = 1): DuckWorld {
        val world = DuckWorld(w, h, size, seed)
        world.sync((1..count).toList())
        var frames = 0
        while (!world.asleep && frames < 60 * 20) { world.step(1f / 60f); frames++ }
        return world
    }

    @Test fun 오리_크기는_화면_안에서_정해진다() {
        assertTrue(size in 250f..520f)
        // 세로로 긴 창에서도 오리 두세 마리는 나란히 들어간다
        assertTrue(DuckWorld.duckSizeFor(841f, 1100f, COUNT) <= 841f / 3.2f)
    }

    @Test fun 쏟아진_오리는_20초_안에_모두_멈추고_잠든다() {
        val world = DuckWorld(w, h, size)
        world.sync((1..COUNT).toList())
        var frames = 0
        while (!world.asleep && frames < 60 * 20) { world.step(1f / 60f); frames++ }
        assertTrue("잠들지 않음", world.asleep)
    }

    @Test fun 멈춘_오리는_모두_화면_안에_있다() {
        val world = settled()
        for (b in world.bodies) {
            assertFalse(b.x.isNaN() || b.y.isNaN() || b.angle.isNaN())
            for (k in 0 until 4) {
                val (cx, cy, r) = world.circleOf(b, k)
                assertTrue("왼쪽 벽 밖", cx - r >= -size * 0.02f)
                assertTrue("오른쪽 벽 밖", cx + r <= w + size * 0.02f)
                assertTrue("바닥 밖", cy + r <= h + size * 0.02f)
            }
        }
    }

    @Test fun 오리끼리_많이_겹치지_않는다() {
        val world = settled()
        var worst = 0f
        for (i in world.bodies.indices) for (j in i + 1 until world.bodies.size) {
            for (p in 0 until 4) for (q in 0 until 4) {
                val (ax, ay, ar) = world.circleOf(world.bodies[i], p)
                val (bx, by, br) = world.circleOf(world.bodies[j], q)
                val d = sqrt((ax - bx) * (ax - bx) + (ay - by) * (ay - by))
                worst = maxOf(worst, ar + br - d)
            }
        }
        assertTrue("가장 깊은 겹침 $worst", worst < size * 0.12f)
    }

    @Test fun 오리는_바닥부터_쌓여_위쪽_절반은_비어_있지_않다() {
        val world = settled()
        val top = world.bodies.minOf { it.y }
        assertTrue("더미가 바닥에 붙어 있어야 한다", world.bodies.maxOf { it.y } > h - size)
        assertTrue("쌓인 높이 $top", top < h - size * 1.5f)
    }

    @Test fun 건드리면_잠든_세계가_깨어난다() {
        val world = settled()
        assertTrue(world.asleep)
        world.nudge(-500f)
        assertFalse(world.asleep)
        var frames = 0
        while (!world.asleep && frames < 60 * 20) { world.step(1f / 60f); frames++ }
        assertTrue(world.asleep)
    }

    @Test fun 정차_쏠림은_오리를_앞쪽으로_밀고_가라앉는다() {
        val world = settled()
        val before = world.bodies.map { it.x }.average()
        world.nudge(-size * 1.2f)
        var frames = 0
        while (!world.asleep && frames < 60 * 20) { world.step(1f / 60f); frames++ }
        val after = world.bodies.map { it.x }.average()
        assertTrue("평균 위치가 왼쪽으로 움직여야 한다: $before -> $after", after < before)
    }

    @Test fun 집어서_끌면_오리가_손가락을_따라온다() {
        val world = settled()
        val target = world.bodies.last()
        val px = target.x; val py = target.y
        assertTrue(world.grab(px, py))
        assertTrue(world.isGrabbing)
        val goalX = w / 2f; val goalY = 300f
        repeat(90) { world.dragTo(goalX, goalY); world.step(1f / 60f) }
        val moved = sqrt((target.x - goalX) * (target.x - goalX) + (target.y - goalY) * (target.y - goalY))
        assertTrue("손가락에서 떨어진 거리 $moved", moved < size)
    }

    @Test fun 던지면_올라갔다가_다시_화면_안으로_떨어진다() {
        val world = settled()
        val b = world.bodies.last()
        assertTrue(world.grab(b.x, b.y))
        world.release(0f, -size * 10f)
        var minY = b.y
        repeat(60 * 6) { world.step(1f / 60f); minY = minOf(minY, b.y) }
        assertTrue("위로 올라갔다", minY < h - size * 2f)
        assertTrue("다시 내려옴", b.y > 0f && b.y <= h)
        assertTrue(b.x in 0f..w)
    }

    @Test fun 던지는_속도에는_상한이_있다() {
        val world = settled()
        val b = world.bodies.last()
        world.grab(b.x, b.y)
        world.release(1_000_000f, 0f)
        val v = sqrt(b.vx * b.vx + b.vy * b.vy)
        assertTrue(v <= DuckPhysicsConfig.GRAB_MAX_SPEED_SIZES * size * 1.001f)
    }

    @Test fun 오리가_없는_곳을_누르면_집히지_않는다() {
        val world = DuckWorld(w, h, size)
        assertFalse(world.grab(10f, 10f))
        assertNull(world.hit(10f, 10f))
    }

    @Test fun 통이_바뀌면_새_오리는_위에서_떨어지고_없어진_오리는_사라진다() {
        val world = settled(COUNT)
        val ids = (2..COUNT).toList() + 99
        world.sync(ids)
        assertTrue(world.has(99))
        assertTrue("새 오리는 화면 위에서 시작", world.bodies.first { it.id == 99 }.y < 0f)
        // 밀려난 오리는 바로 사라지지 않고 아래로 빠져 나간 뒤에 없어진다
        assertTrue(world.bodies.first { it.id == 1 }.falling)
        repeat(240) { world.step(1f / 60f) }
        assertFalse(world.has(1))
    }

    @Test fun 같은_조건이면_같은_결과가_나온다() {
        val a = settled(seed = 5)
        val b = settled(seed = 5)
        assertEquals(a.bodies.map { it.x }, b.bodies.map { it.x })
        assertNotNull(a.bodies.firstOrNull())
    }

    @Test fun 가속도가_바뀌면_깨어난다() {
        val world = settled()
        world.setAcceleration(1500f)
        assertFalse(world.asleep)
    }

    @Test fun 주행_중_기울기는_오리를_앞쪽_왼쪽으로_모으고_다시_가라앉는다() {
        val world = settled()
        val before = world.bodies.map { it.x }.average()
        world.setAcceleration(-6f * size)
        var frames = 0
        while (!world.asleep && frames < 60 * 20) { world.step(1f / 60f); frames++ }
        assertTrue("잠들어야 한다", world.asleep)
        val leaned = world.bodies.map { it.x }.average()
        assertTrue("왼쪽으로 쏠림: $before -> $leaned", leaned < before)
        for (b in world.bodies) assertTrue(b.x in -size..(w + size))
        // 정차하면 가속도가 0이 되어 다시 가운데로 퍼진다
        world.setAcceleration(0f)
        frames = 0
        while (!world.asleep && frames < 60 * 20) { world.step(1f / 60f); frames++ }
        assertTrue(world.asleep)
    }

    @Test fun 위로_세게_던져_화면_밖으로_나가면_한_번만_알린다() {
        val world = settled()
        val b = world.bodies.last()
        assertTrue(world.grab(b.x, b.y))
        world.release(0f, -size * 11f)
        val out = ArrayList<Int>()
        repeat(60 * 6) { world.step(1f / 60f); out += world.pollThrownOut() }
        assertEquals(listOf(b.id), out)
    }

    @Test fun 약하게_던지면_화면_밖으로_나가지_않아_알리지_않는다() {
        val world = settled()
        val b = world.bodies.last()
        assertTrue(world.grab(b.x, b.y))
        world.release(0f, -size * 2f)
        val out = ArrayList<Int>()
        repeat(60 * 6) { world.step(1f / 60f); out += world.pollThrownOut() }
        assertTrue(out.isEmpty())
    }

    @Test fun 옆으로_던지거나_그냥_놓으면_보내기가_아니다() {
        val world = settled()
        val b = world.bodies.last()
        assertTrue(world.grab(b.x, b.y))
        world.release(size * 12f, -size * 2f)
        val out = ArrayList<Int>()
        repeat(60 * 6) { world.step(1f / 60f); out += world.pollThrownOut() }
        assertTrue(out.isEmpty())
    }

    @Test fun 던져서_나간_오리를_치우면_다시_떨어지지_않는다() {
        val world = settled()
        val b = world.bodies.last()
        world.grab(b.x, b.y)
        world.release(0f, -size * 11f)
        var gone = false
        repeat(60 * 4) {
            world.step(1f / 60f)
            for (id in world.pollThrownOut()) { world.removeDuck(id); gone = true }
        }
        assertTrue(gone)
        assertTrue(world.bodies.none { it.id == b.id })
    }

    @Test fun 받은_오리는_왼쪽_밖에서_굴러_들어와_화면_안에_자리잡는다() {
        val world = settled()
        val ids = (1..COUNT).toList().drop(1) + 100
        world.sync(ids, fromLeft = setOf(100))
        val b = world.bodies.first { it.id == 100 }
        assertTrue("처음에는 화면 왼쪽 밖", b.x < 0f)
        var frames = 0
        while (!world.asleep && frames < 60 * 20) { world.step(1f / 60f); frames++ }
        assertTrue(world.asleep)
        for (k in 0 until 4) {
            val (cx, _, r) = world.circleOf(b, k)
            assertTrue("들어온 오리가 화면 안에 있어야 한다: $cx", cx - r >= -size * 0.05f)
        }
    }

    @Test fun 집고_있는_오리의_번호를_알_수_있다() {
        val world = settled()
        val b = world.bodies.last()
        assertEquals(null, world.grabbedId)
        world.grab(b.x, b.y)
        assertEquals(b.id, world.grabbedId)
        world.release(0f, 0f)
        assertEquals(null, world.grabbedId)
    }

    @Test fun 겹친_곳을_누르면_받은_오리_표시가_있는_오리가_먼저_눌린다() {
        val world = DuckWorld(w, h, size)
        val under = world.addDuck(1, 600f, 600f)   // 받은 오리(표시 있음) - 목록에서는 앞쪽이라 일반 순서로는 아래
        val over = world.addDuck(2, 640f, 620f)    // 일반 오리가 위에 겹쳐 있음
        assertTrue(world.hit(620f, 610f) === over)  // 우선순위가 없으면 위에 있는 오리가 눌린다
        world.priorityIds = setOf(1)
        assertTrue(world.hit(620f, 610f) === under) // 받은 오리가 먼저 눌린다
        assertTrue(world.hit(5000f, 5000f) == null)
    }

    @Test fun 시연용으로_맨_위_오리를_날려_보내면_화면_밖으로_나갔다고_한_번_알린다() {
        val world = settled()
        val topId = world.bodies.minByOrNull { it.y }!!.id
        assertEquals(topId, world.launchTopDuck())
        val out = ArrayList<Int>()
        repeat(60 * 4) { world.step(1f / 60f); out += world.pollThrownOut() }
        assertEquals(listOf(topId), out)
    }

    @Test fun 날려_보낼_오리가_없으면_null이다() {
        val world = DuckWorld(w, h, size)
        assertEquals(null, world.launchTopDuck())
    }

    @Test fun 제외한_오리는_날려_보내지_않는다() {
        val world = settled()
        val top = world.bodies.minByOrNull { it.y }!!.id
        val second = world.launchTopDuck(exclude = setOf(top))
        assertTrue(second != null && second != top)
    }
}
