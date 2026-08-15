package dev.fps.interp.core

/**
 * An exact frame rate. Kept as a fraction rather than a float because the
 * broadcast rates are not representable in binary: 23.976 is 24000/1001 and
 * 29.97 is 30000/1001. Rounding those to a Double and accumulating is the
 * classic source of audio/video drift over a feature-length film.
 */
data class Rational(val num: Long, val den: Long) {

    init {
        require(num > 0) { "numerator must be positive, was $num" }
        require(den > 0) { "denominator must be positive, was $den" }
    }

    fun toDouble(): Double = num.toDouble() / den.toDouble()

    override fun toString(): String = if (den == 1L) "$num" else "$num/$den"

    companion object {
        val FILM_24 = Rational(24, 1)
        val NTSC_FILM_23_976 = Rational(24_000, 1001)
        val PAL_25 = Rational(25, 1)
        val NTSC_29_97 = Rational(30_000, 1001)
        val VIDEO_30 = Rational(30, 1)
        val VIDEO_60 = Rational(60, 1)
        val DISPLAY_60 = Rational(60, 1)
        val DISPLAY_120 = Rational(120, 1)
    }
}
