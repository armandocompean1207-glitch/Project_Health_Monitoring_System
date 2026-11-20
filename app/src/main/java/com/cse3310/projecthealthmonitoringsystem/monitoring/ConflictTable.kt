package com.cse3310.projecthealthmonitoringsystem.monitoring

object ConflictTable {

    val pairs: List<ConflictWarning> = listOf(
        ConflictWarning("warfarin", "aspirin", "Higher risk of serious bleeding"),
        ConflictWarning("warfarin", "ibuprofen", "Higher risk of serious bleeding"),
        ConflictWarning("warfarin", "naproxen", "Higher risk of serious bleeding"),
        ConflictWarning("clopidogrel", "omeprazole", "Omeprazole can make clopidogrel less effective"),
        ConflictWarning("sildenafil", "nitroglycerin", "Dangerous drop in blood pressure"),
        ConflictWarning("tadalafil", "nitroglycerin", "Dangerous drop in blood pressure"),
        ConflictWarning("simvastatin", "clarithromycin", "Raises statin levels; risk of muscle damage"),
        ConflictWarning("simvastatin", "amiodarone", "Raises statin levels; risk of muscle damage"),
        ConflictWarning("simvastatin", "ketoconazole", "Raises statin levels; risk of muscle damage"),
        ConflictWarning("digoxin", "amiodarone", "Raises digoxin levels; risk of toxicity"),
        ConflictWarning("lisinopril", "spironolactone", "Risk of high potassium"),
        ConflictWarning("lisinopril", "potassium chloride", "Risk of high potassium"),
        ConflictWarning("lithium", "ibuprofen", "Raises lithium levels; risk of toxicity"),
        ConflictWarning("methotrexate", "trimethoprim", "Raises methotrexate toxicity"),
        ConflictWarning("theophylline", "ciprofloxacin", "Raises theophylline levels"),
        ConflictWarning("tizanidine", "ciprofloxacin", "Large rise in tizanidine; low blood pressure"),
        ConflictWarning("sertraline", "tramadol", "Risk of serotonin syndrome"),
        ConflictWarning("fluoxetine", "tramadol", "Risk of serotonin syndrome"),
        ConflictWarning("oxycodone", "alprazolam", "Opioid plus benzodiazepine: breathing can slow dangerously"),
        ConflictWarning("allopurinol", "azathioprine", "Raises azathioprine toxicity"),
        ConflictWarning("levothyroxine", "calcium carbonate", "Calcium blocks levothyroxine absorption")
    )

    private fun normalize(name: String) = name.trim().lowercase()

    private fun matches(input: String, drug: String) = normalize(input).contains(drug)

    fun findConflicts(newMedication: String, currentMedications: List<String>): List<ConflictWarning> {
        if (newMedication.isBlank()) return emptyList()
        val others = currentMedications
            .filter { normalize(it) != normalize(newMedication) }
            .distinctBy { normalize(it) }

        return others.flatMap { other ->
            pairs.filter { pair ->
                (matches(newMedication, pair.drugA) && matches(other, pair.drugB)) ||
                    (matches(newMedication, pair.drugB) && matches(other, pair.drugA))
            }
        }.distinct()
    }
}
