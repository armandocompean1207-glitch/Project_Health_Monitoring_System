package com.cse3310.projecthealthmonitoringsystem.validation

import com.cse3310.projecthealthmonitoringsystem.data.entity.Pharmacy

object ProviderPharmacyValidation {
    fun optionalText(value: String, label: String, max: Int = 300): String? =
        if (value.length > max || value.any(Char::isISOControl)) "Enter a readable $label of no more than $max characters." else null

    fun pharmacy(p: Pharmacy): String? = HealthValidation.name(p.name, "Pharmacy name")
        ?: HealthValidation.phone(p.phone)
        ?: optionalText(p.street, "street address")
        ?: optionalText(p.city, "city")
        ?: optionalText(p.pharmacistName, "pharmacist name", 160)
        ?: if (p.state.isNotBlank() && !p.state.trim().matches(Regex("[A-Za-z]{2}"))) "Use the two-letter state abbreviation."
        else if (p.zip.isNotBlank() && !p.zip.trim().matches(Regex("[0-9]{5}(-[0-9]{4})?"))) "Enter a five-digit ZIP code or ZIP+4."
        else null

    fun prescription(rx: String?, refills: Int?, date: Long?): String? =
        optionalText(rx.orEmpty(), "prescription number", 100)
            ?: if (refills != null && refills < 0) "Refills remaining must be zero or more."
            else if (date != null && date !in 1..4102444799999L) "Choose a valid next refill date."
            else null

    fun refillInput(text: String): String? =
        if (text.isNotBlank() && (text.toIntOrNull() == null || text.toInt() < 0)) "Enter a whole number of refills, zero or more." else null
}
