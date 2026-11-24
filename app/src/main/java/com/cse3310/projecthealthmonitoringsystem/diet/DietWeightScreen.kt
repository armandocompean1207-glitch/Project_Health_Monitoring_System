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

private fun dateText(time: Long) = SimpleDateFormat("MM/dd/yyyy · h:mm a", Locale.US).format(Date(time))
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
    val pageScroll = rememberScrollState()
    val inlineOpen = editor in listOf("goal", "meal", "weight", "food")
    val dismissInline = rememberGuardedDismiss(inlineOpen, busy) { editor = null }
    LaunchedEffect(editor) { if (inlineOpen) pageScroll.animateScrollTo(0) }

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

    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(onSurface = androidx.compose.ui.graphics.Color(0xFF0F172A),
        onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF0F172A), onBackground = androidx.compose.ui.graphics.Color(0xFF0F172A))) {
    PhmsPage(scrollState = pageScroll) {
        PhmsScreenHeader("Diet and Weight", "Track your meals and weight in one place.", PhmsIcons.Diet, if (inlineOpen) dismissInline else onBack)
        TabRow(selectedTabIndex = tab) {
            listOf("Diet", "Weight").forEachIndexed { index, label ->
                Tab(selected = tab == index, enabled = !busy, onClick = { tab = index }, text = { Text(label) },
                    selectedContentColor = MaterialTheme.colorScheme.onSurface, unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    icon = { Icon(if (index == 0) PhmsIcons.Diet else PhmsIcons.Weight, null) })
            }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        key(editor, mealToEdit?.dietEntryId, foodMealId, foodToEdit?.foodId) {
    if (editor == "goal") {
        NumericInlineForm("Daily calorie goal", "Calories (kcal)", busy, initialText = goal.takeIf { it > 0 }?.toString().orEmpty(), onDismiss = { editor = null }) { value, _, _ ->
            if (value > 0 && value <= Int.MAX_VALUE) {
                goal = value.toInt()
                preferences.edit().putInt("calories_$userId", goal).apply()
                editor = null
            }
        }
    }
    if (editor == "weight") {
        NumericInlineForm("Record weight", "Weight", busy, weight = true, onDismiss = { editor = null }) { value, unit, recordedAt ->
            perform { repository.addWeight(userId, Weight(userId = userId, value = value, unit = unit, recordedAt = recordedAt)) }
        }
    }
    if (editor == "meal") {
        MealInlineForm(mealToEdit, day, busy, onDismiss = { editor = null }) { type, notes, name, kcal, at ->
            perform {
                val old = mealToEdit
                if (old == null) {
                    repository.addDietEntry(userId, DietEntry(userId = userId, mealType = type, eatenAt = at, notes = notes),
                        listOf(Food(userId = userId, dietEntryId = 0, name = name, calories = kcal)))
                } else {
                    check(repository.editDietEntry(userId, old.copy(mealType = type, notes = notes, eatenAt = at))) { "Meal could not be updated." }
                }
                day = at
            }
        }
    }

    if (editor == "food") {
        FoodInlineForm(foodToEdit, busy, onDismiss = { editor = null },
            onDelete = foodToEdit?.let { food -> { perform { check(repository.deleteFood(userId, food.foodId)) } } }) { name, kcal ->
            perform {
                val old = foodToEdit
                if (old == null) {
                    checkNotNull(repository.addFood(userId, Food(userId = userId, dietEntryId = foodMealId,
                        name = name, calories = kcal)))
                } else check(repository.editFood(userId, old.copy(name = name, calories = kcal)))
            }
        }
    }

        }
        if (tab == 0) {
            PhmsDateInput(day, { it?.let { time -> day = time } }, "Change day", enabled = !busy)
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
                    OutlinedButton(enabled = !busy, onClick = { editor = "goal" }, colors = ButtonDefaults.outlinedButtonColors(containerColor = androidx.compose.ui.graphics.Color.White, contentColor = androidx.compose.ui.graphics.Color(0xFF0F172A))) { Text(if (goal > 0) "Change calorie goal" else "Set calorie goal") }
                }
            }
            if (editor != "meal") Button(onClick = { mealToEdit = null; editor = "meal" }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("+ Add meal") }
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
                            FoodHistoryRow(food, enabled = !busy, onEdit = {
                                foodToEdit = food; foodMealId = meal.entry.dietEntryId; editor = "food"
                            })

                        }
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PhmsOutlinedButton(enabled = !busy, onClick = { mealToEdit = meal.entry; editor = "meal" }) { Text("Edit meal", maxLines = 1, softWrap = false) }
                            PhmsOutlinedButton(enabled = !busy, onClick = { foodToEdit = null; foodMealId = meal.entry.dietEntryId; editor = "food" }) { Text("Add food", maxLines = 1, softWrap = false) }
                            PhmsOutlinedButton(enabled = !busy, onClick = { deleteMeal = meal.entry }) { Text("Delete", maxLines = 1, softWrap = false) }
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
                        Text("${number(DietRepository.convert(latest.value, latest.unit, displayUnit))} ${(if (displayUnit == WeightUnit.LB) "lbs" else "kg")}", style = MaterialTheme.typography.headlineMedium)
                        Text(dateText(latest.recordedAt), style = MaterialTheme.typography.bodyMedium)
                        weights.getOrNull(1)?.let { previous ->
                            val change = DietRepository.convert(latest.value, latest.unit, displayUnit) - DietRepository.convert(previous.value, previous.unit, displayUnit)
                            Text(measurementComparison(change, 0.0, if (displayUnit == WeightUnit.LB) "lbs" else "kg").orEmpty(),
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            if (editor != "weight") Button(onClick = { editor = "weight" }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("+ Add weight") }
            if (weights.isEmpty()) {
                PhmsEmptyStateCard("See your weight over time", "Record your first weight to start your personal history.",
                    PhmsIcons.Weight)
            } else {
                val unit = displayUnit
                VitalHistoryChart(weights.map {
                    VitalReadingItem("Weight", DietRepository.convert(it.value, it.unit, unit).toString(),
                        (if (unit == WeightUnit.LB) "lbs" else "kg"), recordedAt = dateText(it.recordedAt), isAbnormal = false, timestamp = it.recordedAt,
                        readingId = it.weightId, primaryNumeric = DietRepository.convert(it.value, it.unit, unit))
                }, VitalTypeOption(-1L, "Weight", (if (unit == WeightUnit.LB) "lbs" else "kg")))
                PhmsSectionHeader("Weight log", supportingText = "Your recorded weights, in ${(if (unit == WeightUnit.LB) "lbs" else "kg")}.")
                weights.forEach { weight ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("${number(DietRepository.convert(weight.value, weight.unit, displayUnit))} ${(if (displayUnit == WeightUnit.LB) "lbs" else "kg")}", style = MaterialTheme.typography.titleMedium)
                            Text(dateText(weight.recordedAt), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
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
}

@Composable
private fun FoodHistoryRow(food: Food, enabled: Boolean, onEdit: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(food.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text("${food.calories} kcal", style = MaterialTheme.typography.bodyMedium)
        PhmsOutlinedButton(enabled = enabled, onClick = onEdit) { Text("Edit", maxLines = 1, softWrap = false) }
    }
}

@Composable
private fun FoodEntryFields(name: String, onNameChange: (String) -> Unit,
    calories: String, onCaloriesChange: (String) -> Unit, enabled: Boolean) {
    OutlinedTextField(name, onNameChange, label = { Text("Food name") },
        enabled = enabled, modifier = Modifier.fillMaxWidth())
    NumberField(calories, onCaloriesChange, "Calories (kcal)", enabled = enabled)
}

@Composable
private fun InlineFormCard(title: String, busy: Boolean, valid: Boolean, onCancel: () -> Unit, onSave: () -> Unit,
    content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(containerColor = androidx.compose.ui.graphics.Color.White)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(enabled = !busy && valid, onClick = onSave,
                    colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF065F46),
                        contentColor = androidx.compose.ui.graphics.Color.White)) { Text(if (busy) "Saving…" else "Save") }
                OutlinedButton(enabled = !busy, onClick = onCancel,
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = androidx.compose.ui.graphics.Color.White,
                        contentColor = androidx.compose.ui.graphics.Color(0xFF0F172A))) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun NumericInlineForm(title: String, label: String, busy: Boolean, weight: Boolean = false,
    initialText: String = "", onDismiss: () -> Unit, onSave: (Double, WeightUnit, Long) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initialText) }
    val prefs = rememberMeasurementPreferences()
    var unit by remember { mutableStateOf(prefs.weightUnit) }
    var recordedAt by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    var dateValid by remember { mutableStateOf(true) }
    val value = text.toDoubleOrNull()
    val dismiss = rememberGuardedDismiss(text != initialText, busy, onDismiss)
    androidx.activity.compose.BackHandler { dismiss() }
    val valid = value != null && value.isFinite() && value > 0 && dateValid &&
        (if (weight) HealthValidation.weightKg(DietRepository.convert(value, unit, WeightUnit.KG)) == null
        else value % 1.0 == 0.0 && value <= 100000)
    InlineFormCard(title, busy, valid, onDismiss, { onSave(value!!, unit, recordedAt) }) {
        NumberField(text, { text = it }, label, enabled = !busy)
        if (weight) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WeightUnit.entries.forEach { item ->
                    FilterChip(selected = unit == item, enabled = !busy, onClick = {
                        text.toDoubleOrNull()?.takeIf { it.isFinite() }?.let {
                            text = String.format(Locale.US, "%.2f", DietRepository.convert(it, unit, item))
                        }; unit = item
                    }, label = { Text(if (item == WeightUnit.LB) "lbs" else "kg") })
                }
            }
            PhmsDateInput(recordedAt, { it?.let { time -> recordedAt = time } }, "Date recorded",
                maxDate = System.currentTimeMillis(), enabled = !busy, onValidityChange = { dateValid = it })
        }
        val validation = if (value == null || !value.isFinite()) "Enter a valid number."
            else if (weight) HealthValidation.weightKg(DietRepository.convert(value, unit, WeightUnit.KG))
            else if (value % 1.0 != 0.0 || value > 100000 || value <= 0) "Enter a whole-number calorie goal from 1 to 100,000." else null
        if (text.isNotBlank() && validation != null) Text(validation, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun MealInlineForm(entry: DietEntry?, day: Long, busy: Boolean, onDismiss: () -> Unit,
    onSave: (String, String, String, Int, Long) -> Unit) {
    var type by rememberSaveable { mutableStateOf(entry?.mealType ?: "Breakfast") }
    var notes by rememberSaveable { mutableStateOf(entry?.notes ?: "") }
    var name by rememberSaveable { mutableStateOf("") }
    var calories by rememberSaveable { mutableStateOf("") }
    var at by rememberSaveable { mutableLongStateOf(entry?.eatenAt ?: day) }
    var dateValid by remember { mutableStateOf(true) }
    val dismiss = rememberGuardedDismiss(true, busy, onDismiss)
    androidx.activity.compose.BackHandler { dismiss() }
    val valid = dateValid && type.isNotBlank() &&
        (entry != null || (name.isNotBlank() && calories.toIntOrNull()?.let { HealthValidation.calories(it) == null } == true))
    InlineFormCard(if (entry == null) "Add meal" else "Edit meal", busy, valid, onDismiss,
        { onSave(type.trim(), notes.trim(), name.trim(), calories.toIntOrNull() ?: 0, at) }) {
        OutlinedTextField(type, { type = it }, label = { Text("Meal (breakfast, lunch, dinner…)") }, enabled = !busy)
        PhmsDateInput(at, { it?.let { time -> at = time } }, "Meal date", minDate = 1, maxDate = 4102444799999L,
            enabled = !busy, onValidityChange = { dateValid = it })
        if (entry == null) {
            FoodEntryFields(name, { name = it }, calories, { calories = it }, enabled = !busy)
        }
        OutlinedTextField(notes, { notes = it }, label = { Text("Notes (optional)") }, enabled = !busy)
        if (entry != null) Text("Edit foods from the meal card.")
    }
}

@Composable
private fun FoodInlineForm(food: Food?, busy: Boolean, onDismiss: () -> Unit, onDelete: (() -> Unit)?,
    onSave: (String, Int) -> Unit) {
    var name by rememberSaveable { mutableStateOf(food?.name ?: "") }
    var kcal by rememberSaveable { mutableStateOf(food?.calories?.toString() ?: "") }
    var confirmFoodDelete by remember { mutableStateOf(false) }
    val dismiss = rememberGuardedDismiss(true, busy, onDismiss)
    androidx.activity.compose.BackHandler { dismiss() }
    val canSave = name.isNotBlank() && kcal.toIntOrNull()?.let { HealthValidation.calories(it) == null } == true
    InlineFormCard(if (food == null) "Add food" else "Edit food", busy, canSave, dismiss,
        { onSave(name.trim(), kcal.toInt()) }) {
        FoodEntryFields(name, { name = it }, kcal, { kcal = it }, enabled = !busy)
        if (onDelete != null) PhmsOutlinedButton(enabled = !busy, onClick = { confirmFoodDelete = true }) { Text("Delete food") }
    }
    if (confirmFoodDelete && onDelete != null) {
        AlertDialog(onDismissRequest = { if (!busy) confirmFoodDelete = false },
            title = { Text("Delete this food?") }, text = { Text("This removes the food from your meal.") },
            confirmButton = { TextButton(enabled = !busy, onClick = { confirmFoodDelete = false; onDelete() }) { Text("Delete food") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { confirmFoodDelete = false }) { Text("Cancel") } })
    }
}

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, label: String, enabled: Boolean = true) {
    val parsed = value.toDoubleOrNull()
    val error = if (value.isBlank()) null else if (parsed == null || !parsed.isFinite() || parsed < 0) "Enter a finite, non-negative number." else null
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true, enabled = enabled,
        isError = error != null, supportingText = { error?.let { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
}
