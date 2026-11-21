package com.cse3310.projecthealthmonitoringsystem.data.entity

/** DoseLog status. Moves SCHEDULED -> TAKEN or SCHEDULED -> MISSED once; then final (enforced by SQL triggers). */
enum class DoseStatus { SCHEDULED, TAKEN, MISSED }

/** Alert.type */
enum class AlertType { MISSED_DOSE, CONFLICT, ABNORMAL_VITAL }

/** Designee.preferredMethod and CommunicationLog.method */
enum class ContactMethod { SMS, EMAIL, CALL }

/** CommunicationLog.status: SENT or FAILED for SMS, OPENED when an email or dial intent was launched. */
enum class DeliveryStatus { SENT, FAILED, OPENED }

/** Weight.unit and Profile.weightUnit */
enum class WeightUnit { KG, LB }
