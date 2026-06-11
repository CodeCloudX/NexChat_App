package com.nexchat.feature.chat.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexchat.core.ui.model.ReactionUiModel
import kotlinx.collections.immutable.ImmutableList

@Composable
fun ReactionRow(
    reactions: ImmutableList<ReactionUiModel>,
    onReactionTap: (emoji: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (reactions.isEmpty()) return

    Row(
        modifier = modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        reactions
            .groupBy { it.emoji }
            .forEach { (emoji, group) ->
                SuggestionChip(
                    onClick = { onReactionTap(emoji) },
                    label = {
                        Text(
                            text = "$emoji ${group.size}",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    shape = RoundedCornerShape(50),
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    border = null,
                )
            }
    }
}
