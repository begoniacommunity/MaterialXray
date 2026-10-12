package com.material.xray.feature.xraycore

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.material.xray.core.ui.components.ActionFeedback
import com.material.xray.core.xraycore.XrayCoreException
import com.material.xray.core.xraycore.XrayCoreFailure
import com.material.xray.core.xraycore.XrayCoreManager
import com.material.xray.core.xraycore.XrayCoreOperation
import com.material.xray.core.xraycore.XrayCoreRelease
import com.material.xray.core.xraycore.XrayCoreState
import com.material.xray.core.xraycore.XrayCoreUpdateAction
import com.material.xray.core.xraycore.XrayCoreUpdateInterval
import com.material.xray.core.xraycore.XrayCoreUpdateSettings
import com.material.xray.core.xraycore.XrayCoreUpdateSettingsStore
import com.material.xray.core.xraycore.newestReleaseFirst
import com.material.xray.core.xraycore.normalizeXrayVersion
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

sealed interface XrayCoreReleasesState {
    data object Loading : XrayCoreReleasesState

    data class Loaded(
        val releases: List<XrayCoreRelease>,
        val hasMore: Boolean,
        val loadingMore: Boolean = false,
        val failure: XrayCoreFailure? = null,
        val refreshing: Boolean = false,
    ) : XrayCoreReleasesState

    data class Failed(val failure: XrayCoreFailure) : XrayCoreReleasesState
}

@KoinViewModel
class XrayCoreViewModel(
    private val context: Application,
    private val manager: XrayCoreManager,
    private val switcher: XrayCoreSwitcher,
    private val updateSettingsStore: XrayCoreUpdateSettingsStore,
    private val updateScheduler: XrayCoreUpdateScheduler,
) : ViewModel() {
    private val cachedPages = manager.cachedReleasePages
    private val _releases = MutableStateFlow<XrayCoreReleasesState>(
        if (cachedPages.isEmpty()) {
            XrayCoreReleasesState.Loading
        } else {
            XrayCoreReleasesState.Loaded(
                releases = cachedPages.flatMap { it.releases }.distinctBy { it.tag }.sortedWith(newestReleaseFirst),
                hasMore = cachedPages.last().hasMore,
            )
        },
    )
    private val autoSelecting = MutableStateFlow(false)
    private var releasesRequested = cachedPages.isNotEmpty()
    private var loadedPages = cachedPages.size
    private var releasesJob: Job? = null
    private var installingFile = false
    private var installingReleaseTag: String? = null
    private val _installFeedback = MutableStateFlow<Map<String, ActionFeedback>>(emptyMap())
    private val _releasesFeedback = MutableStateFlow<ActionFeedback?>(null)
    private val _moreReleasesFeedback = MutableStateFlow<ActionFeedback?>(null)
    private val _fileInstallFeedback = MutableStateFlow<ActionFeedback?>(null)

    val state: StateFlow<XrayCoreState> = manager.state
    val releases: StateFlow<XrayCoreReleasesState> = _releases.asStateFlow()
    val releasesFeedback = _releasesFeedback.asStateFlow()
    val moreReleasesFeedback = _moreReleasesFeedback.asStateFlow()
    val fileInstallFeedback = _fileInstallFeedback.asStateFlow()
    val installFeedback = _installFeedback.asStateFlow()
    val canDownload: Boolean = manager.canDownload
    val updateSettings: StateFlow<XrayCoreUpdateSettings> = updateSettingsStore.settings

    /**
     * How the last install ended, for the page to show once. An install started on the page selects
     * its core instead, unless the user left the page meanwhile.
     */
    val result: StateFlow<XrayCoreOperation?> = combine(manager.state, autoSelecting) { core, selectsOnInstall ->
        when (val operation = core.operation) {
            is XrayCoreOperation.Installed -> operation.takeUnless { selectsOnInstall }
            is XrayCoreOperation.Failed -> operation
            else -> null
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            manager.state.collect { core ->
                val releaseTag = installingReleaseTag
                if (releaseTag != null && (core.operation is XrayCoreOperation.Installed || core.operation is XrayCoreOperation.Failed)) {
                    _installFeedback.update {
                        it + (releaseTag to ActionFeedback(successful = core.operation is XrayCoreOperation.Installed))
                    }
                    installingReleaseTag = null
                }
                if (installingFile && (core.operation is XrayCoreOperation.Installed || core.operation is XrayCoreOperation.Failed)) {
                    _fileInstallFeedback.value = ActionFeedback(successful = core.operation is XrayCoreOperation.Installed)
                    installingFile = false
                }
                when (val operation = core.operation) {
                    is XrayCoreOperation.Installed -> if (autoSelecting.value) {
                        // Cleared before auto-selection ends, so [result] never offers this core.
                        manager.clearResult()
                        autoSelecting.value = false
                        select(operation.core.id)
                    }
                    is XrayCoreOperation.Failed -> autoSelecting.value = false
                    else -> Unit
                }
            }
        }
    }

    /** Reuses cached pages until the user refreshes, since GitHub limits anonymous requests. */
    fun loadReleasesIfNeeded() {
        if (canDownload && !releasesRequested) loadReleases()
    }

    fun loadReleases() {
        releasesRequested = true
        val previousJob = releasesJob
        previousJob?.cancel()
        val previous = (_releases.value as? XrayCoreReleasesState.Loaded)?.copy(loadingMore = false, refreshing = false)
        _releases.value = previous?.copy(refreshing = true, failure = null) ?: XrayCoreReleasesState.Loading
        releasesJob = viewModelScope.launch {
            // Let an in-flight page finish cancellation before replacing its cached list.
            previousJob?.join()
            _releases.value = try {
                val page = manager.releases(page = 1)
                loadedPages = 1
                _releasesFeedback.value = ActionFeedback(successful = true)
                XrayCoreReleasesState.Loaded(page.releases, page.hasMore)
            } catch (error: XrayCoreException) {
                currentCoroutineContext().ensureActive()
                _releasesFeedback.value = ActionFeedback(successful = false)
                previous?.copy(failure = error.failure) ?: XrayCoreReleasesState.Failed(error.failure)
            }
        }
    }

    /** Fetches the next page of releases, only when the user asks for older ones. */
    fun loadMoreReleases() {
        val loaded = _releases.value as? XrayCoreReleasesState.Loaded ?: return
        if (!loaded.hasMore || loaded.loadingMore || loaded.refreshing) return
        _releases.value = loaded.copy(loadingMore = true, failure = null)
        releasesJob = viewModelScope.launch {
            _releases.value = try {
                val page = manager.releases(page = loadedPages + 1)
                loadedPages++
                _moreReleasesFeedback.value = ActionFeedback(successful = true)
                loaded.copy(
                    releases = (loaded.releases + page.releases).distinctBy { it.tag }.sortedWith(newestReleaseFirst),
                    hasMore = page.hasMore,
                )
            } catch (error: XrayCoreException) {
                currentCoroutineContext().ensureActive()
                _moreReleasesFeedback.value = ActionFeedback(successful = false)
                loaded.copy(failure = error.failure)
            }
        }
    }

    fun install(release: XrayCoreRelease) {
        if (state.value.isBusy) return
        installingReleaseTag = normalizeXrayVersion(release.tag)
        if (manager.install(release) != null) autoSelecting.value = true else installingReleaseTag = null
    }

    fun installFromFile(uri: Uri) {
        if (state.value.isBusy) return
        installingFile = true
        val job = manager.installFromFile {
            context.contentResolver.openInputStream(uri) ?: throw IOException("Could not open $uri")
        }
        if (job != null) autoSelecting.value = true else installingFile = false
    }

    /** The user left the page, so a core still installing is only offered once they are back. */
    fun onScreenLeft() {
        autoSelecting.value = false
    }

    fun cancel() {
        installingFile = false
        installingReleaseTag = null
        autoSelecting.value = false
        manager.cancel()
    }

    fun clearResult() = manager.clearResult()

    /** Selects installed core [id], or the bundled core when null, and restarts a running connection on it. */
    fun select(id: String?) = viewModelScope.launch { switcher.switchTo(id) }

    fun setPeriodicChecks(enabled: Boolean) {
        val updated = updateSettingsStore.update { it.copy(periodicChecks = enabled) }
        updateScheduler.setEnabled(updated.periodicChecks, updated.interval)
    }

    fun setUpdateInterval(interval: XrayCoreUpdateInterval) {
        val updated = updateSettingsStore.update { it.copy(intervalHours = interval.hours) }
        updateScheduler.setEnabled(updated.periodicChecks, updated.interval)
    }

    fun setUpdateAction(action: XrayCoreUpdateAction) {
        updateSettingsStore.update { it.copy(action = action) }
    }

    fun delete(id: String) = viewModelScope.launch { manager.delete(id) }
}
