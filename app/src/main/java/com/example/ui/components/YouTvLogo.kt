package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.localization.LocalStrings
import com.example.ui.theme.AdmitAccentCrimson
import com.example.ui.theme.YouTvBlack

@Composable
fun YouTvLogoMark(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(YouTvBlack),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.youtv_logo),
            contentDescription = "YouTV logo",
            modifier = Modifier.size(size),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
fun YouTvBrandLockup(
    modifier: Modifier = Modifier,
    showSlogan: Boolean = false,
    iconSize: Dp = 36.dp
) {
    val strings = LocalStrings.current
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        YouTvLogoMark(size = iconSize)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = strings.appName,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            if (showSlogan) {
                Text(
                    text = strings.slogan,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun AdmitBrandLockup(
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1A2233))
                .border(1.5.dp, AdmitAccentCrimson, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.AdminPanelSettings,
                contentDescription = "ADMIT Owner Shield",
                tint = AdmitAccentCrimson,
                modifier = Modifier.size(22.dp)
            )
        }
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = strings.admitName,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "OWNER",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = AdmitAccentCrimson,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(AdmitAccentCrimson.copy(alpha = 0.16f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Text(
                text = strings.admitSubtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
