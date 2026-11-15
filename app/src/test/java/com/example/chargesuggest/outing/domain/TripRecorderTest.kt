package com.example.chargesuggest.outing.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripRecorderTest {

    @Test
    fun `구간 거리는 계기판 주행거리의 차이로 계산한다`() {
        val recorder = TripRecorder()
        recorder.start(odometerKm = 100.0f, minOfDay = hm(9, 0))

        val first = recorder.onArrive("강남역", odometerKm = 112.5f, navDistanceM = 13_000, minOfDay = hm(10, 0))
        assertEquals(12.5, first.legDistanceKm, 1e-6)
        assertEquals(DistanceSource.ODOMETER, first.source)
        assertEquals(60, first.legDurationMin)

        val second = recorder.onArrive("판교역", odometerKm = 130.0f, navDistanceM = null, minOfDay = hm(11, 40))
        assertEquals(17.5, second.legDistanceKm, 1e-6)
        assertEquals(100, second.legDurationMin)

        val summary = recorder.summary()
        assertEquals(2, summary.visits)
        assertEquals(30.0, summary.totalKm, 1e-6)
        assertEquals(160, summary.totalMinutes)
        assertFalse(summary.hasEstimates)
    }

    @Test
    fun `계기판 값을 못 받으면 내비 거리를 대신 쓰고 추정으로 표시한다`() {
        val recorder = TripRecorder()
        recorder.start(odometerKm = null, minOfDay = hm(9, 0))

        val record = recorder.onArrive("강남역", odometerKm = null, navDistanceM = 13_000, minOfDay = hm(10, 0))
        assertEquals(13.0, record.legDistanceKm, 1e-6)
        assertEquals(DistanceSource.NAVIGATION, record.source)
        assertTrue(recorder.summary().hasEstimates)
    }

    @Test
    fun `계기판 값이 줄어들면 이상값으로 보고 내비 거리로 대체한다`() {
        val recorder = TripRecorder()
        recorder.start(odometerKm = 500.0f, minOfDay = hm(9, 0))
        val record = recorder.onArrive("강남역", odometerKm = 10.0f, navDistanceM = 8_000, minOfDay = hm(9, 30))
        assertEquals(DistanceSource.NAVIGATION, record.source)
        assertEquals(8.0, record.legDistanceKm, 1e-6)
    }

    @Test
    fun `거리 정보가 전혀 없으면 0km로 두고 알 수 없음으로 표시한다`() {
        val recorder = TripRecorder()
        recorder.start(odometerKm = null, minOfDay = hm(9, 0))
        val record = recorder.onArrive("강남역", odometerKm = null, navDistanceM = null, minOfDay = hm(9, 30))
        assertEquals(0.0, record.legDistanceKm, 1e-6)
        assertEquals(DistanceSource.UNKNOWN, record.source)
    }

    @Test
    fun `방문 목적을 한 번 탭으로 남긴다`() {
        val recorder = TripRecorder()
        recorder.start(odometerKm = 0.0f, minOfDay = hm(9, 0))
        recorder.onArrive("강남역", odometerKm = 5.0f, navDistanceM = null, minOfDay = hm(9, 20))
        recorder.setPurpose(0, "고객 미팅")
        assertEquals("고객 미팅", recorder.visits().single().purpose)
    }
}
