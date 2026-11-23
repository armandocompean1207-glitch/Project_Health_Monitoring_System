package com.cse3310.projecthealthmonitoringsystem.data.repository

import com.cse3310.projecthealthmonitoringsystem.data.dao.DietDao
import com.cse3310.projecthealthmonitoringsystem.data.dao.DietEntryWithFoods
import com.cse3310.projecthealthmonitoringsystem.data.entity.DietEntry
import com.cse3310.projecthealthmonitoringsystem.data.entity.Food
import com.cse3310.projecthealthmonitoringsystem.data.entity.Weight
import com.cse3310.projecthealthmonitoringsystem.data.entity.WeightUnit
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import java.util.TimeZone

/** Diet, Food and Weight (Arpan). */
class DietRepository(
    private val dietDao: DietDao,
    private val tx: TransactionRunner,
    private val timeZone: () -> TimeZone = { TimeZone.getDefault() }
) {

    /** Change from the previous weight entry, in the latest entry's unit. */
    data class WeightChange(val latest: Weight, val previous: Weight, val change: Double, val unit: WeightUnit)

    // ---- Diet entries and foods ----

    /** Saves a meal and its foods together. Returns the new dietEntryId. */
    suspend fun addDietEntry(userId: String, entry: DietEntry, foods: List<Food> = emptyList()): Long = tx.run {
        require(entry.userId == userId) { "Diet entry must belong to the logged-in user" }
        validateMeal(entry)
        foods.forEach(::validate)
        val id = dietDao.insertDietEntry(entry)
        if (foods.isNotEmpty()) {
            dietDao.insertFoods(foods.map { it.copy(foodId = 0, userId = userId, dietEntryId = id) })
        }
        id
    }

    suspend fun editDietEntry(userId: String, entry: DietEntry): Boolean {
        validateMeal(entry)
        return dietDao.updateDietEntry(userId, entry)
    }
    private fun validateMeal(entry: DietEntry) {
        com.cse3310.projecthealthmonitoringsystem.validation.HealthValidation.name(entry.mealType, "Meal")
            ?.let { throw IllegalArgumentException(it) }
        require(entry.eatenAt in 1..4102444799999L) { "Choose a valid meal date." }
    }

    suspend fun deleteDietEntry(userId: String, dietEntryId: Long): Boolean = dietDao.deleteDietEntry(userId, dietEntryId) > 0

    /** Returns null if the diet entry is not this user's. */
    suspend fun addFood(userId: String, food: Food): Long? {
        require(food.userId == userId) { "Food must belong to the logged-in user" }
        validate(food)
        if (!dietDao.dietEntryOwned(userId, food.dietEntryId)) return null
        return dietDao.insertFood(food)
    }

    suspend fun editFood(userId: String, food: Food): Boolean {
        validate(food)
        return dietDao.updateFood(userId, food)
    }

    suspend fun deleteFood(userId: String, foodId: Long): Boolean = dietDao.deleteFood(userId, foodId) > 0

    suspend fun getFoods(userId: String, dietEntryId: Long): List<Food> = dietDao.getFoods(userId, dietEntryId)

    fun observeDietEntries(userId: String): Flow<List<DietEntryWithFoods>> = dietDao.observeDietEntriesWithFoods(userId)

    fun observeDietEntries(userId: String, from: Long, to: Long): Flow<List<DietEntryWithFoods>> =
        dietDao.observeDietEntriesWithFoodsInRange(userId, from, to)

    /** Daily calorie total for the local calendar day containing [dayMillis]. */
    fun observeDailyCalorieTotal(userId: String, dayMillis: Long): Flow<Int> {
        val (start, end) = dayBounds(dayMillis)
        return dietDao.observeCalorieTotal(userId, start, end)
    }

    suspend fun getDailyCalorieTotal(userId: String, dayMillis: Long): Int {
        val (start, end) = dayBounds(dayMillis)
        return dietDao.getCalorieTotal(userId, start, end)
    }

    // ---- Weight ----

    suspend fun addWeight(userId: String, weight: Weight): Long {
        require(weight.userId == userId) { "Weight must belong to the logged-in user" }
        require(weight.recordedAt in 1..4102444799999L) { "Choose a valid weight date." }
        com.cse3310.projecthealthmonitoringsystem.validation.HealthValidation.weightKg(convert(weight.value, weight.unit, WeightUnit.KG))?.let { throw IllegalArgumentException(it) }
        return dietDao.insertWeight(weight)
    }

    suspend fun editWeight(userId: String, weight: Weight): Boolean {
        require(weight.recordedAt in 1..4102444799999L) { "Choose a valid weight date." }
        com.cse3310.projecthealthmonitoringsystem.validation.HealthValidation.weightKg(convert(weight.value, weight.unit, WeightUnit.KG))?.let { throw IllegalArgumentException(it) }
        return dietDao.updateWeight(userId, weight)
    }

    suspend fun deleteWeight(userId: String, weightId: Long): Boolean = dietDao.deleteWeight(userId, weightId) > 0

    fun observeWeights(userId: String): Flow<List<Weight>> = dietDao.observeWeights(userId)

    /** Weight change since the previous entry, or null with fewer than two entries. */
    suspend fun weightChangeSinceLast(userId: String): WeightChange? {
        val rows = dietDao.getLatestTwoWeights(userId)
        if (rows.size < 2) return null
        val (latest, previous) = rows
        val previousInLatestUnit = convert(previous.value, previous.unit, latest.unit)
        return WeightChange(latest, previous, latest.value - previousInLatestUnit, latest.unit)
    }

    /** [start, end] in epoch ms of the local day containing [millis]. */
    fun dayBounds(millis: Long): Pair<Long, Long> {
        val cal = Calendar.getInstance(timeZone()).apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_MONTH, 1)
        return start to cal.timeInMillis - 1
    }

    private fun validate(food: Food) {
        val v = com.cse3310.projecthealthmonitoringsystem.validation.HealthValidation
        (v.name(food.name, "Food name") ?: v.calories(food.calories) ?: v.grams(food.proteinGrams) ?: v.grams(food.sugarGrams))
            ?.let { throw IllegalArgumentException(it) }
    }

    companion object {
        const val LB_PER_KG = 2.20462262

        fun convert(value: Double, from: WeightUnit, to: WeightUnit): Double = when {
            from == to -> value
            from == WeightUnit.KG -> value * LB_PER_KG
            else -> value / LB_PER_KG
        }
    }
}
