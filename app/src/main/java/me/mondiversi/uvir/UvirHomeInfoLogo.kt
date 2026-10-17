package me.mondiversi.uvir

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Always present, even with no associated/connected sensor or with simulated data. */
@Composable
internal fun UvirHomeInfoLogo(infoDescription: String, onOpenInfo: () -> Unit) {
    Box(Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)).testTag("home_info_logo")
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
            role = Role.Button, onClickLabel = infoDescription, onClick = onOpenInfo)
        .semantics(mergeDescendants = true) { contentDescription = infoDescription }) {
        Image(painter = painterResource(R.drawable.uvir_logo), modifier = Modifier.fillMaxSize(),
            contentDescription = null, contentScale = ContentScale.Crop)
        Text(BuildConfig.VERSION_NAME,
            Modifier.align(Alignment.BottomCenter).padding(bottom = 1.dp).testTag("home_info_version"),
            color = Color.White, fontSize = 8.sp, lineHeight = 9.sp, maxLines = 1, softWrap = false)
    }
}
