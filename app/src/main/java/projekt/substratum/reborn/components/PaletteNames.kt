/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */
package projekt.substratum.reborn.components

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sqrt

/** The words a palette is described with, so colour is never the only signal. */
enum class ColourName {
    BLACK, NEAR_BLACK, DARK_GREY, GREY, LIGHT_GREY, WHITE,
    RED, ORANGE, AMBER, YELLOW, LIME, GREEN, TEAL, CYAN, BLUE, INDIGO, VIOLET, PINK,
}

/** A colour in HCT: CAM16 hue and chroma, and L* tone (Material's colour space). */
data class Hct(val hue: Double, val chroma: Double, val tone: Double)

/**
 * Names a colour by its HCT hue, chroma and tone. Pure Kotlin (PaletteNamesTest): CAM16 under
 * Material's default viewing conditions, as material-color-utilities computes it.
 */
object PaletteNames {

    /** Below this chroma a colour is a grey, named by its tone */
    const val GREY_CHROMA = 16.0

    fun name(argb: Int, over: Int = 0xff000000.toInt()): ColourName {
        val hct = hct(composite(argb, over))
        if (hct.chroma < GREY_CHROMA) {
            return when {
                hct.tone < 2.5 -> ColourName.BLACK
                hct.tone < 10.0 -> ColourName.NEAR_BLACK
                hct.tone < 40.0 -> ColourName.DARK_GREY
                hct.tone < 70.0 -> ColourName.GREY
                hct.tone < 92.0 -> ColourName.LIGHT_GREY
                else -> ColourName.WHITE
            }
        }
        val h = hct.hue
        return when {
            h >= 335.0 || h < 15.0 -> ColourName.PINK
            h < 42.0 -> ColourName.RED
            h < 70.0 -> ColourName.ORANGE
            h < 98.0 -> ColourName.AMBER
            h < 120.0 -> ColourName.YELLOW
            h < 138.0 -> ColourName.LIME
            h < 170.0 -> ColourName.GREEN
            h < 235.0 -> if (h < 190.0 || hct.tone < 60.0) ColourName.TEAL else ColourName.CYAN
            h < 290.0 -> ColourName.BLUE
            h < 300.0 -> ColourName.INDIGO
            else -> ColourName.VIOLET
        }
    }

    /** [argb] over an opaque [background] (a translucent colour as it shows). */
    fun composite(argb: Int, background: Int): Int {
        val a = (argb ushr 24) and 0xff
        if (a == 0xff) return argb
        val f = a / 255.0
        fun mix(shift: Int): Int {
            val c = (argb shr shift) and 0xff
            val b = (background shr shift) and 0xff
            return Math.round(c * f + b * (1 - f)).toInt().coerceIn(0, 255)
        }
        return (0xff shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    // CAM16 (material-color-utilities' Cam16 and ViewingConditions.DEFAULT)

    private val WHITE_POINT = doubleArrayOf(95.047, 100.0, 108.883)
    private val XYZ_TO_CAM16 = arrayOf(
        doubleArrayOf(0.401288, 0.650173, -0.051461),
        doubleArrayOf(-0.250268, 1.204414, 0.045854),
        doubleArrayOf(-0.002079, 0.048952, 0.953127),
    )

    private class Viewing(
        val n: Double, val aw: Double, val nbb: Double, val ncb: Double, val c: Double, val nc: Double,
        val rgbD: DoubleArray, val fl: Double, val z: Double,
    )

    private val viewing: Viewing by lazy {
        val adapting = (200.0 / Math.PI) * yFromLstar(50.0) / 100.0
        val w = DoubleArray(3) { i -> (0 until 3).sumOf { j -> XYZ_TO_CAM16[i][j] * WHITE_POINT[j] } }
        val f = 1.0
        val c = 0.69
        val d = (f * (1.0 - (1.0 / 3.6) * exp((-adapting - 42.0) / 92.0))).coerceIn(0.0, 1.0)
        val rgbD = DoubleArray(3) { i -> d * (100.0 / w[i]) + 1.0 - d }
        val k = 1.0 / (5.0 * adapting + 1.0)
        val k4 = k * k * k * k
        val k4F = 1.0 - k4
        val fl = k4 * adapting + 0.1 * k4F * k4F * cbrt(5.0 * adapting)
        val n = yFromLstar(50.0) / WHITE_POINT[1]
        val z = 1.48 + sqrt(n)
        val nbb = 0.725 / n.pow(0.2)
        val rgbA = DoubleArray(3) { i ->
            val factor = (fl * rgbD[i] * w[i] / 100.0).pow(0.42)
            400.0 * factor / (factor + 27.13)
        }
        val aw = (2.0 * rgbA[0] + rgbA[1] + 0.05 * rgbA[2]) * nbb
        Viewing(n, aw, nbb, nbb, c, f, rgbD, fl, z)
    }

    fun hct(argb: Int): Hct {
        val r = linearized((argb shr 16) and 0xff)
        val g = linearized((argb shr 8) and 0xff)
        val b = linearized(argb and 0xff)
        val x = 0.41233895 * r + 0.35762064 * g + 0.18051042 * b
        val y = 0.2126 * r + 0.7152 * g + 0.0722 * b
        val zz = 0.01932141 * r + 0.11916382 * g + 0.95034478 * b
        val vc = viewing
        val cone = DoubleArray(3) { i -> XYZ_TO_CAM16[i][0] * x + XYZ_TO_CAM16[i][1] * y + XYZ_TO_CAM16[i][2] * zz }
        val adapted = DoubleArray(3) { i ->
            val d = vc.rgbD[i] * cone[i]
            val af = (vc.fl * abs(d) / 100.0).pow(0.42)
            sign(d) * 400.0 * af / (af + 27.13)
        }
        val (rA, gA, bA) = Triple(adapted[0], adapted[1], adapted[2])
        val a = (11.0 * rA - 12.0 * gA + bA) / 11.0
        val bb = (rA + gA - 2.0 * bA) / 9.0
        val u = (20.0 * rA + 20.0 * gA + 21.0 * bA) / 20.0
        val p2 = (40.0 * rA + 20.0 * gA + bA) / 20.0
        var hue = Math.toDegrees(atan2(bb, a))
        if (hue < 0) hue += 360.0 else if (hue >= 360.0) hue -= 360.0
        val ac = p2 * vc.nbb
        val j = 100.0 * (ac / vc.aw).pow(vc.c * vc.z)
        val huePrime = if (hue < 20.14) hue + 360.0 else hue
        val eHue = 0.25 * (cos(Math.toRadians(huePrime) + 2.0) + 3.8)
        val p1 = 50000.0 / 13.0 * eHue * vc.nc * vc.ncb
        val t = p1 * hypot(a, bb) / (u + 0.305)
        val alpha = (1.64 - 0.29.pow(vc.n)).pow(0.73) * t.pow(0.9)
        val chroma = alpha * sqrt(j / 100.0)
        return Hct(hue, chroma, lstarFromY(y))
    }

    private fun linearized(component: Int): Double {
        val normalized = component / 255.0
        return if (normalized <= 0.040449936) normalized / 12.92 * 100.0
        else ((normalized + 0.055) / 1.055).pow(2.4) * 100.0
    }

    private const val E = 216.0 / 24389.0
    private const val KAPPA = 24389.0 / 27.0

    private fun yFromLstar(lstar: Double): Double {
        val ft = (lstar + 16.0) / 116.0
        val ft3 = ft * ft * ft
        return 100.0 * if (ft3 > E) ft3 else (116.0 * ft - 16.0) / KAPPA
    }

    private fun lstarFromY(y: Double): Double {
        val t = y / 100.0
        val f = if (t > E) cbrt(t) else (KAPPA * t + 16.0) / 116.0
        return 116.0 * f - 16.0
    }
}
