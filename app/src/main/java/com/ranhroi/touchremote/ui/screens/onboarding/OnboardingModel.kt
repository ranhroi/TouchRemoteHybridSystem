package com.ranhroi.touchremote.ui.screens.onboarding

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.ranhroi.touchremote.R

sealed class OnboardingModel(
    @DrawableRes val image: Int,
    @StringRes val titleRes: Int,      // Dùng resource ID
    @StringRes val descriptionRes: Int, // Dùng resource ID
) {

    data object FirstPage : OnboardingModel(
        image = R.drawable.ic_setup_step1,
        titleRes = R.string.onboarding_title_download,
        descriptionRes = R.string.onboarding_desc_download
    )

    data object SecondPage : OnboardingModel(
        image = R.drawable.ic_setup_step2,
        titleRes = R.string.onboarding_title_install,
        descriptionRes = R.string.onboarding_desc_install
    )

    data object ThirdPages : OnboardingModel(
        image = R.drawable.ic_setup_step3,
        titleRes = R.string.onboarding_title_search,
        descriptionRes = R.string.onboarding_desc_search
    )

    data object FivePages : OnboardingModel(
        image = R.drawable.ic_setup_step4,
        titleRes = R.string.onboarding_title_sync,
        descriptionRes = R.string.onboarding_desc_sync
    )
}