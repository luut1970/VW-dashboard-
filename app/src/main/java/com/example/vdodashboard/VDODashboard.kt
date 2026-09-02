package com.example.vdodashboard

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin

// Afmetingen van de brongfoto (gti_dashboard_bg) - alle posities hieronder zijn
// fracties van deze afmetingen, dus onafhankelijk van de uiteindelijke schermgrootte.
private const val IMG_W = 4096f
private const val IMG_H = 2304f

val NeedleColor = Color(0xFFE7C79A)
val LedGreen = Color(0xFF3FC65A)
val LedRed = Color(0xFFD62B1F)
val LedBlue = Color(0xFF3D7FD6)
val LcdTextColor = Color(0xFF3A4A46)
val OdoCellBg = Color(0xFF141414)
val OdoDigitColor = Color(0xFFEDEDED)

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
    val animatedKph by animateFloatAsState(targetValue = kph)
    val animatedRpm by animateFloatAsState(targetValue = rpm)

    var now by remember { mutableStateOf(Calendar.getInstance()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Calendar.getInstance()
            kotlinx.coroutines.delay(1000L)
        }
    }
    val hh = now.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
    val mm = now.get(Calendar.MINUTE).toString().padStart(2, '0')

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(Color.Black).padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        val imgAspect = IMG_W / IMG_H
        var boxW = maxWidth
        var boxH = boxW / imgAspect
        if (boxH > maxHeight) {
            boxH = maxHeight
            boxW = boxH * imgAspect
        }

        Box(modifier = Modifier.size(boxW, boxH)) {
            // Foto als achtergrond: bezels, cijfers, iconen, tekst - alles statisch
            Image(
                painter = painterResource(R.drawable.gti_dashboard_bg),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )

            // Alleen de bewegende onderdelen worden er overheen getekend
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                fun fx(f: Float) = f * w
                fun fy(f: Float) = f * h

                // ---- Snelheidsmeter: 20-260 km/h, blanco stukje voor 0-20 ----
                run {
                    val hub = Offset(fx(0.2485f), fy(0.5182f))
                    val startAngle = 135f
                    val sweepAngle = 270f
                    val maxKph = 260f
                    val zeroGap = 12f
                    fun angleForKph(v: Float): Float =
                        if (v <= 20f) (startAngle - zeroGap) + (v / 20f) * zeroGap
                        else startAngle + ((v - 20f) / 240f) * sweepAngle
                    val angle = angleForKph(animatedKph.coerceIn(0f, maxKph))
                    drawNeedle(hub, angle, 0.1853f * w, w * 0.009f, NeedleColor)
                }

                // ---- Toerenteller: 0-8 x1000 ----
                run {
                    val hub = Offset(fx(0.8270f), fy(0.5065f))
                    val startAngle = 135f
                    val sweepAngle = 270f
                    val maxRpm = 8f
                    val angle = startAngle + (animatedRpm.coerceIn(0f, maxRpm) / maxRpm) * sweepAngle
                    drawNeedle(hub, angle, 0.1915f * w, w * 0.009f, NeedleColor)
                }

                // ---- Temperatuurmeter: waaiert van koud (links) via midden naar heet (rechts) ----
                run {
                    val base = Offset(fx(0.5168f), fy(0.3429f))
                    val angleCold = 200f
                    val angleHot = 340f
                    val tFrac = ((temp - 40f) / 100f).coerceIn(0f, 1f)
                    val angle = angleCold + tFrac * (angleHot - angleCold)
                    drawNeedle(base, angle, 0.0501f * w, w * 0.006f, NeedleColor)
                }

                // ---- Brandstofmeter: waaiert van leeg naar vol ----
                run {
                    val base = Offset(fx(0.7349f), fy(0.7813f))
                    val angleLow = 255f
                    val angleHigh = 335f
                    val fFrac = fuel.coerceIn(0f, 1f)
                    val angle = angleLow + fFrac * (angleHigh - angleLow)
                    drawNeedle(base, angle, 0.0645f * w, w * 0.005f, NeedleColor)
                }

                // ---- 5 lampjes (bovenste rij: blinker, ladung, [onbenut], oel, fernlicht) ----
                val ledY = fy(0.4870f)
                val ledXs = listOf(0.4548f, 0.4861f, 0.5171f, 0.5481f, 0.5803f).map { fx(it) }
                val ledStates = listOf(
                    Pair(blinker, LedGreen),
                    Pair(ladung, LedRed),
                    Pair(false, Color.Black),
                    Pair(oel, LedRed),
                    Pair(fernlicht, LedBlue)
                )
                ledXs.forEachIndexed { i, x ->
                    val (on, color) = ledStates[i]
                    drawLed(Offset(x, ledY), w * 0.017f, on, color)
                }

                // ---- Digitale klok in het LCD-vak ----
                drawText(this, "$hh:$mm", fx(0.523f), fy(0.737f), w * 0.036f, LcdTextColor, mono = true)

                // ---- Kilometerteller (6 cijfers, boven de naaldas van de snelheidsmeter) ----
                drawOdometer(this, totalKm, fx(0.1599f), fy(0.4319f), fx(0.3198f - 0.1599f), fy(0.4818f - 0.4319f))
            }
        }
    }
}

// ---------- Tekenhulpjes ----------

private fun DrawScope.drawNeedle(base: Offset, angleDeg: Float, length: Float, baseWidth: Float, color: Color) {
    val rad = Math.toRadians(angleDeg.toDouble())
    val dx = cos(rad).toFloat()
    val dy = sin(rad).toFloat()
    val px = -dy
    val py = dx
    val tip = Offset(base.x + dx * length, base.y + dy * length)
    val tailLen = length * 0.14f
    val tail = Offset(base.x - dx * tailLen, base.y - dy * tailLen)
    val path = Path().apply {
        moveTo(base.x + px * baseWidth, base.y + py * baseWidth)
        lineTo(tip.x, tip.y)
        lineTo(base.x - px * baseWidth, base.y - py * baseWidth)
        lineTo(tail.x - px * baseWidth * 0.6f, tail.y - py * baseWidth * 0.6f)
        lineTo(tail.x + px * baseWidth * 0.6f, tail.y + py * baseWidth * 0.6f)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawLed(center: Offset, r: Float, on: Boolean, color: Color) {
    drawCircle(color = Color(0xFF1A1A1A), radius = r * 1.15f, center = center)
    drawCircle(color = if (on) color else color.copy(alpha = 0.25f), radius = r, center = center)
    drawCircle(color = Color.White.copy(alpha = if (on) 0.5f else 0.10f), radius = r * 0.32f, center = Offset(center.x - r * 0.3f, center.y - r * 0.3f))
}

private fun drawText(ds: DrawScope, text: String, x: Float, y: Float, sizePx: Float, color: Color, mono: Boolean = false) {
    ds.drawContext.canvas.nativeCanvas.drawText(
        text, x, y,
        Paint().apply {
            isAntiAlias = true
            this.color = color.toArgb()
            textSize = sizePx
            textAlign = Paint.Align.CENTER
            typeface = if (mono) Typeface.MONOSPACE else Typeface.create("sans-serif-condensed", Typeface.BOLD)
        }
    )
}

private fun drawOdometer(ds: DrawScope, totalKm: Float, left: Float, top: Float, boxW: Float, boxH: Float) {
    val totalTenths = (totalKm * 10f).toLong().coerceIn(0L, 999999L)
    val digits = totalTenths.toString().padStart(6, '0')
    val cellW = boxW / digits.length
    var x = left
    digits.forEach { ch ->
        ds.drawRect(color = OdoCellBg, topLeft = Offset(x, top), size = Size(cellW * 0.92f, boxH))
        drawText(ds, ch.toString(), x + cellW * 0.46f, top + boxH * 0.78f, boxH * 0.72f, OdoDigitColor, mono = true)
        x += cellW
    }
}
