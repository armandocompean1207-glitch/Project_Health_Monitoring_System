# Medication, Monitoring, Search, Communication and Data Maintenance – Diyanshu

Screens and alarms on top of Armando's Room data layer (data/repository).

- **Medication** (`medication/`): add, edit, delete medications (name, dosage, intake time, times per day, prescribing doctor), today's doses with Mark taken, change history (AuditLog), conflict check before saving.
- **Monitoring** (`monitoring/`): reminder alarm at dose time creates the DoseLog row; missed-dose check after 30 minutes uses `MonitoringRepository.checkMissedDose`; alerts screen with View details, Acknowledge, Exit; alarms are set again after a restart. `AlertHandoff` notifies the user, texts each designee and writes the CommunicationLog.
- **Search** (`search/`): keyword search by data type with a date range, quick searches (last/next doctor visit, past medications, next annual checkup) and search history.
- **Communication** (`communication/`): add, edit, delete designees with a preferred contact method; Text / Call / Email buttons; communication log of every alert and manual message.
- **Data Maintenance** (`maintenance/`): doctors, doctor visits, and the lookup tables (specialties, vital sign normal ranges, medication conflict pairs) with add, edit, delete.

Uses `SeedData.DEBUG_USER_ID` through `CurrentUser.userId` until Login sets the real user.

Tested on the emulator (API 37): add doctor, add designee and text them, add medication, simulate a missed dose (notification + SMS to designee + log), acknowledge, search.

Note: written with help from an AI assistant (Claude) and reviewed by me.
