package com.zaxo.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class NeuColors(
    val background: Color,
    val surface: Color,
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val error: Color,
    val onSurface: Color,
    val onBackground: Color,
    val muted: Color,
    val shadowDark: Color,
    val shadowLight: Color,
    val outgoingBubble: Color,
    val incomingBubble: Color,
)

val LocalNeuColors = staticCompositionLocalOf {
    NeuColors(
        background = LightBackground,
        surface = LightSurface,
        primary = LightPrimary,
        onPrimary = LightOnPrimary,
        secondary = LightSecondary,
        error = LightError,
        onSurface = LightOnSurface,
        onBackground = LightOnBackground,
        muted = LightMuted,
        shadowDark = LightShadowDark,
        shadowLight = LightShadowLight,
        outgoingBubble = OutgoingBubble,
        incomingBubble = IncomingBubble,
    )
}

object ZaxoTheme {
    val colors: NeuColors
        @Composable get() = LocalNeuColors.current
}

@Composable
fun ZaxoAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val neuColors = if (darkTheme) {
        NeuColors(
            background = DarkBackground,
            surface = DarkSurface,
            primary = DarkPrimary,
            onPrimary = DarkOnPrimary,
            secondary = DarkSecondary,
            error = DarkError,
            onSurface = DarkOnSurface,
            onBackground = DarkOnBackground,
            muted = DarkMuted,
            shadowDark = DarkShadowDark,
            shadowLight = DarkShadowLight,
            outgoingBubble = OutgoingBubbleDark,
            incomingBubble = IncomingBubbleDark,
        )
    } else {
        NeuColors(
            background = LightBackground,
            surface = LightSurface,
            primary = LightPrimary,
            onPrimary = LightOnPrimary,
            secondary = LightSecondary,
            error = LightError,
            onSurface = LightOnSurface,
            onBackground = LightOnBackground,
            muted = LightMuted,
            shadowDark = LightShadowDark,
            shadowLight = LightShadowLight,
            outgoingBubble = OutgoingBubble,
            incomingBubble = IncomingBubble,
        )
    }

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = DarkPrimary,
            onPrimary = DarkOnPrimary,
            secondary = DarkSecondary,
            error = DarkError,
            background = DarkBackground,
            surface = DarkSurface,
            onBackground = DarkOnBackground,
            onSurface = DarkOnSurface,
        )
    } else {
        lightColorScheme(
            primary = LightPrimary,
            onPrimary = LightOnPrimary,
            secondary = LightSecondary,
            error = LightError,
            background = LightBackground,
            surface = LightSurface,
            onBackground = LightOnBackground,
            onSurface = LightOnSurface,
        )
    }

    CompositionLocalProvider(LocalNeuColors provides neuColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography(
                bodyLarge = TextStyle(
                    fontWeight = FontWeight.Normal,
                    fontSize = 16.sp,
                    lineHeight = 24.sp
                ),
                bodyMedium = TextStyle(
                    fontWeight = FontWeight.Normal,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                ),
                titleLarge = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    lineHeight = 28.sp
                ),
                titleMedium = TextStyle(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    lineHeight = 24.sp
                ),
                labelSmall = TextStyle(
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                ),
            ),
            content = content
        )
    }
}
