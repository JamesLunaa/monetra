package io.github.jameslunaa.monetra.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.jameslunaa.monetra.MonetraApp
import io.github.jameslunaa.monetra.di.AppContainer

/** Creates a ViewModel with access to the app's dependencies. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val container = (LocalContext.current.applicationContext as MonetraApp).container
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}
