package com.spotibook.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import com.spotibook.app.R
import com.spotibook.app.data.Settings

val ReadingGold = Color(0xFFF3C64B)
val ReadingCoral = Color(0xFFFF6659)

// Bundled under the SIL Open Font License; no font download at runtime.
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val AppFont = FontFamily(listOf(400, 500, 600, 700, 800).map { weight ->
    Font(R.font.nunito_sans, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))
})

private fun text(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = AppFont, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight)

@Composable fun SpotibookTheme(settings: Settings, content: @Composable () -> Unit) {
    val dark = settings.appearance == "Dark" || (settings.appearance == "System" && isSystemInDarkTheme())
    val primary = when (settings.accent) { "Olive" -> Color(0xFF626D46); "Amber" -> Color(0xFF8B602E); else -> Color(0xFF2F7299) }
    val palette = if (dark) darkColorScheme(
        primary = Color(0xFFA6D3ED), onPrimary = Color(0xFF10394F),
        primaryContainer = Color(0xFF244C63), onPrimaryContainer = Color(0xFFD3EDFC),
        secondary = ReadingGold, onSecondary = Color(0xFF3C300B),
        secondaryContainer = Color(0xFF51452A), onSecondaryContainer = Color(0xFFFBECC1),
        tertiary = Color(0xFFF2AD98), onTertiary = Color(0xFF51291D),
        tertiaryContainer = Color(0xFF57392E), onTertiaryContainer = Color(0xFFFFDBCE),
        background = Color(0xFF15232C), onBackground = Color(0xFFF5F0E8),
        surface = Color(0xFF21323D), onSurface = Color(0xFFF5F0E8),
        surfaceVariant = Color(0xFF34454F), onSurfaceVariant = Color(0xFFBBCAD2),
        outline = Color(0xFF899BA6), outlineVariant = Color(0xFF425560),
        surfaceTint = Color(0xFFA6D3ED), surfaceContainerLowest = Color(0xFF101D25),
        surfaceContainerLow = Color(0xFF1A2A35), surfaceContainer = Color(0xFF253641),
        surfaceContainerHigh = Color(0xFF2E414D), surfaceContainerHighest = Color(0xFF394D59)
    ) else lightColorScheme(
        primary = primary, onPrimary = Color.White,
        primaryContainer = Color(0xFFD9ECF5), onPrimaryContainer = Color(0xFF1C506D),
        secondary = Color(0xFF866518), onSecondary = Color.White,
        secondaryContainer = Color(0xFFF9E9AD), onSecondaryContainer = Color(0xFF5B450F),
        tertiary = Color(0xFFAA4935), onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFBE0D4), onTertiaryContainer = Color(0xFF803A29),
        background = Color(0xFFF6EFE3), onBackground = Color(0xFF283C48),
        surface = Color(0xFFFFFCF6), onSurface = Color(0xFF283C48),
        surfaceVariant = Color(0xFFE7E5DC), onSurfaceVariant = Color(0xFF646D71),
        outline = Color(0xFF81898A), outlineVariant = Color(0xFFD9D8CC),
        surfaceTint = primary, surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFFFF9EF), surfaceContainer = Color(0xFFF1E8DA),
        surfaceContainerHigh = Color(0xFFECE2D3), surfaceContainerHighest = Color(0xFFE5DAC9)
    )
    val coralPalette = if(dark) palette.copy(primary=Color(0xFFFFA397),onPrimary=Color(0xFF491A19),primaryContainer=Color(0xFF59302D),onPrimaryContainer=Color(0xFFFFDAD4),background=Color(0xFF151923),onBackground=Color(0xFFF5F1F3),surface=Color(0xFF202532),onSurface=Color(0xFFF5F1F3),surfaceVariant=Color(0xFF303543),onSurfaceVariant=Color(0xFFC7C5CF),surfaceContainerLow=Color(0xFF1C202B),surfaceContainer=Color(0xFF272C38),surfaceContainerHigh=Color(0xFF303543),surfaceContainerHighest=Color(0xFF3B404E),outlineVariant=Color(0xFF404553))
    else palette.copy(primary=Color(0xFFB93832),onPrimary=Color.White,primaryContainer=Color(0xFFFFEDE7),onPrimaryContainer=Color(0xFF852E29),background=Color(0xFFFFFCFB),onBackground=Color(0xFF121D3C),surface=Color.White,onSurface=Color(0xFF121D3C),surfaceVariant=Color(0xFFF3F1F2),onSurfaceVariant=Color(0xFF606779),surfaceContainerLow=Color(0xFFFFF6F2),surfaceContainer=Color(0xFFF9EEEB),surfaceContainerHigh=Color(0xFFF3E6E2),surfaceContainerHighest=Color(0xFFEADBD7),outlineVariant=Color(0xFFE9DFDC))
    MaterialTheme(colorScheme = coralPalette, shapes = Shapes(
        extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(26.dp), extraLarge = RoundedCornerShape(32.dp)
    ), typography = Typography(
        displayLarge = text(56, 64, FontWeight.Bold), displayMedium = text(46, 54, FontWeight.Bold), displaySmall = text(40, 48, FontWeight.Bold),
        headlineLarge = text(32, 39, FontWeight.ExtraBold), headlineMedium = text(27, 34, FontWeight.Bold), headlineSmall = text(24, 31, FontWeight.Bold),
        titleLarge = text(22, 29, FontWeight.Bold), titleMedium = text(17, 24, FontWeight.Bold), titleSmall = text(15, 22, FontWeight.Bold),
        bodyLarge = text(16, 25), bodyMedium = text(14, 21), bodySmall = text(12, 18),
        labelLarge = text(15, 21, FontWeight.Bold), labelMedium = text(12, 18, FontWeight.Bold), labelSmall = text(11, 16, FontWeight.Bold)
    ), content = content)
}
