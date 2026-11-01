package app.kawkab.android.presentation.ui.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kawkab.android.BuildConfig
import app.kawkab.android.data.update.UpdateChecker
import app.kawkab.android.domain.update.LatestBuild
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The About screen's "Check for updates" button (task 25c). It asks GitHub only when the owner
 * taps, never on its own (spec §0.2), and only offers the download; Android's installer asks again.
 *
 * @property checker Reads the newest pre-release.
 */
@HiltViewModel
class AboutUpdateViewModel @Inject constructor(private val checker: UpdateChecker) : ViewModel() {
    private val mutableState = MutableStateFlow<UpdateState>(UpdateState.Idle)

    /** What the button and the line under it show. */
    val state: StateFlow<UpdateState> = mutableState.asStateFlow()

    /** The owner tapped "Check for updates". */
    fun check() {
        if (mutableState.value == UpdateState.Checking) return
        mutableState.value = UpdateState.Checking
        viewModelScope.launch {
            val latest = checker.latest()
            mutableState.value = when {
                latest == null -> UpdateState.Failed
                latest.isNewerThan(BuildConfig.GIT_SHA) -> UpdateState.Available(latest)
                else -> UpdateState.UpToDate
            }
        }
    }
}

/** The steps of one owner-started update check. */
sealed interface UpdateState {
    /** Not checked since the screen opened. */
    data object Idle : UpdateState

    /** Asking GitHub. */
    data object Checking : UpdateState

    /** The installed build is the newest. */
    data object UpToDate : UpdateState

    /** GitHub could not be reached or answered something else. */
    data object Failed : UpdateState

    /**
     * A newer build is published.
     *
     * @property build Its tag and download link.
     */
    data class Available(val build: LatestBuild) : UpdateState
}
