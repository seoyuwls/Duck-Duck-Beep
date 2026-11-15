package com.example.chargesuggest.duck.nearby

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeohashTest {

    @Test
    fun `알려진 좌표가 알려진 격자 번호로 바뀐다`() {
        // 격자 번호 표준 예시 (위키백과의 geohash 예시 값)
        assertEquals("u4pruydqqvj", Geohash.encode(57.64911, 10.40744, 11))
        assertEquals("ezs42", Geohash.encode(42.605, -5.603, 5))
    }

    @Test
    fun `격자 번호를 풀면 원래 좌표가 그 영역 안에 있다`() {
        val lat = 37.5665; val lon = 126.9780
        val box = Geohash.decodeBox(Geohash.encode(lat, lon, 7))
        assertTrue(lat in box.latLo..box.latHi)
        assertTrue(lon in box.lonLo..box.lonHi)
    }

    @Test
    fun `7자리 격자는 약 150m 크기이다`() {
        val box = Geohash.decodeBox(Geohash.encode(37.5665, 126.9780, 7))
        val heightM = GeoMath.distanceM(box.latLo, box.centerLon, box.latHi, box.centerLon)
        val widthM = GeoMath.distanceM(box.centerLat, box.lonLo, box.centerLat, box.lonHi)
        assertEquals(153.0, heightM, 5.0)
        assertTrue("가로 $widthM", widthM in 110.0..130.0) // 한국 위도에서는 가로가 더 짧다
    }

    @Test
    fun `이웃은 8칸이고 자기 자신은 빠진다`() {
        val cell = Geohash.encode(37.5665, 126.9780, 7)
        val n = Geohash.neighbors(cell)
        assertEquals(8, n.size)
        assertFalse(cell in n)
        assertEquals(9, Geohash.cellAndNeighbors(cell).size)
    }

    @Test
    fun `한 칸 옆으로 간 지점은 이웃 안에 있고 두 칸 옆은 밖이다`() {
        val lat = 37.5665; val lon = 126.9780
        val cell = Geohash.encode(lat, lon, 7)
        val box = Geohash.decodeBox(cell)
        val oneEast = Geohash.encode(lat, lon + box.width, 7)
        val twoEast = Geohash.encode(lat, lon + 2 * box.width, 7)
        val oneNorth = Geohash.encode(lat + box.height, lon, 7)
        assertTrue(oneEast in Geohash.neighbors(cell))
        assertTrue(oneNorth in Geohash.neighbors(cell))
        assertFalse(twoEast in Geohash.cellAndNeighbors(cell))
    }

    @Test
    fun `경도 180도 경계에서도 반대편 칸이 이웃이 된다`() {
        val west = Geohash.encode(0.0, 179.9999, 5)
        val east = Geohash.encode(0.0, -179.9999, 5)
        assertTrue(east in Geohash.neighbors(west))
    }

    @Test
    fun `극지방 끝에서도 오류 없이 이웃을 구한다`() {
        val top = Geohash.encode(89.9999, 10.0, 5)
        val n = Geohash.neighbors(top)
        assertTrue(n.size in 5..8)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `잘못된 격자 번호는 오류이다`() { Geohash.decodeBox("a") /* a는 쓰지 않는 글자 */ }

    @Test
    fun `거리 계산이 맞다`() {
        // 위도 0.001도 차이는 약 111m
        assertEquals(111.3, GeoMath.distanceM(37.0, 127.0, 37.001, 127.0), 1.0)
        assertEquals(0.0, GeoMath.distanceM(37.0, 127.0, 37.0, 127.0), 1e-9)
    }

    @Test
    fun `동쪽으로 100m 움직이면 거리도 100m이다`() {
        val (lat, lon) = GeoMath.moveByMeters(37.5665, 126.9780, 100.0, 0.0)
        assertEquals(100.0, GeoMath.distanceM(37.5665, 126.9780, lat, lon), 1.0)
    }
}
