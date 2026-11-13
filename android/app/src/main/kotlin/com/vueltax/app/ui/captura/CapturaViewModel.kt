package com.vueltax.app.ui.captura

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vueltax.app.capture.ocr.ReceiptOcr
import com.vueltax.app.capture.parser.InvoiceQrParsers
import com.vueltax.app.capture.parser.ReceiptTextParser
import com.vueltax.app.capture.review.InvoiceDraft
import com.vueltax.app.capture.review.InvoiceReviewer
import com.vueltax.app.capture.review.ReviewResult
import com.vueltax.app.capture.storage.EncryptedImageStore
import com.vueltax.app.data.local.PurchaseDao
import com.vueltax.app.data.local.RuleSetDao
import com.vueltax.app.data.local.TripDao
import com.vueltax.app.data.local.toDomain
import com.vueltax.app.data.local.toEntity
import com.vueltax.domain.AppConfig
import com.vueltax.domain.model.Purchase
import com.vueltax.domain.rules.RuleSet
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException

enum class CapturaStep { SCANNING, REVIEW }

data class CapturaUiState(
    val step: CapturaStep = CapturaStep.SCANNING,
    val draft: InvoiceDraft = InvoiceDraft(country = AppConfig.SUPPORTED_COUNTRIES.first()),
    val result: ReviewResult = ReviewResult.NoRules,
    /** Categorías que las reglas del país distinguen, para que el usuario elija una. */
    val categories: List<String> = emptyList(),
    val hasTrip: Boolean = false,
    val busy: Boolean = false,
    val unknownQr: Boolean = false,
    val saved: Boolean = false,
)

/** Reglas y facturas del país elegido, contra las que se evalúa el borrador. */
private data class CountryContext(
    val ruleSet: RuleSet? = null,
    val tripId: Long? = null,
    val otherPurchases: List<Purchase> = emptyList(),
)

@HiltViewModel
class CapturaViewModel @Inject constructor(
    private val ruleSetDao: RuleSetDao,
    private val tripDao: TripDao,
    private val purchaseDao: PurchaseDao,
    private val imageStore: EncryptedImageStore,
    private val ocr: ReceiptOcr,
    private val clock: Clock,
) : ViewModel() {
    private val _state = MutableStateFlow(CapturaUiState())
    val state: StateFlow<CapturaUiState> = _state.asStateFlow()

    private var context = CountryContext()

    init {
        viewModelScope.launch { applyDraft(_state.value.draft) }
    }

    fun onCountrySelected(country: String) {
        viewModelScope.launch { applyDraft(_state.value.draft.copy(country = country, category = null)) }
    }

    /** La cámara avisa en cada fotograma con un código; solo se atiende mientras se escanea. */
    fun onQrScanned(raw: String) {
        if (_state.value.step != CapturaStep.SCANNING || _state.value.busy) return
        val parsed = InvoiceQrParsers.parse(raw)
        if (parsed == null) {
            _state.update { it.copy(unknownQr = true) }
            return
        }
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            applyDraft(_state.value.draft.mergedWith(parsed), CapturaStep.REVIEW)
        }
    }

    /** Sin QR: guarda la foto cifrada y propone los datos que reconoce el OCR. */
    fun onPhotoTaken(jpeg: ByteArray, rotationDegrees: Int) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val imagePath = imageStore.save(jpeg)
            val text = ocr.recognize(jpeg, rotationDegrees)
            val draft = _state.value.draft.copy(imagePath = imagePath)
            val parsed = ReceiptTextParser.parse(text, draft.country)
            applyDraft(draft.mergedWith(parsed), CapturaStep.REVIEW)
        }
    }

    fun onEnterManually() {
        _state.update { it.copy(step = CapturaStep.REVIEW, unknownQr = false) }
    }

    fun onDraftChanged(transform: (InvoiceDraft) -> InvoiceDraft) {
        val draft = transform(_state.value.draft)
        _state.update { it.copy(draft = draft, result = review(draft)) }
    }

    fun onSave() {
        val tripId = context.tripId
        val purchase = (_state.value.result as? ReviewResult.Evaluated)?.purchase
        if (tripId == null || purchase == null || _state.value.busy) return

        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val draft = _state.value.draft
            purchaseDao.upsert(purchase.toEntity(draft.issuerTaxId, draft.documentKey))
            _state.update { it.copy(busy = false, saved = true) }
        }
    }

    /** Descarta el borrador y borra la foto que se hubiera guardado. */
    fun onDiscard() {
        val imagePath = _state.value.draft.imagePath
        viewModelScope.launch {
            if (imagePath != null) imageStore.delete(imagePath)
            applyDraft(InvoiceDraft(country = _state.value.draft.country), CapturaStep.SCANNING)
        }
    }

    /** Publica el borrador, recargando las reglas y el viaje si cambió el país. */
    private suspend fun applyDraft(draft: InvoiceDraft, step: CapturaStep = _state.value.step) {
        context = loadContext(draft.country)
        val ruleSet = context.ruleSet
        _state.update {
            it.copy(
                step = step,
                draft = draft,
                result = review(draft),
                categories = ruleSet?.let { rules -> rules.eligibleGoods + rules.excludedGoods }.orEmpty(),
                hasTrip = context.tripId != null,
                busy = false,
                unknownQr = false,
            )
        }
    }

    private fun review(draft: InvoiceDraft): ReviewResult = InvoiceReviewer.review(
        draft = draft,
        ruleSet = context.ruleSet,
        otherPurchases = context.otherPurchases,
        tripId = context.tripId ?: 0,
        today = LocalDate.now(clock),
    )

    private suspend fun loadContext(country: String): CountryContext {
        val trip = tripDao.latestForCountry(country)
        return CountryContext(
            ruleSet = ruleSetDao.get(country)?.contentJson?.let(::parseRules),
            tripId = trip?.id,
            otherPurchases = trip?.let { purchaseDao.listByTrip(it.id).map { entity -> entity.toDomain() } }
                .orEmpty(),
        )
    }

}

private const val TAG = "Captura"

private fun parseRules(json: String): RuleSet? = try {
    RuleSet.parse(json)
} catch (e: SerializationException) {
    Log.w(TAG, "Reglas guardadas con un formato que esta versión no entiende", e)
    null
}
