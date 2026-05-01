package com.inknironapps.bagger.ui.screens.add_disc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun AddDiscRoute(onDone: () -> Unit, vm: AddDiscViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    when (state.step) {
        AddStep.Camera -> CameraStep(onPhoto = vm::onPhotoCapturedNoContext, processing = state.processing)
        AddStep.Confirm -> ConfirmStep(
            disc = state.confidentMatch!!,
            onAccept = vm::confirmMatch,
            onReject = vm::rejectMatch
        )
        AddStep.Pick -> PickStep(
            candidates = state.candidates,
            onPick = vm::pickCandidate,
            onSearch = { vm.rejectMatch() }
        )
        AddStep.ManualSearch -> ManualSearchStep(
            initialQuery = state.tokens.joinToString(" "),
            onPick = vm::selectFromManualSearch
        )
        AddStep.DetailsForm -> DetailsFormStep(
            state = state,
            onPlastic = vm::setPlastic,
            onWeight = vm::setWeight,
            onColor = vm::setColor,
            onCondition = vm::setCondition,
            onNotes = vm::setNotes,
            onSave = vm::save
        )
        AddStep.Saved -> {
            LaunchedEffect(Unit) { vm.reset(); onDone() }
        }
    }
}
