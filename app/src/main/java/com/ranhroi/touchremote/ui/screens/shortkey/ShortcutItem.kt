package com.ranhroi.touchremote.ui.screens.shortkey

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ranhroi.touchremote.models.ShortKeyModel
import com.ranhroi.touchremote.ui.components.IconAny
import com.ranhroi.touchremote.utils.ConstantsUtils
import com.ranhroi.touchremote.utils.IconsMap


@Composable
fun ShortKeyItem(hotkey: ShortKeyModel) {
    val icon = remember(hotkey.icon) { IconsMap.getIconOrDefault(hotkey.icon) }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyColumn {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconAny(
                        text = hotkey.name,
                        icon = icon,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = hotkey.name,
                        textAlign = TextAlign.Start,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            itemsIndexed(hotkey.keys) { index, key ->
                Text(
                    text = "${ConstantsUtils.getSystemInfoText(index)}: $key",
                    textAlign = TextAlign.Start,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

        }
    }
}
