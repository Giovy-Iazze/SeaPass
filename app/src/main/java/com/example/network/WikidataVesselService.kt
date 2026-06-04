package com.example.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object WikidataVesselService {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun queryVessel(query: String): VesselResponse? = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext null

        // Build the SPARQL query text
        val isNumeric = trimmed.all { it.isDigit() }
        val sparql = if (isNumeric) {
            """
            SELECT ?shipLabel ?imo ?mmsi ?callsign ?flagLabel ?typeLabel ?gt ?len ?beam ?wikiTitle WHERE {
              {
                ?ship wdt:P458 "$trimmed".
              } UNION {
                ?ship wdt:P794 "$trimmed".
              }
              OPTIONAL { ?ship wdt:P458 ?imo. }
              OPTIONAL { ?ship wdt:P794 ?mmsi. }
              OPTIONAL { ?ship wdt:P4092 ?callsign. }
              OPTIONAL { ?ship wdt:P1093 ?gt. }
              OPTIONAL { ?ship wdt:P2043 ?len. }
              OPTIONAL { ?ship wdt:P2049 ?beam. }
              OPTIONAL {
                ?ship wdt:P1525 ?flag.
                ?flag rdfs:label ?flagLabel.
                FILTER(LANG(?flagLabel) = "en")
              }
              OPTIONAL {
                ?ship wdt:P31 ?type.
                ?type rdfs:label ?typeLabel.
                FILTER(LANG(?typeLabel) = "en")
              }
              OPTIONAL {
                ?sitelink schema:about ?ship ;
                          schema:isPartOf <https://en.wikipedia.org/> ;
                          schema:name ?wikiTitle .
              }
              SERVICE wikibase:label { bd:serviceParam wikibase:language "en". }
            } LIMIT 1
            """.trimIndent()
        } else {
            """
            SELECT ?shipLabel ?imo ?mmsi ?callsign ?flagLabel ?typeLabel ?gt ?len ?beam ?wikiTitle WHERE {
              ?ship rdfs:label "$trimmed"@en.
              ?ship wdt:P31/wdt:P279* wd:Q11446.
              OPTIONAL { ?ship wdt:P458 ?imo. }
              OPTIONAL { ?ship wdt:P794 ?mmsi. }
              OPTIONAL { ?ship wdt:P4092 ?callsign. }
              OPTIONAL { ?ship wdt:P1093 ?gt. }
              OPTIONAL { ?ship wdt:P2043 ?len. }
              OPTIONAL { ?ship wdt:P2049 ?beam. }
              OPTIONAL {
                ?ship wdt:P1525 ?flag.
                ?flag rdfs:label ?flagLabel.
                FILTER(LANG(?flagLabel) = "en")
              }
              OPTIONAL {
                ?ship wdt:P31 ?type.
                ?type rdfs:label ?typeLabel.
                FILTER(LANG(?typeLabel) = "en")
              }
              OPTIONAL {
                ?sitelink schema:about ?ship ;
                          schema:isPartOf <https://en.wikipedia.org/> ;
                          schema:name ?wikiTitle .
              }
              SERVICE wikibase:label { bd:serviceParam wikibase:language "en". }
            } LIMIT 1
            """.trimIndent()
        }

        try {
            val urlBuilder = "https://query.wikidata.org/sparql".toHttpUrlOrNull()?.newBuilder()
                ?: return@withContext null
            urlBuilder.addQueryParameter("query", sparql)
            urlBuilder.addQueryParameter("format", "json")

            val request = Request.Builder()
                .url(urlBuilder.build())
                .header("User-Agent", "SeaPass/1.0 (support@seapass.com) OkHttp/4.9")
                .header("Accept", "application/sparql-results+json")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("WikidataVessel", "Wikidata request failed with code: ${response.code}")
                    return@withContext null
                }

                val bodyStr = response.body?.string() ?: return@withContext null
                val json = JSONObject(bodyStr)
                if (!json.has("results")) return@withContext null
                val results = json.getJSONObject("results")
                if (!results.has("bindings")) return@withContext null
                val bindings = results.getJSONArray("bindings")

                if (bindings.length() > 0) {
                    val binding = bindings.getJSONObject(0)
                    
                    val name = binding.optJSONObject("shipLabel")?.optString("value")
                    val imo = binding.optJSONObject("imo")?.optString("value")
                    val mmsi = binding.optJSONObject("mmsi")?.optString("value")
                    val callsign = binding.optJSONObject("callsign")?.optString("value")
                    val flag = binding.optJSONObject("flagLabel")?.optString("value")
                    val type = binding.optJSONObject("typeLabel")?.optString("value")
                    val gtVal = binding.optJSONObject("gt")?.optString("value")
                    val lenVal = binding.optJSONObject("len")?.optString("value")
                    val beamVal = binding.optJSONObject("beam")?.optString("value")

                    val wikiTitle = binding.optJSONObject("wikiTitle")?.optString("value")
                    
                    var finalGt = gtVal
                    var finalLen = lenVal
                    var finalBeam = beamVal
                    
                    // Fallback to scraping wikipedia infobox if data is missing
                    if (!wikiTitle.isNullOrEmpty() && (finalGt.isNullOrEmpty() || finalLen.isNullOrEmpty())) {
                        try {
                            val wikiUrl = "https://en.wikipedia.org/w/api.php".toHttpUrlOrNull()?.newBuilder()?.apply {
                                addQueryParameter("action", "query")
                                addQueryParameter("prop", "revisions")
                                addQueryParameter("rvprop", "content")
                                addQueryParameter("rvslots", "main")
                                addQueryParameter("titles", wikiTitle)
                                addQueryParameter("format", "json")
                            }?.build()
                            
                            if (wikiUrl != null) {
                                val wikiReq = Request.Builder().url(wikiUrl).header("User-Agent", "SeaPass/1.0").build()
                                val wikiResp = okHttpClient.newCall(wikiReq).execute()
                                if (wikiResp.isSuccessful) {
                                    val wikiBody = wikiResp.body?.string() ?: ""
                                    // Extremely basic RegEx to find Tonnage and Length inside infoBox
                                    if (finalGt.isNullOrEmpty()) {
                                        val gtMatch = Regex("""Tonnage\s*=\s*([^|]*)""").find(wikiBody)
                                        if (gtMatch != null) {
                                            val numericGt = gtMatch.groupValues[1].replace(Regex("[^0-9]"), "")
                                            if (numericGt.isNotEmpty()) finalGt = numericGt
                                        }
                                    }
                                    if (finalLen.isNullOrEmpty()) {
                                        val lenMatch = Regex("""Length\s*=\s*([^|]*)""").find(wikiBody)
                                        if (lenMatch != null) {
                                            val lenStr = lenMatch.groupValues[1]
                                            val matchNumeric = Regex("""([0-9.]+)""").find(lenStr)
                                            if (matchNumeric != null) {
                                                finalLen = matchNumeric.groupValues[1]
                                            }
                                        }
                                    }
                                }
                            }
                        } catch(e: Exception) {
                            Log.e("WikidataVessel", "Wiki fallback failed", e)
                        }
                    }

                    var formDimensions: String? = null
                    if (!finalLen.isNullOrBlank() || !finalBeam.isNullOrBlank()) {
                        val fmtLen = finalLen?.toDoubleOrNull()?.let { "${it.toInt()}m" } ?: finalLen ?: ""
                        val fmtBeam = finalBeam?.toDoubleOrNull()?.let { "${it.toInt()}m" } ?: finalBeam ?: ""
                        formDimensions = if (fmtLen.isNotEmpty() && fmtBeam.isNotEmpty()) "$fmtLen x $fmtBeam" else fmtLen.ifEmpty { fmtBeam }
                    }

                    return@withContext VesselResponse(
                        mmsi = mmsi ?: trimmed,
                        name = name ?: trimmed.uppercase(),
                        imo = imo ?: "",
                        callsign = callsign ?: "",
                        type = type ?: "Vessel",
                        flag = flag ?: "Unknown Flag",
                        destination = "N/A",
                        eta = "N/A",
                        grossTonnage = finalGt?.toDoubleOrNull()?.let { "${it.toInt()} GT" } ?: finalGt?.let { "$it GT" },
                        vesselDimensions = formDimensions
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("WikidataVessel", "Exception fetching from wikidata: ${e.message}", e)
        }
        return@withContext null
    }
}
