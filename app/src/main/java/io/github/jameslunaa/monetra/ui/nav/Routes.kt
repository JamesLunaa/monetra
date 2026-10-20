package io.github.jameslunaa.monetra.ui.nav

import kotlinx.serialization.Serializable

@Serializable data object RecordsRoute
@Serializable data object AnalysisRoute
@Serializable data object BudgetsRoute
@Serializable data object AccountsRoute
@Serializable data object CategoriesRoute

/** Add ([id] 0), edit ([id]) or copy ([copyOf]) a record. */
@Serializable data class EditorRoute(val id: Long = 0, val copyOf: Long = 0)
@Serializable data object ImportRoute
@Serializable data object SettingsRoute
@Serializable data object SearchRoute
@Serializable data object DataRoute
@Serializable data object DebtsRoute
