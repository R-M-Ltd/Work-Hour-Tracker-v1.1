package com.rmltd.workhourstracker.data.sync

import android.content.Context
import com.rmltd.workhourstracker.data.CloudSyncPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

class CloudOAuthPkceTest {

    @Test
    fun codeVerifier_lengthAndCharset() {
        val v = Pkce.generateCodeVerifier(64)
        assertEquals(64, v.length)
        assertTrue(v.all { it.isLetterOrDigit() || it in "-._~" })
    }

    @Test
    fun codeChallengeS256_isDeterministicBase64Url() {
        // RFC 7636 appendix B
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        val challenge = Pkce.codeChallengeS256(verifier)
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", challenge)
        assertFalse(challenge.contains('+'))
        assertFalse(challenge.contains('/'))
        assertFalse(challenge.contains('='))
    }

    @Test
    fun authorizeUrl_usesAuthorizationCodeNotImplicit() {
        // Without BuildConfig client IDs URL is null; when challenge supplied the
        // builder still requires a non-blank client id. Assert response_type via
        // a known template fragment that authorizeUrl embeds.
        val drive = CloudOAuthLauncher.authorizeUrl(
            CloudSyncPreferences.Provider.GOOGLE_DRIVE,
            challenge = "abc"
        )
        // Unconfigured CI: null. Configured: must be code flow.
        if (drive != null) {
            assertTrue(drive.contains("response_type=code"))
            assertFalse(drive.contains("response_type=token"))
            assertTrue(drive.contains("code_challenge=abc"))
            assertTrue(drive.contains("code_challenge_method=S256"))
            assertTrue(drive.contains("access_type=offline"))
        }
        // Always: redirect + PKCE helpers exist for auth-code ship
        assertEquals("com.rmltd.workhourstracker://oauth", CloudOAuthLauncher.REDIRECT_URI)
    }

    @Test
    fun authorizeUrl_withChallenge_alwaysIncludesPkceAndCodeForAllProviders() {
        // Production Context path always supplies a non-null S256 challenge.
        val challenge = Pkce.codeChallengeS256(Pkce.generateCodeVerifier())
        val providers = listOf(
            CloudSyncPreferences.Provider.GOOGLE_DRIVE,
            CloudSyncPreferences.Provider.DROPBOX,
            CloudSyncPreferences.Provider.ONEDRIVE
        )
        for (provider in providers) {
            val url = CloudOAuthLauncher.authorizeUrl(provider, challenge)
            if (url == null) continue // provider client id blank in this environment
            assertTrue("$provider missing response_type=code: $url", url.contains("response_type=code"))
            assertFalse("$provider has response_type=token: $url", url.contains("response_type=token"))
            assertTrue("$provider missing code_challenge: $url", url.contains("code_challenge="))
            assertTrue(
                "$provider missing code_challenge_method=S256: $url",
                url.contains("code_challenge_method=S256")
            )
        }
    }

    @Test
    fun authorizeIntent_noPkceOverload_isGone_onlyContextPathRemains() {
        // L1: deprecated authorizeIntent(provider) deleted; only Context+PKCE remains.
        val methods = CloudOAuthLauncher::class.java.declaredMethods
            .filter { it.name == "authorizeIntent" && Modifier.isPublic(it.modifiers) }
        assertEquals(1, methods.size)
        val params = methods.single().parameterTypes.toList()
        assertEquals(listOf(Context::class.java, CloudSyncPreferences.Provider::class.java), params)
        assertFalse(
            methods.any {
                it.parameterTypes.size == 1 &&
                    it.parameterTypes[0] == CloudSyncPreferences.Provider::class.java
            }
        )
    }
}
