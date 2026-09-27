package com.rmltd.workhourstracker.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
            com.rmltd.workhourstracker.data.CloudSyncPreferences.Provider.GOOGLE_DRIVE,
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
}
