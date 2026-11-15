package com.example.chargesuggest.duck

import com.example.chargesuggest.duck.domain.CarModelResolver
import com.example.chargesuggest.duck.domain.CarNameInfo
import com.example.chargesuggest.duck.domain.MyCarModel
import com.example.chargesuggest.duck.domain.VinCode
import com.example.chargesuggest.duck.domain.VinModelTable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 차량 정보를 읽지 않는 구현 (시험용 기본값) */
object NoModelName : ModelNameSource {
    override fun read(onResult: (CarNameInfo?) -> Unit) = onResult(null)
}

/** 메모리에만 두는 저장소. 시험용이고, 앱을 끄면 사라진다 */
class MemoryStore : KeyValueStore {
    private val map = HashMap<String, String>()
    override fun get(key: String): String? = map[key]
    override fun put(key: String, value: String?) { if (value == null) map.remove(key) else map[key] = value }
}

/**
 * 내 차종을 들고 있다. 직접 고른 값, 차량 정보의 모델명, 차대번호에서 알아낸 값을 우선순위(CarModel.kt 참고)로 합쳐 하나로 정한다.
 * 전체 차대번호는 보관하지 않고 앞 8자리만 남긴다. 직접 고른 값은 저장소에 남아서 앱을 다시 켜도 유지된다.
 */
class CarModelHolder(
    private val vinSource: VinSource,
    private val store: KeyValueStore,
    private val table: VinModelTable = VinModelTable(),
    private val nameSource: ModelNameSource = NoModelName,
) {
    private var vinPrefix: String? = null
    private var nameInfo: CarNameInfo? = null
    /** 차량 정보가 알려 준 제조사 이름 (브랜드 판별용). 읽지 못했으면 null */
    val manufacturer: String? get() = nameInfo?.manufacturer
    private val _model = MutableStateFlow(compute())
    val model: StateFlow<MyCarModel> = _model.asStateFlow()

    /** 차대번호를 읽어서 차종을 갱신한다. 읽지 못하면 직접 선택한 값만 쓴다 */
    fun start() {
        vinSource.read { vin ->
            vinPrefix = VinCode.prefix8(vin)
            _model.value = compute()
        }
        nameSource.read { info ->
            nameInfo = info
            _model.value = compute()
        }
    }

    /** 직접 선택한 차종 id를 저장한다. null이면 직접 선택을 지우고 자동 인식으로 돌아간다 */
    fun chooseManual(modelId: String?) {
        store.put(KEY_MANUAL_MODEL, modelId)
        _model.value = compute()
    }

    private fun compute() = CarModelResolver.resolve(store.get(KEY_MANUAL_MODEL), vinPrefix, table, nameInfo)

    private companion object { const val KEY_MANUAL_MODEL = "manual_car_model" }
}
