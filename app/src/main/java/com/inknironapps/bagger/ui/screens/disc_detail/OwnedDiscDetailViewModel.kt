package com.inknironapps.bagger.ui.screens.disc_detail

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inknironapps.bagger.data.db.entity.BagEntity
import com.inknironapps.bagger.data.db.entity.DiscEntity
import com.inknironapps.bagger.data.db.entity.LostDiscEventEntity
import com.inknironapps.bagger.data.db.entity.OwnedDiscEntity
import com.inknironapps.bagger.data.db.entity.OwnedDiscPhotoEntity
import com.inknironapps.bagger.data.location.LocationProvider
import com.inknironapps.bagger.data.photo.PhotoStorage
import com.inknironapps.bagger.domain.repo.BagRepository
import com.inknironapps.bagger.domain.repo.DiscCatalogRepository
import com.inknironapps.bagger.domain.repo.LostDiscEventRepository
import com.inknironapps.bagger.domain.repo.OwnedDiscPhotoRepository
import com.inknironapps.bagger.domain.repo.OwnedDiscRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class OwnedDetailUi(
    val owned: OwnedDiscEntity? = null,
    val catalog: DiscEntity? = null,
    val bags: List<BagEntity> = emptyList(),
    val photos: List<OwnedDiscPhotoEntity> = emptyList()
)

@HiltViewModel
class OwnedDiscDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val ownedRepo: OwnedDiscRepository,
    private val catalogRepo: DiscCatalogRepository,
    private val lostRepo: LostDiscEventRepository,
    private val photoRepo: OwnedDiscPhotoRepository,
    private val photoStorage: PhotoStorage,
    private val location: LocationProvider,
    bagRepo: BagRepository
) : ViewModel() {
    private val ownedId: String = checkNotNull(savedState["ownedId"])

    val ui: StateFlow<OwnedDetailUi> = combine(
        ownedRepo.observeById(ownedId),
        bagRepo.observeAll(),
        photoRepo.observeForDisc(ownedId)
    ) { owned, bags, photos -> Triple(owned, bags, photos) }
        .map { (owned, bags, photos) ->
            val catalog = owned?.let { catalogRepo.getById(it.discId) }
            OwnedDetailUi(owned, catalog, bags, photos)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OwnedDetailUi())

    fun changeState(state: String, bagId: String? = null) {
        val o = ui.value.owned ?: return
        viewModelScope.launch {
            ownedRepo.upsert(
                o.copy(
                    state = state,
                    bagId = bagId,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun markLost(courseName: String?, hole: Int?, notes: String?, captureGps: Boolean) {
        val o = ui.value.owned ?: return
        viewModelScope.launch {
            val pos = if (captureGps) location.current() else null
            lostRepo.upsert(
                LostDiscEventEntity(
                    id = UUID.randomUUID().toString(),
                    ownedDiscId = o.id,
                    lostAt = System.currentTimeMillis(),
                    lat = pos?.lat,
                    lng = pos?.lng,
                    courseName = courseName,
                    holeNumber = hole,
                    notes = notes,
                    foundAt = null
                )
            )
            ownedRepo.upsert(o.copy(state = "Lost", updatedAt = System.currentTimeMillis()))
        }
    }

    fun delete() {
        val o = ui.value.owned ?: return
        viewModelScope.launch { ownedRepo.delete(o) }
    }

    fun addPhotoFromUri(uri: Uri, type: String = "Front") {
        viewModelScope.launch {
            try {
                val bitmap = context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it)
                } ?: return@launch
                val file = photoStorage.savePhoto(bitmap)
                photoRepo.upsert(
                    OwnedDiscPhotoEntity(
                        id = UUID.randomUUID().toString(),
                        ownedDiscId = ownedId,
                        localPath = file.absolutePath,
                        type = type,
                        capturedAt = System.currentTimeMillis()
                    )
                )
            } catch (_: Exception) {
            }
        }
    }

    fun deletePhoto(photo: OwnedDiscPhotoEntity) {
        viewModelScope.launch {
            photoRepo.delete(photo)
            photoStorage.delete(photo.localPath)
        }
    }
}
