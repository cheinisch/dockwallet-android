package app.dockwallet.wallet.ui.detail

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dockwallet.wallet.data.PassEntity
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter

fun parseColor(hex: String?): Color? {
    if (hex == null) return null
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        null
    }
}

fun generateBarcode(content: String, format: BarcodeFormat, width: Int, height: Int): Bitmap? {
    return try {
        val matrix = MultiFormatWriter().encode(content, format, width, height)
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bmp.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bmp
    } catch (e: Exception) {
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    pass: PassEntity,
    onBack: () -> Unit
) {
    val bgColor = parseColor(pass.colorBackground) ?: MaterialTheme.colorScheme.primary
    val fgColor = parseColor(pass.colorForeground) ?: Color.White
    val labelColor = parseColor(pass.colorLabel) ?: fgColor.copy(alpha = 0.7f)

    val bgLuminance = (0.299f * bgColor.red + 0.587f * bgColor.green + 0.114f * bgColor.blue)
    val onBg = if (bgLuminance > 0.5f) Color.Black else Color.White
    val onBgMuted = onBg.copy(alpha = 0.6f)

    val barcodeBitmap = remember(pass.barcode) {
        pass.barcode?.let {
            generateBarcode(it, BarcodeFormat.QR_CODE, 512, 512)
                ?: generateBarcode(it, BarcodeFormat.PDF_417, 700, 200)
                ?: generateBarcode(it, BarcodeFormat.CODE_128, 700, 200)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Zurück")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = bgColor,
                    navigationIconContentColor = fgColor
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // ── Pass Karte ───────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bgColor)
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = pass.logoText ?: "",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = fgColor
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                pass.passengerName?.let { name ->
                    if (name.isNotBlank()) {
                        Text(
                            text = "Name",
                            fontSize = 11.sp,
                            color = labelColor,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = name,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = fgColor,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                pass.subtitle?.let {
                    if (it.isNotBlank()) {
                        Text(
                            text = it,
                            fontSize = 15.sp,
                            color = fgColor,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                if (pass.passType == "boardingPass") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = pass.origin ?: "???",
                                fontSize = 42.sp,
                                fontWeight = FontWeight.Bold,
                                color = fgColor
                            )
                            Text("Abflug", fontSize = 11.sp, color = labelColor)
                        }
                        Icon(
                            Icons.Default.Flight,
                            contentDescription = null,
                            tint = fgColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = pass.destination ?: "???",
                                fontSize = 42.sp,
                                fontWeight = FontWeight.Bold,
                                color = fgColor
                            )
                            Text("Ankunft", fontSize = 11.sp, color = labelColor)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                pass.eventDate?.let {
                    Text(
                        text = it.replace("T", " ").take(16),
                        fontSize = 14.sp,
                        color = labelColor,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                HorizontalDivider(color = fgColor.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(16.dp))

                val fields = buildList {
                    pass.flightNumber?.let { add("Flug" to it) }
                    pass.departureTime?.let { add("Abflug" to it.replace("T", " ").take(16)) }
                    pass.arrivalTime?.let { add("Ankunft" to it.replace("T", " ").take(16)) }
                    pass.seat?.let { add("Sitz" to it) }
                    pass.gate?.let { add("Gate" to it) }
                    pass.bookingReference?.let { add("Buchung" to it) }
                }

                if (fields.isNotEmpty()) {
                    fields.chunked(2).forEach { row ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            row.forEach { (label, value) ->
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(bottom = 12.dp)
                                ) {
                                    Text(
                                        text = label.uppercase(),
                                        fontSize = 10.sp,
                                        color = labelColor,
                                        fontWeight = FontWeight.Medium,
                                        letterSpacing = 0.5.sp
                                    )
                                    Text(
                                        text = value,
                                        fontSize = 15.sp,
                                        color = fgColor,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }

                if (pass.isVoided) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Red.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "⚠ Dieser Pass ist ungültig",
                            modifier = Modifier.padding(12.dp),
                            color = Color.Red,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // ── QR Code / Barcode ────────────────────────────────────────
            if (barcodeBitmap != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Image(
                            bitmap = barcodeBitmap.asImageBitmap(),
                            contentDescription = "QR Code",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                    pass.barcode?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = it,
                            fontSize = 12.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // ── Weitere Informationen ─────────────────────────────────────
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Weitere Informationen",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                pass.logoText?.let {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = it,
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}