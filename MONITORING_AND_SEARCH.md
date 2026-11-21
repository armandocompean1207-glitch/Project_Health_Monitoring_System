# Medication, Monitoring and Search – Diyanshu

Screens and alarms on top of Armando's Room data layer (data/repository).

- **Medication** (`medication/`): add, edit, delete medications (name, dosage, intake time, times per day, prescribing doctor), today's doses with Mark taken, change history (AuditLog), conflict check before saving.
- **Monitoring** (`monitoring/`): reminder alarm at dose time creates the DoseLog row; missed-dose check after 30 minutes uses `MonitoringRepository.checkMissedDose`; alerts screen with View details, Acknowledge, Exit; alarms are set again after a restart. `AlertHandoff` notifies the user, texts each designee and writes the CommunicationLog.
- **Search** (`search/`): keyword search by data type with a date range, quick searches (last/next doctor visit, past medications, next annual checkup) and search history.

Uses `SeedData.DEBUG_USER_ID` through `CurrentUser.userId` until Login sets the real user.
The designee form on the Monitoring screen uses `CommunicationRepository`, so it can move to the Communication screen later.

Note: written with help from an AI assistant (Claude) and reviewed by me. It builds and the unit tests pass; it still needs a full run on the emulator.
