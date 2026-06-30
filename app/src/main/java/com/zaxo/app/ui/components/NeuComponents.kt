package com.zaxo.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.zaxo.app.ui.theme.ZaxoTheme

// ==================== Neumorphic Card ====================
@Composable
fun NeuCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    elevation: Dp = 8.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = ZaxoTheme.colors
    Column(
        modifier = modifier
            .shadow(elevation, shape)
            .background(colors.background, shape)
            .clip(shape),
        content = content
    )
}

// ==================== Neumorphic Elevated Card ====================
@Composable
fun NeuElevatedCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = ZaxoTheme.colors
    Box(
        modifier = modifier
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                ) else Modifier
            )
            .shadow(6.dp, shape, ambientColor = colors.shadowDark, spotColor = colors.shadowLight)
            .background(colors.background, shape)
            .clip(shape),
        content = content
    )
}

// ==================== Neumorphic Button ====================
@Composable
fun NeuButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(12.dp),
    containerColor: Color = ZaxoTheme.colors.primary,
    contentColor: Color = ZaxoTheme.colors.onPrimary,
    content: @Composable RowScope.() -> Unit
) {
    val colors = ZaxoTheme.colors
    Row(
        modifier = modifier
            .shadow(6.dp, shape, ambientColor = colors.shadowDark, spotColor = colors.shadowLight)
            .background(if (enabled) containerColor else colors.muted, shape)
            .clip(shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

// ==================== Neumorphic Icon Button ====================
@Composable
fun NeuIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector,
    contentDescription: String?,
    tint: Color = ZaxoTheme.colors.onSurface
) {
    val colors = ZaxoTheme.colors
    Box(
        modifier = modifier
            .shadow(4.dp, CircleShape, ambientColor = colors.shadowDark, spotColor = colors.shadowLight)
            .background(colors.background, CircleShape)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else colors.muted
        )
    }
}

// ==================== Neumorphic Avatar ====================
@Composable
fun NeuAvatar(
    photoUrl: String,
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    isOnline: Boolean = false
) {
    val colors = ZaxoTheme.colors
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(size)
                .shadow(4.dp, CircleShape, ambientColor = colors.shadowDark, spotColor = colors.shadowLight)
                .background(colors.background, CircleShape)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (photoUrl.isNotEmpty()) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = name,
                    modifier = Modifier
                        .size(size)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = name,
                    modifier = Modifier.size(size * 0.6f),
                    tint = colors.muted
                )
            }
        }
        if (isOnline) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .align(Alignment.BottomEnd)
                    .background(colors.secondary, CircleShape)
            )
        }
    }
}

// ==================== Neumorphic Toggle ====================
@Composable
fun NeuToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ZaxoTheme.colors
    val toggleColor by animateColorAsState(
        targetValue = if (checked) colors.primary else colors.muted,
        animationSpec = tween(300)
    )

    Box(
        modifier = modifier
            .width(52.dp)
            .height(28.dp)
            .shadow(4.dp, RoundedCornerShape(14.dp), ambientColor = colors.shadowDark, spotColor = colors.shadowLight)
            .background(colors.background, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .clickable { onCheckedChange(!checked) },
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .padding(3.dp)
                .size(22.dp)
                .shadow(2.dp, CircleShape, ambientColor = colors.shadowDark, spotColor = colors.shadowLight)
                .background(toggleColor, CircleShape)
        )
    }
}

// ==================== Neumorphic Search Bar ====================
@Composable
fun NeuSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search...",
    onSearch: (() -> Unit)? = null
) {
    val colors = ZaxoTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(16.dp), ambientColor = colors.shadowDark, spotColor = colors.shadowLight)
            .background(colors.background, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Search",
            tint = colors.muted
        )
        Spacer(modifier = Modifier.width(8.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            textStyle = TextStyle(
                color = colors.onSurface,
                fontSize = 16.sp
            ),
            singleLine = true,
            decorationBox = { innerTextField ->
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = colors.muted,
                        fontSize = 16.sp
                    )
                }
                innerTextField()
            }
        )
        if (query.isNotEmpty()) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Clear",
                tint = colors.muted,
                modifier = Modifier.clickable { onQueryChange("") }
            )
        }
    }
}

// ==================== Neumorphic Divider ====================
@Composable
fun NeuDivider(
    modifier: Modifier = Modifier
) {
    val colors = ZaxoTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .shadow(1.dp, RoundedCornerShape(0.5.dp), ambientColor = colors.shadowDark, spotColor = colors.shadowLight)
            .background(colors.background)
    )
}

// ==================== Neumorphic Badge ====================
@Composable
fun NeuBadge(
    count: Int,
    modifier: Modifier = Modifier
) {
    val colors = ZaxoTheme.colors
    if (count > 0) {
        Box(
            modifier = modifier
                .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                .background(colors.primary, RoundedCornerShape(10.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (count > 99) "99+" else count.toString(),
                color = colors.onPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ==================== Neumorphic Chip ====================
@Composable
fun NeuChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ZaxoTheme.colors
    val bgColor by animateColorAsState(
        targetValue = if (selected) colors.primary else colors.background,
        animationSpec = tween(200)
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) colors.onPrimary else colors.onSurface,
        animationSpec = tween(200)
    )

    Box(
        modifier = modifier
            .shadow(4.dp, RoundedCornerShape(20.dp), ambientColor = colors.shadowDark, spotColor = colors.shadowLight)
            .background(bgColor, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
