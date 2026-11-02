package mod.dropper.com

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import mod.dropper.com.core.storage.PermissionHelper
import mod.dropper.com.domain.model.ModLoader
import mod.dropper.com.presentation.DropperScreen
import mod.dropper.com.presentation.DropperViewModel
import mod.dropper.com.ui.theme.DropperTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appContainer = (application as DropperApp).container

        setContent {
            val dropperViewModel: DropperViewModel = viewModel(
                factory = DropperViewModel.provideFactory(
                    instanceRepository = appContainer.instanceRepository,
                    modRepository = appContainer.modRepository,
                    settingsManager = appContainer.settingsManager,
                    workManager = appContainer.workManager,
                    json = appContainer.json
                )
            )

            val uiState by dropperViewModel.uiState.collectAsStateWithLifecycle()
            val lifecycleOwner = LocalLifecycleOwner.current

            // Automatically re-check MANAGE_EXTERNAL_STORAGE when returning from system Settings
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        val currentGranted = PermissionHelper.hasAllFilesAccess()
                        if (currentGranted != uiState.hasAllFilesAccess) {
                            dropperViewModel.refreshPermissionsAndInstances()
                        }
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            DropperTheme(
                themeMode = uiState.settings.themeMode,
                dynamicColor = uiState.settings.useDynamicColor
            ) {
                var pendingSafVersion by remember { mutableStateOf("1.20.1") }
                var pendingSafLoader by remember { mutableStateOf(ModLoader.FABRIC) }

                val allFilesAccessLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) {
                    dropperViewModel.refreshPermissionsAndInstances()
                }

                val safTreeLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocumentTree()
                ) { treeUri ->
                    if (treeUri != null) {
                        dropperViewModel.onSafTreeSelected(
                            uri = treeUri,
                            customName = null,
                            gameVersion = pendingSafVersion,
                            modLoader = pendingSafLoader
                        )
                    }
                }

                DropperScreen(
                    viewModel = dropperViewModel,
                    onRequestStoragePermission = {
                        runCatching {
                            allFilesAccessLauncher.launch(
                                PermissionHelper.createAllFilesAccessIntent(this@MainActivity)
                            )
                        }.onFailure {
                            allFilesAccessLauncher.launch(
                                PermissionHelper.createFallbackAllFilesAccessIntent()
                            )
                        }
                    },
                    onLaunchSafPicker = { gameVersion, modLoader ->
                        pendingSafVersion = gameVersion
                        pendingSafLoader = modLoader
                        safTreeLauncher.launch(null)
                    }
                )
            }
        }
    }
}
