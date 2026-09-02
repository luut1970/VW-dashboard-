package com.example.vdodashboard

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin

// ---------- Kleuren (VW Golf Mk2 GTI / VDO-stijl: vlak, antraciet, geen chrome) ----------
val GtiFace = Color(0xFF232326)
val GtiAnthraciteDark = Color(0xFF17171A)
val GtiAnthraciteMid = Color(0xFF2A2A2D)
val GtiRing = Color(0xFFB0B0B0)
val GtiNeedle = Color(0xFFE7C79A)
val GtiAmber = Color(0xFFFF9F1C)
val GtiRed = Color(0xFFD62B1F)
val GtiGreen = Color(0xFF3FC65A)
val GtiBlue = Color(0xFF3D7FD6)
val GtiWhite = Color(0xFFECECEC)
val GtiLcdBg = Color(0xFF3A4A3A)
val GtiLcdFg = Color(0xFF1C2A1C)

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
        val availableWidth = maxWidth / 2.7f
        val availableHeight = maxHeight * 0.95f
        val gaugeSize = if (availableWidth < availableHeight) availableWidth else availableHeight

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Top
        ) {
            Box(modifier = Modifier.size(gaugeSize)) { VdoSpeedometerGti(currentKph = kph, totalKm = totalKm) }
            Box(modifier = Modifier.size(gaugeSize * 0.42f, gaugeSize)) {
                VdoCenterPanelGti(temperature = temp, blinkerOn = blinker, oelOn = oel, ladungOn = ladung, fernlichtOn = fernlicht)
            }
            Box(modifier = Modifier.size(gaugeSize)) { VdoTachometerGti(currentRpm = rpm, fuelLevel = fuel) }
        }
    }
}

// ---------- Gedeelde tekenhulpjes ----------

private fun DrawScope.gtiFace(c: Offset, r: Float) {
    val safeR = r.coerceAtLeast(1f)
    drawCircle(color = GtiFace, radius = safeR, center = c)
    drawCircle(color = GtiRing, radius = safeR, center = c, style = Stroke(width = safeR * 0.014f))
}

private fun DrawScope.drawLabel(text: String, x: Float, y: Float, sizePx: Float, color: Color, bold: Boolean = true, mono: Boolean = false) {
    drawContext.canvas.nativeCanvas.drawText(
        text, x, y,
        Paint().apply {
            isAntiAlias = true
            this.color = color.toArgb()
            textSize = sizePx
            textAlign = Paint.Align.CENTER
            typeface = if (mono) Typeface.MONOSPACE else Typeface.create("sans-serif-condensed", if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
    )
}

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
fun VdoSpeedometerGti(currentKph: Float, totalKm: Float = 0f) {
    val animatedKph by animateFloatAsState(targetValue = currentKph)
    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = (size.width / 2) * 0.90f

        gtiFace(center, radius)

        val startAngle = 135f
        val sweepAngle = 270f
        val maxKph = 260f
        // Net als het origineel: geen "0" op de schaal, een klein blanco stukje voor 0-20 km/h,
        // en de zichtbare schaal 20-260 lineair en symmetrisch over de rest van de 270°.
        val zeroGap = 12f
        fun angleForKph(v: Float): Float =
            if (v <= 20f) (startAngle - zeroGap) + (v / 20f) * zeroGap
            else startAngle + ((v - 20f) / 240f) * sweepAngle

        var kphVal = 20
        while (kphVal <= 260) {
            val angle = angleForKph(kphVal.toFloat())
            val rad = Math.toRadians(angle.toDouble())
            val isMajor = kphVal % 20 == 0
            val isMid = kphVal % 10 == 0
            val innerFrac = if (isMajor) 0.80f else if (isMid) 0.83f else 0.86f
            val strokeW = if (isMajor) radius * 0.016f else if (isMid) radius * 0.010f else radius * 0.005f
            val startTick = Offset((center.x + radius * innerFrac * cos(rad)).toFloat(), (center.y + radius * innerFrac * sin(rad)).toFloat())
            val endTick = Offset((center.x + radius * 0.90f * cos(rad)).toFloat(), (center.y + radius * 0.90f * sin(rad)).toFloat())
            drawLine(color = GtiWhite, start = startTick, end = endTick, strokeWidth = strokeW, cap = StrokeCap.Round)
            if (isMajor) {
                val labelR = radius * 0.68f
                drawLabel(kphVal.toString(), (center.x + labelR * cos(rad)).toFloat(), (center.y + labelR * sin(rad)).toFloat() + radius * 0.03f, radius * 0.105f, GtiWhite)
            }
            kphVal += 5
        }

        // Rode zone net na 60 km/h
        drawArc(
            color = GtiRed,
            startAngle = angleForKph(45f),
            sweepAngle = (angleForKph(55f) - angleForKph(45f)),
            useCenter = false,
            topLeft = Offset(center.x - radius * 0.90f, center.y - radius * 0.90f),
            size = Size(radius * 1.80f, radius * 1.80f),
            style = Stroke(width = radius * 0.02f)
        )

        drawLabel("VDO", center.x, center.y - radius * 0.38f, radius * 0.07f, Color.LightGray, bold = false)
        drawLabel("km", center.x, center.y - radius * 0.28f, radius * 0.065f, Color.LightGray, bold = false)
        drawLabel("km/h", center.x, center.y + radius * 0.62f, radius * 0.11f, GtiWhite)
        drawLabel("950", center.x, center.y + radius * 0.72f, radius * 0.055f, Color.Gray, bold = false)

        // Mechanische kilometerteller boven de naaldas (echt gekoppeld aan totalKm)
        val totalTenths = (totalKm * 10f).toLong().coerceIn(0L, 999999L)
        val digits = totalTenths.toString().padStart(6, '0')
        val cellW = radius * 0.10f
        val cellH = radius * 0.145f
        val gap = radius * 0.006f
        val totalW = digits.length * cellW + (digits.length - 1) * gap
        val boxCenterY = center.y - radius * 0.20f
        val boxTop = boxCenterY - cellH / 2f
        var cellX = center.x - totalW / 2f
        drawRoundRect(
            color = GtiAnthraciteDark,
            topLeft = Offset(cellX - radius * 0.015f, boxTop - radius * 0.015f),
            size = Size(totalW + radius * 0.03f, cellH + radius * 0.03f),
            cornerRadius = CornerRadius(radius * 0.01f),
            style = Stroke(width = radius * 0.005f)
        )
        digits.forEach { digit ->
            drawRoundRect(color = GtiAnthraciteMid, topLeft = Offset(cellX, boxTop), size = Size(cellW, cellH), cornerRadius = CornerRadius(radius * 0.008f))
            drawLabel(digit.toString(), cellX + cellW / 2f, boxCenterY + cellH * 0.28f, cellH * 0.6f, Color.White)
            cellX += cellW + gap
        }

        // Decoratief tripteller-vakje onder de naaldas, zoals op het origineel
        val tripDigits = "1059"
        val tripCellW = radius * 0.075f
        val tripCellH = radius * 0.12f
        val tripGap = radius * 0.005f
        val tripTotalW = tripDigits.length * tripCellW + (tripDigits.length - 1) * tripGap
        var tripX = center.x - tripTotalW / 2f
        val tripBoxCenterY = center.y + radius * 0.28f
        val tripBoxTop = tripBoxCenterY - tripCellH / 2f
        tripDigits.forEachIndexed { i, ch ->
            val isLast = i == tripDigits.lastIndex
            drawRoundRect(
                color = GtiAnthraciteDark,
                topLeft = Offset(tripX, tripBoxTop),
                size = Size(tripCellW, tripCellH),
                cornerRadius = CornerRadius(radius * 0.008f),
                style = Stroke(width = radius * 0.004f)
            )
            drawLabel(ch.toString(), tripX + tripCellW / 2f, tripBoxCenterY + tripCellH * 0.28f, tripCellH * 0.6f, if (isLast) GtiAmber else GtiWhite)
            tripX += tripCellW + tripGap
        }

        val targetAngle = angleForKph(animatedKph.coerceIn(0f, maxKph))
        taperedNeedle(center, targetAngle, radius * 0.82f, radius * 0.026f, GtiNeedle)
        drawCircle(color = GtiAnthraciteDark, radius = radius * 0.09f, center = center)
        drawCircle(color = Color(0xFF3A3A3A), radius = radius * 0.02f, center = Offset(center.x - radius * 0.05f, center.y - radius * 0.09f))
        drawCircle(color = Color(0xFF3A3A3A), radius = radius * 0.02f, center = Offset(center.x + radius * 0.05f, center.y - radius * 0.09f))
    }
}

// ---------- Toerenteller (met brandstofmeter) ----------

@Composable
fun VdoTachometerGti(currentRpm: Float, fuelLevel: Float) {
    val animatedRpm by animateFloatAsState(targetValue = currentRpm)
    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = (size.width / 2) * 0.90f

        gtiFace(center, radius)

        val startAngle = 135f
        val sweepAngle = 270f
        val maxRpm = 8f
        for (i in 0..8) {
            val rad = Math.toRadians((startAngle + (i / maxRpm) * sweepAngle).toDouble())
            val startTick = Offset((center.x + radius * 0.80f * cos(rad)).toFloat(), (center.y + radius * 0.80f * sin(rad)).toFloat())
            val endTick = Offset((center.x + radius * 0.90f * cos(rad)).toFloat(), (center.y + radius * 0.90f * sin(rad)).toFloat())
            drawLine(color = if (i >= 6) GtiAmber else GtiWhite, start = startTick, end = endTick, strokeWidth = radius * 0.016f, cap = StrokeCap.Round)
            val labelR = radius * 0.68f
            drawLabel(i.toString(), (center.x + labelR * cos(rad)).toFloat(), (center.y + labelR * sin(rad)).toFloat() + radius * 0.03f, radius * 0.105f, GtiWhite)
            if (i < 8) {
                val mRad = Math.toRadians((startAngle + ((i + 0.5f) / maxRpm) * sweepAngle).toDouble())
                drawLine(
                    color = if (i >= 6) GtiAmber.copy(alpha = 0.7f) else Color.LightGray,
                    start = Offset((center.x + radius * 0.85f * cos(mRad)).toFloat(), (center.y + radius * 0.85f * sin(mRad)).toFloat()),
                    end = Offset((center.x + radius * 0.90f * cos(mRad)).toFloat(), (center.y + radius * 0.90f * sin(mRad)).toFloat()),
                    strokeWidth = radius * 0.006f
                )
            }
        }
        // Gearceerde zone 7-8 (extra waarschuwing)
        val hatchStart = startAngle + (7f / maxRpm) * sweepAngle
        val hatchSweep = (1f / maxRpm) * sweepAngle
        var hAngle = hatchStart
        while (hAngle < hatchStart + hatchSweep) {
            val rad = Math.toRadians(hAngle.toDouble())
            drawLine(
                color = GtiAmber.copy(alpha = 0.55f),
                start = Offset((center.x + radius * 0.80f * cos(rad)).toFloat(), (center.y + radius * 0.80f * sin(rad)).toFloat()),
                end = Offset((center.x + radius * 0.90f * cos(rad)).toFloat(), (center.y + radius * 0.90f * sin(rad)).toFloat()),
                strokeWidth = radius * 0.006f
            )
            hAngle += 3f
        }

        drawLabel("VDO", center.x, center.y - radius * 0.34f, radius * 0.075f, Color.LightGray, bold = false)
        drawLabel("1/min x1000", center.x, center.y - radius * 0.22f, radius * 0.08f, GtiWhite)

        val targetAngle = startAngle + (animatedRpm.coerceIn(0f, maxRpm) / maxRpm) * sweepAngle
        taperedNeedle(center, targetAngle, radius * 0.72f, radius * 0.026f, GtiNeedle)
        drawCircle(color = GtiAnthraciteDark, radius = radius * 0.09f, center = center)
        drawCircle(color = Color(0xFF3A3A3A), radius = radius * 0.02f, center = Offset(center.x - radius * 0.05f, center.y - radius * 0.09f))
        drawCircle(color = Color(0xFF3A3A3A), radius = radius * 0.02f, center = Offset(center.x + radius * 0.05f, center.y - radius * 0.09f))

        // Brandstofmeter (liggend), zelfde stijl als de temperatuurmeter
        val fuelBoxW = radius * 0.90f
        val fuelBoxH = radius * 0.22f
        val fuelBoxTop = center.y + radius * 0.44f
        val fuelBoxLeft = center.x - fuelBoxW / 2f
        drawRoundRect(color = GtiAnthraciteMid, topLeft = Offset(fuelBoxLeft, fuelBoxTop), size = Size(fuelBoxW, fuelBoxH), cornerRadius = CornerRadius(radius * 0.05f))
        val lowFuel = fuelLevel < 0.1f
        drawCircle(color = if (lowFuel) GtiAmber else GtiAmber.copy(alpha = 0.25f), radius = radius * 0.028f, center = Offset(center.x, fuelBoxTop + fuelBoxH * 0.28f))
        val fScaleY = fuelBoxTop + fuelBoxH * 0.68f
        drawLine(color = GtiWhite.copy(alpha = 0.4f), start = Offset(fuelBoxLeft + fuelBoxW * 0.15f, fScaleY), end = Offset(fuelBoxLeft + fuelBoxW * 0.85f, fScaleY), strokeWidth = radius * 0.008f)
        val fFrac = fuelLevel.coerceIn(0f, 1f)
        val fNeedleX = fuelBoxLeft + fuelBoxW * 0.15f + fFrac * (fuelBoxW * 0.70f)
        drawLine(
            color = GtiNeedle,
            start = Offset(center.x, fuelBoxTop + fuelBoxH * 1.05f),
            end = Offset(fNeedleX, fScaleY - radius * 0.035f),
            strokeWidth = radius * 0.15f,
            cap = StrokeCap.Round
        )
        drawLabel("", fuelBoxLeft + fuelBoxW * 0.15f, fuelBoxTop + fuelBoxH + radius * 0.045f, radius * 0.045f, Color.Gray, bold = false)
        drawLabel("", fuelBoxLeft + fuelBoxW * 0.85f, fuelBoxTop + fuelBoxH + radius * 0.045f, radius * 0.045f, Color.Gray, bold = false)
    }
}

// ---------- Middenpaneel: temperatuur, lampjes, digitale klok ----------

@Composable
fun VdoCenterPanelGti(
    temperature: Float,
    blinkerOn: Boolean,
    oelOn: Boolean,
    ladungOn: Boolean,
    fernlichtOn: Boolean
) {
    var now by remember { mutableStateOf(Calendar.getInstance()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Calendar.getInstance()
            kotlinx.coroutines.delay(1000L)
        }
    }
    val hh = now.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
    val mm = now.get(Calendar.MINUTE).toString().padStart(2, '0')

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val unit = w

        drawRoundRect(color = Color(0xFF232323), topLeft = Offset(0f, 0f), size = Size(w, h), cornerRadius = CornerRadius(unit * 0.1f))
        val inset = unit * 0.022f
        drawRoundRect(color = GtiAnthraciteDark, topLeft = Offset(inset, inset), size = Size(w - inset * 2f, h - inset * 2f), cornerRadius = CornerRadius(unit * 0.085f))
        drawArc(
            color = Color.White.copy(alpha = 0.10f),
            startAngle = 20f, sweepAngle = 140f, useCenter = false,
            topLeft = Offset(inset, inset), size = Size(w - inset * 2f, h - inset * 2f),
            style = Stroke(width = unit * 0.006f)
        )

        // --- Temperatuurmeter: geen scheidingsstreep, geen tekst ---
        val tempBoxTop = h * 0.03f
        val tempBoxH = h * 0.13f
        val boxLeft = w * 0.10f
        val boxW = w * 0.80f
        drawRoundRect(color = GtiAnthraciteMid, topLeft = Offset(boxLeft, tempBoxTop), size = Size(boxW, tempBoxH), cornerRadius = CornerRadius(unit * 0.06f))

        val overheating = temperature > 110f
        val dotC = Offset(w * 0.5f, tempBoxTop + tempBoxH * 0.18f)
        drawCircle(color = GtiAnthraciteDark, radius = unit * 0.042f, center = dotC)
        drawCircle(color = if (overheating) GtiRed else GtiRed.copy(alpha = 0.30f), radius = unit * 0.033f, center = dotC)
        drawCircle(color = Color.White.copy(alpha = if (overheating) 0.55f else 0.15f), radius = unit * 0.011f, center = Offset(dotC.x - unit * 0.012f, dotC.y - unit * 0.012f))

        // Naald: spil zit lager/naar achteren, waaiert van links(koud) via boven(midden, net onder
        // het lampje) naar rechts(heet)
        val needleBase = Offset(w * 0.5f, tempBoxTop + tempBoxH * 1.55f)
        val needleLen = needleBase.y - dotC.y - unit * 0.05f
        val angleCold = 200f
        val angleHot = 340f
        val tFrac = ((temperature - 40f) / 100f).coerceIn(0f, 1f)
        val needleAngle = angleCold + tFrac * (angleHot - angleCold)
        val nRad = Math.toRadians(needleAngle.toDouble())
        val needleTip = Offset(needleBase.x + needleLen * cos(nRad).toFloat(), needleBase.y + needleLen * sin(nRad).toFloat())

        val coldDir = Math.toRadians(angleCold.toDouble())
        val coldC = Offset(needleBase.x + (needleLen * 0.78f) * cos(coldDir).toFloat(), needleBase.y + (needleLen * 0.78f) * sin(coldDir).toFloat())
        val pw = unit * 0.07f; val ph = unit * 0.07f; val skew = unit * 0.028f
        drawPath(
            Path().apply {
                moveTo(coldC.x - pw / 2f + skew, coldC.y - ph / 2f)
                lineTo(coldC.x + pw / 2f + skew, coldC.y - ph / 2f)
                lineTo(coldC.x + pw / 2f - skew, coldC.y + ph / 2f)
                lineTo(coldC.x - pw / 2f - skew, coldC.y + ph / 2f)
                close()
            },
            GtiWhite
        )
        val hotDir = Math.toRadians(angleHot.toDouble())
        val hotC = Offset(needleBase.x + (needleLen * 0.78f) * cos(hotDir).toFloat(), needleBase.y + (needleLen * 0.78f) * sin(hotDir).toFloat())
        drawLine(color = GtiWhite, start = Offset(hotC.x - unit * 0.026f, hotC.y + unit * 0.042f), end = Offset(hotC.x + unit * 0.026f, hotC.y - unit * 0.042f), strokeWidth = unit * 0.015f, cap = StrokeCap.Round)

        val thermoC = Offset(w * 0.5f, tempBoxTop + tempBoxH * 0.62f)
        drawRoundRect(color = GtiWhite, topLeft = Offset(thermoC.x - unit * 0.008f, thermoC.y - unit * 0.045f), size = Size(unit * 0.016f, unit * 0.054f), cornerRadius = CornerRadius(unit * 0.008f))
        drawCircle(color = GtiWhite, radius = unit * 0.018f, center = Offset(thermoC.x, thermoC.y + unit * 0.02f))
        drawLine(color = GtiWhite, start = Offset(thermoC.x - unit * 0.04f, thermoC.y + unit * 0.042f), end = Offset(thermoC.x + unit * 0.04f, thermoC.y + unit * 0.042f), strokeWidth = unit * 0.006f)
        listOf(-0.11f, -0.075f, 0.075f, 0.11f).forEach { dx ->
            drawCircle(color = GtiWhite.copy(alpha = 0.5f), radius = unit * 0.006f, center = Offset(w * 0.5f + dx * boxW, thermoC.y))
        }

        drawLine(color = GtiNeedle, start = needleBase, end = needleTip, strokeWidth = unit * 0.02f, cap = StrokeCap.Round)

        // --- Controlelampjes: 2 rijen van 5, in een vierkant, LED-uiterlijk ---
        val gridTop = h * 0.30f
        val gridSize = w * 0.86f
        val colGap = gridSize / 5f
        val rowGap = gridSize / 5f
        val gridLeft = (w - gridSize) / 2f
        val colXs = (0 until 5).map { gridLeft + colGap * it + colGap / 2f }
        val row1Y = gridTop + rowGap * 0.5f
        val row2Y = gridTop + rowGap * 1.5f

        data class Lamp(val on: Boolean, val color: Color, val icon: (DrawScope.(Offset, Float, Color) -> Unit)?)
        val topRow = listOf(
            Lamp(blinkerOn, GtiGreen) { c, s, col -> iconArrowsBoth(c, s, col) },
            Lamp(ladungOn, GtiRed) { c, s, col -> iconBattery(c, s, col) },
            Lamp(false, Color(0xFF1A1A1A), null),
            Lamp(oelOn, GtiRed) { c, s, col -> iconOil(c, s, col) },
            Lamp(fernlichtOn, GtiBlue) { c, s, col -> iconHeadlight(c, s, col) }
        )
        val ledR = unit * 0.048f

        fun drawLed(x: Float, y: Float, on: Boolean, color: Color) {
            val center = Offset(x, y)
            val socketSize = ledR * 2.7f
            drawRoundRect(
                color = GtiAnthraciteDark,
                topLeft = Offset(x - socketSize / 2f, y - socketSize / 2f),
                size = Size(socketSize, socketSize),
                cornerRadius = CornerRadius(socketSize * 0.16f)
            )
            drawRoundRect(
                color = GtiAnthraciteMid,
                topLeft = Offset(x - socketSize / 2f, y - socketSize / 2f),
                size = Size(socketSize, socketSize),
                cornerRadius = CornerRadius(socketSize * 0.16f),
                style = Stroke(width = socketSize * 0.05f)
            )
            drawCircle(color = GtiAnthraciteDark, radius = ledR * 1.5f, center = center)
            drawCircle(color = Color(0xFF1A1A1A), radius = ledR * 1.25f, center = center)
            drawCircle(
                color = if (on) color else color.copy(alpha = 0.22f),
                radius = ledR,
                center = center
            )
            drawCircle(color = Color.White.copy(alpha = if (on) 0.55f else 0.12f), radius = ledR * 0.35f, center = Offset(x - ledR * 0.32f, y - ledR * 0.32f))
        }

        colXs.forEachIndexed { i, x ->
            val lamp = topRow[i]
            drawLed(x, row1Y, lamp.on, lamp.color)
            lamp.icon?.let { drawFn ->
                val iconCenter = Offset(x, row1Y - ledR * 2.4f)
                val insetSize = unit * 0.17f
                drawRoundRect(
                    color = GtiAnthraciteDark,
                    topLeft = Offset(iconCenter.x - insetSize / 2f, iconCenter.y - insetSize / 2f),
                    size = Size(insetSize, insetSize),
                    cornerRadius = CornerRadius(insetSize * 0.2f)
                )
                drawFn(this, iconCenter, unit * 0.06f, Color.LightGray)
            }
            drawLed(x, row2Y, false, GtiAnthraciteDark)
        }

        // --- Leeg trimstrookje, zoals op het origineel (geen functie) ---
        val blankTop = h * 0.50f
        val blankH = h * 0.055f
        drawRoundRect(color = GtiAnthraciteDark, topLeft = Offset(w * 0.10f, blankTop), size = Size(w * 0.80f, blankH), cornerRadius = CornerRadius(unit * 0.03f))

        // --- Icoontjes boven de LCD: klokje, "km", "km/h" ---
        val lcdTop = h * 0.62f
        val lcdH = w * 0.50f
        val iconRowY = lcdTop - unit * 0.06f
        val clockC = Offset(w * 0.20f, iconRowY)
        drawCircle(color = Color.Gray, radius = unit * 0.028f, center = clockC, style = Stroke(width = unit * 0.006f))
        drawLine(color = Color.Gray, start = clockC, end = Offset(clockC.x, clockC.y - unit * 0.016f), strokeWidth = unit * 0.005f)
        drawLine(color = Color.Gray, start = clockC, end = Offset(clockC.x + unit * 0.012f, clockC.y), strokeWidth = unit * 0.005f)
        drawLabel("km", w * 0.5f, iconRowY + unit * 0.02f, unit * 0.045f, Color.Gray, bold = false)
        drawLabel("km/h", w * 0.80f, iconRowY + unit * 0.02f, unit * 0.045f, Color.Gray, bold = false)

        drawRoundRect(color = GtiLcdBg, topLeft = Offset(w * 0.12f, lcdTop), size = Size(w * 0.76f, lcdH), cornerRadius = CornerRadius(unit * 0.03f))
        drawLabel("$hh:$mm", w * 0.5f, lcdTop + lcdH * 0.5f + unit * 0.05f, unit * 0.15f, GtiLcdFg, bold = true, mono = true)

        // --- Icoontjes onder de LCD: l/100km, olie+°C, zon+°C ---
        val bottomRowY = lcdTop + lcdH + unit * 0.06f
        drawLabel("l/100km", w * 0.22f, bottomRowY + unit * 0.015f, unit * 0.04f, Color.Gray, bold = false)
        iconOil(Offset(w * 0.52f, bottomRowY), unit * 0.035f, Color.Gray)
        drawLabel("°C", w * 0.58f, bottomRowY + unit * 0.015f, unit * 0.04f, Color.Gray, bold = false)
        val sunC = Offset(w * 0.78f, bottomRowY)
        drawCircle(color = Color.Gray, radius = unit * 0.018f, center = sunC)
        for (a in 0 until 8) {
            val rad = Math.toRadians((a * 45).toDouble())
            drawLine(
                color = Color.Gray,
                start = Offset((sunC.x + unit * 0.022f * cos(rad)).toFloat(), (sunC.y + unit * 0.022f * sin(rad)).toFloat()),
                end = Offset((sunC.x + unit * 0.032f * cos(rad)).toFloat(), (sunC.y + unit * 0.032f * sin(rad)).toFloat()),
                strokeWidth = unit * 0.004f
            )
        }
        drawLabel("°C", w * 0.88f, bottomRowY + unit * 0.015f, unit * 0.04f, Color.Gray, bold = false)
    }
}

// ---------- Kleine symboolpictogrammen voor de lampjes ----------

private fun DrawScope.iconArrowsBoth(c: Offset, s: Float, color: Color) {
    val left = Path().apply {
        moveTo(c.x - s * 0.05f, c.y - s * 0.22f)
        lineTo(c.x - s * 0.5f, c.y - s * 0.22f)
        lineTo(c.x - s * 0.5f, c.y - s * 0.4f)
        lineTo(c.x - s * 0.85f, c.y)
        lineTo(c.x - s * 0.5f, c.y + s * 0.4f)
        lineTo(c.x - s * 0.5f, c.y + s * 0.22f)
        lineTo(c.x - s * 0.05f, c.y + s * 0.22f)
        close()
    }
    val right = Path().apply {
        moveTo(c.x + s * 0.05f, c.y - s * 0.22f)
        lineTo(c.x + s * 0.5f, c.y - s * 0.22f)
        lineTo(c.x + s * 0.5f, c.y - s * 0.4f)
        lineTo(c.x + s * 0.85f, c.y)
        lineTo(c.x + s * 0.5f, c.y + s * 0.4f)
        lineTo(c.x + s * 0.5f, c.y + s * 0.22f)
        lineTo(c.x + s * 0.05f, c.y + s * 0.22f)
        close()
    }
    drawPath(left, color)
    drawPath(right, color)
}

private fun DrawScope.iconBattery(c: Offset, s: Float, color: Color) {
    drawRoundRect(color = color, topLeft = Offset(c.x - s * 0.4f, c.y - s * 0.28f), size = Size(s * 0.8f, s * 0.56f), cornerRadius = CornerRadius(s * 0.06f), style = Stroke(width = s * 0.08f))
    drawRect(color = color, topLeft = Offset(c.x - s * 0.16f, c.y - s * 0.42f), size = Size(s * 0.11f, s * 0.14f))
    drawRect(color = color, topLeft = Offset(c.x + s * 0.05f, c.y - s * 0.42f), size = Size(s * 0.11f, s * 0.14f))
    drawLine(color = color, start = Offset(c.x - s * 0.16f, c.y), end = Offset(c.x + s * 0.16f, c.y), strokeWidth = s * 0.07f)
    drawLine(color = color, start = Offset(c.x, c.y - s * 0.1f), end = Offset(c.x, c.y + s * 0.1f), strokeWidth = s * 0.07f)
}

private fun DrawScope.iconOil(c: Offset, s: Float, color: Color) {
    val path = Path().apply {
        moveTo(c.x, c.y - s * 0.5f)
        cubicTo(c.x + s * 0.48f, c.y + s * 0.05f, c.x + s * 0.28f, c.y + s * 0.5f, c.x, c.y + s * 0.5f)
        cubicTo(c.x - s * 0.28f, c.y + s * 0.5f, c.x - s * 0.48f, c.y + s * 0.05f, c.x, c.y - s * 0.5f)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.iconHeadlight(c: Offset, s: Float, color: Color) {
    drawArc(
        color = color,
        startAngle = 90f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(c.x - s * 0.4f, c.y - s * 0.35f),
        size = Size(s * 0.7f, s * 0.7f)
    )
    for (i in -1..1) {
        val yOff = i * s * 0.22f
        drawLine(color = color, start = Offset(c.x + s * 0.32f, c.y + yOff), end = Offset(c.x + s * 0.6f, c.y + yOff * 1.4f), strokeWidth = s * 0.06f, cap = StrokeCap.Round)
    }
}
