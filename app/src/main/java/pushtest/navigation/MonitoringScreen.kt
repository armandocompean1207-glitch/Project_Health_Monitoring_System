package pushtest.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.Duration

@Composable
fun MonitoringScreen(
    medications: List<Medication>,
    onBack: () -> Unit
) {
    val today = LocalDate.now()
    val now = LocalDateTime.now()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Monitoring",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            "Medication Schedule",
            style = MaterialTheme.typography.titleLarge
        )

        if (medications.isEmpty()) {
            Text("No medications added yet.")
        }

        medications.forEach { medication ->

            val firstDose = LocalDateTime.of(
                medication.startDate,
                medication.firstDoseTime
            )

            val frequency = medication.frequency.toLongOrNull() ?: 0L

            val intervalMinutes = when (medication.frequencyUnit) {
                "Hours" -> frequency * 60
                "Days" -> frequency * 1440
                "Weeks" -> frequency * 10080
                else -> 0L
            }

            val nextDose = if (
                intervalMinutes > 0 && now.isAfter(firstDose)
            ) {
                val elapsed = Duration.between(
                    firstDose,
                    now
                ).toMinutes()

                val intervals = elapsed / intervalMinutes + 1

                firstDose.plusMinutes(intervals * intervalMinutes)
            } else {
                firstDose
            }

            val status = when {
                medication.endDate != null &&
                        today.isAfter(medication.endDate) ->
                    "Medication Ended"

                now.isAfter(firstDose) &&
                        intervalMinutes == 0L ->
                    "First Dose Passed"

                nextDose.toLocalDate().isAfter(today) ->
                    "Upcoming"

                else -> "Due"
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFE5E7EB)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        medication.name,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        "Dosage: ${medication.dosage} ${medication.dosageUnit}"
                    )

                    Text(
                        "First Dose: ${
                            firstDose.format(
                                DateTimeFormatter.ofPattern(
                                    "MM/dd/yyyy hh:mm a"
                                )
                            )
                        }"
                    )

                    Text(
                        "Next Dose: ${
                            nextDose.format(
                                DateTimeFormatter.ofPattern(
                                    "MM/dd/yyyy hh:mm a"
                                )
                            )
                        }"
                    )

                    Text(
                        "Status: $status",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back")
        }
    }
}