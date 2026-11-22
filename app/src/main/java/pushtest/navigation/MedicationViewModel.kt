package pushtest.navigation

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel

class MedicationViewModel : ViewModel() {

    val medications = mutableStateListOf<Medication>()

    fun addMedication(medication: Medication) {
        medications.add(medication)
    }

    fun updateMedication(index: Int, medication: Medication) {
        if (index in medications.indices) {
            medications[index] = medication
        }
    }

    fun deleteMedication(index: Int) {
        if (index in medications.indices) {
            medications.removeAt(index)
        }
    }
}