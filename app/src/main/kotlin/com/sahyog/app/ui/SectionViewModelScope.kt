package com.sahyog.app.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Gives a section of the app (auth, onboarding, customer, worker) its own ViewModel scope.
 *
 * Why: the sections aren't destinations in one NavHost, so without this their ViewModels
 * would be scoped to the Activity and outlive the section. E.g. after signing out and in
 * again, onboarding would reappear with the previous attempt's state.
 *
 * The scope is cleared when the section leaves the screen, but survives configuration
 * changes (rotation, theme, language) because the stores live in an Activity ViewModel.
 */
@Composable
internal fun SectionViewModelScope(key: String, content: @Composable () -> Unit) {
    val holder: SectionStoresHolder = viewModel()
    val activity = LocalActivity.current
    val owner = remember(key) {
        object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = holder.storeFor(key)
        }
    }
    DisposableEffect(key) {
        onDispose {
            if (activity?.isChangingConfigurations != true) holder.clear(key)
        }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
}

internal class SectionStoresHolder : ViewModel() {
    private val stores = mutableMapOf<String, ViewModelStore>()

    fun storeFor(key: String): ViewModelStore = stores.getOrPut(key) { ViewModelStore() }

    fun clear(key: String) {
        stores.remove(key)?.clear()
    }

    override fun onCleared() {
        stores.values.forEach(ViewModelStore::clear)
        stores.clear()
    }
}
