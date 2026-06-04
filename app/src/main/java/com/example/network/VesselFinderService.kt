package com.example.network

import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface VesselFinderApi {
    @GET("vessels")
    suspend fun getVesselData(
        @Query("userkey") apiKey: String,
        @Query("mmsi") mmsi: String? = null,
        @Query("imo") imo: String? = null
    ): List<VesselResponse>
}

object VesselFinderService {
    private const val BASE_URL = "https://api.vesselfinder.com/"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    val api: VesselFinderApi = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(VesselFinderApi::class.java)

    /**
     * Checks for a list of mock vessels to make prototyping interactive even when
     * offline or without real VesselFinder subscriptions. Keys are IMSI or IMO.
     */
    fun findMockVessel(query: String): VesselResponse? {
        val trimmed = query.trim()
        val mockDatabase = listOf(
            VesselResponse("247313000", "Amerigo Vespucci", "1002361", "IBZQ", "Sailing Vessel", "IT", "Genoa", "Jun 12, 18:00", grossTonnage = "4146 GT", vesselDimensions = "100m x 15m"),
            VesselResponse("247443300", "Costa Toscana", "9781891", "C6ES9", "Passenger Ship", "IT", "Civitavecchia", "Jun 18, 08:00", grossTonnage = "185010 GT", vesselDimensions = "337m x 42m"),
            VesselResponse("219156000", "Emma Maersk", "9321483", "OYGR2", "Container Ship", "DK", "Rotterdam", "Jun 06, 04:30"),
            VesselResponse("353136000", "Ever Given", "9811000", "H3UI", "Container Ship", "PA", "Suez Canal", "Jun 05, 12:00"),
            VesselResponse("219397000", "Maersk Mc-Kinney Moller", "9632064", "OWIZ2", "Container Ship", "DK", "Singapore", "Jun 11, 09:15"),
            VesselResponse("247012345", "Stella Marina", "9415678", "IABC", "LPG Carrier", "IT", "Naples", "Jun 04, 22:00"),
            VesselResponse("548123456", "Magsaysay Explorer", "9123456", "DU12", "Training Vessel", "PH", "Manila", "Jun 08, 08:30")
        )
        return mockDatabase.find {
            it.mmsi == trimmed || it.imo == trimmed || it.name?.equals(trimmed, ignoreCase = true) == true
        }
    }
    
    suspend fun scrapeVessel(query: String): VesselResponse? = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext null

        try {
            val url = "https://www.vesselfinder.com/vessels/details/$trimmed"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Referer", "https://www.google.com/")
                .build()
                
            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val html = response.body?.string() ?: ""
                
                val titleRegex = Regex("""<title>(.*?),\s*(.*?)\s+-\s+Details""")
                val titleMatch = titleRegex.find(html)
                var name: String? = null
                var type: String? = null
                
                if (titleMatch != null) {
                    name = titleMatch.groupValues[1].trim()
                    type = titleMatch.groupValues[2].trim()
                }
                
                if (name != null) {
                    val mmsiImoRegex = Regex("""IMO\s+(\d+),\s+MMSI\s+(\d+)""")
                    val mmsiImoMatch = mmsiImoRegex.find(html)
                    var imo: String? = null
                    var mmsi: String? = null
                    if (mmsiImoMatch != null) {
                        imo = mmsiImoMatch.groupValues[1]
                        mmsi = mmsiImoMatch.groupValues[2]
                    } else if (trimmed.length == 7) {
                        imo = trimmed
                    } else if (trimmed.length == 9) {
                        mmsi = trimmed
                    }
                    
                    val flagRegex = Regex("""(?i)sailing under the flag of\s+([a-zA-Z\s]+)[.<]""")
                    val flagMatch = flagRegex.find(html)
                    var flag = flagMatch?.groupValues?.get(1)?.trim()

                    if (flag == null || flag.isEmpty() || flag.contains("Unknown", ignoreCase = true)) {
                        val altFlagRegex = Regex("""Flag(?:</td>|</div>)\s*(?:<td class="v3">|<div class="v3">)\s*(?:<[^>]+>\s*)*([^<]+)""", RegexOption.DOT_MATCHES_ALL)
                        val altFlagMatch = altFlagRegex.find(html)
                        flag = altFlagMatch?.groupValues?.get(1)?.trim()
                    }

                    if (flag.isNullOrEmpty()) {
                        flag = "Unknown"
                    }

                    val gtRegex = Regex("""(?:Gross Tonnage|Gross Tonnage</div>)\s*<div class="v3">(.*?)</div>""")
                    val gtMatch = gtRegex.find(html)
                    val gt = gtMatch?.groupValues?.get(1)?.replace("-", "")?.trim()?.takeIf { it.isNotEmpty() }?.let { if (it.endsWith("GT")) it else "$it GT" }
                    
                    val lenRegex = Regex("""(?:Length / Beam|Length / Beam</div>)\s*<div class="v3">(.*?)</div>""")
                    val lenMatch = lenRegex.find(html)
                    val lenInfo = lenMatch?.groupValues?.get(1)?.replace("-", "")?.trim()?.takeIf { it.isNotEmpty() }
                    
                    val callsignRegex = Regex("""(?:Callsign|Callsign</div>)\s*<div class="v3">(.*?)</div>""")
                    val callsignMatch = callsignRegex.find(html)
                    val callsign = callsignMatch?.groupValues?.get(1)?.replace("-", "")?.trim()
                    
                    return@withContext VesselResponse(
                        mmsi = mmsi ?: trimmed,
                        name = name,
                        imo = imo,
                        callsign = callsign ?: "N/A",
                        type = type ?: "Unknown Type",
                        flag = flag,
                        destination = "N/A",
                        eta = "N/A",
                        grossTonnage = gt,
                        vesselDimensions = lenInfo
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("VesselScraper", "Error scraping vessel", e)
        }
        null
    }
}
