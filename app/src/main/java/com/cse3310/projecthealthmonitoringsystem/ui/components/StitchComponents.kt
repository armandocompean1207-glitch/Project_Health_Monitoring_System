package com.cse3310.projecthealthmonitoringsystem.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** Shared Stitch surfaces and actions; text actions use slate rather than yellow on white. */
@Composable
fun PhmsTextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    colors: ButtonColors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
    content: @Composable RowScope.() -> Unit) {
    TextButton(onClick, modifier.heightIn(min = 48.dp), enabled = enabled, colors = colors,
        contentPadding = contentPadding, content = content)
}

@Composable
fun PhmsOutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit) {
    OutlinedButton(onClick, modifier.heightIn(min = 48.dp), enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), content = content)
}

@Composable
fun PhmsCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.large,
    colors: CardColors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    border: BorderStroke = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    elevation: CardElevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    content: @Composable ColumnScope.() -> Unit) {
    if (onClick == null) OutlinedCard(modifier, shape, colors, elevation, border, content = content)
    else OutlinedCard(onClick = onClick, modifier = modifier, shape = shape, colors = colors,
        elevation = elevation, border = border, content = content)
}

@Composable
fun PhmsScreenHeader(title: String, description: String? = null, icon: ImageVector = PhmsIcons.Brand,
    onBack: (() -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            else PhmsIconBadge(icon)
            Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
        }
        description?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
fun PhmsStatusPill(text: String, modifier: Modifier = Modifier) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer, modifier = modifier) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun PhmsMetricCard(label: String, value: String?, unit: String?, icon: ImageVector, onClick: () -> Unit,
    modifier: Modifier = Modifier) {
    PhmsCard(modifier = modifier, onClick = onClick) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PhmsIconBadge(icon, Modifier.size(36.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value ?: "—", style = MaterialTheme.typography.headlineMedium)
            Text(if (value == null) "Add reading" else unit.orEmpty(),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
