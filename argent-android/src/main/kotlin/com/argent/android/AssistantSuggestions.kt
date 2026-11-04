package com.argent.android

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.argent.client.core.AssistantPromptCatalog
import com.argent.client.core.UiStrings
import com.argent.client.core.UiTextKey

@Composable
internal fun AssistantSuggestions(
    language: String,
    modifier: Modifier = Modifier,
    onSuggestion: (String) -> Unit,
) {
    val suggestions = AssistantPromptCatalog.suggestions(language)
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            UiStrings.text(UiTextKey.ASSISTANT_GREETING, language),
            color = Color(0xFFE0E4E8),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            UiStrings.text(UiTextKey.ASSISTANT_HELP, language),
            color = Muted,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        suggestions.forEach { suggestion ->
            Surface(
                color = Panel,
                shape = RoundedCornerShape(10.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clickable { onSuggestion(suggestion) },
            ) {
                Text(
                    suggestion,
                    color = Color(0xFFE0E4E8),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
    }
}
