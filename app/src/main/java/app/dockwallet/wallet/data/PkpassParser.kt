package app.dockwallet.wallet.data

import android.content.Context
import android.net.Uri
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.json.JSONObject
import java.io.ByteArrayOutputStream

object PkpassParser {

    fun parse(context: Context, uri: Uri): PassEntity? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val zip = ZipArchiveInputStream(inputStream)
            var passJson: String? = null

            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "pass.json") {
                    val buffer = ByteArrayOutputStream()
                    val bytes = ByteArray(4096)
                    var len: Int
                    while (zip.read(bytes).also { len = it } != -1) {
                        buffer.write(bytes, 0, len)
                    }
                    passJson = buffer.toString("UTF-8")
                    break
                }
                entry = zip.nextEntry
            }
            zip.close()

            passJson?.let { parsePassJson(it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun parsePassJson(json: String): PassEntity {
        val p = JSONObject(json)

        val passType = when {
            p.has("boardingPass") -> "boardingPass"
            p.has("eventTicket")  -> "eventTicket"
            p.has("coupon")       -> "coupon"
            p.has("storeCard")    -> "storeCard"
            else                  -> "generic"
        }

        val passBody = if (p.has(passType)) p.getJSONObject(passType) else JSONObject()

        val allFields = mutableListOf<JSONObject>()
        listOf("headerFields", "primaryFields", "secondaryFields", "auxiliaryFields", "backFields")
            .forEach { key ->
                if (passBody.has(key)) {
                    val arr = passBody.getJSONArray(key)
                    for (i in 0 until arr.length()) {
                        allFields.add(arr.getJSONObject(i))
                    }
                }
            }

        fun getField(vararg keys: String): String? {
            for (key in keys) {
                val field = allFields.find {
                    it.optString("key").lowercase() == key.lowercase() ||
                            it.optString("label").lowercase().contains(key.lowercase())
                }
                val value = field?.optString("value")?.trim()
                if (!value.isNullOrEmpty()) return value
            }
            return null
        }

        val primaryValue = if (passBody.has("primaryFields")) {
            val arr = passBody.getJSONArray("primaryFields")
            if (arr.length() > 0) arr.getJSONObject(0).optString("value").trim() else null
        } else null

        val barcodes = p.optJSONArray("barcodes")
        val barcode = barcodes?.getJSONObject(0)?.optString("message")
            ?: p.optJSONObject("barcode")?.optString("message")

        val isBoardingPass = passType == "boardingPass"

        return PassEntity(
            id = -(System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
            passType = passType,
            passengerName = getField("passenger", "name", "passengerName") ?: primaryValue,
            flightNumber = if (isBoardingPass) getField("flightNumber", "flight") else null,
            origin = if (isBoardingPass) getField("origin", "depart", "from") else null,
            destination = if (isBoardingPass) getField("destination", "arrive", "to") else null,
            departureTime = if (isBoardingPass) getField("departureDate", "boardingTime", "departure") else null,
            arrivalTime = if (isBoardingPass) getField("arrivalDate", "arrivalTime", "arrival") else null,
            eventDate = if (!isBoardingPass) p.optString("relevantDate").ifEmpty { null } else null,
            seat = if (isBoardingPass) getField("seat", "seatNumber") else null,
            gate = if (isBoardingPass) getField("gate") else null,
            bookingReference = if (isBoardingPass) getField("confirmationNumber", "bookingRef", "pnr") else null,
            barcode = barcode,
            subtitle = if (!isBoardingPass) {
                if (passBody.has("secondaryFields")) {
                    val arr = passBody.getJSONArray("secondaryFields")
                    if (arr.length() > 0) arr.getJSONObject(0).optString("value").trim() else null
                } else null
            } else null,
            logoText = p.optString("logoText").ifEmpty { null }
                ?: p.optString("description").ifEmpty { null },
            colorBackground = p.optString("backgroundColor").ifEmpty { null },
            colorForeground = p.optString("foregroundColor").ifEmpty { null },
            colorLabel = p.optString("labelColor").ifEmpty { null },
            isVoided = p.optBoolean("voided", false),
            signatureValid = false,
            isLocal = true
        )
    }
}