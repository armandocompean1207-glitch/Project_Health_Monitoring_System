package pushtest.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class DietFoodEntry(
    val id: Int,
    val name: String,
    val calories: Int,
    val protein: Int,
    val sugar: Int,
    val date: String,
    val time: String
)

data class FavoriteFood(
    val name: String,
    val calories: Int,
    val protein: Int,
    val sugar: Int
)

data class WeightEntry(
    val id: Int,
    val weight: Double,
    val unit: String,
    val date: String
)

@Composable
fun DietWeightScreen(onExit: () -> Unit) {
    val foodEntries = remember { mutableStateListOf<DietFoodEntry>() }
    val favoriteFoods = remember { mutableStateListOf<FavoriteFood>() }
    val weightEntries = remember { mutableStateListOf<WeightEntry>() }

    var nextFoodId by remember { mutableIntStateOf(1) }
    var nextWeightId by remember { mutableIntStateOf(1) }

    var selectedTab by remember { mutableStateOf("Diet") }
    var showFoodForm by remember { mutableStateOf(false) }
    var showWeightForm by remember { mutableStateOf(false) }

    var editingFood by remember { mutableStateOf<DietFoodEntry?>(null) }
    var editingWeight by remember { mutableStateOf<WeightEntry?>(null) }

    var selectedFoodId by remember { mutableIntStateOf(-1) }
    var selectedWeightId by remember { mutableIntStateOf(-1) }

    var selectedFavorite by remember { mutableStateOf<FavoriteFood?>(null) }

    if (showFoodForm) {
        DietFoodForm(
            existingFood = editingFood,
            favoriteFood = selectedFavorite,
            onSave = { name, calories, protein, sugar, date, time, saveFavorite ->
                val food = DietFoodEntry(
                    id = editingFood?.id ?: nextFoodId++,
                    name = name,
                    calories = calories,
                    protein = protein,
                    sugar = sugar,
                    date = date,
                    time = time
                )

                if (editingFood != null) {
                    val index = foodEntries.indexOfFirst { it.id == editingFood!!.id }
                    if (index >= 0) foodEntries[index] = food
                } else {
                    foodEntries.add(food)
                }

                if (saveFavorite) {
                    val favorite = FavoriteFood(name, calories, protein, sugar)
                    val oldIndex = favoriteFoods.indexOfFirst {
                        it.name.equals(name, ignoreCase = true)
                    }

                    if (oldIndex >= 0) favoriteFoods[oldIndex] = favorite
                    else favoriteFoods.add(favorite)
                }

                showFoodForm = false
                editingFood = null
                selectedFavorite = null
                selectedFoodId = -1
            },
            onCancel = {
                showFoodForm = false
                editingFood = null
                selectedFavorite = null
            },
            onExit = onExit
        )
        return
    }

    if (showWeightForm) {
        WeightForm(
            existingWeight = editingWeight,
            onSave = { weight, unit, date ->
                val entry = WeightEntry(
                    id = editingWeight?.id ?: nextWeightId++,
                    weight = weight,
                    unit = unit,
                    date = date
                )

                if (editingWeight != null) {
                    val index = weightEntries.indexOfFirst {
                        it.id == editingWeight!!.id
                    }
                    if (index >= 0) weightEntries[index] = entry
                } else {
                    weightEntries.add(entry)
                }

                showWeightForm = false
                editingWeight = null
                selectedWeightId = -1
            },
            onCancel = {
                showWeightForm = false
                editingWeight = null
            },
            onExit = onExit
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Diet and Weight",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { selectedTab = "Diet" },
                modifier = Modifier.weight(1f)
            ) {
                Text("Diet")
            }

            OutlinedButton(
                onClick = { selectedTab = "Weight" },
                modifier = Modifier.weight(1f)
            ) {
                Text("Weight")
            }
        }

        HorizontalDivider()

        if (selectedTab == "Diet") {
            Text(
                "Food Entries",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Text("${foodEntries.size} food entries recorded")

            if (foodEntries.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "No food entries yet. Add your first meal!",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            foodEntries.sortedWith(
                compareByDescending<DietFoodEntry> { it.date }
                    .thenByDescending { it.time }
            ).forEach { food ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedFoodId = food.id },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedFoodId == food.id)
                            Color(0xFFBBDEFB) else Color(0xFFE5E7EB)
                    )
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(food.name, fontWeight = FontWeight.Bold)
                        Text("Calories: ${food.calories} kcal")
                        Text("Protein: ${food.protein} g")
                        Text("Sugar: ${food.sugar} g")
                        Text("Date: ${food.date}")
                        Text("Time: ${food.time}")
                    }
                }
            }

            Button(
                onClick = {
                    editingFood = null
                    selectedFavorite = null
                    showFoodForm = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add Food")
            }

            OutlinedButton(
                onClick = {
                    editingFood = foodEntries.find { it.id == selectedFoodId }
                    if (editingFood != null) {
                        selectedFavorite = null
                        showFoodForm = true
                    }
                },
                enabled = selectedFoodId != -1,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Edit Entry")
            }

            OutlinedButton(
                onClick = {
                    foodEntries.removeAll { it.id == selectedFoodId }
                    selectedFoodId = -1
                },
                enabled = selectedFoodId != -1,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Remove Food / Delete Entry")
            }

            HorizontalDivider()

            Text(
                "Favorite Foods",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Text("Select a favorite to reuse its nutrition information.")

            if (favoriteFoods.isEmpty()) {
                Text("No favorite foods saved yet.")
            }

            favoriteFoods.forEach { favorite ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            editingFood = null
                            selectedFavorite = favorite
                            showFoodForm = true
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFF3CD)
                    )
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(favorite.name, fontWeight = FontWeight.Bold)
                        Text("${favorite.calories} kcal")
                        Text("Protein: ${favorite.protein} g")
                        Text("Sugar: ${favorite.sugar} g")
                        Text("Tap to reuse this food")
                    }
                }
            }

            OutlinedButton(
                onClick = {
                    val selectedFood = foodEntries.find {
                        it.id == selectedFoodId
                    }
                    val favorite = favoriteFoods.find {
                        it.name.equals(selectedFood?.name, ignoreCase = true)
                    }
                    if (favorite != null) favoriteFoods.remove(favorite)
                },
                enabled = selectedFoodId != -1,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Remove Selected Food from Favorites")
            }
        }

        if (selectedTab == "Weight") {
            Text(
                "Weight Tracking",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            if (weightEntries.isEmpty()) {
                Text("You have not recorded your weight yet.")

                Button(
                    onClick = {
                        editingWeight = null
                        showWeightForm = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Record First Weight")
                }
            } else {
                val latestWeight = weightEntries.maxByOrNull { it.date }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFE5E7EB)
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Latest Recorded Weight", fontWeight = FontWeight.Bold)

                        if (latestWeight != null) {
                            Text(
                                "${latestWeight.weight} ${latestWeight.unit}",
                                style = MaterialTheme.typography.headlineMedium
                            )
                            Text("Recorded: ${latestWeight.date}")
                        }
                    }
                }

                Button(
                    onClick = {
                        editingWeight = null
                        showWeightForm = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Update Weight")
                }

                HorizontalDivider()

                Text(
                    "Weight History",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                weightEntries.sortedByDescending { it.date }.forEach { entry ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedWeightId = entry.id },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedWeightId == entry.id)
                                Color(0xFFBBDEFB) else Color(0xFFE5E7EB)
                        )
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                "${entry.weight} ${entry.unit}",
                                fontWeight = FontWeight.Bold
                            )
                            Text("Date: ${entry.date}")
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
                        editingWeight = weightEntries.find {
                            it.id == selectedWeightId
                        }
                        if (editingWeight != null) showWeightForm = true
                    },
                    enabled = selectedWeightId != -1,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Edit Selected Weight")
                }

                OutlinedButton(
                    onClick = {
                        weightEntries.removeAll { it.id == selectedWeightId }
                        selectedWeightId = -1
                    },
                    enabled = selectedWeightId != -1,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Delete Selected Weight")
                }
            }
        }

        HorizontalDivider()

        OutlinedButton(
            onClick = onExit,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Exit to Home")
        }
    }
}
@Composable
private fun DietFoodForm(
    existingFood: DietFoodEntry?,
    favoriteFood: FavoriteFood?,
    onSave: (String, Int, Int, Int, String, String, Boolean) -> Unit,
    onCancel: () -> Unit,
    onExit: () -> Unit
) {
    var name by remember(existingFood, favoriteFood) {
        mutableStateOf(existingFood?.name ?: favoriteFood?.name ?: "")
    }

    var calories by remember(existingFood, favoriteFood) {
        mutableStateOf(
            (existingFood?.calories ?: favoriteFood?.calories)?.toString() ?: ""
        )
    }

    var protein by remember(existingFood, favoriteFood) {
        mutableStateOf(
            (existingFood?.protein ?: favoriteFood?.protein)?.toString() ?: ""
        )
    }

    var sugar by remember(existingFood, favoriteFood) {
        mutableStateOf(
            (existingFood?.sugar ?: favoriteFood?.sugar)?.toString() ?: ""
        )
    }

    var date by remember(existingFood) {
        mutableStateOf(existingFood?.date ?: LocalDate.now().toString())
    }

    var time by remember(existingFood) {
        mutableStateOf(
            existingFood?.time ?: LocalTime.now().format(
                DateTimeFormatter.ofPattern("HH:mm")
            )
        )
    }

    var saveFavorite by remember(favoriteFood, existingFood) {
        mutableStateOf(favoriteFood != null)
    }

    val valid =
        name.isNotBlank() &&
                (calories.toIntOrNull() ?: -1) >= 0 &&
                (protein.toIntOrNull() ?: -1) >= 0 &&
                (sugar.toIntOrNull() ?: -1) >= 0 &&
                runCatching { LocalDate.parse(date) }.isSuccess &&
                runCatching { LocalTime.parse(time) }.isSuccess

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = if (existingFood == null)
                "Add Food"
            else
                "Edit Food Entry",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Food Name *") },
            placeholder = { Text("Enter food name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = calories,
            onValueChange = {
                if (it.all(Char::isDigit)) calories = it
            },
            label = { Text("Calories (kcal) *") },
            placeholder = { Text("Whole number only") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = protein,
            onValueChange = {
                if (it.all(Char::isDigit)) protein = it
            },
            label = { Text("Protein (grams) *") },
            placeholder = { Text("Whole number only") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = sugar,
            onValueChange = {
                if (it.all(Char::isDigit)) sugar = it
            },
            label = { Text("Sugar (grams) *") },
            placeholder = { Text("Whole number only") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        HorizontalDivider()

        Text("Date of Food Entry", fontWeight = FontWeight.Bold)

        OutlinedTextField(
            value = date,
            onValueChange = { date = it },
            label = { Text("Date (YYYY-MM-DD) *") },
            placeholder = { Text("2026-11-19") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Text("Time of Food Entry", fontWeight = FontWeight.Bold)

        OutlinedTextField(
            value = time,
            onValueChange = { time = it },
            label = { Text("Time (HH:mm) *") },
            placeholder = { Text("08:30") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Text(
            "You can enter an earlier date or time if you are recording a meal after eating.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider()

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = saveFavorite,
                onCheckedChange = { saveFavorite = it }
            )

            Column {
                Text(
                    "Save as Favorite Food",
                    fontWeight = FontWeight.Bold
                )

                Text(
                    "Reuse this food and its nutrition information later.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Button(
            onClick = {
                if (valid) {
                    onSave(
                        name.trim(),
                        calories.toInt(),
                        protein.toInt(),
                        sugar.toInt(),
                        date,
                        time,
                        saveFavorite
                    )
                }
            },
            enabled = valid,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Food Entry")
        }

        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cancel")
        }

        TextButton(
            onClick = onExit,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Exit to Home")
        }
    }
}

@Composable
private fun WeightForm(
    existingWeight: WeightEntry?,
    onSave: (Double, String, String) -> Unit,
    onCancel: () -> Unit,
    onExit: () -> Unit
) {
    var weight by remember(existingWeight) {
        mutableStateOf(existingWeight?.weight?.toString() ?: "")
    }

    var unit by remember(existingWeight) {
        mutableStateOf(existingWeight?.unit ?: "lb")
    }

    var date by remember(existingWeight) {
        mutableStateOf(
            existingWeight?.date ?: LocalDate.now().toString()
        )
    }

    var unitExpanded by remember {
        mutableStateOf(false)
    }

    val valid =
        weight.toDoubleOrNull()?.let { it > 0 } == true &&
                runCatching { LocalDate.parse(date) }.isSuccess

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = if (existingWeight == null)
                "Record Weight"
            else
                "Update Weight",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        OutlinedTextField(
            value = weight,
            onValueChange = { weight = it },
            label = { Text("Weight *") },
            placeholder = { Text("Enter your weight") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Text("Weight Unit", fontWeight = FontWeight.Bold)

        Box {
            OutlinedButton(
                onClick = { unitExpanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(unit)
            }

            DropdownMenu(
                expanded = unitExpanded,
                onDismissRequest = { unitExpanded = false }
            ) {
                listOf("lb", "kg").forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            unit = option
                            unitExpanded = false
                        }
                    )
                }
            }
        }

        Text("Date of Measurement", fontWeight = FontWeight.Bold)

        OutlinedTextField(
            value = date,
            onValueChange = { date = it },
            label = { Text("Date (YYYY-MM-DD) *") },
            placeholder = { Text("2026-11-19") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Text(
            "Enter the date when you actually measured your weight.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                if (valid) {
                    onSave(
                        weight.toDouble(),
                        unit,
                        date
                    )
                }
            },
            enabled = valid,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (existingWeight == null)
                    "Save Weight"
                else
                    "Update Weight"
            )
        }

        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cancel")
        }

        TextButton(
            onClick = onExit,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Exit to Home")
        }
    }
}