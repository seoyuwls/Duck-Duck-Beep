package com.example.chargesuggest.duck

import com.example.chargesuggest.duck.domain.CarModelResolver
import com.example.chargesuggest.duck.domain.ModelSource
import com.example.chargesuggest.duck.domain.VinCode
import com.example.chargesuggest.duck.domain.VinModelTable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CarModelTest {
    // 실제 차대번호가 아니라 형식만 맞춘 가짜 값
    private val vin = "KMHAB12C3DM456789"

    @Test fun `표준 차대번호는 앞 8자리를 돌려준다`() = assertEquals("KMHAB12C", VinCode.prefix8(vin))

    @Test fun `소문자와 공백이 있어도 처리한다`() = assertEquals("KMHAB12C", VinCode.prefix8("  kmhab12c3dm456789 "))

    @Test
    fun `자리표시 값이나 형식이 틀린 값은 null이다`() {
        assertNull(VinCode.prefix8("\"invalid\""))   // 에뮬레이터가 돌려주는 값
        assertNull(VinCode.prefix8("invalid"))
        assertNull(VinCode.prefix8(""))
        assertNull(VinCode.prefix8(null))
        assertNull(VinCode.prefix8("KMHAB12C3DM45678"))      // 16자
        assertNull(VinCode.prefix8("KMHAB12C3DM4567890"))    // 18자
        assertNull(VinCode.prefix8("KMHAO12C3DM456789"))     // O는 쓰지 않는 글자
    }

    @Test
    fun `직접 선택이 가장 우선이다`() {
        val m = CarModelResolver.resolve("hyundai_ioniq5", "KMHAB12C", VinModelTable(mapOf("KMHAB12C" to "hyundai_kona")))
        assertEquals("hyundai_ioniq5", m.key)
        assertEquals(ModelSource.MANUAL, m.source)
    }

    @Test
    fun `대응표에 있으면 차대번호로 차종 이름까지 안다`() {
        val m = CarModelResolver.resolve(null, "KMHAB12C", VinModelTable(mapOf("KMHAB12C" to "hyundai_kona")))
        assertEquals("hyundai_kona", m.key)
        assertEquals(ModelSource.VIN_TABLE, m.source)
    }

    @Test
    fun `대응표에 없으면 차대번호 코드만으로 같은 차종을 판단한다`() {
        val a = CarModelResolver.resolve(null, "KMHAB12C")
        val b = CarModelResolver.resolve(null, "KMHAB12C")
        assertEquals(ModelSource.VIN_CODE, a.source)
        assertEquals(a.key, b.key)                       // 같은 코드끼리는 같은 키
        assertEquals("vin:KMHAB12C", a.key)
        // 화면 문구에는 차대번호 일부가 들어가지 않는다
        assert(!a.label.contains("KMHAB12C"))
    }

    @Test
    fun `아무 정보도 없으면 모름이다`() {
        val m = CarModelResolver.resolve(null, null)
        assertNull(m.key)
        assertEquals(ModelSource.NONE, m.source)
    }

    @Test
    fun `목록에 없는 직접 선택 값은 무시한다`() {
        val m = CarModelResolver.resolve("없는_차종", null)
        assertEquals(ModelSource.NONE, m.source)
    }
}

class CarModelNameTest {
    private val info = { maker: String?, model: String? -> com.example.chargesuggest.duck.domain.CarNameInfo(maker, model, "2024") }

    @Test
    fun `차량 정보의 모델명이 목록과 맞으면 목록의 차종으로 정한다`() {
        val m = CarModelResolver.resolve(null, null, nameInfo = info("Hyundai", "IONIQ 5"))
        assertEquals("hyundai_ioniq5", m.key)
        assertEquals(ModelSource.SYSTEM, m.source)
    }

    @Test
    fun `대소문자와 공백이 달라도 같은 차종으로 맞춘다`() {
        for (name in listOf("ioniq 5", "IONIQ5", "Ioniq-5 Long Range", "아이오닉 5")) {
            assertEquals(name, "hyundai_ioniq5", CarModelResolver.resolve(null, null, nameInfo = info("Hyundai", name)).key)
        }
    }

    @Test
    fun `비슷한 이름은 더 긴 이름이 맞은 차종을 고른다`() {
        // GV70, GV80 같은 이름이 서로 섞이지 않는다
        assertEquals("genesis_gv70", CarModelResolver.resolve(null, null, nameInfo = info("Genesis", "GV70")).key)
        assertEquals("genesis_gv80", CarModelResolver.resolve(null, null, nameInfo = info("Genesis", "GV80")).key)
    }

    @Test
    fun `목록에 없는 모델명은 이름 그대로 같은 차종 키로 쓴다`() {
        val a = CarModelResolver.resolve(null, null, nameInfo = info("Hyundai 42dot", "Connect"))
        val b = CarModelResolver.resolve(null, null, nameInfo = info("hyundai 42dot", " CONNECT "))
        assertEquals(ModelSource.SYSTEM_NAME, a.source)
        assertEquals("Hyundai 42dot Connect", a.label)
        assertEquals(a.key, b.key)    // 대소문자·공백이 달라도 같은 키
    }

    @Test
    fun `직접 선택이 차량 정보보다 우선이다`() {
        val m = CarModelResolver.resolve("kia_ev6", null, nameInfo = info("Hyundai", "IONIQ 5"))
        assertEquals("kia_ev6", m.key)
        assertEquals(ModelSource.MANUAL, m.source)
    }

    @Test
    fun `모델명이 비어 있으면 차대번호로 넘어간다`() {
        val m = CarModelResolver.resolve(null, "KMHAB12C", nameInfo = info("Hyundai", "  "))
        assertEquals(ModelSource.VIN_CODE, m.source)
        assertNull(CarModelResolver.resolve(null, null, nameInfo = info(null, null)).key)
    }

    @Test
    fun `차량 정보가 목록 차종과 맞으면 직접 선택한 사람과 같은 키가 된다`() {
        val auto = CarModelResolver.resolve(null, null, nameInfo = info("Hyundai", "IONIQ 5")).key
        val manual = CarModelResolver.resolve("hyundai_ioniq5", null).key
        assertEquals(manual, auto)    // 서로 다른 방식으로 정해도 같은 차종끼리 만난다
    }
}

class CarModelHolderNameTest {
    private class FakeName(val info: com.example.chargesuggest.duck.domain.CarNameInfo?) : ModelNameSource {
        override fun read(onResult: (com.example.chargesuggest.duck.domain.CarNameInfo?) -> Unit) = onResult(info)
    }
    private class NoVin : VinSource { override fun read(onResult: (String?) -> Unit) = onResult(null) }

    @Test
    fun `시작하면 차량 정보로 차종이 정해진다`() {
        val h = CarModelHolder(NoVin(), MemoryStore(), nameSource = FakeName(com.example.chargesuggest.duck.domain.CarNameInfo("Kia", "EV6", "2025")))
        h.start()
        assertEquals("kia_ev6", h.model.value.key)
        assertEquals(ModelSource.SYSTEM, h.model.value.source)
    }

    @Test
    fun `직접 선택하면 차량 정보를 덮고 지우면 돌아온다`() {
        val h = CarModelHolder(NoVin(), MemoryStore(), nameSource = FakeName(com.example.chargesuggest.duck.domain.CarNameInfo("Kia", "EV6", "2025")))
        h.start()
        h.chooseManual("hyundai_kona")
        assertEquals("hyundai_kona", h.model.value.key)
        h.chooseManual(null)
        assertEquals("kia_ev6", h.model.value.key)
    }
}
