package com.example.chargesuggest.duckduck.physics

import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/*
 * 차의 움직임을 오리 통이 받는 "가로 가속도"로 바꾼다. 오리들이 화면이 아니라 차 안의 상자에 들어 있다고 생각한다.
 *
 *   출발(가속)  오리들이 뒤(오른쪽)로 쏠린다        브레이크   오리들이 앞(왼쪽)으로 쏠린다
 *   좌회전      오리들이 오른쪽으로 쏠린다           우회전     오리들이 왼쪽으로 쏠린다
 *   요철        오리들이 통통 뛴다
 *
 * 값은 모두 "오리 크기/초²" 단위의 가속도다(중력은 9). +는 화면 오른쪽(차의 뒤쪽·왼쪽 회전의 바깥쪽).
 * 실제 차량 값(조향각, 브레이크 페달)은 setSteering/setBrake로 받고, 시연이나 속도 값이 없을 때를 위해
 * trigger()로 사건을 직접 일으키거나 auto로 가끔 저절로 일어나게 할 수 있다. Android에 의존하지 않아 JVM 테스트가 된다.
 */
class DriveMotion(seed: Int = 3) {

    enum class Event(val durationS: Float, val peak: Float) {
        ACCELERATE(1.5f, 9.5f),
        BRAKE(1.2f, -12f),
        TURN_LEFT(2.1f, 10f),
        TURN_RIGHT(2.1f, -10f),
        BUMP(0.01f, 0f),
    }

    /** ax: 가로 가속도(오리 크기/초²), bump: 위로 튀는 속도(오리 크기/초, 이번 호출에서 처음 일어난 것만) */
    class Output(val ax: Float, val bump: Float)

    private class Active(val event: Event, val scale: Float) { var t = 0f }

    private val rnd = Random(seed)
    private val active = ArrayList<Active>()
    private var steerTarget = 0f
    private var steerNow = 0f
    private var brakeHeld = false
    private var brakeNow = 0f
    private var nextAutoIn = 2f
    private var pendingBump = 0f
    private var speedBumpIn = -1f
    private var rearWheelsIn = -1f

    /** true면 가끔 회전·브레이크·요철이 저절로 일어난다(시연용. 실제 차량 값이 연결되면 끈다) */
    var auto = true

    fun reset() {
        active.clear(); steerNow = 0f; brakeNow = 0f; pendingBump = 0f; nextAutoIn = 2f
    }

    /**
     * 방지턱: delayS초 뒤에 앞바퀴가 턱을 넘는 큰 튐, 0.3초 뒤에 뒷바퀴가 넘는 작은 튐이 이어진다(살짝 감속했다가 다시 가속).
     * 주행을 시작하고 3초쯤 뒤에 한 번 보여 주려고 쓴다. update가 불리는 동안(주행 중)에만 시간이 간다.
     */
    fun startSpeedBump(delayS: Float = 3f) { speedBumpIn = delayS }

    /** 사건 하나를 일으킨다. scale로 세기를 조절한다 */
    fun trigger(event: Event, scale: Float = 1f) {
        if (event == Event.BUMP) { pendingBump += (1.0f + rnd.nextFloat() * 0.5f) * scale; return }
        active.add(Active(event, scale))
    }

    /**
     * 조향각(도). 왼쪽으로 꺾으면 오리가 오른쪽으로 쏠린다. null이면 모름(0으로 본다).
     * TODO(verify): Pleos SteeringWheel 각도의 부호(왼쪽이 음수인지)를 에뮬레이터 Virtual Console로 확인한다.
     */
    fun setSteering(angleDeg: Float?) {
        val a = angleDeg ?: 0f
        val sign = if (LEFT_IS_NEGATIVE) -1f else 1f
        steerTarget = (sign * a / FULL_STEER_DEG).coerceIn(-1f, 1f) * PEAK_TURN
    }

    /** 브레이크 페달. 밟는 순간 앞으로 쏠리고, 밟고 있는 동안 조금 쏠려 있으며, 떼면 살짝 되돌아온다 */
    fun setBrake(pressed: Boolean?) {
        val p = pressed == true
        if (p && !brakeHeld) trigger(Event.BRAKE)
        if (!p && brakeHeld) trigger(Event.ACCELERATE, 0.45f)
        brakeHeld = p
    }

    fun update(dt: Float): Output {
        if (speedBumpIn >= 0f) {
            speedBumpIn -= dt
            if (speedBumpIn < 0f) {
                trigger(Event.BRAKE, 0.35f)            // 턱 앞에서 살짝 감속
                pendingBump += SPEED_BUMP_FRONT         // 앞바퀴가 턱을 넘음
            }
        }
        if (rearWheelsIn >= 0f) {
            rearWheelsIn -= dt
            if (rearWheelsIn < 0f) {
                pendingBump += SPEED_BUMP_REAR          // 뒷바퀴가 턱을 넘음
                trigger(Event.ACCELERATE, 0.5f)         // 다시 가속
            }
        }
        if (auto) {
            nextAutoIn -= dt
            if (nextAutoIn <= 0f) {
                val r = rnd.nextFloat()
                trigger(
                    when {
                        r < 0.35f -> Event.TURN_LEFT
                        r < 0.70f -> Event.TURN_RIGHT
                        r < 0.85f -> Event.BRAKE
                        r < 0.95f -> Event.BUMP
                        else -> Event.ACCELERATE
                    },
                    0.7f + rnd.nextFloat() * 0.5f,
                )
                nextAutoIn = 1.6f + rnd.nextFloat() * 2.6f
            }
        }
        var ax = 0f
        val it = active.iterator()
        while (it.hasNext()) {
            val a = it.next()
            a.t += dt
            if (a.t >= a.event.durationS) { it.remove(); continue }
            ax += a.event.peak * a.scale * sin(PI.toFloat() * a.t / a.event.durationS)
        }
        val k = (dt / 0.25f).coerceAtMost(1f)
        steerNow += (steerTarget - steerNow) * k
        brakeNow += ((if (brakeHeld) HELD_BRAKE else 0f) - brakeNow) * k
        ax = (ax + steerNow + brakeNow).coerceIn(-MAX_AX, MAX_AX)
        val bump = pendingBump
        pendingBump = 0f
        return Output(ax, bump)
    }

    companion object {
        /** 이 각도(도)까지 꺾으면 가장 세게 쏠린다 */
        /** 방지턱 튐의 세기(오리 크기/초). 앞바퀴가 더 크고 뒷바퀴가 작다 */
        const val SPEED_BUMP_FRONT = 2.6f
        const val SPEED_BUMP_REAR = 1.5f

        const val FULL_STEER_DEG = 120f
        const val PEAK_TURN = 10f
        const val HELD_BRAKE = -5f
        const val MAX_AX = 15f

        /** 조향각이 왼쪽일 때 음수인지. AAOS 표준은 왼쪽이 음수. TODO(verify): Pleos 값으로 확인 */
        const val LEFT_IS_NEGATIVE = true
    }
}
