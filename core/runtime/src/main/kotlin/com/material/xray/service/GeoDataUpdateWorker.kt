package com.material.xray.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.material.xray.core.data.repository.SettingsRepository
import com.material.xray.core.runtime.GeoDataAsset
import com.material.xray.core.runtime.GeoDataManager
import kotlinx.coroutines.flow.first
import org.koin.android.annotation.KoinWorker

@KoinWorker
class GeoDataUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val geoDataManager: GeoDataManager,
    private val settingsRepository: SettingsRepository,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result = updateGeoDataAssets(GeoDataAsset.entries.toSet(), geoDataManager, settingsRepository)
}

@KoinWorker
class GeoIpUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val geoDataManager: GeoDataManager,
    private val settingsRepository: SettingsRepository,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result = updateGeoDataAssets(setOf(GeoDataAsset.GEOIP), geoDataManager, settingsRepository)
}

@KoinWorker
class GeoSiteUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val geoDataManager: GeoDataManager,
    private val settingsRepository: SettingsRepository,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result = updateGeoDataAssets(setOf(GeoDataAsset.GEOSITE), geoDataManager, settingsRepository)
}

private suspend fun updateGeoDataAssets(assets: Set<GeoDataAsset>, manager: GeoDataManager, settings: SettingsRepository): ListenableWorker.Result = runCatching {
    val enabledAssets = assets.filterTo(linkedSetOf()) { asset ->
        when (asset) {
            GeoDataAsset.GEOIP -> settings.geoipUpdateChecksEnabled.first()
            GeoDataAsset.GEOSITE -> settings.geositeUpdateChecksEnabled.first()
        }
    }
    if (enabledAssets.isNotEmpty()) manager.refreshForScheduledUpdate(enabledAssets)
}.fold(onSuccess = { ListenableWorker.Result.success() }, onFailure = { ListenableWorker.Result.retry() })
