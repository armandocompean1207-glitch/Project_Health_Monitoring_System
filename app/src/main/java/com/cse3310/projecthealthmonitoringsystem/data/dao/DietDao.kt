package com.cse3310.projecthealthmonitoringsystem.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.cse3310.projecthealthmonitoringsystem.data.entity.DietEntry
import com.cse3310.projecthealthmonitoringsystem.data.entity.Food
import com.cse3310.projecthealthmonitoringsystem.data.entity.Weight
import kotlinx.coroutines.flow.Flow

/** DietEntry, Food and Weight. Every query is scoped by userId. */
@Dao
abstract class DietDao {

    // ---- DietEntry ----

    @Insert
    abstract suspend fun insertDietEntry(entry: DietEntry): Long

    @Update
    protected abstract suspend fun updateDietEntryRow(entry: DietEntry): Int

    @Transaction
    open suspend fun updateDietEntry(userId: String, entry: DietEntry): Boolean =
        entry.userId == userId && dietEntryOwned(userId, entry.dietEntryId) && updateDietEntryRow(entry) > 0

    /** Also deletes the entry's foods (CASCADE). */
    @Query("DELETE FROM DietEntry WHERE dietEntryId = :dietEntryId AND userId = :userId")
    abstract suspend fun deleteDietEntry(userId: String, dietEntryId: Long): Int

    @Query("SELECT EXISTS(SELECT 1 FROM DietEntry WHERE dietEntryId = :dietEntryId AND userId = :userId)")
    abstract suspend fun dietEntryOwned(userId: String, dietEntryId: Long): Boolean

    @Query("SELECT * FROM DietEntry WHERE dietEntryId = :dietEntryId AND userId = :userId")
    abstract suspend fun getDietEntry(userId: String, dietEntryId: Long): DietEntry?

    /** Entries with their foods. Room loads all foods in one extra batched query, not one per entry. */
    @Transaction
    @Query("SELECT * FROM DietEntry WHERE userId = :userId ORDER BY eatenAt DESC")
    abstract fun observeDietEntriesWithFoods(userId: String): Flow<List<DietEntryWithFoods>>

    @Transaction
    @Query("SELECT * FROM DietEntry WHERE userId = :userId AND eatenAt BETWEEN :from AND :to ORDER BY eatenAt")
    abstract fun observeDietEntriesWithFoodsInRange(userId: String, from: Long, to: Long): Flow<List<DietEntryWithFoods>>

    /** Daily calorie total: sum of food calories for entries eaten in [dayStart, dayEnd]. */
    @Query(
        """
        SELECT COALESCE(SUM(f.calories), 0)
        FROM Food f JOIN DietEntry d ON d.dietEntryId = f.dietEntryId AND d.userId = f.userId
        WHERE d.userId = :userId AND d.eatenAt BETWEEN :dayStart AND :dayEnd
        """
    )
    abstract fun observeCalorieTotal(userId: String, dayStart: Long, dayEnd: Long): Flow<Int>

    @Query(
        """
        SELECT COALESCE(SUM(f.calories), 0)
        FROM Food f JOIN DietEntry d ON d.dietEntryId = f.dietEntryId AND d.userId = f.userId
        WHERE d.userId = :userId AND d.eatenAt BETWEEN :dayStart AND :dayEnd
        """
    )
    abstract suspend fun getCalorieTotal(userId: String, dayStart: Long, dayEnd: Long): Int

    // ---- Food ----

    @Insert
    abstract suspend fun insertFood(food: Food): Long

    @Insert
    abstract suspend fun insertFoods(foods: List<Food>): List<Long>

    @Update
    protected abstract suspend fun updateFoodRow(food: Food): Int

    @Transaction
    open suspend fun updateFood(userId: String, food: Food): Boolean =
        food.userId == userId &&
            foodOwned(userId, food.foodId) &&
            dietEntryOwned(userId, food.dietEntryId) &&
            updateFoodRow(food) > 0

    @Query("DELETE FROM Food WHERE foodId = :foodId AND userId = :userId")
    abstract suspend fun deleteFood(userId: String, foodId: Long): Int

    @Query("SELECT EXISTS(SELECT 1 FROM Food WHERE foodId = :foodId AND userId = :userId)")
    abstract suspend fun foodOwned(userId: String, foodId: Long): Boolean

    @Query("SELECT * FROM Food WHERE userId = :userId AND dietEntryId = :dietEntryId ORDER BY foodId")
    abstract suspend fun getFoods(userId: String, dietEntryId: Long): List<Food>

    // ---- Weight ----

    @Insert
    abstract suspend fun insertWeight(weight: Weight): Long

    @Update
    protected abstract suspend fun updateWeightRow(weight: Weight): Int

    @Transaction
    open suspend fun updateWeight(userId: String, weight: Weight): Boolean =
        weight.userId == userId && weightOwned(userId, weight.weightId) && updateWeightRow(weight) > 0

    @Query("DELETE FROM Weight WHERE weightId = :weightId AND userId = :userId")
    abstract suspend fun deleteWeight(userId: String, weightId: Long): Int

    @Query("SELECT EXISTS(SELECT 1 FROM Weight WHERE weightId = :weightId AND userId = :userId)")
    abstract suspend fun weightOwned(userId: String, weightId: Long): Boolean

    @Query("SELECT * FROM Weight WHERE userId = :userId ORDER BY recordedAt DESC, weightId DESC")
    abstract fun observeWeights(userId: String): Flow<List<Weight>>

    /** The latest two weight entries, newest first, for "weight change since last entry". */
    @Query("SELECT * FROM Weight WHERE userId = :userId ORDER BY recordedAt DESC, weightId DESC LIMIT 2")
    abstract suspend fun getLatestTwoWeights(userId: String): List<Weight>
}
