package com.material.xray

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import androidx.work.WorkManager
import com.material.xray.core.android.launcher.LauncherIconManager
import com.material.xray.core.android.locale.initializeAppLocales
import com.material.xray.core.android.telemetry.diagnosticsConsentMirror
import com.material.xray.core.android.telemetry.initializeSentryTelemetry
import com.material.xray.core.common.di.ApplicationScope
import com.material.xray.core.data.repository.BackupManager
import com.material.xray.core.data.repository.NetworkServerRepository
import com.material.xray.core.data.repository.ProviderGeoDataManager
import com.material.xray.core.data.repository.ServerRepository
import com.material.xray.core.data.repository.SettingsRepository
import com.material.xray.core.data.repository.SubscriptionRepository
import com.material.xray.core.data.repository.nextAutoRefreshAt
import com.material.xray.core.database.DatabaseOpenChecker
import com.material.xray.core.database.entity.effectiveAutoUpdateIntervalSeconds
import com.material.xray.core.model.Ipv6Mode
import com.material.xray.core.network.Ipv6Detector
import com.material.xray.core.runtime.AppUpdateScheduler
import com.material.xray.core.runtime.ConnectionRuntimeManager
import com.material.xray.core.runtime.GeoDataAsset
import com.material.xray.core.runtime.GeoDataUpdateScheduler
import com.material.xray.core.runtime.NetworkServerManager
import com.material.xray.core.runtime.OemAutostartManager
import com.material.xray.core.runtime.StartupDiagnosticsLogger
import com.material.xray.core.runtime.SubscriptionUpdateScheduler
import com.material.xray.core.telemetry.TelemetryReporter
import com.material.xray.core.xraycore.XrayCoreUpdateSettingsStore
import com.material.xray.feature.xraycore.XrayCoreUpdateScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.factory.KoinWorkerFactory
import org.koin.core.annotation.KoinApplication
import org.koin.core.qualifier.named
import org.koin.plugin.module.dsl.startKoin

@KoinApplication
class MaterialXrayApp : Application() {

    private val subscriptionUpdateScheduler: SubscriptionUpdateScheduler by inject()

    private val appUpdateScheduler: AppUpdateScheduler by inject()

    private val xrayCoreUpdateScheduler: XrayCoreUpdateScheduler by inject()

    private val xrayCoreUpdateSettingsStore: XrayCoreUpdateSettingsStore by inject()

    private val geoDataUpdateScheduler: GeoDataUpdateScheduler by inject()

    private val settingsRepository: SettingsRepository by inject()

    private val launcherIconManager: LauncherIconManager by inject()

    private val backupManager: BackupManager by inject()

    private val subscriptionRepository: SubscriptionRepository by inject()

    private val databaseOpenChecker: DatabaseOpenChecker by inject()

    private val startupDiagnosticsLogger: StartupDiagnosticsLogger by inject()

    private val oemAutostartManager: OemAutostartManager by inject()

    private val telemetryReporter: TelemetryReporter by inject()

    private val providerGeoDataManager: ProviderGeoDataManager by inject()

    private val serverRepository: ServerRepository by inject()

    private val ipv6Detector: Ipv6Detector by inject()
    private val networkServerManager: NetworkServerManager by inject()
    private val networkServerRepository: NetworkServerRepository by inject()
    private val connectionRuntimeManager: ConnectionRuntimeManager by inject()

    private val appScope: CoroutineScope by inject(named<ApplicationScope>())

    override fun onCreate() {
        val diagnosticsConsentMirror = diagnosticsConsentMirror(this)
        // This must precede starting Koin so opted-in users can report failures while the
        // application graph and eager startup state are being constructed.
        if (diagnosticsConsentMirror.isEnabled()) initializeSentryTelemetry(this)
        // Initialize locales before Koin constructs UI data when MainActivity starts, so its
        // first locale-dependent server summaries use the selected language on API <= 32.
        initializeAppLocales(this)
        super.onCreate()
        startKoin<MaterialXrayApp> {
            androidContext(this@MaterialXrayApp)
        }
        initializeWorkManager()
        appScope.launch(start = CoroutineStart.UNDISPATCHED) {
            settingsRepository.diagnosticsEnabled.distinctUntilChanged().collectLatest { enabled ->
                diagnosticsConsentMirror.setEnabled(enabled)
                telemetryReporter.setEnabled(enabled)
            }
        }
        appScope.launch(start = CoroutineStart.UNDISPATCHED) {
            combine(settingsRepository.geoipUpdateChecksEnabled, settingsRepository.geoipUpdateIntervalSeconds) { enabled, seconds -> if (enabled) seconds else 0 }
                .distinctUntilChanged().collectLatest { geoDataUpdateScheduler.schedulePeriodicRefresh(GeoDataAsset.GEOIP, it) }
        }
        appScope.launch(start = CoroutineStart.UNDISPATCHED) {
            combine(settingsRepository.geositeUpdateChecksEnabled, settingsRepository.geositeUpdateIntervalSeconds) { enabled, seconds -> if (enabled) seconds else 0 }
                .distinctUntilChanged().collectLatest { geoDataUpdateScheduler.schedulePeriodicRefresh(GeoDataAsset.GEOSITE, it) }
        }
        appScope.launch {
            runCatching {
                if (settingsRepository.geoipUpdateChecksEnabled.first() || settingsRepository.geositeUpdateChecksEnabled.first()) {
                    geoDataUpdateScheduler.enqueueInitialRefresh()
                }
            }.onFailure { error -> Log.e(LOG_TAG, "Unable to schedule initial geodata refresh", error) }
        }
        appScope.launch {
            databaseOpenChecker.awaitReadable()
            providerGeoDataManager.keepUpToDate()
        }
        appScope.launch {
            // Settle IPv6 for the selected server before the user connects, so Auto can start with it.
            if (settingsRepository.ipv6Mode.first() != Ipv6Mode.Auto || !databaseOpenChecker.canRead()) return@launch
            val server = serverRepository.getById(settingsRepository.lastServerId.first()) ?: return@launch
            runCatching { ipv6Detector.check(serverRepository.parseConfig(server)) }
                .onFailure { error -> Log.e(LOG_TAG, "Unable to check IPv6", error) }
        }
        appScope.launch {
            if (settingsRepository.autoConnect.first()) {
                delay(STARTUP_BACKGROUND_WORK_DELAY_SECONDS * 1_000)
                oemAutostartManager.restoreRootGrant()
            }
        }
        appScope.launch {
            databaseOpenChecker.awaitReadable()
            runCatching { backupManager.recoverInterruptedRestore() }
                .onFailure { error -> Log.e(LOG_TAG, "Unable to recover interrupted backup restore", error) }
            runCatching { subscriptionRepository.consolidateLocalServers() }
                .onFailure { error -> Log.e(LOG_TAG, "Unable to collect share-link servers into one group", error) }
            // Account for a root runtime that survived this process before changing its server.
            if (networkServerRepository.policy.first().enabled) connectionRuntimeManager.reconcileState(verifyRecordedRuntime = true)
            networkServerManager.start(appScope)
            runCatching { startupDiagnosticsLogger.logIfMissing() }
                .onFailure { error -> Log.e(LOG_TAG, "Unable to record startup diagnostics", error) }
            launcherIconManager.apply(settingsRepository.launcherIcon.first(), settingsRepository.launcherName.first())
            appUpdateScheduler.setEnabled(
                settingsRepository.appUpdateChecksEnabled.first(),
                settingsRepository.appUpdateIntervalSeconds.first(),
            )
            val coreUpdates = xrayCoreUpdateSettingsStore.settings.value
            xrayCoreUpdateScheduler.setEnabled(coreUpdates.periodicChecks, coreUpdates.effectiveIntervalSeconds)
            appScope.launch {
                subscriptionRepository.observeAll().collectLatest { subscriptions ->
                    val interval = subscriptions.filterNot { it.isLocal }.map { it.effectiveAutoUpdateIntervalSeconds }.filter { it > 0 }.minOrNull() ?: 0
                    val nextRefreshAt = subscriptions.mapNotNull { it.nextAutoRefreshAt() }.minOrNull()
                    subscriptionUpdateScheduler.schedulePeriodicUpdates(interval, nextRefreshAt)
                }
            }
            subscriptionUpdateScheduler.enqueueDueCheckNow(STARTUP_BACKGROUND_WORK_DELAY_SECONDS)
        }
    }

    /**
     * Koin's worker factory, as workManagerFactory() would install it, plus an initialization
     * handler: WorkManager throws from a background thread when its storage is unreadable, such as
     * in a profile that is still locked, and nothing could catch that.
     */
    private fun initializeWorkManager() {
        val configuration = Configuration.Builder()
            .setWorkerFactory(KoinWorkerFactory())
            .setInitializationExceptionHandler { error -> Log.e(LOG_TAG, "Unable to initialize WorkManager", error) }
            .build()
        WorkManager.initialize(this, configuration)
    }

    private companion object {
        const val LOG_TAG = "MaterialXrayApp"
        const val STARTUP_BACKGROUND_WORK_DELAY_SECONDS = 30L
    }
}
