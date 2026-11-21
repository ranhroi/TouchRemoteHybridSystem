package com.ranhroi.touchremote.ui.screens.onboarding

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ranhroi.touchremote.ui.navigation.NavRoute
import com.ranhroi.touchremote.utils.ConstantsUtils.FIRST_LAUNCH
import com.ranhroi.touchremote.utils.DataStoreManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val prefs: DataStoreManager,
) : ViewModel() {

    private val _startDestination: MutableState<String> = mutableStateOf(NavRoute.Loading.route)
    val startDestination: State<String> = _startDestination

    init {
        viewModelScope.launch {

            prefs.get(FIRST_LAUNCH, true).collect { completed ->
                delay(500)
                if (completed) {
                    _startDestination.value = NavRoute.Onboarding.route
                } else {
                    _startDestination.value = NavRoute.Main.route
                }
            }
        }
    }

    fun updateOnboarding(b: Boolean) {
        viewModelScope.launch { prefs.put(FIRST_LAUNCH, b) }
    }
}