package com.example.app

import android.app.Application
import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.core.database.EncryptedDatabaseFactory
import com.example.core.database.OrganizerRepository
import com.example.core.files.ServiceAssetStorage
import com.example.core.files.WebsiteLogoFetcher
import com.example.core.security.AndroidKeystoreKeyWrapper
import com.example.core.security.DatabaseKeyManager
import com.example.core.security.FileKeyEnvelopeStore
import com.example.feature.backup.BackupEngine
import java.io.File

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val securityDir = File(appContext.filesDir, "security_state")

    val envelopeStore = FileKeyEnvelopeStore(securityDir)
    val keystoreWrapper = AndroidKeystoreKeyWrapper(fallbackDirectory = securityDir)
    val keyManager = DatabaseKeyManager(
        envelopeStore = envelopeStore,
        keystoreWrapper = keystoreWrapper
    )
    val databaseFactory = EncryptedDatabaseFactory(
        context = appContext,
        keyManager = keyManager
    )
    val assetStorage = ServiceAssetStorage(appContext)
    val websiteLogoFetcher = WebsiteLogoFetcher(
        context = appContext,
        assetStorage = assetStorage
    )
    val repository = OrganizerRepository(
        databaseFactory = databaseFactory,
        assetStorage = assetStorage,
        keyManager = keyManager,
        websiteLogoFetcher = websiteLogoFetcher
    )
    val backupEngine = BackupEngine(
        databaseFactory = databaseFactory,
        assetStorage = assetStorage,
        keyManager = keyManager
    )
}

class DigitalAccountOrganizerApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.keyManager.initializeOrAutoUnlock()

        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    container.keyManager.onAppForegrounded()
                }

                override fun onStop(owner: LifecycleOwner) {
                    container.keyManager.onAppBackgrounded()
                }
            }
        )
    }
}
