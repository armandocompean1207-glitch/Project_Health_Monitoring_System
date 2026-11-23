package com.cse3310.projecthealthmonitoringsystem.data

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import com.cse3310.projecthealthmonitoringsystem.auth.AccountSession
import com.cse3310.projecthealthmonitoringsystem.auth.Authentication
import com.cse3310.projecthealthmonitoringsystem.monitoring.CurrentUser
import com.cse3310.projecthealthmonitoringsystem.security.PasswordHasher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AuthenticationSecurityTest : DbTest() {
    @Test fun realCredentialsUseExistingLockoutAndPrivateProfile() = runBlocking<Unit> {
        val service = Authentication(auth)
        val id = "secureuser01"
        val password = "Test-Password-2026"
        assertTrue(service.register(id, "secure@example.com", "Secure User", password.toCharArray(), "Test question", "Test answer".toCharArray()))
        val stored = auth.getUser(id)!!
        assertNotEquals(password, stored.passwordHash)
        assertTrue(PasswordHasher.verify(password.toCharArray(), stored.passwordHash, stored.passwordSalt))
        assertFalse(PasswordHasher.verify("wrong".toCharArray(), stored.passwordHash, stored.passwordSalt))
        repeat(3) {
            val wrong = "wrong".toCharArray()
            try { service.login(id, wrong); fail("Wrong password accepted") } catch (_: IllegalArgumentException) {}
            assertTrue(wrong.all { it == '\u0000' })
        }
        assertTrue(auth.isLocked(id))
        try { service.login(id, password.toCharArray()); fail("Locked login accepted") } catch (_: IllegalStateException) {}
        now += com.cse3310.projecthealthmonitoringsystem.data.repository.AuthRepository.LOCKOUT_MS
        assertEquals(id, service.login("SECURE@EXAMPLE.COM", password.toCharArray()))
        val profile = auth.getProfile(id)!!
        assertFalse(auth.updateProfile(B, profile.copy(name = "Other account")))
        assertEquals("Secure User", auth.getProfile(id)!!.name)
        assertFalse(service.register(id, "different@example.com", "Duplicate", password.toCharArray(), "Test question", "answer".toCharArray()))
    }

    @Test fun saltedHashRejectsMalformedAndDebugCredentials() {
        val first = PasswordHasher.hash("test-password-123".toCharArray())
        val second = PasswordHasher.hash("test-password-123".toCharArray())
        assertNotEquals(first.salt, second.salt)
        assertFalse(PasswordHasher.verify("test".toCharArray(), SeedData.UNUSABLE_HASH, SeedData.UNUSABLE_HASH))
        assertFalse(PasswordHasher.verify("test".toCharArray(), "PBKDF2WithHmacSHA256:999999999:bad", first.salt))
    }

    @Test fun reminderOwnershipLogoutAndReleaseDemoRestriction() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("reminder_account", Context.MODE_PRIVATE)
        val flags = context.applicationInfo.flags
        try {
            preferences.edit().putString("owner", A).commit()
            var called = false
            assertFalse(AccountSession.whileActive(context, B) { called = true })
            assertFalse(called)
            assertTrue(AccountSession.whileActive(context, A) { called = true })
            assertTrue(called)
            // No preference token restores an authenticated UI session.
            CurrentUser.clear()
            assertEquals("", CurrentUser.userId)
            preferences.edit().remove("owner").commit()
            CurrentUser.signIn(A)
            AccountSession.logout(context)
            assertEquals("", CurrentUser.userId)
            assertNull(AccountSession.reminderUser(context))
            context.applicationInfo.flags = flags and ApplicationInfo.FLAG_DEBUGGABLE.inv()
            preferences.edit().putString("owner", SeedData.DEBUG_USER_ID).commit()
            assertNull(AccountSession.reminderUser(context))
            try { AccountSession.start(context, SeedData.DEBUG_USER_ID, demo = true); fail("Release demo accepted") }
            catch (_: IllegalArgumentException) {}
        } finally { preferences.edit().clear().commit(); CurrentUser.clear(); context.applicationInfo.flags = flags }
    }
}
