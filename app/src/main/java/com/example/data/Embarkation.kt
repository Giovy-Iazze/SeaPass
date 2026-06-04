package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "embarkations")
data class Embarkation(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val vesselName: String,
    val vesselImo: String,
    val vesselMmsi: String? = null,
    val rank: String, // Rank / Qualifica
    val vesselType: String? = null,
    val vesselFlag: String? = null,
    val signOnDate: Long, // Start Date timestamp
    val signOffDate: Long?, // End Date timestamp (null if still onboard)
    val seaDaysOverride: Int? = null, // Manual override for sea days
    val signOnPort: String? = null,
    val signOffPort: String? = null, // Journey End Port
    val grossTonnage: String? = null, // Gross Tonnage (Tonnellaggio)
    val vesselDimensions: String? = null // Ship Dimensions / Stazza
) {
    /**
     * Calculates the total sea days for this boarding period.
     * If the mariner is still onboard (signOffDate is null), calculates up to current date.
     */
    fun calculateSeaDays(currentTimeMillis: Long = System.currentTimeMillis()): Int {
        if (seaDaysOverride != null) return seaDaysOverride
        val end = signOffDate ?: currentTimeMillis
        if (end < signOnDate) return 0
        val diffMillis = end - signOnDate
        // Calculate days cleanly
        val days = (diffMillis / (1000L * 60 * 60 * 24)).toInt()
        return if (days < 0) 0 else days
    }
}
