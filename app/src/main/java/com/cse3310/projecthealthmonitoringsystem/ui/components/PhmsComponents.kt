package com.cse3310.projecthealthmonitoringsystem.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Outlined Material icons used consistently across the app shell and feature cards. */
object PhmsIcons {
    val Home = Icons.Outlined.Home
    val Medication = Icons.Outlined.Medication
    val Vitals = Icons.Outlined.MonitorHeart
    val Profile = Icons.Outlined.Person
    val More = Icons.Outlined.MoreHoriz
    val PersonalInformation = Icons.Outlined.Person
    val WellGuide = Icons.Outlined.HealthAndSafety
    val BloodPressure = Icons.Outlined.Speed
    val Glucose = Icons.Outlined.WaterDrop
    val Weight = Icons.Outlined.MonitorWeight
    val Appointments = Icons.Outlined.CalendarMonth
    val Alerts = Icons.Outlined.NotificationsNone
    val Notes = Icons.Outlined.Description
    val Diet = Icons.Outlined.Restaurant
    val Search = Icons.Outlined.Search
    val DataMaintenance = Icons.Outlined.Settings
    val Providers: ImageVector by lazy {
        ImageVector.Builder("Stethoscope", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                fill = null,
                stroke = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color(0xFF0F172A)),
                strokeLineWidth = 1.8f,
                strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
                strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
            ) {
                moveTo(5f, 3f); lineTo(3f, 3f); lineTo(3f, 8f)
                curveTo(3f, 14.7f, 13f, 14.7f, 13f, 8f)
                lineTo(13f, 3f); lineTo(11f, 3f)
                moveTo(5f, 2f); lineTo(5f, 5f)
                moveTo(11f, 2f); lineTo(11f, 5f)
                moveTo(8f, 13f); lineTo(8f, 16f)
                curveTo(8f, 22.5f, 19f, 22.5f, 19f, 16f)
                lineTo(19f, 14f)
                moveTo(21.5f, 11.5f)
                arcTo(2.5f, 2.5f, 0f, true, true, 16.5f, 11.5f)
                arcTo(2.5f, 2.5f, 0f, true, true, 21.5f, 11.5f)
                close()
            }
        }.build()
    }
    val Communication = Icons.AutoMirrored.Outlined.Chat
    val Next = Icons.Outlined.ChevronRight
    val Account = Icons.Outlined.AccountCircle
    val Continue = Icons.Outlined.History
    val Brand = Icons.Outlined.HealthAndSafety
}

@Composable
fun PhmsPage(
    modifier: Modifier = Modifier,
    scrollState: androidx.compose.foundation.ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

@Composable
fun PhmsSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (supportingText != null) {
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun PhmsActionCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PhmsIconBadge(icon = icon)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = PhmsIcons.Next,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
fun PhmsEmptyStateCard(
    title: String,
    description: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    PhmsEmptyStateCard(title, description, icon, modifier, actionLabel, onAction, outlinedAction = false)
}

@Composable
fun PhmsEmptyStateCard(
    title: String,
    description: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    outlinedAction: Boolean,
) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PhmsIconBadge(icon = icon)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (actionLabel != null && onAction != null) {
                    if (outlinedAction) {
                        PhmsOutlinedButton(onClick = onAction) { Text(actionLabel) }
                    } else TextButton(
                        onClick = onAction,
                        modifier = Modifier.padding(start = 0.dp)
                    ) {
                        Text(actionLabel)
                    }
                }
            }
        }
    }
}

@Composable
fun PhmsIconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(androidx.compose.ui.graphics.Color(0xFF065F46))
            .padding(9.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = androidx.compose.ui.graphics.Color.White,
            modifier = Modifier.fillMaxSize()
        )
    }
}
