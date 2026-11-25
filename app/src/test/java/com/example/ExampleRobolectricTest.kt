package com.example

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.admit.AdmitAccessDeniedScreen
import com.example.data.model.UiState
import com.example.data.model.Video
import com.example.localization.AppLanguage
import com.example.localization.LocalLanguage
import com.example.localization.LocalStrings
import com.example.localization.stringsFor
import com.example.ui.components.AuthScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.AdmitTheme
import com.example.ui.theme.YouTvTheme
import com.example.viewmodel.FeedSortMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `read string resources from context match YouTV and ADMIT`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("YouTV", context.getString(R.string.app_name))
        assertEquals("ADMIT", context.getString(R.string.admit_app_name))
    }

    @Test
    fun `auth screen switches languages between English Bengali and Hindi`() {
        composeTestRule.setContent {
            var lang by remember { mutableStateOf(AppLanguage.EN) }
            val strings = remember(lang) { stringsFor(lang) }
            YouTvTheme {
                CompositionLocalProvider(
                    LocalLanguage provides lang,
                    LocalStrings provides strings
                ) {
                    AuthScreen(
                        isAdmitMode = false,
                        onLanguageSelected = { lang = it },
                        onAuthSuccess = {}
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Watch • Create • Share").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("google_sign_in_button").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("lang_chip_bn").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("দেখুন • তৈরি করুন • শেয়ার করুন").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("lang_chip_hi").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("देखें • बनाएं • साझा करें").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `home screen renders videos and toggles search input`() {
        val sampleVideo = Video(
            id = "vid_test_001",
            ownerId = "owner_1",
            channelId = "chan_1",
            channelName = "Tech Creator",
            channelHandle = "@techcreator",
            title = "Jetpack Compose Adaptive Video",
            category = "Technology",
            viewCount = 1200L
        )
        composeTestRule.setContent {
            YouTvTheme {
                CompositionLocalProvider(
                    LocalLanguage provides AppLanguage.EN,
                    LocalStrings provides stringsFor(AppLanguage.EN)
                ) {
                    HomeScreen(
                        feedState = UiState.Success(listOf(sampleVideo)),
                        selectedCategory = "Home",
                        searchQuery = "",
                        sortMode = FeedSortMode.PERSONALIZED,
                        searchHistory = emptyList(),
                        unreadNotificationsCount = 2,
                        columnsCount = 1,
                        onSelectCategory = {},
                        onSearchQueryChange = {},
                        onCommitSearch = {},
                        onClearSearchHistory = {},
                        onToggleSortMode = {},
                        onOpenNotifications = {},
                        onOpenVideo = {},
                        onOpenUpload = {}
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Jetpack Compose Adaptive Video").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_search_toggle_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_search_input").assertIsDisplayed()
    }

    @Test
    fun `admit access denied screen renders owner setup notice and recheck action`() {
        var rechecked = false
        composeTestRule.setContent {
            AdmitTheme {
                CompositionLocalProvider(
                    LocalLanguage provides AppLanguage.EN,
                    LocalStrings provides stringsFor(AppLanguage.EN)
                ) {
                    AdmitAccessDeniedScreen(
                        email = "nonowner@example.com",
                        uid = "uid_nonowner",
                        reason = "Account is not verified owner.",
                        setupRequired = true,
                        currentLanguage = AppLanguage.EN,
                        onLanguageSelected = {},
                        onRecheck = { rechecked = true },
                        onSignOut = {}
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Owner Authorization Required").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("admit_recheck_owner_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(true, rechecked)
    }
}
