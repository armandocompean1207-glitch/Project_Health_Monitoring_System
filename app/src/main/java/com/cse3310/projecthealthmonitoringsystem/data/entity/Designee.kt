package com.cse3310.projecthealthmonitoringsystem.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Trusted contact notified about missed doses and conflicts. */
@Entity(
    tableName = "Designee",
    foreignKeys = [
        ForeignKey(entity = User::class, parentColumns = ["userId"], childColumns = ["userId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("userId")]
)
data class Designee(
    @PrimaryKey(autoGenerate = true) val designeeId: Long = 0,
    val userId: String,
    val name: String,
    val relationship: String = "",
    val phone: String = "",
    val email: String = "",
    val preferredMethod: ContactMethod,
    /** Null preserves legacy contact methods and consent until this contact is edited. */
    val contactMethodsMask: Int? = null,
    val automaticSmsConsent: Boolean? = null
) {
    fun selectedMethods(): Set<ContactMethod> = contactMethodsMask?.let { mask ->
        ContactMethod.entries.filter { mask and methodBit(it) != 0 }.toSet()
    } ?: buildSet {
        add(preferredMethod)
        // Legacy contacts offered all available manual methods; keep those available.
        if (phone.isNotBlank()) { add(ContactMethod.SMS); add(ContactMethod.CALL) }
        if (email.isNotBlank()) add(ContactMethod.EMAIL)
    }

    fun allowsAutomaticSms(): Boolean = ContactMethod.SMS in selectedMethods() &&
        (automaticSmsConsent ?: (preferredMethod == ContactMethod.SMS))

    companion object {
        private fun methodBit(method: ContactMethod) = when (method) {
            ContactMethod.SMS -> 1
            ContactMethod.EMAIL -> 2
            ContactMethod.CALL -> 4
        }
        fun methodsMask(methods: Set<ContactMethod>): Int = methods.fold(0) { mask, method -> mask or methodBit(method) }
    }
}
