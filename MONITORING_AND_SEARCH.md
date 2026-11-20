# Monitoring and Search – Diyanshu

Monitoring: designees, dose reminder + missed-dose alarm, SMS alert to designees, medication conflict check, alert history.
Search: searches medications, alerts and contacts. Other modules can add their data with `SearchRegistry.register(...)`.

Medication module (Nathan) can hook in with:
- `MonitoringRepository.scheduleDose(context, name, timeMillis, graceMinutes)`
- `MonitoringRepository.markTaken(context, doseId)`
- `MonitoringRepository.externalMedicationNames = { ... }`

Data is saved with SharedPreferences for now; can move to the Room database later.

Note: written with help from an AI assistant (Claude), reviewed and tested by me.
