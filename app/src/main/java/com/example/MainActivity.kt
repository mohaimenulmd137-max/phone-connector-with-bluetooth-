package com.example

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.core.ApkShareHelper
import com.example.core.ConnectionStatus
import kotlinx.coroutines.launch
import com.example.data.AppLanguage
import com.example.ui.LinkViewModel
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.RemoteViewerScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.stringsFor
import com.example.ui.theme.DisplayFontFamily
import com.example.ui.theme.SutoLinkTheme

const val ROUTE_HOME = "home"
const val ROUTE_REMOTE = "remote"
const val ROUTE_SETTINGS = "settings"

private data class NavItem(
  val route: String,
  val label: String,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
  val testTag: String,
)

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      SutoLinkTheme(darkTheme = true) {
        SutoLinkApp()
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SutoLinkApp(viewModel: LinkViewModel = viewModel()) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val strings = remember(uiState.preferences.language) { stringsFor(uiState.preferences.language) }
  val navController = rememberNavController()
  val snackbarHostState = remember { SnackbarHostState() }
  val coroutineScope = rememberCoroutineScope()

  fun shareAppApk() {
    coroutineScope.launch {
      val uri = ApkShareHelper.exportSelfApkUri(context)
      if (uri != null) {
        ApkShareHelper.launchShareApkIntent(context, uri, strings.shareApkChooserTitle)
      } else {
        snackbarHostState.showSnackbar(strings.shareApkFailed)
      }
    }
  }

  val requiredPermissions = remember {
    buildList {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(Manifest.permission.BLUETOOTH_SCAN)
        add(Manifest.permission.BLUETOOTH_CONNECT)
        add(Manifest.permission.BLUETOOTH_ADVERTISE)
      } else {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
      }
    }
  }

  fun checkAllPermissionsGranted(): Boolean {
    return requiredPermissions.all { perm ->
      ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }
  }

  val permissionLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
      val granted = checkAllPermissionsGranted()
      viewModel.refreshSystemChecks(granted)
      if (granted && uiState.preferences.autoListenOnOpen) {
        viewModel.activateLinkServiceAndListen()
      }
    }

  val enableBtLauncher =
    rememberLauncherForActivityResult(contract = ActivityResultContracts.StartActivityForResult()) {
      viewModel.refreshSystemChecks(checkAllPermissionsGranted())
    }

  val discoverableLauncher =
    rememberLauncherForActivityResult(contract = ActivityResultContracts.StartActivityForResult()) {
      viewModel.refreshSystemChecks(checkAllPermissionsGranted())
    }

  val screenCaptureLauncher =
    rememberLauncherForActivityResult(contract = ActivityResultContracts.StartActivityForResult()) {
      result ->
      if (result.resultCode == Activity.RESULT_OK && result.data != null) {
        viewModel.onScreenCaptureResult(result.resultCode, result.data!!)
      }
    }

  fun launchScreenCapturePrompt() {
    val projectionManager =
      context.getSystemService(MediaProjectionManager::class.java) ?: return
    screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
  }

  // Refresh permission & Accessibility status whenever returning from System Settings
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        val granted = checkAllPermissionsGranted()
        viewModel.refreshSystemChecks(granted)
        if (granted && uiState.preferences.autoListenOnOpen && !uiState.isServiceActive) {
          viewModel.activateLinkServiceAndListen()
        }
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  LaunchedEffect(uiState.errorMessage) {
    val err = uiState.errorMessage
    if (!err.isNullOrBlank()) {
      snackbarHostState.showSnackbar(err)
      viewModel.clearError()
    }
  }

  if (uiState.remoteRequestedShare && !uiState.isScreenSharing) {
    AlertDialog(
      onDismissRequest = { viewModel.dismissRemoteShareRequest() },
      title = { Text(strings.screenShareLabel) },
      text = { Text(strings.masterHelperOn) },
      confirmButton = {
        Button(
          onClick = {
            viewModel.dismissRemoteShareRequest()
            launchScreenCapturePrompt()
          }
        ) {
          Text(strings.startShare)
        }
      },
      dismissButton = {
        TextButton(onClick = { viewModel.dismissRemoteShareRequest() }) {
          Text(strings.stopShare)
        }
      },
    )
  }

  val navItems =
    listOf(
      NavItem(
        route = ROUTE_HOME,
        label = strings.navHome,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        testTag = "nav_home",
      ),
      NavItem(
        route = ROUTE_REMOTE,
        label = strings.navRemote,
        selectedIcon = Icons.Filled.Cast,
        unselectedIcon = Icons.Outlined.Cast,
        testTag = "nav_remote",
      ),
      NavItem(
        route = ROUTE_SETTINGS,
        label = strings.navSettings,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        testTag = "nav_settings",
      ),
    )

  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentDestination = navBackStackEntry?.destination

  fun navigateTo(route: String) {
    navController.navigate(route) {
      popUpTo(navController.graph.findStartDestination().id) { saveState = true }
      launchSingleTop = true
      restoreState = true
    }
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useNavigationRail = maxWidth >= 680.dp

    Scaffold(
      contentWindowInsets = WindowInsets.safeDrawing,
      snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
      topBar = {
        TopAppBar(
          title = {
            Text(
              text = strings.appName,
              fontFamily = DisplayFontFamily,
              style = MaterialTheme.typography.titleLarge,
            )
          },
          actions = {
            IconButton(
              onClick = { viewModel.toggleLanguage() },
              modifier = Modifier.minimumInteractiveComponentSize().testTag("top_language_toggle"),
            ) {
              Icon(
                imageVector = Icons.Default.Language,
                contentDescription = strings.languageLabel,
              )
            }
            TextButton(
              onClick = { viewModel.toggleLanguage() },
              modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
              Text(
                if (uiState.preferences.language == AppLanguage.BENGALI) "বাংলা" else "EN",
                color = MaterialTheme.colorScheme.primary,
              )
            }
          },
          colors =
            TopAppBarDefaults.topAppBarColors(
              containerColor = MaterialTheme.colorScheme.background,
              titleContentColor = MaterialTheme.colorScheme.onBackground,
            ),
        )
      },
      bottomBar = {
        if (!useNavigationRail) {
          NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
            navItems.forEach { item ->
              val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
              NavigationBarItem(
                selected = selected,
                onClick = { navigateTo(item.route) },
                icon = {
                  val showBadge =
                    item.route == ROUTE_REMOTE &&
                      uiState.connectionStatus == ConnectionStatus.CONNECTED
                  BadgedBox(
                    badge = {
                      if (showBadge) {
                        Badge(containerColor = MaterialTheme.colorScheme.tertiary)
                      }
                    }
                  ) {
                    Icon(
                      imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                      contentDescription = item.label,
                    )
                  }
                },
                label = { Text(item.label) },
                modifier = Modifier.testTag(item.testTag),
              )
            }
          }
        }
      },
    ) { innerPadding ->
      Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        if (useNavigationRail) {
          NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
            navItems.forEach { item ->
              val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
              NavigationRailItem(
                selected = selected,
                onClick = { navigateTo(item.route) },
                icon = {
                  Icon(
                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                    contentDescription = item.label,
                  )
                },
                label = { Text(item.label) },
                modifier = Modifier.testTag(item.testTag),
              )
            }
          }
        }

        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
          NavHost(navController = navController, startDestination = ROUTE_HOME) {
            composable(ROUTE_HOME) {
              DashboardScreen(
                uiState = uiState,
                strings = strings,
                onActivateService = {
                  if (!checkAllPermissionsGranted()) {
                    permissionLauncher.launch(requiredPermissions.toTypedArray())
                  } else {
                    viewModel.activateLinkServiceAndListen()
                  }
                },
                onDisconnectAll = { viewModel.disconnectAndAutoRemove() },
                onRequestPermissions = {
                  permissionLauncher.launch(requiredPermissions.toTypedArray())
                },
                onEnableBluetooth = {
                  enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                },
                onMakeDiscoverable = {
                  val discoverIntent =
                    Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                      putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
                    }
                  discoverableLauncher.launch(discoverIntent)
                },
                onOpenAccessibilitySettings = {
                  context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
                onToggleScreenShare = {
                  if (uiState.isScreenSharing) {
                    viewModel.stopScreenSharing()
                  } else {
                    launchScreenCapturePrompt()
                  }
                },
                onRequestRemoteShare = { viewModel.requestRemoteScreenShare() },
                onOpenRemoteViewer = { navigateTo(ROUTE_REMOTE) },
                onStartScan = {
                  if (!checkAllPermissionsGranted()) {
                    permissionLauncher.launch(requiredPermissions.toTypedArray())
                  } else {
                    viewModel.startScan()
                  }
                },
                onStopScan = { viewModel.stopScan() },
                onConnectDevice = { device -> viewModel.connectToDevice(device) },
                onShareApk = { shareAppApk() },
              )
            }

            composable(ROUTE_REMOTE) {
              RemoteViewerScreen(
                uiState = uiState,
                strings = strings,
                onSendTap = { x, y -> viewModel.sendTap(x, y) },
                onSendLongPress = { x, y -> viewModel.sendLongPress(x, y) },
                onSendSwipe = { sx, sy, ex, ey, dur -> viewModel.sendSwipe(sx, sy, ex, ey, dur) },
                onSendGlobalAction = { action -> viewModel.sendGlobalAction(action) },
                onRequestRemoteShare = { viewModel.requestRemoteScreenShare() },
                onDisconnectAll = {
                  viewModel.disconnectAndAutoRemove()
                  navigateTo(ROUTE_HOME)
                },
                onGoHome = { navigateTo(ROUTE_HOME) },
              )
            }

            composable(ROUTE_SETTINGS) {
              SettingsScreen(
                uiState = uiState,
                strings = strings,
                onToggleAutoListen = { viewModel.setAutoListenOnOpen(it) },
                onToggleAutoRemove = { viewModel.setAutoRemoveOnDisconnect(it) },
                onToggleAutoShare = { viewModel.setAutoShareOnConnect(it) },
                onSelectQuality = { viewModel.setStreamQuality(it) },
                onToggleLanguage = { viewModel.toggleLanguage() },
                onShareApk = { shareAppApk() },
              )
            }
          }
        }
      }
    }
  }
}
