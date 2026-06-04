package com.example.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class VesselResponse(
    @Json(name = "mmsi") val mmsi: String?,
    @Json(name = "name") val name: String?,
    @Json(name = "imo") val imo: String?,
    @Json(name = "callsign") val callsign: String? = null,
    @Json(name = "type") val type: String? = null,
    @Json(name = "flag") val flag: String? = null,
    @Json(name = "dest") val destination: String? = null,
    @Json(name = "eta") val eta: String? = null,
    val grossTonnage: String? = null,
    val vesselDimensions: String? = null
)
