package uz.robutpit.znatoki.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.robutpit.znatoki.R
import uz.robutpit.znatoki.data.local.ThemeMode

/** Дополнительные «клубные» цвета, которых нет в Material-схеме. */
@Immutable
data class ClubColors(
    val brass: Color,
    val brassDeep: Color,
    val felt: Color,
    val feltDark: Color,
    val wood: Color,
    val woodDark: Color,
    val experts: Color,
    val viewers: Color,
    val success: Color,
    val muted: Color,
    val cardStroke: Color,
    val isDark: Boolean,
)

private val DarkClub = ClubColors(
    brass = Color(0xFFD4A853), brassDeep = Color(0xFF8C6A2A), felt = Color(0xFF1B4A45), feltDark = Color(0xFF0E2B28),
    wood = Color(0xFF5A3A22), woodDark = Color(0xFF2E1D11), experts = Color(0xFFE8C46A), viewers = Color(0xFF7FC4D6),
    success = Color(0xFF6CC48A), muted = Color(0xFF9FB1B0), cardStroke = Color(0x33D4A853), isDark = true,
)
private val LightClub = ClubColors(
    brass = Color(0xFF9A7224), brassDeep = Color(0xFF6E5018), felt = Color(0xFF2B6B63), feltDark = Color(0xFF1B4A45),
    wood = Color(0xFF7A5233), woodDark = Color(0xFF4A2F1B), experts = Color(0xFF8A6418), viewers = Color(0xFF1F6F86),
    success = Color(0xFF2E7D4F), muted = Color(0xFF55625F), cardStroke = Color(0x339A7224), isDark = false,
)

val LocalClub = staticCompositionLocalOf { DarkClub }

private val darkScheme = darkColorScheme(
    primary = Color(0xFFD4A853), onPrimary = Color(0xFF1A1405), primaryContainer = Color(0xFF3B2E12), onPrimaryContainer = Color(0xFFF3DDA6),
    secondary = Color(0xFF4FB3AA), onSecondary = Color(0xFF00201D), secondaryContainer = Color(0xFF173F3B), onSecondaryContainer = Color(0xFFBDEDE6),
    tertiary = Color(0xFF7FC4D6), onTertiary = Color(0xFF00222B),
    background = Color(0xFF0D1719), onBackground = Color(0xFFF1EBDD),
    surface = Color(0xFF132124), onSurface = Color(0xFFF1EBDD),
    surfaceVariant = Color(0xFF1C3034), onSurfaceVariant = Color(0xFFB9C7C5),
    surfaceContainer = Color(0xFF172729), surfaceContainerHigh = Color(0xFF1D2F32), surfaceContainerHighest = Color(0xFF24393C),
    surfaceContainerLow = Color(0xFF111E20), surfaceContainerLowest = Color(0xFF0A1214),
    outline = Color(0xFF52696A), outlineVariant = Color(0xFF2C4245),
    error = Color(0xFFF08A73), onError = Color(0xFF3B0A02),
)

private val lightScheme = lightColorScheme(
    primary = Color(0xFF8A6418), onPrimary = Color.White, primaryContainer = Color(0xFFF6E2B0), onPrimaryContainer = Color(0xFF2B1F00),
    secondary = Color(0xFF1F6F6A), onSecondary = Color.White, secondaryContainer = Color(0xFFCDEDE8), onSecondaryContainer = Color(0xFF00201D),
    tertiary = Color(0xFF1F6F86), onTertiary = Color.White,
    background = Color(0xFFF6F1E6), onBackground = Color(0xFF1B2629),
    surface = Color(0xFFFFFCF5), onSurface = Color(0xFF1B2629),
    surfaceVariant = Color(0xFFECE5D6), onSurfaceVariant = Color(0xFF4A5654),
    surfaceContainer = Color(0xFFF3EDE1), surfaceContainerHigh = Color(0xFFEDE6D8), surfaceContainerHighest = Color(0xFFE6DECE),
    surfaceContainerLow = Color(0xFFF9F5EC), surfaceContainerLowest = Color.White,
    outline = Color(0xFF7D8886), outlineVariant = Color(0xFFD2CABA),
    error = Color(0xFFB3261E), onError = Color.White,
)

val Lora = FontFamily(Font(R.font.lora_semibold, FontWeight.SemiBold), Font(R.font.lora_bold, FontWeight.Bold))
val Manrope = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal), Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
)

private val baseType = Typography()
val ClubTypography = Typography(
    displayLarge = TextStyle(fontFamily = Lora, fontWeight = FontWeight.Bold, fontSize = 52.sp, lineHeight = 58.sp),
    displayMedium = TextStyle(fontFamily = Lora, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 46.sp),
    displaySmall = TextStyle(fontFamily = Lora, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp),
    headlineLarge = TextStyle(fontFamily = Lora, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
    headlineMedium = TextStyle(fontFamily = Lora, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp),
    headlineSmall = TextStyle(fontFamily = Lora, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.3.sp),
    labelMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp),
    labelSmall = baseType.labelSmall.copy(fontFamily = Manrope, fontWeight = FontWeight.SemiBold),
)

val ClubShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun ClubTheme(mode: ThemeMode = ThemeMode.DARK, content: @Composable () -> Unit) {
    val dark = when (mode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.DARK -> true; ThemeMode.LIGHT -> false }
    CompositionLocalProvider(LocalClub provides if (dark) DarkClub else LightClub) {
        MaterialTheme(colorScheme = if (dark) darkScheme else lightScheme, typography = ClubTypography, shapes = ClubShapes, content = content)
    }
}

object Club {
    val colors: ClubColors @Composable get() = LocalClub.current
}
