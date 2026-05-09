package fi.kotinaytto.tv.ui.weather

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Grain
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Thunderstorm
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbCloudy
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

fun weatherConditionIcon(code: Int?, isDay: Boolean): ImageVector = when (code) {
    null -> Icons.AutoMirrored.Outlined.HelpOutline
    0 -> if (isDay) Icons.Outlined.WbSunny else Icons.Outlined.NightsStay
    1, 2, 3 -> Icons.Outlined.WbCloudy
    in 45..48 -> Icons.Outlined.Cloud
    in 51..57 -> Icons.Outlined.WaterDrop
    in 61..67 -> Icons.Outlined.Grain
    in 71..77, in 85..86 -> Icons.Outlined.AcUnit
    in 80..82 -> Icons.Outlined.WaterDrop
    in 95..99 -> Icons.Outlined.Thunderstorm
    else -> Icons.Outlined.Cloud
}

@Composable
fun WeatherConditionIcon(
    code: Int?,
    isDay: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Icon(
        imageVector = weatherConditionIcon(code, isDay),
        contentDescription = null,
        modifier = modifier,
        tint = tint,
    )
}
