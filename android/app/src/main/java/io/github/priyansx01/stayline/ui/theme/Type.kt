package io.github.priyansx01.stayline.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.priyansx01.stayline.R

private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private val manrope = GoogleFont("Manrope")

/** Downloaded on first use, so the APK carries no font; falls back to the system font. */
val Manrope = FontFamily(
    Font(googleFont = manrope, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = manrope, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = manrope, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = manrope, fontProvider = provider, weight = FontWeight.Bold),
    Font(googleFont = manrope, fontProvider = provider, weight = FontWeight.ExtraBold),
)

/** Digits that do not shift as they change. */
const val TABULAR = "tnum"

val StaylineTypography = Typography(
    displaySmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp, letterSpacing = (-0.02).em, fontFeatureSettings = TABULAR),
    headlineMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, letterSpacing = (-0.02).em),
    headlineSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 24.sp, letterSpacing = (-0.01).em),
    titleLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    titleSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    bodyLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 0.01.em),
    labelMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.04.em),
    labelSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.08.em),
)
