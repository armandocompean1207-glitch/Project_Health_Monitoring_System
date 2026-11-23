package com.cse3310.projecthealthmonitoringsystem.notes

import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsTextField as OutlinedTextField

import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsTextButton as TextButton
import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsOutlinedButton as OutlinedButton
import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsCard as OutlinedCard
import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsCard as Card

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cse3310.projecthealthmonitoringsystem.data.entity.Note
import com.cse3310.projecthealthmonitoringsystem.monitoring.CurrentUser
import com.cse3310.projecthealthmonitoringsystem.monitoring.phms
import com.cse3310.projecthealthmonitoringsystem.ui.components.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun dateLabel(time: Long) = SimpleDateFormat("MM/dd/yyyy · h:mm a", Locale.US).format(Date(time))
private fun categoryIcon(category: NoteCategory) = when (category) {
    NoteCategory.RECIPES -> PhmsIcons.Diet
    NoteCategory.HEALTH -> PhmsIcons.Vitals
    NoteCategory.GENERAL -> PhmsIcons.Notes
}

@Composable
fun NotesScreen(onBack: () -> Unit) {
    val repository = LocalContext.current.phms().notesRepository
    val userId = CurrentUser.userId
    val notes by remember(repository, userId) { repository.observeNotes(userId) }.collectAsState(initial = null as List<Note>?)
    var query by rememberSaveable(userId) { mutableStateOf("") }
    var filter by rememberSaveable(userId) { mutableStateOf<String?>(null) }
    var selectedId by rememberSaveable(userId) { mutableStateOf<Long?>(null) }
    var editing by rememberSaveable(userId) { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val selected = notes?.firstOrNull { it.noteId == selectedId }
    val scope = rememberCoroutineScope()

    fun save(title: String, body: String, category: NoteCategory) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                val encoded = categorizedContent(category, body)
                val id = selectedId
                if (id == null) selectedId = repository.createNote(userId, title, encoded)
                else check(repository.editNote(userId, id, title, encoded)) { "This note is no longer available." }
                editing = false
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { error = e.message ?: "Couldn't save your note. Please try again."
            } finally { busy = false }
        }
    }
    BackHandler(enabled = !editing && selectedId != null) { selectedId = null; error = null }

    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(
        primaryContainer = androidx.compose.ui.graphics.Color(0xFFFFDF48),
        onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF111827))) {
    if (editing) {
        key(userId, selectedId) {
            NoteEditor(selected, busy, error, onCancel = { editing = false; error = null }, onSave = ::save)
        }
    } else if (selectedId != null) {
        PhmsPage {
            PhmsScreenHeader("Your note", icon = PhmsIcons.Notes, onBack = { selectedId = null; error = null })
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (selected == null) {
                if (notes == null) CircularProgressIndicator()
                else PhmsEmptyStateCard("Note unavailable", "This note may have been removed.",
                    PhmsIcons.Notes, actionLabel = "Back to notes", onAction = { selectedId = null })
            } else {
                PhmsIconBadge(categoryIcon(noteCategory(selected.body)))
                Text(selected.title, style = MaterialTheme.typography.headlineLarge)
                NoteDates(selected)
                Text(noteCategory(selected.body).label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(noteContent(selected.body).ifBlank { "No content added." }, style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(enabled = !busy, onClick = { editing = true }) { Text("Edit note") }
                    OutlinedButton(enabled = !busy, onClick = { confirmDelete = true }) { Text("Delete note") }
                }
            }
        }
    } else {
        val visible = notes.orEmpty().filter {
            (filter == null || noteCategory(it.body).name == filter) &&
                (it.title.contains(query.trim(), true) || noteContent(it.body).contains(query.trim(), true))
        }
        Column(Modifier.fillMaxSize().imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PhmsScreenHeader("Notes", "Keep questions, recipes and reminders close.", PhmsIcons.Notes, onBack)
            OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Search notes") }, leadingIcon = { Icon(PhmsIcons.Search, null) })
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("All") })
                NoteCategory.entries.forEach { category ->
                    FilterChip(selected = filter == category.name, onClick = { filter = category.name },
                        label = { Text(category.label) }, leadingIcon = { NoteCategoryIcon(category) })
                }
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (notes == null) item { CircularProgressIndicator() }
                else if (visible.isEmpty()) item {
                    if (query.isNotBlank() || filter != null) {
                        PhmsEmptyStateCard("No matching notes", "Try another search or category.",
                            PhmsIcons.Search, actionLabel = "Clear filters", onAction = { query = ""; filter = null })
                    } else {
                        PhmsEmptyStateCard("A place for your thoughts", "Save a recipe, a health question, or a reminder for later.",
                            PhmsIcons.Notes)
                    }
                }
                items(visible, key = { it.noteId }) { note ->
                    OutlinedCard(onClick = { selectedId = note.noteId; error = null }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            PhmsIconBadge(categoryIcon(noteCategory(note.body)))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(note.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("${noteCategory(note.body).label} · ${dateLabel(note.editedAt)}", style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (noteContent(note.body).isNotBlank()) Text(noteContent(note.body), style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(PhmsIcons.Next, null)
                        }
                    }
                }
            }
            Button(onClick = { selectedId = null; editing = true; error = null }, modifier = Modifier.fillMaxWidth()) { Text("+ New note") }
        }
    }

    if (confirmDelete && selected != null) {
        AlertDialog(onDismissRequest = { if (!busy) confirmDelete = false },
            title = { Text("Delete this note?") }, text = { Text("“${selected.title}” will be permanently deleted.") },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            check(repository.deleteNote(userId, selected.noteId)) { "This note is no longer available." }
                            confirmDelete = false
                            selectedId = null
                            error = null
                        } catch (e: CancellationException) { throw e
                        } catch (e: Exception) {
                            confirmDelete = false
                            error = e.message ?: "Couldn't delete the note."
                        } finally { busy = false }
                    }
                }) { Text(if (busy) "Deleting…" else "Delete") }
            }, dismissButton = { TextButton(enabled = !busy, onClick = { confirmDelete = false }) { Text("Cancel") } })
    }
    }
}

@Composable
private fun NoteCategoryIcon(category: NoteCategory) {
    androidx.compose.material3.Surface(shape = androidx.compose.foundation.shape.CircleShape,
        color = androidx.compose.ui.graphics.Color(0xFFFFDF48)) {
        Icon(categoryIcon(category), null, Modifier.padding(4.dp).size(20.dp),
            tint = androidx.compose.ui.graphics.Color(0xFF111827))
    }
}

@Composable
private fun NoteDates(note: Note) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Created ${dateLabel(note.createdAt)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Updated ${dateLabel(note.editedAt)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NoteEditor(note: Note?, busy: Boolean, error: String?, onCancel: () -> Unit,
    onSave: (String, String, NoteCategory) -> Unit) {
    var title by rememberSaveable { mutableStateOf(note?.title ?: "") }
    var body by rememberSaveable { mutableStateOf(note?.let { noteContent(it.body) } ?: "") }
    var categoryName by rememberSaveable { mutableStateOf(note?.let { noteCategory(it.body).name } ?: NoteCategory.GENERAL.name) }
    var discard by remember { mutableStateOf(false) }
    val category = NoteCategory.valueOf(categoryName)
    val changed = title != (note?.title ?: "") || body != (note?.let { noteContent(it.body) } ?: "") ||
        category != (note?.let { noteCategory(it.body) } ?: NoteCategory.GENERAL)
    val guardedDismiss = rememberGuardedDismiss(changed, busy, onCancel)
    fun leave() { guardedDismiss() }
    BackHandler { leave() }
    Column(Modifier.fillMaxSize().imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PhmsScreenHeader(if (note == null) "New note" else "Edit note", icon = PhmsIcons.Notes, onBack = ::leave)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            OutlinedTextField(title, { title = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Title") },
                isError = title.isBlank(), supportingText = { if (title.isBlank()) Text("A title is required.") }, enabled = !busy)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NoteCategory.entries.forEach {
                    FilterChip(selected = category == it, onClick = { categoryName = it.name }, enabled = !busy,
                        label = { Text(it.label) }, leadingIcon = { NoteCategoryIcon(it) })
                }
            }
            OutlinedTextField(body, { body = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp),
                label = { Text("Content") }, enabled = !busy)
            note?.let { NoteDates(it) }
        }
        Button(enabled = !busy && title.isNotBlank(), onClick = { onSave(title.trim(), body, category) }, modifier = Modifier.fillMaxWidth()) {
            Text(if (busy) "Saving…" else "Save note")
        }
    }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("Discard changes?") },
        text = { Text("Your unsaved changes will be lost.") },
        confirmButton = { TextButton(onClick = onCancel) { Text("Discard") } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep editing") } })
}
