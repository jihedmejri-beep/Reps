package com.reps.app.feature.bodymap

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reps.app.R
import com.reps.app.domain.model.BodyMap
import com.reps.app.domain.model.BodyMuscle
import com.reps.app.domain.model.Exercise
import com.reps.app.domain.repository.ExerciseRepository
import com.reps.app.domain.repository.MuscleSvgRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/** Which body illustration the map is showing. */
enum class BodySide(@param:StringRes val labelRes: Int) {
    FRONT(R.string.exercise_body_front),
    BACK(R.string.exercise_body_back),
}

data class BodyMapUiState(
    val loading: Boolean = true,
    val frontBodyAsset: String? = null,
    val backBodyAsset: String? = null,
    val frontMuscles: List<BodyMuscle> = emptyList(),
    val backMuscles: List<BodyMuscle> = emptyList(),
    val side: BodySide = BodySide.FRONT,
    /** Null until the user taps a muscle; cleared by tapping it again. */
    val selectedMuscle: BodyMuscle? = null,
    val exercises: List<Exercise> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BodyMapViewModel @Inject constructor(
    private val exerciseRepository: ExerciseRepository,
    muscleSvgRepository: MuscleSvgRepository,
) : ViewModel() {

    private val side = MutableStateFlow(BodySide.FRONT)
    private val selectedName = MutableStateFlow<String?>(null)

    /**
     * The artwork index cannot change without a new APK, so it loads once and
     * lives for as long as the screen does.
     */
    private val bodyMap: StateFlow<BodyMap?> = flow { emit(muscleSvgRepository.bodyMap()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val exercises = selectedName.flatMapLatest { name ->
        if (name == null) flowOf(emptyList())
        else exerciseRepository.observeExercisesByMuscle(name)
    }

    val uiState: StateFlow<BodyMapUiState> = combine(
        bodyMap,
        side,
        selectedName,
        exercises,
    ) { map, currentSide, selected, exercises ->
        val muscles = map?.muscles.orEmpty()
        BodyMapUiState(
            loading = map == null,
            frontBodyAsset = map?.frontBodyAsset,
            backBodyAsset = map?.backBodyAsset,
            frontMuscles = muscles.filter { it.isFront },
            backMuscles = muscles.filterNot { it.isFront },
            side = currentSide,
            selectedMuscle = muscles.firstOrNull { it.name == selected },
            exercises = exercises,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BodyMapUiState(),
    )

    fun onSideChange(newSide: BodySide) {
        if (side.value == newSide) return
        side.value = newSide
        // A selection belongs to one illustration; on the other it would be an
        // invisible filter over the list below, which reads as a bug.
        selectedName.value = null
    }

    fun onMuscleTap(name: String?) {
        selectedName.value = if (name == selectedName.value) null else name
    }
}
