package com.cse3310.projecthealthmonitoringsystem.notes

/** Category tags keep notes portable through the existing body field without changing Room's schema. */
enum class NoteCategory(val label: String) {
    RECIPES("Recipes"), HEALTH("Health"), GENERAL("General")
}

fun noteCategory(body: String): NoteCategory =
    NoteCategory.entries.firstOrNull { body.startsWith("[PHMS:${it.name}]\n") } ?: NoteCategory.GENERAL

fun noteContent(body: String): String {
    val category = NoteCategory.entries.firstOrNull { body.startsWith("[PHMS:${it.name}]\n") }
    return if (category == null) body else body.removePrefix("[PHMS:${category.name}]\n")
}

fun categorizedContent(category: NoteCategory, content: String): String = "[PHMS:${category.name}]\n$content"
