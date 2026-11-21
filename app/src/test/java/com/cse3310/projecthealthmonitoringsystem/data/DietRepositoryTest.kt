package com.cse3310.projecthealthmonitoringsystem.data

import com.cse3310.projecthealthmonitoringsystem.data.entity.DietEntry
import com.cse3310.projecthealthmonitoringsystem.data.entity.Food
import com.cse3310.projecthealthmonitoringsystem.data.entity.Weight
import com.cse3310.projecthealthmonitoringsystem.data.entity.WeightUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DietRepositoryTest : DbTest() {

    private fun food(name: String, calories: Int) =
        Food(userId = A, dietEntryId = 0, name = name, calories = calories, proteinGrams = 5.0, sugarGrams = 2.0)

    private suspend fun meal(userId: String, at: Long, vararg calories: Int): Long =
        diet.addDietEntry(
            userId,
            DietEntry(userId = userId, mealType = "Lunch", eatenAt = at),
            calories.mapIndexed { i, c -> food("Food $i", c).copy(userId = userId) }
        )

    @Test
    fun dailyCalorieTotalUsesDayBoundaries() = runBlocking<Unit> {
        val (start, end) = diet.dayBounds(NOON)
        meal(A, start, 300, 200)          // first ms of the day
        meal(A, end, 100)                 // last ms of the day
        meal(A, start - 1, 999)           // previous day
        meal(A, end + 1, 999)             // next day
        meal(B, NOON, 5000)               // other user

        assertEquals(600, diet.getDailyCalorieTotal(A, NOON))
        assertEquals(600, diet.observeDailyCalorieTotal(A, NOON).first())
        assertEquals(0, diet.getDailyCalorieTotal(A, NOON + 7 * DAY))
    }

    @Test
    fun entriesWithFoodsAndCascadeDelete() = runBlocking<Unit> {
        val id = meal(A, NOON, 300, 200)
        val entries = diet.observeDietEntries(A).first()
        assertEquals(1, entries.size)
        assertEquals(2, entries.single().foods.size)

        val extra = diet.addFood(A, food("Apple", 95).copy(dietEntryId = id))
        assertEquals(3, diet.getFoods(A, id).size)
        assertTrue(diet.editFood(A, diet.getFoods(A, id).first { it.foodId == extra }.copy(calories = 100)))
        assertEquals(600, diet.getDailyCalorieTotal(A, NOON))

        assertTrue(diet.deleteDietEntry(A, id))
        assertEquals(0, count("Food"))
    }

    @Test
    fun foodMustBelongToOwnEntryAndBeValid() = runBlocking<Unit> {
        val bEntry = meal(B, NOON, 100)
        assertNull(diet.addFood(A, food("Apple", 95).copy(dietEntryId = bEntry)))
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { meal(A, NOON, -5) }
        }
        assertEquals(0, diet.observeDietEntries(A).first().size)
    }

    @Test
    fun weightChangeSinceLastEntry() = runBlocking<Unit> {
        assertNull(diet.weightChangeSinceLast(A))
        diet.addWeight(A, Weight(userId = A, value = 80.0, unit = WeightUnit.KG, recordedAt = NOON - 2 * DAY))
        assertNull(diet.weightChangeSinceLast(A))
        diet.addWeight(A, Weight(userId = A, value = 78.5, unit = WeightUnit.KG, recordedAt = NOON - DAY))
        assertEquals(-1.5, diet.weightChangeSinceLast(A)!!.change, 1e-9)

        // mixed units: latest in LB, previous 78.5 kg = 173.06 lb
        diet.addWeight(A, Weight(userId = A, value = 170.0, unit = WeightUnit.LB, recordedAt = NOON))
        val change = diet.weightChangeSinceLast(A)!!
        assertEquals(WeightUnit.LB, change.unit)
        assertEquals(170.0 - 78.5 * 2.20462262, change.change, 1e-6)

        diet.addWeight(B, Weight(userId = B, value = 50.0, unit = WeightUnit.KG, recordedAt = NOON + DAY))
        assertEquals(170.0, diet.weightChangeSinceLast(A)!!.latest.value, 0.0)
        assertEquals(3, diet.observeWeights(A).first().size)
    }

    @Test
    fun weightValidationAndEditDelete() = runBlocking<Unit> {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { diet.addWeight(A, Weight(userId = A, value = 0.0, unit = WeightUnit.KG, recordedAt = NOON)) }
        }
        val id = diet.addWeight(A, Weight(userId = A, value = 70.0, unit = WeightUnit.KG, recordedAt = NOON))
        assertTrue(diet.editWeight(A, Weight(weightId = id, userId = A, value = 71.0, unit = WeightUnit.KG, recordedAt = NOON)))
        assertEquals(71.0, diet.observeWeights(A).first().single().value, 0.0)
        assertTrue(diet.deleteWeight(A, id))
    }
}
