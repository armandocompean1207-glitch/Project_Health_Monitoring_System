package com.cse3310.projecthealthmonitoringsystem

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.cse3310.projecthealthmonitoringsystem.auth.Authentication
import com.cse3310.projecthealthmonitoringsystem.data.entity.*
import com.cse3310.projecthealthmonitoringsystem.monitoring.CurrentUser
import com.cse3310.projecthealthmonitoringsystem.monitoring.DoseTimes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Test-APK-only fixtures: real Room records in fresh fictional accounts, never production sample data. */
class HomeDashboardReviewTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun reviewEmptyAndPopulatedAccounts() = runBlocking<Unit> {
        val app = compose.activity.application as PhmsApplication
        val stamp = System.currentTimeMillis().toString()
        val emptyId = "HomeEmpty$stamp"
        val populatedId = "HomeAlex$stamp"
        val auth = Authentication(app.authRepository)
        for ((id, name) in listOf(emptyId to "Jamie Review", populatedId to "Alex Review")) {
            check(auth.register(id, "$id@example.com", name, "FictionalReview2026!".toCharArray(),
                "Fictional test question", "Fictional answer".toCharArray()))
        }
        compose.runOnIdle { CurrentUser.signIn(emptyId) }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("No medicines yet").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("No upcoming appointments").assertIsDisplayed()
        compose.onNodeWithText("Add your first reading").assertExists()
        capture("home-empty")

        val now = System.currentTimeMillis()
        val day = DoseTimes.todayRange().first
        val doctor = app.maintenanceRepository.addDoctor(populatedId, Doctor(userId = populatedId, name = "Dr. Ramirez"))
        app.maintenanceRepository.addDoctorVisit(populatedId, DoctorVisit(userId = populatedId,
            doctorId = doctor, visitAt = now + 2 * 86_400_000L, reason = "Routine appointment"))
        val med = requireNotNull(app.medicationRepository.addMedication(populatedId, Medication(userId = populatedId,
            name = "Metformin", dosage = "500 mg", frequency = "Daily", intakeTimeMinutes = 12 * 60,
            startDate = day - 86_400_000, createdAt = now, updatedAt = now)))
        val dose = requireNotNull(app.medicationRepository.scheduleDose(populatedId, med, day + 12 * 3_600_000L))
        check(app.medicationRepository.markDoseTaken(populatedId, dose))
        val types = app.vitalsRepository.observeVitalTypes().first()
        val bp = types.first { it.name.contains("Blood Pressure", true) }.vitalTypeId
        val glucose = types.first { it.name.contains("Glucose", true) }.vitalTypeId
        val cholesterol = types.first { it.name.contains("Cholesterol", true) }.vitalTypeId
        listOf(130.0, 128.0, 135.0, 132.0, 140.0, 148.0).forEachIndexed { i, value ->
            val at = now - (5 - i) * 86_400_000L
            app.vitalsRepository.addVitalSign(populatedId, bp, value, if (i == 5) 92.0 else 82.0, at)
            app.vitalsRepository.addVitalSign(populatedId, glucose, listOf(105.0, 99.0, 110.0, 106.0, 115.0, 112.0)[i], recordedAt = at)
            app.vitalsRepository.addVitalSign(populatedId, cholesterol, listOf(180.0, 178.0, 183.0, 189.0, 184.0, 186.0)[i], recordedAt = at)
        }
        app.dietRepository.addDietEntry(populatedId, DietEntry(userId = populatedId, mealType = "Lunch", eatenAt = now),
            listOf(Food(userId = populatedId, dietEntryId = 0, name = "Fictional review meal", calories = 620)))
        app.dietRepository.addWeight(populatedId, Weight(userId = populatedId, value = 78.4, unit = WeightUnit.KG, recordedAt = now))
        app.getSharedPreferences("nutrition_goals", 0).edit().putInt("calories_$populatedId", 1800).commit()
        // Another account's newly written data must not appear in the empty account.
        compose.onNodeWithText("148/92").assertDoesNotExist()
        compose.runOnIdle { CurrentUser.signIn(populatedId) }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("148/92").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Dr. Ramirez").assertExists()
        compose.onNodeWithText("112").assertExists()
        compose.onNodeWithText("186").assertExists()
        compose.onNodeWithText("1 of 1 taken").assertExists()
        capture("home-populated")
        compose.onNodeWithText("620 kcal").performScrollTo()
        compose.onNodeWithText("620 kcal").assertExists()
        compose.onNodeWithText("78.4 kg").assertExists()
        capture("home-diet-weight")
        compose.onNodeWithText("View history").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Weight log").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Latest weight").assertExists()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(200)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val directory = File(compose.activity.getExternalFilesDir(null), "home-review").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
