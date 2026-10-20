package io.github.jameslunaa.monetra.ui.nav

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.ImportExport
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.jameslunaa.monetra.ui.accounts.AccountsScreen
import io.github.jameslunaa.monetra.ui.analysis.AnalysisScreen
import io.github.jameslunaa.monetra.ui.budgets.BudgetsScreen
import io.github.jameslunaa.monetra.ui.data.DataScreen
import io.github.jameslunaa.monetra.ui.debts.DebtsScreen
import io.github.jameslunaa.monetra.ui.search.SearchScreen
import io.github.jameslunaa.monetra.ui.categories.CategoriesScreen
import io.github.jameslunaa.monetra.ui.components.FieldHeader
import io.github.jameslunaa.monetra.ui.components.LogoMark
import io.github.jameslunaa.monetra.ui.theme.MonetraTheme
import io.github.jameslunaa.monetra.ui.editor.EditorScreen
import io.github.jameslunaa.monetra.ui.io.ImportScreen
import io.github.jameslunaa.monetra.ui.records.RecordsScreen
import io.github.jameslunaa.monetra.ui.settings.SettingsScreen
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

private data class Tab(val route: Any, val type: KClass<*>, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(RecordsRoute, RecordsRoute::class, "Records", Icons.AutoMirrored.Rounded.ReceiptLong),
    Tab(AnalysisRoute, AnalysisRoute::class, "Analysis", Icons.Rounded.DonutLarge),
    Tab(BudgetsRoute, BudgetsRoute::class, "Budgets", Icons.Rounded.Savings),
    Tab(AccountsRoute, AccountsRoute::class, "Accounts", Icons.Rounded.AccountBalanceWallet),
    Tab(CategoriesRoute, CategoriesRoute::class, "Categories", Icons.Rounded.Category),
)

@Composable
fun MonetraNavHost() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination
    val onTab = tabs.any { t -> current?.hasRoute(t.type) == true }
    // Accounts and Categories have their own add buttons; one "+" per screen.
    val showFab = onTab && current?.hasRoute(AccountsRoute::class) != true && current?.hasRoute(CategoriesRoute::class) != true &&
        // Reading screens: the button would sit on top of the figures being read.
        current?.hasRoute(AnalysisRoute::class) != true && current?.hasRoute(BudgetsRoute::class) != true
    val snackbar = remember { SnackbarHostState() }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val openDrawer: () -> Unit = { scope.launch { drawer.open() } }

    fun go(route: Any) { scope.launch { drawer.close() }; nav.navigate(route) }

    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = onTab,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow, windowInsets = WindowInsets(0)) {
                // Brand band: the logo mark and name on the ube field, like every screen's header.
                val field = MonetraTheme.colors
                Row(
                    Modifier.fillMaxWidth().background(field.field).statusBarsPadding().padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    LogoMark(field.logo, field.logoShadow, Modifier.size(44.dp), contentDescription = null)
                    Text("Monetra", style = MaterialTheme.typography.headlineSmall, color = field.onField)
                }
                Spacer(Modifier.height(12.dp))
                NavigationDrawerItem(
                    label = { Text("Debts") }, icon = { Icon(Icons.Rounded.Handshake, null) },
                    selected = false, onClick = { go(DebtsRoute) }, modifier = Modifier.padding(horizontal = 12.dp),
                )
                NavigationDrawerItem(
                    label = { Text("Import / export") }, icon = { Icon(Icons.Rounded.ImportExport, null) },
                    selected = false, onClick = { go(ImportRoute) }, modifier = Modifier.padding(horizontal = 12.dp),
                )
                NavigationDrawerItem(
                    label = { Text("Backup & reset") }, icon = { Icon(Icons.Rounded.Backup, null) },
                    selected = false, onClick = { go(DataRoute) }, modifier = Modifier.padding(horizontal = 12.dp),
                )
                NavigationDrawerItem(
                    label = { Text("Preferences") }, icon = { Icon(Icons.Rounded.Settings, null) },
                    selected = false, onClick = { go(SettingsRoute) }, modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        },
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            floatingActionButton = {
                if (showFab) {
                    FloatingActionButton(
                        onClick = { nav.navigate(EditorRoute()) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = MaterialTheme.shapes.large,
                    ) { Icon(Icons.Rounded.Add, contentDescription = "Add record") }
                }
            },
            bottomBar = {
                if (onTab) {
                    // Five labels only fit at roughly normal text size; above that only the selected tab
                    // keeps its label (Material's own fallback) and every icon carries its name.
                    val showLabels = LocalDensity.current.fontScale <= 1.15f
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                        tabs.forEach { t ->
                            NavigationBarItem(
                                selected = current?.hasRoute(t.type) == true,
                                onClick = {
                                    nav.navigate(t.route) {
                                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                // When labels are hidden (large font scale) the icon carries the name.
                                icon = { Icon(t.icon, contentDescription = if (showLabels) null else t.label) },
                                // Shrinks rather than truncates at large font scales.
                                label = {
                                    if (showLabels) {
                                        Text(t.label, maxLines = 1, style = MaterialTheme.typography.labelMedium)
                                    } else {
                                        // The lone selected label shrinks to fit inside its item, clear of the screen edge.
                                        val color = LocalContentColor.current
                                        val floor = (9f / LocalDensity.current.fontScale).sp
                                        BasicText(
                                            t.label, maxLines = 1, color = { color }, modifier = Modifier.padding(horizontal = 8.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                            autoSize = TextAutoSize.StepBased(minFontSize = floor, maxFontSize = MaterialTheme.typography.labelMedium.fontSize),
                                        )
                                    }
                                },
                                colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer),
                                // At large font scales only the selected tab keeps its label (Material's own fallback).
                                alwaysShowLabel = showLabels,
                            )
                        }
                    }
                }
            },
            // Screens draw their own field under the status bar.
            contentWindowInsets = WindowInsets(0),
        ) { padding ->
            NavHost(
                nav, startDestination = RecordsRoute,
                enterTransition = { fadeIn() }, exitTransition = { fadeOut() },
                modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
            ) {
                composable<RecordsRoute> {
                    RecordsScreen(
                        PaddingValues(), snackbar, openDrawer,
                        onEdit = { nav.navigate(EditorRoute(id = it)) },
                        onDuplicate = { nav.navigate(EditorRoute(copyOf = it)) },
                        onImport = { nav.navigate(ImportRoute) },
                        onSearch = { nav.navigate(SearchRoute) },
                        onOpenDebts = { nav.navigate(DebtsRoute) },
                    )
                }
                composable<AnalysisRoute> { AnalysisScreen(openDrawer) }
                composable<BudgetsRoute> { BudgetsScreen(openDrawer) }
                composable<AccountsRoute> { AccountsScreen(PaddingValues(), snackbar, openDrawer, onOpenDebts = { nav.navigate(DebtsRoute) }) }
                composable<CategoriesRoute> { CategoriesScreen(PaddingValues(), snackbar, openDrawer) }
                composable<EditorRoute> {
                    val r = it.toRoute<EditorRoute>()
                    EditorScreen(r.id, r.copyOf, onClose = { nav.popBackStack() })
                }
                composable<ImportRoute> { ImportScreen(onBack = { nav.popBackStack() }) }
                composable<SettingsRoute> { SettingsScreen(onBack = { nav.popBackStack() }) }
                composable<SearchRoute> { SearchScreen(onBack = { nav.popBackStack() }, onOpen = { nav.navigate(EditorRoute(id = it)) }) }
                composable<DataRoute> { DataScreen(onBack = { nav.popBackStack() }) }
                composable<DebtsRoute> { DebtsScreen(onBack = { nav.popBackStack() }) }
            }
        }
    }
}
