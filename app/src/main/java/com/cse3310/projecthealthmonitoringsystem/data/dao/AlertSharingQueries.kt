package com.cse3310.projecthealthmonitoringsystem.data.dao

/**
 * Sharing eligibility is derived from the owned current source, never by deleting alert history.
 * There is no clinical age/TTL inference: source replacement, correction or treatment end makes
 * an alert ineligible. An explicit createdAfter cutoff can be supplied by a sharing policy.
 */
internal const val SHAREABLE_ALERTS = """
SELECT a.* FROM Alert a
WHERE a.userId = :userId AND a.acknowledgedAt IS NULL
AND a.createdAt BETWEEN :createdAfter AND :now
AND (
    (a.type = 'ABNORMAL_VITAL' AND EXISTS (
        SELECT 1 FROM VitalSign v WHERE v.userId = a.userId AND v.vitalId = a.vitalId
        AND v.isAbnormal = 1 AND v.recordedAt <= :now
        AND NOT EXISTS (SELECT 1 FROM VitalSign newer
            WHERE newer.userId = v.userId AND newer.vitalTypeId = v.vitalTypeId
            AND newer.recordedAt <= :now AND (newer.recordedAt > v.recordedAt
                OR (newer.recordedAt = v.recordedAt AND newer.vitalId > v.vitalId)))
    ))
    OR (a.type = 'MISSED_DOSE' AND EXISTS (
        SELECT 1 FROM EffectiveDoseLog d JOIN Medication m ON m.medicationId = d.medicationId AND m.userId = d.userId
        WHERE d.userId = a.userId AND d.doseId = a.doseId AND d.status = 'MISSED'
        AND m.isActive = 1 AND m.startDate <= :now AND (m.endDate IS NULL OR m.endDate >= :dayStart)
        AND d.scheduledAt <= :now
        AND NOT EXISTS (SELECT 1 FROM EffectiveDoseLog newer WHERE newer.userId = d.userId
            AND newer.medicationId = d.medicationId AND newer.scheduledAt <= :now
            AND newer.scheduledAt > d.scheduledAt)
    ))
    OR (a.type = 'CONFLICT' AND EXISTS (
        SELECT 1 FROM ConflictRule r
        JOIN Medication ma ON ma.userId = a.userId AND ma.isActive = 1 AND lower(trim(ma.name)) = r.drugA
        JOIN Medication mb ON mb.userId = a.userId AND mb.isActive = 1 AND lower(trim(mb.name)) = r.drugB
        WHERE mb.medicationId = a.medicationId
        AND ma.startDate <= :now AND (ma.endDate IS NULL OR ma.endDate >= :dayStart)
        AND mb.startDate <= :now AND (mb.endDate IS NULL OR mb.endDate >= :dayStart)
        AND a.message = ma.name || ' and ' || mb.name || ': ' || r.description
    ))
)
AND NOT EXISTS (
    SELECT 1 FROM Alert newer WHERE newer.userId = a.userId AND newer.type = a.type
    AND newer.createdAt <= :now
    AND (newer.createdAt > a.createdAt OR (newer.createdAt = a.createdAt AND newer.alertId > a.alertId))
    AND ((a.type = 'ABNORMAL_VITAL' AND newer.vitalId = a.vitalId)
        OR (a.type = 'MISSED_DOSE' AND newer.doseId = a.doseId)
        OR (a.type = 'CONFLICT' AND newer.medicationId = a.medicationId AND newer.message = a.message))
)
"""
