package com.cse3310.projecthealthmonitoringsystem.data

import com.cse3310.projecthealthmonitoringsystem.data.repository.AuthRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthRepositoryTest : DbTest() {

    @Test
    fun loginLookupByUserIdOrEmail() = runBlocking<Unit> {
        assertEquals(A, auth.findAccount(A)?.userId)
        assertEquals(A, auth.findAccount("$A@example.com")?.userId)
        assertEquals(A, auth.findAccount("  ${A.uppercase()}@EXAMPLE.COM ")?.userId)
        assertNull(auth.findAccount("nobody00"))
    }

    @Test
    fun duplicateUserIdOrEmailIsRejected() = runBlocking<Unit> {
        assertTrue(auth.isUserIdTaken(A))
        assertTrue(auth.isEmailTaken("$A@example.com"))
        assertFalse(auth.register(user(A).copy(email = "new@example.com"), profile(A)))
        assertFalse(auth.register(user("newUser99").copy(email = "$A@example.com"), profile("newUser99")))
        // a failed registration leaves nothing behind
        assertNull(auth.getUser("newUser99"))
        assertNull(auth.getProfile("newUser99"))
    }

    @Test
    fun threeFailedLoginsLockForFiveMinutes() = runBlocking<Unit> {
        assertNull(auth.recordFailedLogin(A).lockedUntil)
        assertEquals(2, auth.recordFailedLogin(A).failedAttempts)
        assertFalse(auth.isLocked(A))

        val third = auth.recordFailedLogin(A)
        assertEquals(now + AuthRepository.LOCKOUT_MS, third.lockedUntil)
        assertTrue(auth.isLocked(A))

        now += AuthRepository.LOCKOUT_MS - 1
        assertTrue(auth.isLocked(A))
        now += 1
        assertFalse(auth.isLocked(A))
        // B is unaffected
        assertEquals(0, auth.getUser(B)!!.failedLoginAttempts)
    }

    @Test
    fun successfulLoginResetsCounter() = runBlocking<Unit> {
        auth.recordFailedLogin(A)
        auth.recordFailedLogin(A)
        auth.recordSuccessfulLogin(A)
        assertEquals(0, auth.getUser(A)!!.failedLoginAttempts)
        assertNull(auth.recordFailedLogin(A).lockedUntil)
    }

    @Test
    fun passwordResetStoresNewHashAndUnlocks() = runBlocking<Unit> {
        repeat(3) { auth.recordFailedLogin(A) }
        assertTrue(auth.updatePassword(A, "newHash", "newSalt"))
        val user = auth.getUser(A)!!
        assertEquals("newHash", user.passwordHash)
        assertEquals("newSalt", user.passwordSalt)
        assertNull(user.lockedUntil)
        assertFalse(auth.isLocked(A))
    }

    @Test
    fun profileReadAndUpdate() = runBlocking<Unit> {
        val profile = auth.getProfile(A)!!
        assertTrue(auth.updateProfile(A, profile.copy(age = 31, annualCheckupAt = NOON + DAY)))
        assertEquals(31, auth.observeProfile(A).first()!!.age)
        assertFalse(auth.updateProfile(B, profile.copy(age = 99)))
        assertNotNull(auth.getProfile(A))
        assertEquals(31, auth.getProfile(A)!!.age)
    }

    @Test
    fun userCanUpdateOnlyOwnAccount() = runBlocking<Unit> {
        val a = auth.getUser(A)!!
        assertFalse(auth.updateUser(B, a.copy(email = "stolen@example.com")))
        assertTrue(auth.updateUser(A, a.copy(email = "alpha.new@example.com")))
        assertEquals(A, auth.findAccount("alpha.new@example.com")?.userId)
        // taken email
        assertFalse(auth.updateUser(A, a.copy(email = "$B@example.com")))
    }

    @Test
    fun updateUserCannotChangePasswordOrClearLockout() = runBlocking<Unit> {
        repeat(3) { auth.recordFailedLogin(A) }
        val locked = auth.getUser(A)!!
        assertTrue(auth.updateUser(A, locked.copy(
            securityQuestion = "Favorite color?",
            passwordHash = "attacker", passwordSalt = "attacker",
            failedLoginAttempts = 0, lockedUntil = null
        )))
        val stored = auth.getUser(A)!!
        assertEquals("Favorite color?", stored.securityQuestion)
        assertEquals(locked.passwordHash, stored.passwordHash)
        assertEquals(locked.passwordSalt, stored.passwordSalt)
        assertEquals(locked.lockedUntil, stored.lockedUntil)
        assertTrue(auth.isLocked(A))
    }
}
