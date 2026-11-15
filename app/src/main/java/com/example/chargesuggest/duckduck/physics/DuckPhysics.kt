package com.example.chargesuggest.duckduck.physics

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/*
 * 오리 통의 물리. Android에 의존하지 않는 순수 Kotlin이라 JVM 단위 테스트로 확인할 수 있다.
 *
 * 한 마리는 "원 네 개를 붙인 단단한 물체"다(머리, 가슴, 몸, 꼬리). 중력, 바닥·양옆 벽, 오리끼리의 충돌과 마찰을 계산하고
 * 손가락으로 집어 끌거나 던질 수 있다. 위쪽 벽은 없다(새 오리가 위에서 떨어지고, 던진 오리는 올라갔다 다시 떨어진다).
 * 모든 길이는 화면 픽셀이고 오리 크기(size = 이미지 가로폭)에 비례하는 상수를 쓴다.
 *
 * 가벼운 고무 인형이라 잘 튀지만 찌그러지지는 않는다. 모두 멈추면 잠들어서(asleep) 계산을 쉰다.
 */

/** 오리 한 마리의 충돌 모양. 오리 이미지의 가로폭을 1로 본 비율이고, 원점은 무게중심이다 */
object DuckShape {
    class C(val x: Float, val y: Float, val r: Float)

    /** 이미지 높이 / 가로폭 (여백을 뺀 오리 그림 기준) */
    const val HEIGHT_RATIO = 1.0565f

    /** 오리 그림(왼쪽을 보는 옆모습) 왼쪽 위를 원점으로 한 원들. 실제 그림에서 측정한 값 (그림의 91%를 덮고 4%만 밖으로 나감) */
    private val img = listOf(
        C(0.40f, 0.25f, 0.26f), // 머리
        C(0.30f, 0.76f, 0.30f), // 가슴·몸 왼쪽
        C(0.66f, 0.74f, 0.30f), // 몸 오른쪽
        C(0.87f, 0.55f, 0.13f), // 꼬리
    )

    /** 무게중심(그림 왼쪽 위 기준) */
    val comX: Float
    val comY: Float

    /** 무게중심 기준 원들 */
    val circles: List<C>

    /** 질량 1일 때의 관성모멘트(가로폭 제곱 단위) */
    val inertia: Float

    /** 무게중심에서 가장 먼 원 가장자리까지 */
    val boundR: Float

    init {
        var a = 0f; var sx = 0f; var sy = 0f
        for (c in img) { val ar = c.r * c.r; a += ar; sx += ar * c.x; sy += ar * c.y }
        comX = sx / a; comY = sy / a
        circles = img.map { C(it.x - comX, it.y - comY, it.r) }
        var i = 0f
        for (c in circles) { val m = c.r * c.r / a; i += m * (c.r * c.r / 2f + c.x * c.x + c.y * c.y) }
        inertia = i
        boundR = circles.maxOf { sqrt(it.x * it.x + it.y * it.y) + it.r }
    }
}

/** 물리 튜닝 값. 오리 크기(size)에 대한 비율이라 화면 크기가 달라도 같은 느낌이 난다 */
object DuckPhysicsConfig {
    /** 중력(오리 크기/초²). 작을수록 느리고 둥둥 뜬 느낌 */
    const val GRAVITY_SIZES = 9f
    /** 튕김. 고무 오리라 통통 튀지만 지나치지 않게 */
    const val RESTITUTION = 0.45f
    const val FRICTION = 0.45f
    /** 이 속도(오리 크기/초)보다 느린 부딪힘은 튕기지 않는다(쌓인 오리가 떨리는 것을 막는다) */
    const val REST_SPEED_SIZES = 0.35f
    const val LINEAR_DAMPING = 0.15f
    const val ANGULAR_DAMPING = 0.6f
    const val ITERATIONS = 6
    const val SUBSTEPS = 3
    /** 손가락으로 집었을 때 손가락을 따라오는 세기(1/초)와 최대 속도(오리 크기/초) */
    const val GRAB_STIFFNESS = 28f
    const val GRAB_MAX_SPEED_SIZES = 14f
    /** 이 속도 이하가 이 시간 이어지면 잠든다 */
    const val SLEEP_SPEED_SIZES = 0.10f
    const val SLEEP_OMEGA = 0.7f
    const val SLEEP_SECONDS = 0.6f
    /** 이 속도(오리 크기/초)보다 세게 위로 던져야 "던져서 보내기"로 본다 */
    const val THROW_MIN_SPEED_SIZES = 5f
}

class DuckBody(val id: Int, var x: Float, var y: Float, var angle: Float, val accelScale: Float = 1f) {
    var vx = 0f
    var vy = 0f
    var omega = 0f
    /** 손가락으로 위쪽으로 세게 던져진 뒤 아직 화면 위로 나가지 않은 오리 */
    internal var thrown = false
    /** 왼쪽에서 들어오는 중: 화면 안쪽으로 충분히 들어올 때까지 왼쪽 벽에 막히지 않는다 */
    internal var entering = false
    /** 통이 가득 차서 밀려나 아래로 빠져 나가는 중: 바닥·벽·다른 오리와 부딪히지 않고 화면 아래로 떨어진다 */
    internal var falling = false
    internal var c = 1f
    internal var s = 0f
    internal val cx = FloatArray(4)
    internal val cy = FloatArray(4)
}

class DuckWorld(val width: Float, val height: Float, val size: Float, seed: Int = 1) {
    /** 그리는 순서(뒤에 있을수록 위에 그려진다). 집은 오리는 맨 뒤로 간다 */
    val bodies = ArrayList<DuckBody>()

    var asleep = true
        private set

    /** 먼저 눌리는 오리의 id(받은 오리 표시가 있는 오리). 그리는 쪽이 갱신한다 */
    var priorityIds: Set<Int> = emptySet()

    /** 이 속도(오리 크기/초)보다 세게 위로 튕기면 화면 위로 나가지 않아도 바로 던진 것으로 본다. 튜토리얼에서는 더 작게 쓴다 */

    var instantThrowSizes = 4f

    private val rnd = Random(seed)
    private val gravity = DuckPhysicsConfig.GRAVITY_SIZES * size
    private val invI = 1f / (DuckShape.inertia * size * size)
    private val rad = FloatArray(4) { DuckShape.circles[it].r * size }
    private val ox = FloatArray(4) { DuckShape.circles[it].x * size }
    private val oy = FloatArray(4) { DuckShape.circles[it].y * size }
    private val restSpeed = DuckPhysicsConfig.REST_SPEED_SIZES * size
    private val bound2 = (2f * DuckShape.boundR * size).let { it * it }

    private var accelX = 0f
    private val thrownOut = ArrayList<Int>()
    private var restTime = 0f

    private var grabbed: DuckBody? = null
    private var grabLx = 0f
    private var grabLy = 0f
    private var targetX = 0f
    private var targetY = 0f

    private class Contact {
        lateinit var a: DuckBody
        var b: DuckBody? = null
        var nx = 0f; var ny = 0f; var px = 0f; var py = 0f; var pen = 0f
    }

    private val pool = ArrayList<Contact>()
    private var nContacts = 0

    // ---------------- 오리 추가·삭제 ----------------

    fun wake() { asleep = false; restTime = 0f }

    fun has(id: Int) = bodies.any { it.id == id }

    /** 오리를 넣는다. 위치는 무게중심 기준 */
    fun addDuck(id: Int, x: Float, y: Float, angle: Float = 0f): DuckBody {
        // 오리마다 차의 쏠림에 반응하는 정도가 조금씩 달라서 한꺼번에 같이 움직이지 않고 흩어진다
        val b = DuckBody(id, x, y, angle, 0.8f + rnd.nextFloat() * 0.4f)
        bodies.add(b)
        wake()
        return b
    }

    fun removeDuck(id: Int) {
        if (grabbed?.id == id) grabbed = null
        bodies.removeAll { it.id == id }
        wake()
    }

    /**
     * 통의 오리 목록(id)에 맞춘다. 없어진 오리는 치우고, 새 오리는 화면 위에서 떨어뜨린다.
     * 처음 맞출 때는 오리들이 차례로 쏟아져 내려온다.
     */
    fun sync(ids: List<Int>, fromLeft: Set<Int> = emptySet()) {
        val keep = ids.toHashSet()
        if (grabbed != null && grabbed!!.id !in keep) grabbed = null
        // 통이 가득 차서 밀려난 오리(가장 오래된 오리)는 갑자기 사라지지 않고, 바닥이 열린 듯 다른 오리와 부딪히지 않고 아래로 빠져 나간다
        for (b in bodies) if (b.id !in keep && !b.falling) { b.falling = true; b.thrown = false; b.entering = false; b.vy = max(b.vy, size * 1.5f) }
        bodies.removeAll { it.id !in keep && !it.falling }
        var k = 0
        for (id in ids) {
            if (has(id)) continue
            if (id in fromLeft) {
                // 받은 오리: 화면 왼쪽 밖에서 굴러 들어온다
                val b = addDuck(id, -size * 0.9f, height * (0.35f + 0.25f * rnd.nextFloat()), (rnd.nextFloat() - 0.5f) * 0.8f)
                b.vx = size * 6f
                b.vy = -size * 1.5f
                b.entering = true
                continue
            }
            val x = size * 0.5f + rnd.nextFloat() * max(1f, width - size)
            val y = -size * (0.8f + 0.95f * k)
            val b = addDuck(id, x, y, (rnd.nextFloat() - 0.5f) * 1.6f)
            b.vx = (rnd.nextFloat() - 0.5f) * size * 0.6f
            k++
        }
        wake()
    }

    // ---------------- 차의 움직임 ----------------

    /** 모든 오리에 순간 속도를 더한다(오리 크기/초 단위가 아니라 픽셀/초). 출발·정차 때의 쏠림에 쓴다 */
    fun nudge(dvx: Float, dvy: Float = 0f) {
        for (b in bodies) { b.vx += dvx; b.vy += dvy }
        wake()
    }

    /** 요철: 모든 오리가 위로 통통 튄다(오리마다 높이와 회전이 조금씩 다르다). up은 픽셀/초 */
    fun jolt(up: Float) {
        for (b in bodies) {
            b.vy -= up * (0.5f + rnd.nextFloat())
            b.omega += (rnd.nextFloat() - 0.5f) * 3f
        }
        wake()
    }

    /** 가로 가속도(픽셀/초²). 브레이크를 밟는 동안 오리들이 앞쪽(+/-)으로 쏠려 있게 한다 */
    fun setAcceleration(ax: Float) {
        if (kotlin.math.abs(ax - accelX) > 1f) { accelX = ax; wake() }
    }

    // ---------------- 손가락 ----------------

    /** 손가락 위치에 있는 가장 위의 오리를 찾는다(없으면 null) */
    fun hit(px: Float, py: Float): DuckBody? {
        // 화면에서 맨 위에 그려지는 오리(받은 오리 표시가 있는 오리)를 먼저 찾는다. 그래야 다른 오리와 겹친 곳을 눌러도 받은 오리가 눌린다
        for (pass in 0..1) for (i in bodies.indices.reversed()) {
            val b = bodies[i]
            if (b.falling || (b.id in priorityIds) != (pass == 0)) continue
            val c = cos(b.angle); val s = sin(b.angle)
            for (k in 0 until 4) {
                val cx = b.x + ox[k] * c - oy[k] * s
                val cy = b.y + ox[k] * s + oy[k] * c
                val dx = px - cx; val dy = py - cy
                if (dx * dx + dy * dy <= rad[k] * rad[k]) return b
            }
        }
        return null
    }

    /** 오리를 집는다. 집은 오리는 손가락을 따라다니고 다른 오리와는 계속 부딪힌다 */
    fun grab(px: Float, py: Float): Boolean {
        val b = hit(px, py) ?: return false
        grabbed = b
        b.thrown = false
        bodies.remove(b); bodies.add(b)
        val c = cos(b.angle); val s = sin(b.angle)
        val dx = px - b.x; val dy = py - b.y
        grabLx = dx * c + dy * s
        grabLy = -dx * s + dy * c
        targetX = px; targetY = py
        b.vx = 0f; b.vy = 0f
        wake()
        return true
    }

    fun dragTo(px: Float, py: Float) {
        if (grabbed == null) return
        targetX = px; targetY = py
        wake()
    }

    /** 놓는다. 손가락 속도(픽셀/초)로 던져진다 */
    fun release(vx: Float, vy: Float) {
        val b = grabbed ?: return
        val maxV = DuckPhysicsConfig.GRAB_MAX_SPEED_SIZES * size
        val sp = sqrt(vx * vx + vy * vy)
        val k = if (sp > maxV) maxV / sp else 1f
        b.vx = vx * k; b.vy = vy * k
        // 위쪽으로 충분히 세게 던졌으면 "던져서 보내기"로 본다(화면 위로 나가면 알린다). 옆이나 아래로 던진 것은 해당 없음
        b.thrown = -b.vy > DuckPhysicsConfig.THROW_MIN_SPEED_SIZES * size && -b.vy > kotlin.math.abs(b.vx) * 0.6f
        // 위로 시원하게 튕겼으면 화면 위까지 날아가지 않아도 던진 것으로 본다(오리가 멀리 못 날아가서 못 보내는 일이 없게)
        if (-b.vy > instantThrowSizes * size && -b.vy > kotlin.math.abs(b.vx) * 0.5f) { thrownOut += b.id; b.thrown = false }
        grabbed = null
        wake()
    }

    val isGrabbing: Boolean get() = grabbed != null

    /**
     * 시연용: 통에서 가장 위에 있는 오리(제외할 id 빼고) 하나를 위로 날려 보낸다. 손가락으로 던진 것과 같게 처리된다.
     * 날려 보낸 오리의 id, 오리가 없으면 null
     */
    fun launchTopDuck(exclude: Set<Int> = emptySet()): Int? {
        val b = bodies.filter { it.id !in exclude && it !== grabbed }.minByOrNull { it.y } ?: return null
        b.vx = size * 2.5f
        b.vy = -size * 16f
        b.omega = 3f
        b.thrown = true
        wake()
        return b.id
    }

    /** 지금 손가락으로 집고 있는 오리의 id(없으면 null) */
    val grabbedId: Int? get() = grabbed?.id

    // ---------------- 한 프레임 ----------------

    fun step(frameSeconds: Float) {
        if (asleep) return
        val dt = frameSeconds.coerceIn(0f, 1f / 30f)
        if (dt <= 0f) return
        val h = dt / DuckPhysicsConfig.SUBSTEPS
        repeat(DuckPhysicsConfig.SUBSTEPS) { substep(h) }
        detectThrownOut()
        bodies.removeAll { it.falling && it.y > height + size * 1.5f } // 화면 아래로 다 빠진 오리
        updateSleep(dt)
    }

    private fun substep(h: Float) {
        val gb = grabbed
        val lin = 1f - DuckPhysicsConfig.LINEAR_DAMPING * h
        val ang = 1f - DuckPhysicsConfig.ANGULAR_DAMPING * h
        for (b in bodies) {
            if (b === gb) continue
            b.vx = (b.vx + accelX * b.accelScale * h) * lin
            b.vy = (b.vy + gravity * h) * lin
            b.omega *= ang
        }
        if (gb != null) {
            val c = cos(gb.angle); val s = sin(gb.angle)
            val ax = gb.x + grabLx * c - grabLy * s
            val ay = gb.y + grabLx * s + grabLy * c
            val maxV = DuckPhysicsConfig.GRAB_MAX_SPEED_SIZES * size
            val dvx = ((targetX - ax) * DuckPhysicsConfig.GRAB_STIFFNESS).coerceIn(-maxV, maxV)
            val dvy = ((targetY - ay) * DuckPhysicsConfig.GRAB_STIFFNESS).coerceIn(-maxV, maxV)
            val rx = ax - gb.x; val ry = ay - gb.y
            gb.vx = dvx + gb.omega * ry
            gb.vy = dvy - gb.omega * rx
            gb.omega *= (1f - 4f * h)
        }
        for (b in bodies) { b.x += b.vx * h; b.y += b.vy * h; b.angle += b.omega * h }
        for (b in bodies) if (b.entering && b.x > size * 0.75f) b.entering = false

        collect()
        repeat(DuckPhysicsConfig.ITERATIONS) { for (i in 0 until nContacts) solve(pool[i]) }
        correct()
    }

    // ---------------- 충돌 ----------------

    private fun newContact(a: DuckBody, b: DuckBody?, nx: Float, ny: Float, px: Float, py: Float, pen: Float) {
        if (nContacts == pool.size) pool.add(Contact())
        val c = pool[nContacts++]
        c.a = a; c.b = b; c.nx = nx; c.ny = ny; c.px = px; c.py = py; c.pen = pen
    }

    private fun collect() {
        nContacts = 0
        for (b in bodies) {
            b.c = cos(b.angle); b.s = sin(b.angle)
            if (b.falling) { for (i in 0 until 4) { b.cx[i] = b.x + ox[i] * b.c - oy[i] * b.s; b.cy[i] = b.y + ox[i] * b.s + oy[i] * b.c }; continue }
            for (i in 0 until 4) {
                b.cx[i] = b.x + ox[i] * b.c - oy[i] * b.s
                b.cy[i] = b.y + ox[i] * b.s + oy[i] * b.c
                val r = rad[i]
                if (b.cy[i] + r > height) newContact(b, null, 0f, 1f, b.cx[i], height, b.cy[i] + r - height)
                if (b.cx[i] - r < 0f && !b.entering) newContact(b, null, -1f, 0f, 0f, b.cy[i], r - b.cx[i])
                if (b.cx[i] + r > width) newContact(b, null, 1f, 0f, width, b.cy[i], b.cx[i] + r - width)
            }
        }
        for (i in bodies.indices) {
            val a = bodies[i]
            for (j in i + 1 until bodies.size) {
                val b = bodies[j]
                // 왼쪽에서 들어오는 중인 오리는 안쪽으로 들어올 때까지 다른 오리를 통과한다(빽빽한 더미를 뚫고 들어올 수 있게)
                if (a.entering || b.entering || a.falling || b.falling) continue // 던져진 오리는 다른 오리를 통과해 날아간다
                val dx = b.x - a.x; val dy = b.y - a.y
                if (dx * dx + dy * dy > bound2) continue
                for (p in 0 until 4) for (q in 0 until 4) {
                    val ddx = b.cx[q] - a.cx[p]; val ddy = b.cy[q] - a.cy[p]
                    val rr = rad[p] + rad[q]
                    val d2 = ddx * ddx + ddy * ddy
                    if (d2 >= rr * rr) continue
                    val d = sqrt(d2)
                    val nx: Float; val ny: Float
                    if (d > 1e-4f) { nx = ddx / d; ny = ddy / d } else { nx = 0f; ny = -1f }
                    val pen = rr - d
                    newContact(a, b, nx, ny, a.cx[p] + nx * (rad[p] - pen / 2f), a.cy[p] + ny * (rad[p] - pen / 2f), pen)
                }
            }
        }
    }

    private fun solve(c: Contact) {
        val a = c.a
        val b = c.b
        val rax = c.px - a.x; val ray = c.py - a.y
        val rbx = if (b != null) c.px - b.x else 0f
        val rby = if (b != null) c.py - b.y else 0f

        fun relVn(): Float {
            val vax = a.vx - a.omega * ray; val vay = a.vy + a.omega * rax
            val vbx = if (b != null) b.vx - b.omega * rby else 0f
            val vby = if (b != null) b.vy + b.omega * rbx else 0f
            return (vbx - vax) * c.nx + (vby - vay) * c.ny
        }
        fun relVt(): Float {
            val vax = a.vx - a.omega * ray; val vay = a.vy + a.omega * rax
            val vbx = if (b != null) b.vx - b.omega * rby else 0f
            val vby = if (b != null) b.vy + b.omega * rbx else 0f
            return (vbx - vax) * (-c.ny) + (vby - vay) * c.nx
        }
        fun apply(jx: Float, jy: Float) {
            a.vx -= jx; a.vy -= jy
            a.omega -= (rax * jy - ray * jx) * invI
            if (b != null) {
                b.vx += jx; b.vy += jy
                b.omega += (rbx * jy - rby * jx) * invI
            }
        }

        val vn = relVn()
        if (vn >= 0f) return // 이미 멀어지는 중
        val raN = rax * c.ny - ray * c.nx
        val rbN = if (b != null) rbx * c.ny - rby * c.nx else 0f
        val kn = 1f + (if (b != null) 1f else 0f) + raN * raN * invI + rbN * rbN * invI
        val e = if (-vn > restSpeed) DuckPhysicsConfig.RESTITUTION else 0f
        val j = -(1f + e) * vn / kn
        apply(c.nx * j, c.ny * j)

        // 마찰
        val vt = relVt()
        val tx = -c.ny; val ty = c.nx
        val raT = rax * ty - ray * tx
        val rbT = if (b != null) rbx * ty - rby * tx else 0f
        val kt = 1f + (if (b != null) 1f else 0f) + raT * raT * invI + rbT * rbT * invI
        val maxF = DuckPhysicsConfig.FRICTION * j
        val jt = (-vt / kt).coerceIn(-maxF, maxF)
        apply(tx * jt, ty * jt)
    }

    private fun correct() {
        val slop = 0.002f * size
        for (i in 0 until nContacts) {
            val c = pool[i]
            // 겹침이 커도(들어온 오리) 한 번에 튀어나가지 않게 한 번에 풀어 주는 양에 상한을 둔다
            val corr = min(max(c.pen - slop, 0f), 0.06f * size) * 0.6f
            if (corr <= 0f) continue
            val b = c.b
            if (b == null) {
                c.a.x -= c.nx * corr; c.a.y -= c.ny * corr
            } else {
                c.a.x -= c.nx * corr * 0.5f; c.a.y -= c.ny * corr * 0.5f
                b.x += c.nx * corr * 0.5f; b.y += c.ny * corr * 0.5f
            }
        }
    }

    /** 던져진 오리가 화면 위쪽 밖으로 나갔는지. 나간 순간 한 번만 알린다. 올라가다 말고 떨어지면(약하게 던짐) 해당 없음 */
    private fun detectThrownOut() {
        for (b in bodies) {
            if (!b.thrown) continue
            if (b.y < -0.35f * size) { b.thrown = false; thrownOut += b.id }
            else if (b.vy > 0f) b.thrown = false
        }
    }

    /** 화면 위로 던져져 나간 오리들의 id를 한 번만 돌려준다 */
    fun pollThrownOut(): List<Int> {
        if (thrownOut.isEmpty()) return emptyList()
        val out = thrownOut.toList()
        thrownOut.clear()
        return out
    }

    private fun updateSleep(dt: Float) {
        if (bodies.any { it.falling }) return // 빠져 나가는 오리가 있으면 다 나갈 때까지 잠들지 않는다
        if (grabbed != null) { restTime = 0f; return }
        var maxV = 0f; var maxW = 0f
        for (b in bodies) {
            maxV = max(maxV, sqrt(b.vx * b.vx + b.vy * b.vy))
            maxW = max(maxW, kotlin.math.abs(b.omega))
        }
        if (maxV < DuckPhysicsConfig.SLEEP_SPEED_SIZES * size && maxW < DuckPhysicsConfig.SLEEP_OMEGA) {
            restTime += dt
            if (restTime >= DuckPhysicsConfig.SLEEP_SECONDS) {
                for (b in bodies) { b.vx = 0f; b.vy = 0f; b.omega = 0f }
                asleep = true
            }
        } else {
            restTime = 0f
        }
    }

    /** 테스트와 그리기용: 오리 i번째 원의 화면 좌표와 반지름 */
    fun circleOf(b: DuckBody, k: Int): Triple<Float, Float, Float> {
        val c = cos(b.angle); val s = sin(b.angle)
        return Triple(b.x + ox[k] * c - oy[k] * s, b.y + ox[k] * s + oy[k] * c, rad[k])
    }

    /** 오리 크기를 화면 크기와 마릿수로 정한다. 오리가 크게 보이도록 10마리가 화면의 절반 남짓을 채우는 밀도 */
    companion object {
        fun duckSizeFor(width: Float, height: Float, count: Int): Float {
            val bySpace = sqrt(0.85f * width * height / max(1, count))
            return min(bySpace, min(width / 3.2f, height / 2.2f))
        }
    }
}
