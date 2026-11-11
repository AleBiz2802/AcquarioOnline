package com.acquarioonline.ui.home

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.location.LocationManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Menu
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.acquarioonline.R
import com.acquarioonline.data.auth.UserProfile
import com.acquarioonline.data.local.Section
import com.acquarioonline.ui.components.OpenStreetMapMultipleMarkers
import com.acquarioonline.ui.section.SectionScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(factory = com.acquarioonline.ui.AppViewModelProvider.Factory)
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var sectionToDelete by remember { mutableStateOf<Section?>(null) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    val selected = state.selectedSection

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            fetchLocationAndOpenSheet(context, viewModel)
        } else {
            viewModel.onAddSectionClicked(null, null)
        }
    }

    // Dalla schermata di una sezione o della mappa, "indietro" riporta alla home.
    BackHandler(enabled = (selected != null || state.isMapSelected) && !drawerState.isOpen) { viewModel.onHomeSelected() }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    DrawerHeader(state.user)
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.drawer_home)) },
                        icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                        selected = selected == null && !state.isMapSelected,
                        onClick = {
                            viewModel.onHomeSelected()
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.drawer_map)) },
                        icon = { Icon(Icons.Filled.Map, contentDescription = null) },
                        selected = state.isMapSelected,
                        onClick = {
                            viewModel.onMapSelected()
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp, horizontal = 28.dp))
                    Text(
                        text = stringResource(R.string.drawer_sections),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp)
                    )
                    state.sections.forEach { section ->
                        NavigationDrawerItem(
                            label = { Text(section.name) },
                            icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                            selected = section.id == selected?.id,
                            onClick = {
                                viewModel.onSectionSelected(section.id)
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp, horizontal = 28.dp))
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.language)) },
                        icon = { Icon(Icons.Filled.Language, contentDescription = null) },
                        selected = false,
                        onClick = {
                            showLanguageDialog = true
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp, horizontal = 28.dp))
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.drawer_sign_out)) },
                        icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null) },
                        selected = false,
                        onClick = viewModel::signOut,
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )

                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            when {
                                state.isMapSelected -> stringResource(R.string.drawer_map)
                                selected != null -> selected.name
                                else -> stringResource(R.string.app_name)
                            }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.open_menu))
                        }
                    },
                    actions = {
                        if (selected != null) {
                            IconButton(onClick = { sectionToDelete = selected }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.delete_section)
                                )
                            }
                        }
                    }
                )
            },
            floatingActionButton = {
                if (selected == null && !state.isMapSelected) {
                    FloatingActionButton(onClick = {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_section))
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { padding ->
            when {
                state.isMapSelected -> {
                    OpenStreetMapMultipleMarkers(
                        modifier = Modifier
                            .padding(padding)
                            .fillMaxSize(),
                        sections = state.sections,
                        onSectionClick = { viewModel.onSectionSelected(it.id) }
                    )
                }
                selected == null -> {
                    SectionList(
                        sections = state.sections,
                        onSectionClick = { viewModel.onSectionSelected(it.id) },
                        modifier = Modifier.padding(padding)
                    )
                }
                else -> {
                    SectionScreen(
                        section = selected,
                        snackbarHostState = snackbarHostState,
                        modifier = Modifier.padding(padding),
                        onBack = viewModel::onHomeSelected
                    )
                }
            }
        }
    }

    if (state.form.visible) {
        AddSectionSheet(
            form = state.form,
            onNameChange = viewModel::onSectionNameChange,
            onBrokerChange = viewModel::onSectionBrokerChange,
            onDismiss = viewModel::dismissForm,
            onConfirm = viewModel::saveSection
        )
    }

    if (showLanguageDialog) {
        val tags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        val current = if (tags.isBlank()) "it" else tags
        LanguageSelectionDialog(
            currentLanguage = current,
            onLanguageSelected = { code ->
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(code))
                showLanguageDialog = false
                findActivity(context)?.recreate()
            },
            onDismiss = { showLanguageDialog = false }
        )
    }

    sectionToDelete?.let { section ->
        AlertDialog(
            onDismissRequest = { sectionToDelete = null },
            title = { Text(stringResource(R.string.delete_section_title)) },
            text = { Text(stringResource(R.string.delete_section_message, section.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSection(section)
                    sectionToDelete = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { sectionToDelete = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@SuppressLint("MissingPermission")
private fun fetchLocationAndOpenSheet(context: Context, viewModel: HomeViewModel) {
    try {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val gps = try { locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) } catch (e: Exception) { null }
        val net = try { locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) } catch (e: Exception) { null }
        val best = when {
            gps != null && net != null -> if (gps.time > net.time) gps else net
            gps != null -> gps
            net != null -> net
            else -> null
        }
        if (best != null && (System.currentTimeMillis() - best.time) < 120_000) {
            viewModel.onAddSectionClicked(best.latitude, best.longitude)
        } else {
            @Suppress("DEPRECATION")
            val listener = object : android.location.LocationListener {
                override fun onLocationChanged(location: android.location.Location) {
                    try {
                        locationManager.removeUpdates(this)
                    } catch (e: Exception) {}
                    viewModel.onAddSectionClicked(location.latitude, location.longitude)
                }
                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }

            var requested = false
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, listener, context.mainLooper)
                requested = true
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0L, 0f, listener, context.mainLooper)
                requested = true
            }

            if (!requested) {
                if (best != null) {
                    viewModel.onAddSectionClicked(best.latitude, best.longitude)
                } else {
                    viewModel.onAddSectionClicked(41.9028, 12.4964)
                }
            }
        }
    } catch (e: Exception) {
        viewModel.onAddSectionClicked(41.9028, 12.4964)
    }
}

private fun findActivity(context: Context): Activity? {
    var ctx = context
    var depth = 0
    while (ctx is ContextWrapper && depth < 10) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
        depth++
    }
    return null
}

@Composable
private fun LanguageSelectionDialog(
    currentLanguage: String,
    onLanguageSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedLang by remember { mutableStateOf(currentLanguage) }
    val languages = listOf(
        "it" to R.string.language_it,
        "en" to R.string.language_en,
        "fr" to R.string.language_fr,
        "de" to R.string.language_de
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                languages.forEach { (code, resId) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedLang = code }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        RadioButton(
                            selected = (selectedLang == code),
                            onClick = { selectedLang = code }
                        )
                        Text(text = stringResource(resId), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onLanguageSelected(selectedLang) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun DrawerHeader(user: UserProfile?) {
    Column(modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp)) {
        user?.displayName?.let {
            Text(text = it, style = MaterialTheme.typography.titleMedium)
        }
        user?.email?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionList(
    sections: List<Section>,
    onSectionClick: (Section) -> Unit,
    modifier: Modifier = Modifier
) {
    if (sections.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.home_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    } else {
        Box(
            modifier = modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(sections, key = { it.id }) { section ->
                    ElevatedCard(
                        onClick = { onSectionClick(section) },
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = if (isSystemInDarkTheme()) androidx.compose.ui.graphics.Color(0xFF003A48) else androidx.compose.ui.graphics.Color(0xFFE0F2FE)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = section.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = stringResource(R.string.section_broker_line, section.brokerAddress),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
