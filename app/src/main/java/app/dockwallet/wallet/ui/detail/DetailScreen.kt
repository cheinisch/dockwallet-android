package app.dockwallet.wallet.ui.detail

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dockwallet.wallet.data.PassEntity
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter

// ── Helpers ───────────────────────────────────────────────────────────────────

fun parseColor(hex: String?): Color? {
    if (hex == null) return null
    return try {
        val raw = hex.trim()
        val rgbMatch = Regex("""rgb\s*\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*\)""").find(raw)
        if (rgbMatch != null) {
            val (r, g, b) = rgbMatch.destructured
            Color(r.toInt(), g.toInt(), b.toInt())
        } else {
            Color(android.graphics.Color.parseColor(raw))
        }
    } catch (e: Exception) { null }
}

// Try formats in order, return first that works
fun generateBarcode(content: String, format: BarcodeFormat, width: Int, height: Int): Bitmap? {
    return try {
        val matrix = MultiFormatWriter().encode(content, format, width, height)
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) for (y in 0 until height)
            bmp.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        bmp
    } catch (e: Exception) { null }
}

data class BarcodeResult(val bitmap: Bitmap, val format: BarcodeFormat)

fun detectBarcode(content: String): BarcodeResult? {
    // Try QR first (most common in pkpass), then PDF417 (boarding passes), then Code128
    val candidates = listOf(
        BarcodeFormat.QR_CODE   to (512 to 512),
        BarcodeFormat.PDF_417   to (800 to 240),
        BarcodeFormat.AZTEC     to (512 to 512),
        BarcodeFormat.CODE_128  to (800 to 240),
    )
    for ((fmt, size) in candidates) {
        val bmp = generateBarcode(content, fmt, size.first, size.second)
        if (bmp != null) return BarcodeResult(bmp, fmt)
    }
    return null
}

// ── DetailScreen ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    pass: PassEntity,
    onBack: () -> Unit
) {
    val bgColor  = parseColor(pass.colorBackground) ?: Color(0xFF1C3A6B)
    val fgColor  = parseColor(pass.colorForeground) ?: Color.White
    val lblColor = parseColor(pass.colorLabel)      ?: fgColor.copy(alpha = 0.65f)

    val barcodeResult = remember(pass.barcode) {
        pass.barcode?.let { detectBarcode(it) }
    }

    var showInfoSheet by remember { mutableStateOf(false) }

    // ── Info Bottom Sheet ─────────────────────────────────────────────────────
    if (showInfoSheet) {
        ModalBottomSheet(
            onDismissRequest = { showInfoSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            PassInfoSheet(pass = pass, barcodeResult = barcodeResult)
        }
    }

    // ── Main Screen ───────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Top bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Zurück",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                // Info button top right
                IconButton(
                    onClick = { showInfoSheet = true },
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Karteninfos",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            // ── Card ──────────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(bgColor)
                    // Tap the card → open info sheet (like Apple Wallet)
                    .clickable { showInfoSheet = true }
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = pass.logoText ?: "",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = fgColor
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = fgColor.copy(alpha = 0.15f), thickness = 0.5.dp
                )
                Spacer(Modifier.height(20.dp))

                // Passenger name
                pass.passengerName?.takeIf { it.isNotBlank() }?.let { name ->
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        Text("NAME", fontSize = 10.sp, color = lblColor, letterSpacing = 1.sp)
                        Text(name, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = fgColor)
                    }
                    Spacer(Modifier.height(20.dp))
                }

                // Boarding pass route
                if (pass.passType == "boardingPass") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("ABFLUG", fontSize = 10.sp, color = lblColor, letterSpacing = 1.sp)
                            Text(pass.origin ?: "—", fontSize = 48.sp, fontWeight = FontWeight.Bold,
                                color = fgColor, lineHeight = 50.sp)
                        }
                        Icon(Icons.Default.Flight, null, tint = fgColor.copy(alpha = 0.7f),
                            modifier = Modifier.size(28.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text("ANKUNFT", fontSize = 10.sp, color = lblColor, letterSpacing = 1.sp)
                            Text(pass.destination ?: "—", fontSize = 48.sp, fontWeight = FontWeight.Bold,
                                color = fgColor, lineHeight = 50.sp)
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }

                // Subtitle
                pass.subtitle?.takeIf { it.isNotBlank() }?.let {
                    Text(it, fontSize = 16.sp, color = fgColor,
                        modifier = Modifier.padding(horizontal = 20.dp))
                    Spacer(Modifier.height(12.dp))
                }

                // Fields grid
                val fields = buildList {
                    pass.flightNumber?.let     { add("FLUG"    to it) }
                    pass.departureTime?.let    { add("ABFLUG"  to it.replace("T", " ").take(16)) }
                    pass.arrivalTime?.let      { add("ANKUNFT" to it.replace("T", " ").take(16)) }
                    pass.seat?.let             { add("SITZ"    to it) }
                    pass.gate?.let             { add("GATE"    to it) }
                    pass.bookingReference?.let { add("BUCHUNG" to it) }
                    pass.eventDate?.let        { add("DATUM"   to it.replace("T", " ").take(16)) }
                }

                if (fields.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp),
                        color = fgColor.copy(alpha = 0.15f), thickness = 0.5.dp)
                    Spacer(Modifier.height(16.dp))
                    fields.chunked(2).forEach { row ->
                        Row(modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 16.dp)) {
                            row.forEach { (label, value) ->
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(label, fontSize = 10.sp, color = lblColor,
                                        letterSpacing = 1.sp, fontWeight = FontWeight.Medium)
                                    Text(value, fontSize = 16.sp, color = fgColor,
                                        fontWeight = FontWeight.SemiBold)
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }

                // Voided
                if (pass.isVoided) {
                    Spacer(Modifier.height(8.dp))
                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Red.copy(alpha = 0.25f))
                        .padding(12.dp),
                        contentAlignment = Alignment.Center) {
                        Text("⚠  Dieser Pass ist ungültig", color = Color(0xFFFF6B6B),
                            fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // Barcode
                if (barcodeResult != null) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp),
                        color = fgColor.copy(alpha = 0.15f), thickness = 0.5.dp)
                    Spacer(Modifier.height(24.dp))
                    Box(modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .padding(16.dp)) {
                        val isLinear = barcodeResult.format in listOf(
                            BarcodeFormat.PDF_417, BarcodeFormat.CODE_128, BarcodeFormat.CODE_39)
                        Image(
                            bitmap = barcodeResult.bitmap.asImageBitmap(),
                            contentDescription = "Barcode",
                            modifier = if (isLinear)
                                Modifier.width(260.dp).height(80.dp)
                            else
                                Modifier.size(200.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                    // Format label
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = barcodeResult.format.name.replace("_", " "),
                        fontSize = 10.sp,
                        color = fgColor.copy(alpha = 0.4f),
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(24.dp))
                }

                // Tap hint
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ExpandMore, null,
                        tint = fgColor.copy(alpha = 0.35f), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Mehr Infos", fontSize = 11.sp, color = fgColor.copy(alpha = 0.35f))
                }
            }

            Spacer(Modifier.navigationBarsPadding().height(32.dp))
        }
    }
}

// ── Info Bottom Sheet ─────────────────────────────────────────────────────────

@Composable
private fun PassInfoSheet(
    pass: PassEntity,
    barcodeResult: BarcodeResult?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 32.dp)
    ) {
        // Title
        Text(
            text = pass.logoText ?: pass.subtitle ?: "Karteninfos",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = passTypeLabel(pass.passType),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // All info fields
        val infoFields = buildList {
            pass.passengerName?.let     { add("Name"           to it) }
            pass.flightNumber?.let      { add("Flugnummer"     to it) }
            pass.origin?.let            { add("Abflugort"      to it) }
            pass.destination?.let       { add("Ankunftsort"    to it) }
            pass.departureTime?.let     { add("Abflug"         to it.replace("T", " ").take(16)) }
            pass.arrivalTime?.let       { add("Ankunft"        to it.replace("T", " ").take(16)) }
            pass.seat?.let              { add("Sitzplatz"      to it) }
            pass.gate?.let              { add("Gate"           to it) }
            pass.bookingReference?.let  { add("Buchungscode"   to it) }
            pass.subtitle?.let          { add("Beschreibung"   to it) }
            pass.eventDate?.let         { add("Datum"          to it.replace("T", " ").take(16)) }
            pass.createdAt?.let         { add("Hinzugefügt"    to it.take(10)) }
            barcodeResult?.let          { add("Barcode-Format" to it.format.name.replace("_", " ")) }
            if (pass.isVoided)          add("Status"           to "Ungültig / abgelaufen")
        }

        infoFields.forEachIndexed { index, (label, value) ->
            InfoRow(label = label, value = value)
            if (index < infoFields.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }

        // Barcode enlarged in sheet
        if (barcodeResult != null && pass.barcode != null) {
            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(20.dp))
            Text(
                "Barcode",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            val isLinear = barcodeResult.format in listOf(
                BarcodeFormat.PDF_417, BarcodeFormat.CODE_128, BarcodeFormat.CODE_39)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = barcodeResult.bitmap.asImageBitmap(),
                    contentDescription = "Barcode",
                    modifier = if (isLinear)
                        Modifier.fillMaxWidth().height(100.dp)
                    else
                        Modifier.size(240.dp),
                    contentScale = ContentScale.Fit
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = pass.barcode!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.6f)
        )
    }
}

private fun passTypeLabel(type: String?) = when (type) {
    "boardingPass" -> "Boarding Pass"
    "eventTicket"  -> "Veranstaltungsticket"
    "coupon"       -> "Coupon"
    "storeCard"    -> "Kundenkarte"
    else           -> "Pass"
}