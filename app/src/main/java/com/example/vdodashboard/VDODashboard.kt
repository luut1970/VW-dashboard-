package com.example.vdodashboard

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin

// Officiële VDO Retro Kleuren
val VdoBlack = Color(0xFF060606)
val VdoFaceLight = Color(0xFF1a1a1a)
val VdoGreyCap = Color(0xFF262626)
val VdoChromeRing = Color(0xFFDADADA)
val VdoOrangeNeedle = Color(0xFFFF9100)
val VdoRedline = Color(0xFFD50000)
val VdoDimGray = Color(0xFF696969)
val VdoGreen = Color(0xFF39C64A)
val VdoBlue = Color(0xFF438CFF)
val VdoIvory = Color(0xFFECE7D8)

@Composable
fun VDODashboardScreen(
    kph: Float,
    rpm: Float,
    fuel: Float = 0.7f,
    temp: Float = 90f,
    totalKm: Float = 0f,
    blinker: Boolean = false,
    oel: Boolean = false,
    ladung: Boolean = false,
    fernlicht: Boolean = false
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        val availableWidth = maxWidth / 3.3f
        val availableHeight = maxHeight * 0.95f
        val gaugeSize = if (availableWidth < availableHeight) availableWidth else availableHeight

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Top
        ) {
            Box(modifier = Modifier.size(gaugeSize)) { VdoSpeedometer(currentKph = kph, totalKm = totalKm) }
            Box(modifier = Modifier.size(gaugeSize * 0.65f)) { VdoClock() }
            Box(modifier = Modifier.size(gaugeSize)) {
                VdoTachometerCombo(
                    currentRpm = rpm,
                    fuelLevel = fuel,
                    temperature = temp,
                    blinkerOn = blinker,
                    oelOn = oel,
                    ladungOn = ladung,
                    fernlichtOn = fernlicht
                )
            }
        }
    }
}

// ---------- Gedeelde tekenhulpjes ----------

private fun lerpColor(a: Color, b: Color, t: Float): Color {
    val ct = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * ct,
        green = a.green + (b.green - a.green) * ct,
        blue = a.blue + (b.blue - a.blue) * ct,
        alpha = 1f
    )
}

private fun chromeBrush(c: Offset, r: Float): Brush {
    val safeR = r.coerceAtLeast(1f)
    // Scherpe banden i.p.v. één zachte vloeiende overgang: echt chroom weerkaatst de
    // omgeving in afwisselend felle en donkere stroken, niet in één egale verloop.
    return Brush.linearGradient(
        colorStops = arrayOf(
            0.00f to Color(0xFFFFFFFF),
            0.07f to Color(0xFFFFFFFF),
            0.14f to Color(0xFF262626),
            0.27f to Color(0xFF1A1A1A),
            0.34f to Color(0xFFF2F2F2),
            0.46f to Color(0xFFEDEDED),
            0.53f to Color(0xFF141414),
            0.66f to Color(0xFF0D0D0D),
            0.74f to Color(0xFFFAFAFA),
            0.85f to Color(0xFFC9C9C9),
            0.92f to Color(0xFF3A3A3A),
            1.00f to Color(0xFF8A8A8A)
        ),
        start = Offset(c.x - safeR, c.y - safeR),
        end = Offset(c.x + safeR, c.y + safeR)
    )
}

// Chrome ring + zwarte wijzerplaat, gedeeld door alle grote meters
private fun DrawScope.bezel(c: Offset, r: Float) {
    val safeR = r.coerceAtLeast(1f)
    drawCircle(color = Color.Black.copy(alpha = 0.5f), radius = safeR * 1.05f, center = Offset(c.x, c.y + safeR * 0.02f))
    drawCircle(brush = chromeBrush(c, safeR), radius = safeR, center = c)

    // Losse felle highlight-boog linksboven: het typische scherpe lichtlijntje op gepolijst chroom
    drawArc(
        color = Color.White.copy(alpha = 0.9f),
        startAngle = 200f,
        sweepAngle = 55f,
        useCenter = false,
        topLeft = Offset(c.x - safeR * 0.97f, c.y - safeR * 0.97f),
        size = Size(safeR * 1.94f, safeR * 1.94f),
        style = Stroke(width = safeR * 0.035f)
    )
    // Zachte schaduwboog rechtsonder, tegenovergesteld van de highlight
    drawArc(
        color = Color.Black.copy(alpha = 0.55f),
        startAngle = 20f,
        sweepAngle = 55f,
        useCenter = false,
        topLeft = Offset(c.x - safeR * 0.97f, c.y - safeR * 0.97f),
        size = Size(safeR * 1.94f, safeR * 1.94f),
        style = Stroke(width = safeR * 0.035f)
    )
    // Dunne felle rand op de allerbuitenste kilometerrand voor extra scherpte
    drawCircle(color = Color.White.copy(alpha = 0.6f), radius = safeR * 0.995f, center = c, style = Stroke(width = safeR * 0.008f))

    drawCircle(
        brush = Brush.radialGradient(colors = listOf(VdoFaceLight, VdoBlack), center = Offset(c.x - safeR * 0.12f, c.y - safeR * 0.12f), radius = safeR * 1.05f),
        radius = safeR * 0.90f,
        center = c
    )
    drawCircle(color = Color.White.copy(alpha = 0.22f), radius = safeR * 0.905f, center = c, style = Stroke(width = safeR * 0.005f))
}

private fun DrawScope.drawLabel(text: String, x: Float, y: Float, sizePx: Float, color: Color, bold: Boolean = true) {
    drawContext.canvas.nativeCanvas.drawText(
        text, x, y,
        Paint().apply {
            isAntiAlias = true
            this.color = color.toArgb()
            textSize = sizePx
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-condensed", if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
    )
}

// Tapse naald i.p.v. een simpele lijn
private fun DrawScope.taperedNeedle(c: Offset, angleDeg: Float, length: Float, baseWidth: Float, color: Color) {
    val rad = Math.toRadians(angleDeg.toDouble())
    val dx = cos(rad).toFloat()
    val dy = sin(rad).toFloat()
    val px = -dy
    val py = dx
    val tip = Offset(c.x + dx * length, c.y + dy * length)
    val tailLen = length * 0.16f
    val tail = Offset(c.x - dx * tailLen, c.y - dy * tailLen)
    val path = Path().apply {
        moveTo(c.x + px * baseWidth, c.y + py * baseWidth)
        lineTo(tip.x, tip.y)
        lineTo(c.x - px * baseWidth, c.y - py * baseWidth)
        lineTo(tail.x - px * baseWidth * 0.6f, tail.y - py * baseWidth * 0.6f)
        lineTo(tail.x + px * baseWidth * 0.6f, tail.y + py * baseWidth * 0.6f)
        close()
    }
    drawPath(path, color)
}

// ---------- Snelheidsmeter ----------

@Composable
fun VdoSpeedometer(currentKph: Float, totalKm: Float = 0f) {
    val animatedKph by animateFloatAsState(targetValue = currentKph)
    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = (size.width / 2) * 0.90f

        bezel(center, radius)

        val startAngle = 135f
        val sweepAngle = 270f
        val maxKph = 200f
        // Het zichtbare gedeelte (20 t/m 200) loopt symmetrisch over de volle 270°, zoals
        // het origineel - dat zorgt ervoor dat 20 en 200 op dezelfde hoogte staan.
        // Het blanco, onbenoemde stukje voor 0-20 km/h komt er apart vóór te hangen.
        val zeroGap = 14f // hoek voor het blanco stukje 0-20 km/h, vóór het zichtbare gedeelte
        fun angleForKph(v: Float): Float =
            if (v <= 20f) (startAngle - zeroGap) + (v / 20f) * zeroGap
            else startAngle + ((v - 20f) / 180f) * sweepAngle

        // Drieledige schaalverdeling zoals het origineel: groot (elke 20, met cijfer),
        // middel (elke 10) en klein (elke 2)
        var kphVal = 20
        while (kphVal <= 200) {
            val tickAngle = angleForKph(kphVal.toFloat())
            val rad = Math.toRadians(tickAngle.toDouble())
            val isMajor = kphVal % 20 == 0
            val isMid = kphVal % 10 == 0
            val innerFrac = if (isMajor) 0.81f else if (isMid) 0.83f else 0.85f
            val strokeW = if (isMajor) radius * 0.018f else if (isMid) radius * 0.011f else radius * 0.006f
            val tickColor = if (isMajor) Color.White else Color.LightGray

            val startTick = Offset((center.x + radius * innerFrac * cos(rad)).toFloat(), (center.y + radius * innerFrac * sin(rad)).toFloat())
            val endTick = Offset((center.x + radius * 0.88f * cos(rad)).toFloat(), (center.y + radius * 0.88f * sin(rad)).toFloat())
            drawLine(color = tickColor, start = startTick, end = endTick, strokeWidth = strokeW, cap = StrokeCap.Round)

            // Geen "0" printen, net als het origineel - de schaal begint zichtbaar bij 20
            if (isMajor) {
                val labelR = radius * 0.685f
                drawLabel(kphVal.toString(), (center.x + labelR * cos(rad)).toFloat(), (center.y + labelR * sin(rad)).toFloat() + radius * 0.03f, radius * 0.11f, VdoIvory)
            }
            kphVal += 2
        }

        // Rode markering bij 100 km/h
        val redRad = Math.toRadians(angleForKph(100f).toDouble())
        drawLine(
            color = VdoRedline,
            start = Offset((center.x + radius * 0.81f * cos(redRad)).toFloat(), (center.y + radius * 0.81f * sin(redRad)).toFloat()),
            end = Offset((center.x + radius * 0.88f * cos(redRad)).toFloat(), (center.y + radius * 0.88f * sin(redRad)).toFloat()),
            strokeWidth = radius * 0.02f
        )

        drawLabel("km/h", center.x, center.y - radius * 0.42f, radius * 0.12f, Color.LightGray)
        drawLabel("VDO", center.x, center.y + radius * 0.55f, radius * 0.10f, Color.LightGray, bold = true)

        // Mechanische kilometerteller: losse vakjes per cijfer, boven de naaldas (zoals het origineel).
        // Getekend VOOR de naald, zodat de naald er letterlijk overheen loopt (zoals in het echt).
        // 5 cijfers hele kilometers + 1 (lichter) cijfer tienden km, net als een echte odometer.
        val totalTenths = (totalKm * 10f).toLong().coerceIn(0L, 999999L)
        val digits = totalTenths.toString().padStart(6, '0')
        val cellW = radius * 0.105f
        val cellH = radius * 0.15f
        val gap = radius * 0.008f
        val totalW = digits.length * cellW + (digits.length - 1) * gap
        val boxCenterY = center.y - radius * 0.24f
        val boxTop = boxCenterY - cellH / 2f
        var cellX = center.x - totalW / 2f

        drawRoundRect(
            color = Color(0xFF0D0D0D),
            topLeft = Offset(cellX - radius * 0.02f, boxTop - radius * 0.02f),
            size = Size(totalW + radius * 0.04f, cellH + radius * 0.04f),
            cornerRadius = CornerRadius(radius * 0.015f),
            style = Stroke(width = radius * 0.006f)
        )
        digits.forEachIndexed { index, digit ->
            val isLast = index == digits.lastIndex
            drawRoundRect(
                color = if (isLast) Color(0xFF3A3A3A) else Color(0xFF1A1A1A),
                topLeft = Offset(cellX, boxTop),
                size = Size(cellW, cellH),
                cornerRadius = CornerRadius(radius * 0.01f)
            )
            drawLabel(digit.toString(), cellX + cellW / 2f, boxCenterY + cellH * 0.28f, cellH * 0.62f, Color.White)
            cellX += cellW + gap
        }

        val targetAngle = angleForKph(animatedKph.coerceIn(0f, maxKph))
        taperedNeedle(center, targetAngle, radius * 0.80f, radius * 0.028f, VdoIvory)
        drawCircle(brush = chromeBrush(center, radius * 0.16f), radius = radius * 0.16f, center = center)
        drawCircle(color = VdoGreyCap, radius = radius * 0.12f, center = center)
    }
}

// ---------- Klok ----------

@Composable
fun VdoClock() {
    var now by remember { mutableStateOf(Calendar.getInstance()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Calendar.getInstance()
            kotlinx.coroutines.delay(1000L)
        }
    }
    val hour = now.get(Calendar.HOUR)
    val minute = now.get(Calendar.MINUTE)
    val second = now.get(Calendar.SECOND)
    val hourAngle = -90f + hour * 30f + minute * 0.5f
    val minuteAngle = -90f + minute * 6f + second * 0.1f

    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = (size.width / 2) * 0.90f

        bezel(center, radius)

        // 60 strepen: elk uur (om de 5) dik, ertussen 4 dunne minuutstreepjes
        for (i in 0..59) {
            val angleDeg = i * 6f - 90f
            val rad = Math.toRadians(angleDeg.toDouble())
            val isHour = i % 5 == 0
            val inner = if (isHour) radius * 0.71f else radius * 0.78f
            val startTick = Offset((center.x + inner * cos(rad)).toFloat(), (center.y + inner * sin(rad)).toFloat())
            val endTick = Offset((center.x + radius * 0.84f * cos(rad)).toFloat(), (center.y + radius * 0.84f * sin(rad)).toFloat())
            drawLine(color = Color.White, start = startTick, end = endTick, strokeWidth = if (isHour) radius * 0.03f else radius * 0.010f)
        }

        // Alleen 12, 3, 6 en 9 als cijfer
        for (h in intArrayOf(12, 3, 6, 9)) {
            val angleDeg = (h % 12) * 30f - 90f
            val rad = Math.toRadians(angleDeg.toDouble())
            val labelR = radius * 0.55f
            drawLabel(h.toString(), (center.x + labelR * cos(rad)).toFloat(), (center.y + labelR * sin(rad)).toFloat() + radius * 0.05f, radius * 0.15f, VdoIvory)
        }

        // VDO tussen de 6 en het streepje eronder; Kienzle tussen de 12 en het streepje erboven
        drawLabel("VDO", center.x, center.y + radius * 0.665f, radius * 0.085f, Color.LightGray, bold = true)
        drawLabel("Kienzle", center.x, center.y - radius * 0.615f, radius * 0.075f, Color.LightGray, bold = false)

        taperedNeedle(center, hourAngle, radius * 0.48f, radius * 0.05f, VdoIvory)
        taperedNeedle(center, minuteAngle, radius * 0.62f, radius * 0.04f, VdoIvory)
        drawCircle(color = VdoBlack, radius = radius * 0.09f, center = center)
    }
}

// ---------- Toerenteller combo ----------

@Composable
fun VdoTachometerCombo(
    currentRpm: Float,
    fuelLevel: Float,
    temperature: Float,
    blinkerOn: Boolean = false,
    oelOn: Boolean = false,
    ladungOn: Boolean = false,
    fernlichtOn: Boolean = false
) {
    val animatedRpm by animateFloatAsState(targetValue = currentRpm)
    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = (size.width / 2) * 0.90f

        bezel(center, radius)

        // 5 vierkante controlelampjes: 4 onderin, gelijk verdeeld tussen tank- en tempmeter, 1 bovenin
        squareLamp(center, radius, 270f, fernlichtOn, VdoBlue, "FERNLICHT")            // boven, midden
        squareLamp(center, radius, 130f, fuelLevel < 0.1f, Color.White, "TANK")        // onder, links: tank leeg
        squareLamp(center, radius, 110f, blinkerOn, VdoGreen, "BLINKER")               // onder
        squareLamp(center, radius, 70f, oelOn, VdoOrangeNeedle, "OEL")                 // onder
        squareLamp(center, radius, 50f, ladungOn, VdoRedline, "LADUNG")                // onder, rechts

        // TEMP-boog rechts: van onder (blauw) via wit naar boven (rood)
        arcGauge(center, radius, 30f, -90f, ((temperature - 50f) / 80f).coerceIn(0f, 1f), "TEMP", Color(0xFF3D7FD6), VdoRedline, Color.White)

        // TANK-boog links: van onder (leeg/R) naar boven (vol/V), neutrale baan met rode zone bij R
        arcGauge(center, radius, 150f, 90f, fuelLevel.coerceIn(0f, 1f), "TANK", Color(0xFF2A2A2A), Color(0xFF2A2A2A))
        drawArc(
            color = VdoRedline,
            startAngle = 150f,
            sweepAngle = 14f,
            useCenter = false,
            topLeft = Offset(center.x - radius * 0.73f, center.y - radius * 0.73f),
            size = Size(radius * 1.46f, radius * 1.46f),
            style = Stroke(width = radius * 0.045f)
        )
        // 5 schaalstreepjes op de tankmeter: R (dik, rood) - dun - half (dik) - dun - V (dik)
        val tankAngles = listOf(150f, 172.5f, 195f, 217.5f, 240f)
        val tankLabels = listOf("R", null, "½", null, "V")
        tankAngles.forEachIndexed { idx, angle ->
            val rad = Math.toRadians(angle.toDouble())
            val thick = idx % 2 == 0
            val halfW = if (thick) radius * 0.075f else radius * 0.045f
            val arcR = radius * 0.73f
            drawLine(
                color = if (idx == 0) VdoRedline else Color.White,
                start = Offset((center.x + (arcR - halfW) * cos(rad)).toFloat(), (center.y + (arcR - halfW) * sin(rad)).toFloat()),
                end = Offset((center.x + (arcR + halfW) * cos(rad)).toFloat(), (center.y + (arcR + halfW) * sin(rad)).toFloat()),
                strokeWidth = if (thick) radius * 0.012f else radius * 0.006f
            )
            tankLabels[idx]?.let { lbl ->
                val lblR = arcR - radius * 0.105f
                drawLabel(lbl, (center.x + lblR * cos(rad)).toFloat(), (center.y + lblR * sin(rad)).toFloat(), radius * 0.06f, if (lbl == "R") VdoRedline else VdoIvory)
            }
        }

        drawLabel("VDO", center.x, center.y + radius * 0.80f, radius * 0.07f, Color.Gray, bold = false)

        // Middelste zwarte plaatje met de chrome knop: hierin zit de (extra) toerenteller
        val knobR = radius * 0.56f
        val tStart = 200f
        val tSweep = 140f
        val maxRpm = 8f
        for (i in 0..8) {
            val tickAngle = tStart + (i / maxRpm) * tSweep
            val rad = Math.toRadians(tickAngle.toDouble())
            val startTick = Offset((center.x + knobR * 0.62f * cos(rad)).toFloat(), (center.y + knobR * 0.62f * sin(rad)).toFloat())
            val endTick = Offset((center.x + knobR * 0.80f * cos(rad)).toFloat(), (center.y + knobR * 0.80f * sin(rad)).toFloat())
            drawLine(color = if (i >= 6) VdoRedline else Color.White, start = startTick, end = endTick, strokeWidth = radius * 0.011f, cap = StrokeCap.Round)
            if (i % 2 == 0) {
                val labelR = knobR * 0.46f
                drawLabel(i.toString(), (center.x + labelR * cos(rad)).toFloat(), (center.y + labelR * sin(rad)).toFloat() + radius * 0.020f, radius * 0.068f, VdoIvory)
            }
        }
        drawLabel("RPM x1000", center.x, center.y - knobR * 0.05f, radius * 0.050f, Color.Gray, bold = false)

        val targetAngle = tStart + (animatedRpm.coerceIn(0f, maxRpm) / maxRpm) * tSweep
        taperedNeedle(center, targetAngle, knobR * 0.72f, radius * 0.016f, VdoIvory)

        // Chrome knop als naaf
        drawCircle(brush = chromeBrush(center, radius * 0.13f), radius = radius * 0.13f, center = center)
        drawCircle(color = VdoGreyCap, radius = radius * 0.10f, center = center)
    }
}

// Vierkant controlelampje, tangentieel gedraaid op de rand, met een label ernaast
private fun DrawScope.squareLamp(center: Offset, gaugeRadius: Float, angleDeg: Float, on: Boolean, color: Color, label: String) {
    val rad = Math.toRadians(angleDeg.toDouble())
    val midR = gaugeRadius * 0.63f
    val pos = Offset(center.x + (midR * cos(rad)).toFloat(), center.y + (midR * sin(rad)).toFloat())
    val lampSize = gaugeRadius * 0.17f

    rotate(degrees = angleDeg + 90f, pivot = pos) {
        drawRoundRect(
            color = Color(0xFF0A0A0A),
            topLeft = Offset(pos.x - lampSize / 2f - lampSize * 0.06f, pos.y - lampSize / 2f - lampSize * 0.06f),
            size = Size(lampSize * 1.12f, lampSize * 1.12f),
            cornerRadius = CornerRadius(lampSize * 0.12f)
        )
        drawRoundRect(
            color = if (on) color else Color(0xFF1C1C1C),
            topLeft = Offset(pos.x - lampSize / 2f, pos.y - lampSize / 2f),
            size = Size(lampSize, lampSize),
            cornerRadius = CornerRadius(lampSize * 0.08f)
        )
        if (!on) {
            // gearceerd patroon voor de "uit"-stand, zoals het origineel
            var offset = -lampSize / 2f
            while (offset < lampSize / 2f) {
                drawLine(
                    color = color.copy(alpha = 0.55f),
                    start = Offset(pos.x - lampSize / 2f, pos.y + offset),
                    end = Offset(pos.x + lampSize / 2f, pos.y + offset),
                    strokeWidth = lampSize * 0.05f
                )
                offset += lampSize * 0.22f
            }
        }
    }

    val labelR = gaugeRadius * 0.79f
    drawCurvedLabel(label, center, labelR, angleDeg, gaugeRadius * 0.058f, Color.LightGray)
}

// Tekst die de ronding van de meter volgt (tangentieel geroteerd), rechtop leesbaar
private fun DrawScope.drawCurvedLabel(text: String, center: Offset, r: Float, angleDeg: Float, sizePx: Float, color: Color, bold: Boolean = false) {
    val rad = Math.toRadians(angleDeg.toDouble())
    val pos = Offset((center.x + r * cos(rad)).toFloat(), (center.y + r * sin(rad)).toFloat())
    val inBottomHalf = angleDeg.mod(360f) in 1f..179f
    val rot = if (inBottomHalf) angleDeg - 90f else angleDeg + 90f
    rotate(degrees = rot, pivot = pos) {
        drawLabel(text, pos.x, pos.y, sizePx, color, bold)
    }
}

// Gebogen schaalstrip (TANK / TEMP) met kleurverloop en een streepje als wijzer
private fun DrawScope.arcGauge(center: Offset, gaugeRadius: Float, startAngle: Float, sweepTotal: Float, fraction: Float, label: String, lowColor: Color, highColor: Color, midColor: Color? = null) {
    val arcR = gaugeRadius * 0.73f
    val strokeW = gaugeRadius * 0.045f
    val topLeft = Offset(center.x - arcR, center.y - arcR)
    val arcSize = Size(arcR * 2f, arcR * 2f)

    drawArc(color = Color(0xFF262626), startAngle = startAngle, sweepAngle = sweepTotal, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(width = strokeW))

    if (midColor != null) {
        // Vloeiend verloop laag -> midden -> hoog, benaderd met kleine boogsegmentjes.
        // Wit domineert het middenstuk, blauw/rood blijven beperkt tot de uiteinden.
        val segments = 24
        for (s in 0 until segments) {
            val t0 = s / segments.toFloat()
            val t1 = (s + 1) / segments.toFloat()
            val tMid = (t0 + t1) / 2f
            val segColor = when {
                tMid < 0.32f -> lerpColor(lowColor, midColor, tMid / 0.32f)
                tMid > 0.68f -> lerpColor(midColor, highColor, (tMid - 0.68f) / 0.32f)
                else -> midColor
            }
            drawArc(
                color = segColor,
                startAngle = startAngle + t0 * sweepTotal,
                sweepAngle = (t1 - t0) * sweepTotal,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeW)
            )
        }
    } else {
        drawArc(color = lowColor, startAngle = startAngle, sweepAngle = sweepTotal * 0.5f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(width = strokeW))
        drawArc(color = highColor, startAngle = startAngle + sweepTotal * 0.5f, sweepAngle = sweepTotal * 0.5f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(width = strokeW))
    }

    val markerAngle = startAngle + fraction * sweepTotal
    val mRad = Math.toRadians(markerAngle.toDouble())
    val innerR = arcR - strokeW * 1.53f
    val outerR = arcR + strokeW * 1.53f
    drawLine(
        color = VdoOrangeNeedle,
        start = Offset((center.x + innerR * cos(mRad)).toFloat(), (center.y + innerR * sin(mRad)).toFloat()),
        end = Offset((center.x + outerR * cos(mRad)).toFloat(), (center.y + outerR * sin(mRad)).toFloat()),
        strokeWidth = strokeW * 0.55f,
        cap = StrokeCap.Round
    )

    val midAngle = startAngle + sweepTotal / 2f
    val labelR = gaugeRadius * 0.79f
    drawCurvedLabel(label, center, labelR, midAngle, gaugeRadius * 0.058f, Color.LightGray)
}
