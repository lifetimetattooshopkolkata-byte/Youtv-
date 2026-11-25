package com.example

import com.example.data.distribution.AppRelease
import com.example.data.distribution.DistributionManager
import com.example.data.model.PlatformSettings
import com.example.data.model.UserProfile
import com.example.localization.AppLanguage
import com.example.localization.stringsFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTvLogicUnitTest {

    @Test
    fun `all three languages provide complete category translations`() {
        val expectedKeys = setOf(
            "Home", "Trending", "Latest", "Shorts",
            "Music", "Education", "Entertainment", "Technology", "Gaming", "Sports"
        )
        AppLanguage.entries.forEach { lang ->
            val strings = stringsFor(lang)
            assertEquals(expectedKeys, strings.categories.keys)
            assertTrue(strings.signInWithGoogle.isNotBlank())
            assertTrue(strings.admitDashboard.isNotBlank())
        }
    }

    @Test
    fun `language code resolution defaults safely to English`() {
        assertEquals(AppLanguage.BN, AppLanguage.fromCode("bn"))
        assertEquals(AppLanguage.HI, AppLanguage.fromCode("HI"))
        assertEquals(AppLanguage.EN, AppLanguage.fromCode("unknown"))
        assertEquals(AppLanguage.EN, AppLanguage.fromCode(null))
    }

    @Test
    fun `user profile moderation restriction checks suspended and blocked states`() {
        assertFalse(UserProfile(status = "active").isRestrictedAccount)
        assertTrue(UserProfile(status = "suspended").isRestrictedAccount)
        assertTrue(UserProfile(status = "blocked").isRestrictedAccount)
    }

    @Test
    fun `platform settings defaults support EN BN HI and safe limits`() {
        val settings = PlatformSettings()
        assertTrue(settings.registrationsEnabled)
        assertTrue(settings.uploadsEnabled)
        assertFalse(settings.maintenanceMode)
        assertEquals(listOf("en", "bn", "hi"), settings.supportedLanguages)
        assertEquals(512L, settings.maxUploadSizeMb)
    }

    @Test
    fun `distribution manager validates safe HTTPS APK URLs and blocks unsafe schemes`() {
        assertTrue(DistributionManager.isSafeDownloadUrl("https://storage.googleapis.com/bucket/app.apk"))
        assertTrue(DistributionManager.isSafeDownloadUrl("https://gen-lang-client-0489321426.web.app/releases/app.apk"))
        assertTrue(DistributionManager.isSafeDownloadUrl("http://10.0.2.2:8080/apk"))
        assertFalse(DistributionManager.isSafeDownloadUrl("javascript:alert(1)"))
        assertFalse(DistributionManager.isSafeDownloadUrl("file:///sdcard/app.apk"))
        assertFalse(DistributionManager.isSafeDownloadUrl("http://evil.example.com/app.apk"))
        assertFalse(DistributionManager.isSafeDownloadUrl(null))

        assertEquals("Pending artifact", DistributionManager.formatFileSizeMb(0L))
        assertEquals("28.0 MB", DistributionManager.formatFileSizeMb(28L * 1024L * 1024L))
        assertEquals("SETUP_REQUIRED", AppRelease().status)
    }
}
