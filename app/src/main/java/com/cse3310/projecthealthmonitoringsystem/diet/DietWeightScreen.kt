package com.cse3310.projecthealthmonitoringsystem.diet

import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsTextField as OutlinedTextField

import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsTextButton as TextButton
import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsCard as OutlinedCard
import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsCard as Card

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cse3310.projecthealthmonitoringsystem.data.entity.*
import com.cse3310.projecthealthmonitoringsystem.data.repository.DietRepository
import com.cse3310.projecthealthmonitoringsystem.monitoring.CurrentUser
import com.cse3310.projecthealthmonitoringsystem.monitoring.phms
import com.cse3310.projecthealthmonitoringsystem.ui.components.*
import com.cse3310.projecthealthmonitoringsystem.vitals.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import com.cse3310.projecthealthmonitoringsystem.settings.*
import com.cse3310.projecthealthmonitoringsystem.validation.HealthValidation

private fun dateText(time: Long) = SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.US).format(Date(time))
private fun number(value: Double) = String.format(Locale.US, "%.1f", value)

/** Uses the same active-user identity and repository boundary as the other integrated screens. */
@Composable
fun DietWeightScreen(onBack: () -> Unit, initialTab: Int = 0) {
    val context = LocalContext.current
    val displayUnit = rememberMeasurementPreferences().weightUnit
    val userId = CurrentUser.userId
    val repository = context.phms().dietRepository
    val meals by remember(repository, userId) { repository.observeDietEntries(userId) }.collectAsState(emptyList())
    val weights by remember(repository, userId) { repository.observeWeights(userId) }.collectAsState(emptyList())
    var day by rememberSaveable(userId) { mutableLongStateOf(System.currentTimeMillis()) }
    val calories by remember(repository, userId, day) { repository.observeDailyCalorieTotal(userId, day) }.collectAsState(0)
    val preferences = remember(userId) { context.getSharedPreferences("nutrition_goals", Context.MODE_PRIVATE) }
    var goal by remember(userId) { mutableIntStateOf(preferences.getInt("calories_$userId", 0)) }
    var tab by rememberSaveable(userId) { mutableIntStateOf(initialTab.coerceIn(0, 1)) }
    var editor by remember(userId) { mutableStateOf<String?>(null) }
    var mealToEdit by remember(userId) { mutableStateOf<DietEntry?>(null) }
    var foodToEdit by remember(userId) { mutableStateOf<Food?>(null) }
    var foodMealId by remember(userId) { mutableLongStateOf(0L) }
    var deleteMeal by remember(userId) { mutableStateOf<DietEntry?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun perform(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        message = null
        scope.launch {
            try {
                action()
                editor = null
                deleteMeal = null
                message = "Changes saved."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = e.message ?: "Couldn't save. Please try again."
            } finally { busy = false }
        }
    }

    PhmsPage {
        PhmsScreenHeader("Diet and Weight", "Small entries. A clearer picture over time.", PhmsIcons.Diet, onBack)
        TabRow(selectedTabIndex = tab) {
            listOf("Diet", "Weight").forEachIndexed { index, label ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(label) },
                    selectedContentColor = MaterialTheme.colorScheme.onSurface, unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    icon = { Icon(if (index == 0) PhmsIcons.Diet else PhmsIcons.Weight, null) })
            }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        if (tab == 0) {
            TextButton(onClick = {
                val calendar = Calendar.getInstance().apply { timeInMillis = day }
                DatePickerDialog(context, { _, year, month, date ->
                    day = Calendar.getInstance().apply { set(year, month, date) }.timeInMillis
                }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
            }) { Text(SimpleDateFormat("EEEE, MMM d", Locale.US).format(Date(day)) + " · Change day") }
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        PhmsIconBadge(PhmsIcons.Diet)
                        Column {
                            Text("Daily intake", style = MaterialTheme.typography.titleMedium)
                            Text("$calories kcal", style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                    if (goal > 0) {
                        LinearProgressIndicator(progress = { (calories.toFloat() / goal).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        Text("Of your $goal kcal goal · ${(calories * 100L / goal)}%",
                            style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text("Set your own daily goal to see progress.", style = MaterialTheme.typography.bodyMedium)
                    }
                    TextButton(onClick = { editor = "goal" }) { Text(if (goal > 0) "Change calorie goal" else "Set calorie goal") }
                }
            }
            Button(onClick = { mealToEdit = null; editor = "meal" }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("+ Add meal") }
            PhmsSectionHeader("Meal history", supportingText = "All your recorded meals, newest first.")
            if (meals.isEmpty()) {
                PhmsEmptyStateCard("Start your food journal", "Add a meal and its foods to track your daily intake.",
                    PhmsIcons.Diet)
            }
            meals.forEach { meal ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(meal.entry.mealType, style = MaterialTheme.typography.titleMedium)
                        Text(dateText(meal.entry.eatenAt), style = MaterialTheme.typography.bodyMedium)
                        Text("${meal.foods.sumOf { it.calories }} kcal", style = MaterialTheme.typography.titleMedium)
                        if (meal.entry.notes.isNotBlank()) Text(meal.entry.notes)
                        meal.foods.forEach { food ->
                            TextButton(enabled = !busy, onClick = {
                                foodToEdit = food; foodMealId = meal.entry.dietEntryId; editor = "food"
                            }) { Text("${food.name} · ${food.calories} kcal · Edit") }
                            Text("Protein ${number(food.proteinGrams)} g · Sugar ${number(food.sugarGrams)} g",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(enabled = !busy, onClick = { mealToEdit = meal.entry; editor = "meal" }) { Text("Edit meal") }
                            TextButton(enabled = !busy, onClick = { foodToEdit = null; foodMealId = meal.entry.dietEntryId; editor = "food" }) { Text("Add food") }
                            TextButton(enabled = !busy, onClick = { deleteMeal = meal.entry }) { Text("Delete") }
                        }
                    }
                }
            }
        } else {
            val latest = weights.firstOrNull()
            if (latest != null) {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PhmsIconBadge(PhmsIcons.Weight)
                        Text("Latest weight", style = MaterialTheme.typography.titleMedium)
                        Text("${number(DietRepository.convert(latest.value, latest.unit, displayUnit))} ${displayUnit.name.lowercase()}", style = MaterialTheme.typography.headlineMedium)
                        Text(dateText(latest.recordedAt), style = MaterialTheme.typography.bodyMedium)
                        weights.getOrNull(1)?.let { previous ->
                            val change = DietRepository.convert(latest.value, latest.unit, displayUnit) - DietRepository.convert(previous.value, previous.unit, displayUnit)
                            Text("${if (change > 0) "+" else ""}${number(change)} ${displayUnit.name.lowercase()} since previous entry",
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            Button(onClick = { editor = "weight" }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("+ Add weight") }
            if (weights.isEmpty()) {
                PhmsEmptyStateCard("See your weight over time", "Record your first weight to start your personal history.",
                    PhmsIcons.Weight)
            } else {
                val unit = displayUnit
                VitalHistoryChart(weights.map {
                    VitalReadingItem("Weight", DietRepository.convert(it.value, it.unit, unit).toString(),
                        unit.name.lowercase(), recordedAt = dateText(it.recordedAt), isAbnormal = false, timestamp = it.recordedAt)
                }, VitalTypeOption(-1L, "Weight", unit.name.lowercase()))
                PhmsSectionHeader("Weight log", supportingText = "Your recorded weights, in ${unit.name.lowercase()}.")
                weights.forEach { weight ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("${number(DietRepository.convert(weight.value, weight.unit, displayUnit))} ${displayUnit.name.lowercase()}", style = MaterialTheme.typography.titleMedium)
                            Text(dateText(weight.recordedAt), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }

    if (editor == "goal") {
        NumericDialog("Daily calorie goal", "Calories (kcal)", busy, onDismiss = { editor = null }) { value, _ ->
            if (value > 0 && value <= Int.MAX_VALUE) {
                goal = value.toInt()
                preferences.edit().putInt("calories_$userId", goal).apply()
                editor = null
            }
        }
    }
    if (editor == "weight") {
        NumericDialog("Record weight", "Weight", busy, weight = true, onDismiss = { editor = null }) { value, unit ->
            perform { repository.addWeight(userId, Weight(userId = userId, value = value, unit = unit, recordedAt = System.currentTimeMillis())) }
        }
    }
    if (editor == "meal") {
        MealDialog(mealToEdit, busy, onDismiss = { editor = null }) { type, notes, name, kcal ->
            perform {
                val old = mealToEdit
                if (old == null) {
                    repository.addDietEntry(userId, DietEntry(userId = userId, mealType = type, eatenAt = day, notes = notes),
                        listOf(Food(userId = userId, dietEntryId = 0, name = name, calories = kcal)))
                } else {
                    check(repository.editDietEntry(userId, old.copy(mealType = type, notes = notes))) { "Meal could not be updated." }
                }
            }
        }
    }
    if (editor == "food") {
        FoodDialog(foodToEdit, busy, onDismiss = { editor = null },
            onDelete = foodToEdit?.let { food -> { perform { check(repository.deleteFood(userId, food.foodId)) } } }) { name, kcal, protein, sugar ->
            perform {
                val old = foodToEdit
                if (old == null) {
                    checkNotNull(repository.addFood(userId, Food(userId = userId, dietEntryId = foodMealId,
                        name = name, calories = kcal, proteinGrams = protein, sugarGrams = sugar)))
                } else check(repository.editFood(userId, old.copy(name = name, calories = kcal, proteinGrams = protein, sugarGrams = sugar)))
            }
        }
    }
    deleteMeal?.let { entry ->
        AlertDialog(onDismissRequest = { if (!busy) deleteMeal = null },
            title = { Text("Delete this meal?") }, text = { Text("This removes the meal and all its foods from your journal.") },
            confirmButton = { TextButton(enabled = !busy, onClick = { perform { check(repository.deleteDietEntry(userId, entry.dietEntryId)) } }) { Text("Delete meal") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { deleteMeal = null }) { Text("Cancel") } })
    }
}

@Composable
private fun NumericDialog(title: String, label: String, busy: Boolean, weight: Boolean = false,
    onDismiss: () -> Unit, onSave: (Double, WeightUnit) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val prefs = rememberMeasurementPreferences()
    var unit by remember { mutableStateOf(prefs.weightUnit) }
    val value = text.toDoubleOrNull()
    val dismiss = rememberGuardedDismiss(text.isNotBlank(), busy, onDismiss)
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(text, { text = it }, label)
                if (weight) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    WeightUnit.entries.forEach { item ->
                        FilterChip(selected = unit == item, onClick = { text.toDoubleOrNull()?.takeIf { it.isFinite() }?.let { text = String.format(Locale.US, "%.2f", DietRepository.convert(it, unit, item)) }; unit = item }, label = { Text(item.name.lowercase()) })
                    }
                }
                val validation = if (value == null) "Enter a number." else if (weight) HealthValidation.weightKg(DietRepository.convert(value, unit, WeightUnit.KG)) else HealthValidation.calories(value.toInt())
                if (text.isNotBlank() && validation != null) Text(validation, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }, confirmButton = { TextButton(enabled = !busy && value != null && value.isFinite() && value > 0 &&
            (if (weight) HealthValidation.weightKg(DietRepository.convert(value, unit, WeightUnit.KG)) == null else value % 1.0 == 0.0 && value <= 100000), onClick = { onSave(value!!, unit) }) { Text(if (busy) "Saving…" else "Save") } },
        dismissButton = { TextButton(enabled = !busy, onClick = dismiss) { Text("Cancel") } })
}

@Composable
private fun MealDialog(entry: DietEntry?, busy: Boolean, onDismiss: () -> Unit,
    onSave: (String, String, String, Int) -> Unit) {
    var type by rememberSaveable { mutableStateOf(entry?.mealType ?: "Breakfast") }
    var notes by rememberSaveable { mutableStateOf(entry?.notes ?: "") }
    var name by rememberSaveable { mutableStateOf("") }
    var calories by rememberSaveable { mutableStateOf("") }
    val dismiss = rememberGuardedDismiss(true, busy, onDismiss)
    AlertDialog(onDismissRequest = dismiss, title = { Text(if (entry == null) "Add meal" else "Edit meal") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(type, { type = it }, label = { Text("Meal (breakfast, lunch, dinner…)") })
                if (entry == null) {
                    OutlinedTextField(name, { name = it }, label = { Text("Food name") })
                    NumberField(calories, { calories = it }, "Calories (kcal)")
                }
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes (optional)") })
                if (entry != null) Text("Edit food names and nutrition from each food in the meal card.")
            }
        }, confirmButton = { TextButton(enabled = !busy && type.isNotBlank() &&
            (entry != null || (name.isNotBlank() && calories.toIntOrNull()?.let { HealthValidation.calories(it) == null } == true)),
            onClick = { onSave(type.trim(), notes.trim(), name.trim(), calories.toIntOrNull() ?: 0) }) { Text(if (busy) "Saving…" else "Save") } },
        dismissButton = { TextButton(enabled = !busy, onClick = dismiss) { Text("Cancel") } })
}

@Composable
private fun FoodDialog(food: Food?, busy: Boolean, onDismiss: () -> Unit, onDelete: (() -> Unit)?,
    onSave: (String, Int, Double, Double) -> Unit) {
    var name by rememberSaveable { mutableStateOf(food?.name ?: "") }
    var kcal by rememberSaveable { mutableStateOf(food?.calories?.toString() ?: "") }
    var protein by rememberSaveable { mutableStateOf(food?.proteinGrams?.toString() ?: "0") }
    var sugar by rememberSaveable { mutableStateOf(food?.sugarGrams?.toString() ?: "0") }
    var confirmFoodDelete by remember { mutableStateOf(false) }
    fun valid(value: String) = value.toDoubleOrNull()?.let { HealthValidation.grams(it) == null } == true
    val dismiss = rememberGuardedDismiss(true, busy, onDismiss)
    AlertDialog(onDismissRequest = dismiss, title = { Text(if (food == null) "Add food" else "Edit food") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Food name") })
                NumberField(kcal, { kcal = it }, "Calories (kcal)")
                NumberField(protein, { protein = it }, "Protein (g)")
                NumberField(sugar, { sugar = it }, "Sugar (g)")
            }
        }, confirmButton = { TextButton(enabled = !busy && name.isNotBlank() && kcal.toIntOrNull()?.let { HealthValidation.calories(it) == null } == true &&
            valid(protein) && valid(sugar), onClick = { onSave(name.trim(), kcal.toInt(), protein.toDouble(), sugar.toDouble()) }) { Text(if (busy) "Saving…" else "Save") } },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(enabled = !busy, onClick = { confirmFoodDelete = true }) { Text("Delete food") }
                TextButton(enabled = !busy, onClick = dismiss) { Text("Cancel") }
            }
        })
    if (confirmFoodDelete && onDelete != null) {
        AlertDialog(onDismissRequest = { if (!busy) confirmFoodDelete = false },
            title = { Text("Delete this food?") }, text = { Text("This removes the food from your meal.") },
            confirmButton = { TextButton(enabled = !busy, onClick = { confirmFoodDelete = false; onDelete() }) { Text("Delete food") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { confirmFoodDelete = false }) { Text("Cancel") } })
    }
}

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, label: String) {
    val parsed = value.toDoubleOrNull()
    val error = if (value.isBlank()) null else if (parsed == null || !parsed.isFinite() || parsed < 0) "Enter a finite, non-negative number." else null
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true,
        isError = error != null, supportingText = { error?.let { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
}
