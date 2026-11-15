package com.example.chargesuggest.duck

import com.example.chargesuggest.duck.domain.ModelSource
import com.example.chargesuggest.duck.domain.VinModelTable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private class FakeVin(var vin: String?) : VinSource {
    override fun read(onResult: (String?) -> Unit) = onResult(vin)
}

class CarModelHolderTest {
    private val vin = "KMHAB12C3DM456789" // 형식만 맞춘 가짜 값

    @Test
    fun `읽기 전에는 모름이다`() {
        val h = CarModelHolder(FakeVin(vin), MemoryStore())
        assertEquals(ModelSource.NONE, h.model.value.source)
    }

    @Test
    fun `차대번호를 읽으면 같은 차종 키가 정해진다`() {
        val h = CarModelHolder(FakeVin(vin), MemoryStore()).also { it.start() }
        assertEquals(ModelSource.VIN_CODE, h.model.value.source)
        assertEquals("vin:KMHAB12C", h.model.value.key)
    }

    @Test
    fun `차대번호를 못 읽으면 직접 선택으로 정할 수 있다`() {
        val h = CarModelHolder(FakeVin("\"invalid\""), MemoryStore()).also { it.start() }
        assertEquals(ModelSource.NONE, h.model.value.source)
        h.chooseManual("hyundai_ioniq5")
        assertEquals("hyundai_ioniq5", h.model.value.key)
        assertEquals(ModelSource.MANUAL, h.model.value.source)
    }

    @Test
    fun `직접 선택은 저장되어 새 인스턴스에서도 유지된다`() {
        val store = MemoryStore()
        CarModelHolder(FakeVin(null), store).chooseManual("kia_ev6")
        val again = CarModelHolder(FakeVin(null), store)
        assertEquals("kia_ev6", again.model.value.key)
    }

    @Test
    fun `직접 선택을 지우면 차대번호 인식으로 돌아간다`() {
        val h = CarModelHolder(FakeVin(vin), MemoryStore()).also { it.start() }
        h.chooseManual("kia_ev6")
        assertEquals(ModelSource.MANUAL, h.model.value.source)
        h.chooseManual(null)
        assertEquals(ModelSource.VIN_CODE, h.model.value.source)
    }

    @Test
    fun `대응표가 있으면 차대번호로 차종 이름까지 안다`() {
        val h = CarModelHolder(FakeVin(vin), MemoryStore(), VinModelTable(mapOf("KMHAB12C" to "hyundai_kona")))
            .also { it.start() }
        assertEquals("hyundai_kona", h.model.value.key)
        assertEquals(ModelSource.VIN_TABLE, h.model.value.source)
    }

    @Test
    fun `차대번호 전체는 어디에도 남지 않고 키와 문구에 앞 8자리 외 부분이 없다`() {
        val h = CarModelHolder(FakeVin(vin), MemoryStore()).also { it.start() }
        val shown = h.model.value.key + h.model.value.label
        assert(!shown.contains("3DM456789")) { "차대번호 뒷부분이 노출됨: $shown" }
        assertNull(MemoryStore().get("vin"))
    }
}
