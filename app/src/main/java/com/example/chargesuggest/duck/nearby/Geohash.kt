package com.example.chargesuggest.duck.nearby

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * 근처 인식에서 "대략적인 위치"를 다루는 부분 (docs/nearby-design.md).
 * 정확한 위도·경도 대신 격자 번호(geohash)만 서버로 보내서 위치를 가린다.
 * 7자리 격자는 약 150m x 120m(한국 위도 기준)이고, 이웃 8칸까지 합치면 서로 최대 약 300m 안이다.
 */
object Geohash {
    private const val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"
    const val DEFAULT_PRECISION = 7

    data class Box(val latLo: Double, val latHi: Double, val lonLo: Double, val lonHi: Double) {
        val centerLat get() = (latLo + latHi) / 2
        val centerLon get() = (lonLo + lonHi) / 2
        val height get() = latHi - latLo
        val width get() = lonHi - lonLo
    }

    /** 위도·경도를 격자 번호로 바꾼다 */
    fun encode(lat: Double, lon: Double, precision: Int = DEFAULT_PRECISION): String {
        val la = lat.coerceIn(-90.0, 90.0 - 1e-9)
        val lo = normalizeLon(lon)
        var latLo = -90.0; var latHi = 90.0; var lonLo = -180.0; var lonHi = 180.0
        val sb = StringBuilder()
        var bits = 0; var value = 0; var evenBit = true
        while (sb.length < precision) {
            if (evenBit) {
                val mid = (lonLo + lonHi) / 2
                if (lo >= mid) { value = (value shl 1) or 1; lonLo = mid } else { value = value shl 1; lonHi = mid }
            } else {
                val mid = (latLo + latHi) / 2
                if (la >= mid) { value = (value shl 1) or 1; latLo = mid } else { value = value shl 1; latHi = mid }
            }
            evenBit = !evenBit
            if (++bits == 5) { sb.append(BASE32[value]); bits = 0; value = 0 }
        }
        return sb.toString()
    }

    /** 격자 번호가 가리키는 영역 */
    fun decodeBox(hash: String): Box {
        var latLo = -90.0; var latHi = 90.0; var lonLo = -180.0; var lonHi = 180.0
        var evenBit = true
        for (ch in hash) {
            val idx = BASE32.indexOf(ch)
            require(idx >= 0) { "잘못된 격자 번호: $hash" }
            for (bit in 4 downTo 0) {
                val b = (idx shr bit) and 1
                if (evenBit) { val mid = (lonLo + lonHi) / 2; if (b == 1) lonLo = mid else lonHi = mid }
                else { val mid = (latLo + latHi) / 2; if (b == 1) latLo = mid else latHi = mid }
                evenBit = !evenBit
            }
        }
        return Box(latLo, latHi, lonLo, lonHi)
    }

    /** 둘러싼 8칸. 극지방 끝은 건너뛰고, 경도 180도 경계는 반대편으로 이어 준다 */
    fun neighbors(hash: String): Set<String> {
        val box = decodeBox(hash)
        val out = LinkedHashSet<String>()
        for (dLat in -1..1) for (dLon in -1..1) {
            if (dLat == 0 && dLon == 0) continue
            val lat = box.centerLat + dLat * box.height
            if (lat < -90.0 || lat >= 90.0) continue
            out += encode(lat, box.centerLon + dLon * box.width, hash.length)
        }
        out.remove(hash)
        return out
    }

    fun cellAndNeighbors(hash: String): Set<String> = neighbors(hash) + hash

    private fun normalizeLon(lon: Double): Double {
        var x = ((lon + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
        if (x >= 180.0) x -= 360.0
        return x
    }
}

object GeoMath {
    private const val EARTH_RADIUS_M = 6_371_000.0

    /** 두 지점 사이의 거리(m) */
    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = lat1 * PI / 180; val p2 = lat2 * PI / 180
        val dp = (lat2 - lat1) * PI / 180; val dl = (lon2 - lon1) * PI / 180
        val a = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(a))
    }

    /** 동쪽(x)·북쪽(y)으로 몇 m 움직인 위치. 짧은 거리에서만 정확하다 */
    fun moveByMeters(lat: Double, lon: Double, eastM: Double, northM: Double): Pair<Double, Double> {
        val dLat = northM / 111_320.0
        val dLon = eastM / (111_320.0 * cos(lat * PI / 180))
        return (lat + dLat) to (lon + dLon)
    }
}
