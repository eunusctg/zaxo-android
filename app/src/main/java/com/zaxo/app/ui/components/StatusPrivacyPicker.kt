package com.zaxo.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.PeopleOutline
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zaxo.app.model.StatusPrivacy
import com.zaxo.app.ui.theme.ZaxoTheme

/**
 * StatusPrivacyPicker — Bottom sheet content for selecting who can see a status update.
 *
 * Options:
 * - My Contacts (default) — All contacts can see
 * - My Contacts Except... — Exclude specific contacts
 * - Only Share With... — Share with specific contacts only
 */
@Composable
fun StatusPrivacyPicker(
    currentPrivacy: StatusPrivacy = StatusPrivacy.MY_CONTACTS,
    excludedIds: Set<String> = emptySet(),
    includedIds: Set<String> = emptySet(),
    onPrivacyChange: (StatusPrivacy) -> Unit,
    onExcludedChange: (Set<String>) -> Unit = {},
    onIncludedChange: (Set<String>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = ZaxoTheme.colors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Header
        Text(
            text = "Who can see my status updates",
            color = colors.onSurface,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Option rows
        PrivacyOption(
            icon = Icons.Default.Contacts,
            label = "My Contacts",
            description = "All your contacts can see your status updates",
            selected = currentPrivacy == StatusPrivacy.MY_CONTACTS,
            onClick = { onPrivacyChange(StatusPrivacy.MY_CONTACTS) }
        )

        PrivacyOption(
            icon = Icons.Outlined.Block,
            label = "My Contacts Except...",
            description = "Exclude specific contacts from seeing your status",
            selected = currentPrivacy == StatusPrivacy.MY_CONTACTS_EXCEPT,
            onClick = { onPrivacyChange(StatusPrivacy.MY_CONTACTS_EXCEPT) }
        )

        // Sub-row for excluded contacts
        AnimatedVisibility(
            visible = currentPrivacy == StatusPrivacy.MY_CONTACTS_EXCEPT,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            ContactSelectionRow(
                count = excludedIds.size,
                label = "excluded",
                onSelectClick = { /* TODO: Launch contact picker */ }
            )
        }

        PrivacyOption(
            icon = Icons.Default.PeopleOutline,
            label = "Only Share With...",
            description = "Only selected contacts can see your status",
            selected = currentPrivacy == StatusPrivacy.ONLY_SHARE_WITH,
            onClick = { onPrivacyChange(StatusPrivacy.ONLY_SHARE_WITH) }
        )

        // Sub-row for included contacts
        AnimatedVisibility(
            visible = currentPrivacy == StatusPrivacy.ONLY_SHARE_WITH,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            ContactSelectionRow(
                count = includedIds.size,
                label = "selected",
                onSelectClick = { /* TODO: Launch contact picker */ }
            )
        }
    }
}

@Composable
private fun PrivacyOption(
    icon: ImageVector,
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = ZaxoTheme.colors
    val selectionColor by animateColorAsState(
        targetValue = if (selected) colors.primary else colors.muted,
        animationSpec = tween(250)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(14.dp), ambientColor = colors.shadowDark, spotColor = colors.shadowLight)
            .background(colors.background, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) colors.primary else colors.muted,
            modifier = Modifier.size(22.dp)
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = colors.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                color = colors.muted,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }

        // Neumorphic radio indicator
        Box(
            modifier = Modifier
                .size(22.dp)
                .shadow(3.dp, CircleShape, ambientColor = colors.shadowDark, spotColor = colors.shadowLight)
                .background(colors.background, CircleShape)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(selectionColor, CircleShape)
                )
            }
        }
    }
}

@Composable
private fun ContactSelectionRow(
    count: Int,
    label: String,
    onSelectClick: () -> Unit
) {
    val colors = ZaxoTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 36.dp, top = 4.dp, bottom = 6.dp)
            .shadow(3.dp, RoundedCornerShape(12.dp), ambientColor = colors.shadowDark, spotColor = colors.shadowLight)
            .background(colors.background, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onSelectClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.PersonAdd,
            contentDescription = "Select contacts",
            tint = colors.primary,
            modifier = Modifier.size(18.dp)
        )

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = if (count > 0) "$count $label" else "Select contacts",
            color = if (count > 0) colors.onSurface else colors.muted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )

        if (count > 0) {
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
