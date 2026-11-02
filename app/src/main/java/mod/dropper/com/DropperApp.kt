package mod.dropper.com

import android.app.Application
import android.content.Context
import androidx.work.WorkManager
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json
import mod.dropper.com.core.network.HttpClientFactory
import mod.dropper.com.core.storage.CompositeModStorageManager
import mod.dropper.com.core.storage.DirectFileModStorageManager
import mod.dropper.com.core.storage.ModStorageManager
import mod.dropper.com.core.storage.SafModStorageManager
import mod.dropper.com.core.storage.SettingsPreferencesManager
import mod.dropper.com.data.instances.InstanceDiscoveryService
import mod.dropper.com.data.modrinth.ModRepositoryImpl
import mod.dropper.com.data.modrinth.ModrinthApiService
import mod.dropper.com.domain.repository.InstanceRepository
import mod.dropper.com.domain.repository.ModRepository

class AppContainer(context: Context) {
    val json: Json = HttpClientFactory.defaultJson
    val httpClient: HttpClient = HttpClientFactory.create()

    val settingsManager = SettingsPreferencesManager(context.applicationContext)

    private val directStorageManager = DirectFileModStorageManager()
    private val safStorageManager = SafModStorageManager(context.applicationContext)

    val storageManager: ModStorageManager = CompositeModStorageManager(
        directManager = directStorageManager,
        safManager = safStorageManager
    )

    val instanceRepository: InstanceRepository = InstanceDiscoveryService(
        context = context.applicationContext,
        storageManager = storageManager,
        json = json
    )

    private val modrinthApiService = ModrinthApiService(httpClient)

    val modRepository: ModRepository = ModRepositoryImpl(
        context = context.applicationContext,
        apiService = modrinthApiService,
        storageManager = storageManager,
        json = json
    )

    val workManager: WorkManager = WorkManager.getInstance(context.applicationContext)
}

class DropperApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
