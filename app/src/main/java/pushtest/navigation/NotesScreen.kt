package pushtest.navigation

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun NotesScreen(
    onExit: () -> Unit
) {
    val notes = remember {
        mutableStateListOf<HealthNote>()
    }

    var nextId by remember {
        mutableIntStateOf(1)
    }

    var showForm by remember {
        mutableStateOf(false)
    }

    var editingNote by remember {
        mutableStateOf<HealthNote?>(null)
    }

    var selectedFilter by remember {
        mutableStateOf("All Categories")
    }

    var filterExpanded by remember {
        mutableStateOf(false)
    }

    val dateFormatter = DateTimeFormatter.ofPattern(
        "MM/dd/yyyy 'at' hh:mm a"
    )

    val sortedNotes = notes.sortedByDescending {
        it.updatedAt
    }

    if (showForm) {
        HealthNoteForm(
            existingNote = editingNote,
            onSave = { title, category, body ->

                val currentTime = LocalDateTime.now()
                val old = editingNote

                if (old == null) {
                    notes.add(
                        HealthNote(
                            id = nextId,
                            title = title,
                            category = category,
                            body = body,
                            createdAt = currentTime,
                            updatedAt = currentTime
                        )
                    )

                    nextId++
                } else {
                    val index = notes.indexOfFirst {
                        it.id == old.id
                    }

                    if (index >= 0) {
                        notes[index] = old.copy(
                            title = title,
                            category = category,
                            body = body,
                            updatedAt = currentTime
                        )
                    }
                }

                showForm = false
                editingNote = null
            },
            onCancel = {
                showForm = false
                editingNote = null
            },
            onExit = onExit
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "Notes",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "${notes.size} saved note(s)",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Filter Notes",
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedButton(
                        onClick = {
                            filterExpanded = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(selectedFilter)
                    }

                    DropdownMenu(
                        expanded = filterExpanded,
                        onDismissRequest = {
                            filterExpanded = false
                        }
                    ) {
                        DropdownMenuItem(
                            text = { Text("All Categories") },
                            onClick = {
                                selectedFilter = "All Categories"
                                filterExpanded = false
                            }
                        )

                        noteCategories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category) },
                                onClick = {
                                    selectedFilter = category
                                    filterExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
                        selectedFilter = "All Categories"
                    }
                ) {
                    Text("All")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                val categoriesToDisplay =
                    if (selectedFilter == "All Categories") {
                        noteCategories
                    } else {
                        listOf(selectedFilter)
                    }

                categoriesToDisplay.forEach { category ->

                    val categoryNotes = sortedNotes.filter {
                        it.category == category
                    }

                    item(key = "heading_$category") {
                        Text(
                            text = category,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (categoryNotes.isEmpty()) {
                        item(key = "empty_$category") {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFE5E7EB)
                                )
                            ) {
                                Text(
                                    text = "No notes in this category.",
                                    modifier = Modifier.padding(18.dp)
                                )
                            }
                        }
                    } else {
                        items(
                            items = categoryNotes,
                            key = { it.id }
                        ) { note ->
                            NoteCard(
                                note = note,
                                onClick = {
                                    editingNote = note
                                    showForm = true
                                },
                                onDelete = {
                                    notes.remove(note)
                                }
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    editingNote = null
                    showForm = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add Note")
            }

            TextButton(
                onClick = onExit,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Exit to Home")
            }
        }
    }
}

@Composable
private fun NoteCard(
    note: HealthNote,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val cardColor = when (note.category) {
        "Doctor Appointment" -> Color(0xFFFFF59D)
        "Medication" -> Color(0xFFA5D6A7)
        "Diet" -> Color(0xFFFFCC80)
        "Vital Signs" -> Color(0xFF90CAF9)
        "General" -> Color(0xFFCE93D8)
        else -> Color.White
    }

    val dateFormatter = DateTimeFormatter.ofPattern(
        "MM/dd/yyyy 'at' hh:mm a"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = cardColor
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = note.title,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = note.body,
                    color = Color.DarkGray,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Last edited: ${
                        note.updatedAt.format(dateFormatter)
                    }",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.DarkGray
                )
            }

            TextButton(
                onClick = onDelete
            ) {
                Text(
                    "Delete",
                    color = Color(0xFFB71C1C)
                )
            }
        }
    }
}

@Composable
private fun HealthNoteForm(
    existingNote: HealthNote?,
    onSave: (String, String, String) -> Unit,
    onCancel: () -> Unit,
    onExit: () -> Unit
) {
    var title by remember(existingNote) {
        mutableStateOf(existingNote?.title ?: "")
    }

    var category by remember(existingNote) {
        mutableStateOf(existingNote?.category ?: "Select")
    }

    var body by remember(existingNote) {
        mutableStateOf(existingNote?.body ?: "")
    }

    var expanded by remember {
        mutableStateOf(false)
    }

    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("MM/dd/yyyy 'at' hh:mm a")
    }

    val valid =
        title.isNotBlank() &&
                category in noteCategories

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            if (existingNote == null) "Create Note"
            else "Edit Note",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title *") },
            placeholder = { Text("Enter note title") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Text(
            "Category *",
            fontWeight = FontWeight.Bold
        )

        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(category)
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Select") },
                    onClick = {
                        category = "Select"
                        expanded = false
                    }
                )

                noteCategories.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            category = option
                            expanded = false
                        }
                    )
                }
            }
        }

        Text(
            "Note Body",
            fontWeight = FontWeight.Bold
        )

        OutlinedTextField(
            value = body,
            onValueChange = {
                if (it.length <= 1000) {
                    body = it
                }
            },
            label = { Text("Enter your notes") },
            placeholder = {
                Text("Type your health notes here...")
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            minLines = 6,
            maxLines = 8
        )

        Text(
            "${body.length}/1000 characters",
            color = if (body.length >= 1000)
                Color.Red
            else
                MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )

        HorizontalDivider()

        Text(
            "Linked Item",
            fontWeight = FontWeight.Bold
        )

        OutlinedTextField(
            value = "Maintenance",
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (existingNote != null) {
            HorizontalDivider()

            Text(
                "Created: ${
                    existingNote.createdAt.format(dateFormatter)
                }",
                style = MaterialTheme.typography.bodySmall
            )

            Text(
                "Last edited: ${
                    existingNote.updatedAt.format(dateFormatter)
                }",
                style = MaterialTheme.typography.bodySmall
            )
        }

        Button(
            onClick = {
                if (valid) {
                    onSave(
                        title.trim(),
                        category,
                        body.trim()
                    )
                }
            },
            enabled = valid,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Note")
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