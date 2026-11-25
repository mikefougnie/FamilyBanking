package com.godskingdomnow.familybank.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/* Bank navy and coin gold. */
private val Light = lightColorScheme(
    primary = Color(0xFF16325C), onPrimary = Color.White,
    primaryContainer = Color(0xFFD7E3F8), onPrimaryContainer = Color(0xFF001B3E),
    secondary = Color(0xFF8A5A00), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE3A8), onSecondaryContainer = Color(0xFF2C1A00),
    tertiary = Color(0xFF3E6A4B), onTertiary = Color.White,
    background = Color(0xFFF5F6F9), onBackground = Color(0xFF1A1C20),
    surface = Color(0xFFF5F6F9), onSurface = Color(0xFF1A1C20),
    surfaceVariant = Color(0xFFDFE2EB), onSurfaceVariant = Color(0xFF43474E),
    surfaceContainer = Color(0xFFECEEF3), surfaceContainerHigh = Color(0xFFE5E8EE),
    outline = Color(0xFF73777F), outlineVariant = Color(0xFFC3C6CF),
    error = Color(0xFFB3261E),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFAAC7FF), onPrimary = Color(0xFF002F65),
    primaryContainer = Color(0xFF1F3F6E), onPrimaryContainer = Color(0xFFD7E3FF),
    secondary = Color(0xFFF5C04A), onSecondary = Color(0xFF452B00),
    secondaryContainer = Color(0xFF624000), onSecondaryContainer = Color(0xFFFFDEA6),
    tertiary = Color(0xFFA3D3AF), onTertiary = Color(0xFF0A3920),
    background = Color(0xFF10131A), onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF10131A), onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF43474E), onSurfaceVariant = Color(0xFFC3C6CF),
    surfaceContainer = Color(0xFF1C2028), surfaceContainerHigh = Color(0xFF262A32),
    outline = Color(0xFF8D9199), outlineVariant = Color(0xFF43474E),
    error = Color(0xFFF2B8B5),
)

/** Deposit green and withdraw rust, the same meaning as on the website. */
@Immutable
data class MoneyColors(val deposit: Color, val depositSoft: Color, val withdraw: Color, val withdrawSoft: Color)

private val LightMoney = MoneyColors(Color(0xFF2E7D4F), Color(0xFFDDF1E4), Color(0xFFB0472A), Color(0xFFF8E1D9))
private val DarkMoney = MoneyColors(Color(0xFF7FD39E), Color(0xFF173A26), Color(0xFFF4A58A), Color(0xFF45251B))

val LocalMoneyColors = staticCompositionLocalOf { LightMoney }

@Composable
fun FamilyBankTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalMoneyColors provides if (dark) DarkMoney else LightMoney) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
    }
}
