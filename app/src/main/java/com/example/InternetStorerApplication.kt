package com.example

import android.app.Application
import com.example.core.work.OfflineOperationRecoveryWorker
import com.example.core.work.TransferChunkWorker
import com.example.data.AppContainer
import com.example.data.DefaultAppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class InternetStorerApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)

        // Schedule safe, periodic local storage maintenance & recovery
        OfflineOperationRecoveryWorker.schedule(this)
        TransferChunkWorker.schedule(this)

        // Asynchronously recover any stale operations and interrupted transfers from prior app runs
        CoroutineScope(Dispatchers.IO).launch {
            container.offlineOperationEngine.recoverStaleOperations()
            container.transferEngine.recoverInterruptedTransfers()
            container.storageManager.cleanStaleTempFiles()
        }
    }
}
