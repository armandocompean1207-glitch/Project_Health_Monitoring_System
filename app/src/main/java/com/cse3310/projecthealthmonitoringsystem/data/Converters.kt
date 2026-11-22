package com.cse3310.projecthealthmonitoringsystem.data

import androidx.room.TypeConverter
import com.cse3310.projecthealthmonitoringsystem.data.entity.AlertType
import com.cse3310.projecthealthmonitoringsystem.data.entity.ContactMethod
import com.cse3310.projecthealthmonitoringsystem.data.entity.DeliveryStatus
import com.cse3310.projecthealthmonitoringsystem.data.entity.DoseStatus
import com.cse3310.projecthealthmonitoringsystem.data.entity.WeightUnit

/**
 * Stores enums by name. The SQL triggers in [DatabaseTriggers] compare against these names
 * ('SCHEDULED', 'TAKEN', 'MISSED'), so never rename an enum constant without a migration.
 */
class Converters {
    @TypeConverter fun doseStatusToString(value: DoseStatus): String = value.name
    @TypeConverter fun stringToDoseStatus(value: String): DoseStatus = DoseStatus.valueOf(value)

    @TypeConverter fun alertTypeToString(value: AlertType): String = value.name
    @TypeConverter fun stringToAlertType(value: String): AlertType = AlertType.valueOf(value)

    @TypeConverter fun contactMethodToString(value: ContactMethod): String = value.name
    @TypeConverter fun stringToContactMethod(value: String): ContactMethod = ContactMethod.valueOf(value)

    @TypeConverter fun deliveryStatusToString(value: DeliveryStatus): String = value.name
    @TypeConverter fun stringToDeliveryStatus(value: String): DeliveryStatus = DeliveryStatus.valueOf(value)

    @TypeConverter fun weightUnitToString(value: WeightUnit): String = value.name
    @TypeConverter fun stringToWeightUnit(value: String): WeightUnit = WeightUnit.valueOf(value)
}
