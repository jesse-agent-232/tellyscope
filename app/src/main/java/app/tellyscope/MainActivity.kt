// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.tellyscope.data.AdbDeviceSource
import app.tellyscope.data.AndroidPhoneStorage
import app.tellyscope.data.FakeDeviceSource
import app.tellyscope.ui.Copy
import app.tellyscope.ui.DeviceActions
import app.tellyscope.ui.DeviceScreen
import app.tellyscope.ui.PermissionScreen
import app.tellyscope.ui.ScanScreen
import app.tellyscope.ui.TellyscopeViewModel
import app.tellyscope.ui.theme.TellyscopeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Real TVs by default. Launch with `--es scenario fake|empty|noallow` to run on
        // made-up data: the full app, the empty-scan guide, or the Allow timeout.
        val scenario = when (intent.getStringExtra("scenario")) {
            "fake" -> FakeDeviceSource.Scenario.Normal
            "empty" -> FakeDeviceSource.Scenario.Empty
            "noallow" -> FakeDeviceSource.Scenario.NoAllow
            else -> null
        }
        setContent {
            TellyscopeTheme {
                LocalNetworkGate(skip = scenario != null) {
                    val vm: TellyscopeViewModel = viewModel(
                        factory = viewModelFactory {
                            initializer {
                                TellyscopeViewModel(
                                    scenario?.let(::FakeDeviceSource) ?: AdbDeviceSource(applicationContext),
                                    AndroidPhoneStorage(applicationContext),
                                )
                            }
                        },
                    )
                    TellyscopeApp(vm)
                }
            }
        }
    }

    /**
     * Android 17 blocks LAN connections and mDNS for apps targeting 37 until the user grants
     * ACCESS_LOCAL_NETWORK, so nothing scans until then. The view model is created inside
     * [content], which keeps the first scan behind the grant.
     */
    @Composable
    private fun LocalNetworkGate(skip: Boolean, content: @Composable () -> Unit) {
        // Not in Manifest.permission below API 37; the string is the same on every SDK.
        val permission = "android.permission.ACCESS_LOCAL_NETWORK"
        fun granted() = skip || Build.VERSION.SDK_INT < 37 ||
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

        var ok by remember { mutableStateOf(granted()) }
        var denied by rememberSaveable { mutableStateOf(false) }
        var canAsk by rememberSaveable { mutableStateOf(true) }
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { yes ->
            ok = yes
            denied = !yes
            // No rationale after a refusal means Android won't show its dialog again: only Settings can grant it.
            canAsk = shouldShowRequestPermissionRationale(permission)
        }
        // Coming back from Settings.
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { ok = granted() }

        if (ok) {
            content()
        } else {
            PermissionScreen(
                denied = denied,
                canAsk = canAsk,
                onAllow = { launcher.launch(permission) },
                onSettings = {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
                },
            )
        }
    }
}

@Composable
fun TellyscopeApp(vm: TellyscopeViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val done by vm.done.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var adding by rememberSaveable { mutableStateOf(false) }

    // "*/*": file managers label .apks and .xapk bundles inconsistently, so a narrower type would grey them out.
    val pickApp = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.pickedApp(it) }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::push) }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream"), vm::saveTo)

    LaunchedEffect(done) {
        val entry = done ?: return@LaunchedEffect
        vm.doneShown()
        val result = snackbar.showSnackbar(
            message = Copy.done(entry.action, entry.label),
            actionLabel = entry.undo?.let(Copy::undo),
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) vm.undo(entry)
    }

    LaunchedEffect(notice) {
        val message = notice ?: return@LaunchedEffect
        vm.noticeShown()
        snackbar.showSnackbar(message, duration = SnackbarDuration.Long)
    }

    // A newly connected TV becomes the selection; leave the add flow then, not on tap.
    LaunchedEffect(state.selectedId) { adding = false }

    if (state.selected == null || adding) {
        BackHandler(enabled = adding) { adding = false }
        ScanScreen(
            scan = state.scan,
            now = state.now,
            onRescan = vm::rescan,
            onConnect = vm::connect,
            onCode = vm::codeChanged,
            onPair = vm::pair,
            onAddByIp = vm::addByIp,
            onBack = if (adding) ({ adding = false }) else null,
        )
    } else {
        DeviceScreen(
            state = state,
            snackbar = snackbar,
            actions = DeviceActions(
                select = vm::select,
                addDevice = { adding = true; vm.rescan() },
                showOffline = vm::showOffline,
                query = vm::query,
                propose = vm::propose,
                confirm = vm::confirm,
                cancel = vm::cancel,
                undo = vm::undo,
                reconnect = { state.selectedId?.let(vm::reconnect) },
                forget = vm::forget,
                rename = vm::rename,
                restoreAll = vm::restoreAll,
                pickApp = { pickApp.launch(arrayOf("*/*")) },
                allowDowngrade = vm::allowDowngrade,
                install = vm::install,
                closeSideload = vm::closeSideload,
                openInstalled = vm::openInstalled,
                folder = vm::folder,
                pickFile = { folder -> vm.folder(folder); pickFile.launch(arrayOf("*/*")) },
                saveFile = { f -> vm.saving(f); saveFile.launch(f.name) },
                cancelTransfer = vm::cancelTransfer,
            ),
        )
    }
}
