package com.ranhroi.touchremote.ui.screens.onboarding

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.ranhroi.touchremote.R
import com.ranhroi.touchremote.ui.navigation.NavRoute
import com.ranhroi.touchremote.ui.screens.setting.SettingsViewModel
import com.ranhroi.touchremote.utils.ConstantsUtils
import com.ranhroi.touchremote.utils.playSoundClick
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    navController: NavHostController,
    onboarding: OnboardingViewModel,
    languageApp: SettingsViewModel = hiltViewModel()
) {

    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val currentLanguageName = languageApp.getCurrentDisplayLanguageName(context)

    var expanded by remember { mutableStateOf(false) }

    val pages = listOf(
        OnboardingModel.FirstPage,
        OnboardingModel.SecondPage,
        OnboardingModel.ThirdPages,
        OnboardingModel.FivePages
    )

    val pagerState = rememberPagerState(initialPage = 0) {
        pages.size
    }

    val currentPage = pagerState.currentPage

    val showBackButton = currentPage in 1..3

    val backButtonTextRes = when (currentPage) {
        1, 2, 3 -> R.string.onboarding_button_skip
        else -> null
    }

    val nextButtonTextRes = when (currentPage) {
        0 -> R.string.onboarding_button_next
        1 -> R.string.onboarding_button_next
        2 -> R.string.onboarding_button_next
        3 -> R.string.onboarding_button_get_started
        else -> R.string.string_empty
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                },
                actions = {
                    TextButton(
                        onClick = {
                            expanded = true
                            view.playSoundClick()
                        }
                    ) {
                        Text(currentLanguageName)
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.widthIn(min = 180.dp)
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text("🌐 ${stringResource(R.string.language_auto)}")
                            },
                            onClick = {
                                languageApp.changeLanguage("auto")
                                expanded = false
                            }
                        )

                        ConstantsUtils.languageOptionsList.forEach { item ->
                            DropdownMenuItem(
                                text = {
                                    Text("${item.flag} ${stringResource(id = item.labelRes)}")
                                },
                                onClick = {
                                    languageApp.changeLanguage(item.code)
                                    expanded = false
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                )
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.Transparent) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp, 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Nút Back (trái)
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (showBackButton && backButtonTextRes != null) {
                            ButtonUi(
                                text = stringResource(id = backButtonTextRes),
                                backgroundColor = Color.Transparent,
                                textColor = Color.Gray
                            ) {
                                scope.launch {
                                    if (pagerState.currentPage > 0) {
                                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                    }
                                }
                                view.playSoundClick()
                            }
                        }
                    }

                    // Indicator
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        IndicatorUI(pageSize = pages.size, currentPage = pagerState.currentPage)
                    }

                    // Nút Next (phải)
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        ButtonUi(
                            text = stringResource(id = nextButtonTextRes),
                            backgroundColor = MaterialTheme.colorScheme.primary,
                            textColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            scope.launch {
                                if (pagerState.currentPage < pages.size - 1) {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                } else {
                                    onboarding.updateOnboarding(false)
                                    navController.navigate(NavRoute.Main.route) {
                                        popUpTo(NavRoute.Onboarding.route) { inclusive = true }
                                    }
                                }
                            }
                            view.playSoundClick()
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            HorizontalPager(
                state = pagerState,
                key = { index -> index }
            ) { index ->
                OnboardingGraphUI(onboardingModel = pages[index])
            }
        }
    }
}


@Composable
fun ButtonUi(
    text: String = stringResource(id = R.string.onboarding_button_next),
    backgroundColor: Color = MaterialTheme.colorScheme.primary,
    textColor: Color = MaterialTheme.colorScheme.onPrimary,
    textStyle: TextStyle = MaterialTheme.typography.titleMedium,
    fontSize: Int = 14,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            contentColor = textColor
        ),
        shape = RoundedCornerShape(5.dp)
    ) {
        Text(
            text = text,
            fontSize = fontSize.sp,
            style = textStyle
        )
    }
}

@Composable
fun IndicatorUI(
    pageSize: Int,
    currentPage: Int,
    selectedColor: Color = MaterialTheme.colorScheme.secondary,
    unselectedColor: Color = MaterialTheme.colorScheme.secondaryContainer
) {
    Row(horizontalArrangement = Arrangement.SpaceBetween) {
        repeat(pageSize) {
            Spacer(modifier = Modifier.size(2.5.dp))

            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(width = if (it == currentPage) 32.dp else 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color = if (it == currentPage) selectedColor else unselectedColor)
            )
            Spacer(modifier = Modifier.size(2.5.dp))
        }
    }
}