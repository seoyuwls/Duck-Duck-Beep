package com.example.chargesuggest.duck

import com.example.chargesuggest.duck.domain.DriveMode
import com.example.chargesuggest.duck.domain.DriveStateResolver.resolve
import com.example.chargesuggest.duck.domain.GearPosition
import org.junit.Assert.assertEquals
import org.junit.Test

class DriveStateResolverTest {

    @Test
    fun `기어가 P면 주행 규제 값과 상관없이 주차다`() {
        assertEquals(DriveMode.PARKED, resolve(GearPosition.PARK, requiresDo = false))
        assertEquals(DriveMode.PARKED, resolve(GearPosition.PARK, requiresDo = true))
        assertEquals(DriveMode.PARKED, resolve(GearPosition.PARK, requiresDo = null))
    }

    @Test
    fun `P가 아니고 주행 규제가 필요하면 주행 중이다`() {
        for (g in listOf(GearPosition.DRIVE, GearPosition.REVERSE, GearPosition.NEUTRAL, GearPosition.UNKNOWN)) {
            assertEquals("기어 $g", DriveMode.DRIVING, resolve(g, requiresDo = true))
        }
    }

    @Test
    fun `P가 아니어도 주행 규제가 필요 없으면 정차다`() {
        assertEquals(DriveMode.STOPPED, resolve(GearPosition.DRIVE, requiresDo = false))
        assertEquals(DriveMode.STOPPED, resolve(GearPosition.NEUTRAL, requiresDo = false))
    }

    @Test
    fun `주행 규제 값을 모르면 안전하게 주행 중으로 본다`() {
        assertEquals(DriveMode.DRIVING, resolve(GearPosition.DRIVE, requiresDo = null))
        assertEquals(DriveMode.DRIVING, resolve(GearPosition.UNKNOWN, requiresDo = null))
    }

    @Test
    fun `기어를 모르고 주행 규제도 없으면 정차다`() {
        assertEquals(DriveMode.STOPPED, resolve(GearPosition.UNKNOWN, requiresDo = false))
    }
}
