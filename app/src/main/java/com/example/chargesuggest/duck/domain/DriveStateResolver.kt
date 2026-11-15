package com.example.chargesuggest.duck.domain

/** 기어 위치. Pleos Vehicle SDK의 기어 값을 앱이 쓰는 단순한 값으로 줄인 것 */
enum class GearPosition { PARK, REVERSE, NEUTRAL, DRIVE, UNKNOWN }

/**
 * 실제 차량 값으로 주행 상태(주차/정차/주행)를 정한다.
 *
 *  - 기어가 P면 주차
 *  - P가 아니고 **주행 규제(운전자 주의 분산 최적화, DO)가 필요하면** 주행 중
 *  - P가 아니지만 주행 규제가 필요 없으면 정차(신호 대기 등)
 *  - 주행 규제 값을 읽지 못했으면 **가장 엄격한 주행 중**으로 본다 (모르면 안전한 쪽)
 *
 * 속도는 Pleos Vehicle SDK에 없어서(크루즈 목표 속도뿐) 쓰지 않고, 플랫폼이 속도로 정하는 주행 규제를 대신 쓴다.
 */
object DriveStateResolver {
    fun resolve(gear: GearPosition, requiresDo: Boolean?): DriveMode = when {
        gear == GearPosition.PARK -> DriveMode.PARKED
        requiresDo == true -> DriveMode.DRIVING
        requiresDo == false -> DriveMode.STOPPED
        else -> DriveMode.DRIVING
    }
}
