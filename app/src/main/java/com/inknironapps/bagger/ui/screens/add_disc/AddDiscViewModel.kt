package com.inknironapps.bagger.ui.screens.add_disc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.inknironapps.bagger.data.db.dao.IdSubmissionQueueDao
import com.inknironapps.bagger.data.db.entity.DiscEntity
import com.inknironapps.bagger.data.db.entity.IdSubmissionQueueEntity
import com.inknironapps.bagger.data.db.entity.OwnedDiscEntity
import com.inknironapps.bagger.data.photo.PhotoStorage
import com.inknironapps.bagger.domain.repo.DiscCatalogRepository
import com.inknironapps.bagger.domain.repo.OwnedDiscRepository
import com.inknironapps.bagger.ml.DiscMatcher
import com.inknironapps.bagger.ml.MatchResult
import com.inknironapps.bagger.ml.ScoredDisc
import com.inknironapps.bagger.ml.TokenExtractor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.resume

enum class AddStep { Camera, Confirm, Pick, ManualSearch, DetailsForm, Saved }

data class AddDiscState(
    val step: AddStep = AddStep.Camera,
    val photoPath: String? = null,
    val tokens: List<String> = emptyList(),
    val candidates: List<ScoredDisc> = emptyList(),
    val confidentMatch: DiscEntity? = null,
    val selectedDisc: DiscEntity? = null,
    val plasticType: String = "",
    val weight: String = "",
    val color: String = "",
    val condition: String = "Good",
    val notes: String = "",
    val isOriginalOwner: Boolean = true,
    val processing: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AddDiscViewModel @Inject constructor(
    private val photoStorage: PhotoStorage,
    private val catalogRepo: DiscCatalogRepository,
    private val ownedRepo: OwnedDiscRepository,
    private val idQueueDao: IdSubmissionQueueDao
) : ViewModel() {

    private val _state = MutableStateFlow(AddDiscState())
    val state: StateFlow<AddDiscState> = _state.asStateFlow()

    private val matcher = DiscMatcher()

    fun onPhotoCapturedNoContext(file: File) {
        _state.value = _state.value.copy(photoPath = file.absolutePath, processing = true)
        viewModelScope.launch {
            try {
                val bitmap = photoStorage.loadBitmap(file.absolutePath)
                    ?: throw IllegalStateException("decode failed")
                val image = InputImage.fromBitmap(bitmap, 0)
                val text = recognizeText(image)
                val tokens = TokenExtractor.extract(text)
                val catalog = catalogRepo.observeAll().first()
                val result = matcher.match(tokens, catalog)
                handleMatch(tokens, result)
            } catch (e: Exception) {
                _state.value = _state.value.copy(processing = false, error = e.message, step = AddStep.ManualSearch)
            }
        }
    }

    private suspend fun recognizeText(image: InputImage): String =
        suspendCancellableCoroutine { cont ->
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(image)
                .addOnSuccessListener { cont.resume(it.text) }
                .addOnFailureListener { cont.resume("") }
        }

    private fun handleMatch(tokens: List<String>, result: MatchResult) {
        _state.value = when (result) {
            is MatchResult.Confident -> _state.value.copy(
                step = AddStep.Confirm,
                tokens = tokens,
                confidentMatch = result.disc,
                selectedDisc = result.disc,
                processing = false
            )
            is MatchResult.Candidates -> _state.value.copy(
                step = AddStep.Pick,
                tokens = tokens,
                candidates = result.candidates,
                processing = false
            )
            is MatchResult.Fallback -> _state.value.copy(
                step = AddStep.ManualSearch,
                tokens = tokens,
                processing = false
            )
        }
    }

    fun confirmMatch() { _state.value = _state.value.copy(step = AddStep.DetailsForm) }
    fun rejectMatch() { _state.value = _state.value.copy(step = AddStep.ManualSearch, confidentMatch = null) }
    fun pickCandidate(disc: DiscEntity) { _state.value = _state.value.copy(selectedDisc = disc, step = AddStep.DetailsForm) }
    fun selectFromManualSearch(disc: DiscEntity) {
        _state.value = _state.value.copy(selectedDisc = disc, step = AddStep.DetailsForm)
        val tokens = _state.value.tokens
        val photo = _state.value.photoPath
        if (tokens.isNotEmpty() && photo != null) {
            viewModelScope.launch {
                idQueueDao.upsert(
                    IdSubmissionQueueEntity(
                        id = UUID.randomUUID().toString(),
                        photoPath = photo,
                        confirmedDiscId = disc.id,
                        ocrTokens = tokens,
                        capturedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun setPlastic(s: String) { _state.value = _state.value.copy(plasticType = s) }
    fun setWeight(s: String) { _state.value = _state.value.copy(weight = s) }
    fun setColor(s: String) { _state.value = _state.value.copy(color = s) }
    fun setCondition(s: String) { _state.value = _state.value.copy(condition = s) }
    fun setNotes(s: String) { _state.value = _state.value.copy(notes = s) }

    fun save() {
        val disc = _state.value.selectedDisc ?: return
        val s = _state.value
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            ownedRepo.upsert(
                OwnedDiscEntity(
                    id = UUID.randomUUID().toString(),
                    discId = disc.id,
                    plasticType = s.plasticType.takeIf { it.isNotBlank() },
                    weight = s.weight.toIntOrNull(),
                    color = s.color.takeIf { it.isNotBlank() },
                    condition = s.condition,
                    state = "Shelf",
                    bagId = null,
                    purchaseDate = null,
                    purchasePrice = null,
                    notes = s.notes.takeIf { it.isNotBlank() },
                    isOriginalOwner = s.isOriginalOwner,
                    customTags = emptyList(),
                    createdAt = now, updatedAt = now,
                    userId = null, syncedAt = null
                )
            )
            _state.value = _state.value.copy(step = AddStep.Saved)
        }
    }

    fun reset() { _state.value = AddDiscState() }
}
