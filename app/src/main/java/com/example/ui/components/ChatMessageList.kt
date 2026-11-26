package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.ChatMessage
import com.example.model.LocalAppStrings
import com.example.model.MessageRole
import java.io.File

@Composable
fun ChatMessageList(
    messages: List<ChatMessage>,
    isGenerating: Boolean,
    onImageClick: (String) -> Unit,
    onCopyAnswer: (String) -> Unit,
    onFollowUpClick: (String) -> Unit,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val isNearBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            if (totalItems == 0) true
            else {
                val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisible >= totalItems - 2
            }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    LaunchedEffect(messages.lastOrNull()?.text?.length) {
        if (isGenerating && isNearBottom && messages.isNotEmpty()) {
            listState.scrollToItem(messages.lastIndex, scrollOffset = Int.MAX_VALUE)
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            itemsIndexed(
                items = messages,
                key = { index, msg -> "${msg.id}_$index" }
            ) { index, message ->
                val isLastAssistant = index == messages.lastIndex && message.role == MessageRole.ASSISTANT
                if (message.role == MessageRole.USER) {
                    UserMessageBubble(
                        message = message,
                        onImageClick = onImageClick
                    )
                } else {
                    AssistantSolutionCard(
                        message = message,
                        showFollowUpActions = isLastAssistant && !isGenerating && !message.isError,
                        onCopyAnswer = { onCopyAnswer(message.text) },
                        onFollowUpClick = onFollowUpClick,
                        onRetryClick = onRetryClick
                    )
                }
            }
        }
    }
}

@Composable
private fun UserMessageBubble(
    message: ChatMessage,
    onImageClick: (String) -> Unit
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp),
            color = MaterialTheme.colorScheme.primary,
            tonalElevation = 1.dp,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (message.imagePath != null) {
                    val imageRequest = remember(message.imagePath) {
                        ImageRequest.Builder(context)
                            .data(File(message.imagePath))
                            .crossfade(true)
                            .size(900)
                            .build()
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onImageClick(message.imagePath) }
                            .testTag("user_message_image")
                    ) {
                        AsyncImage(
                            model = imageRequest,
                            contentDescription = strings.cdAttachedImage,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ZoomIn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .padding(6.dp)
                                    .size(16.dp)
                            )
                        }
                    }
                }

                if (message.text.isNotBlank()) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }

                if (message.languageLabel != null || message.subjectLabel != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOfNotNull(message.subjectLabel, message.languageLabel).forEach { badge ->
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = badge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistantSolutionCard(
    message: ChatMessage,
    showFollowUpActions: Boolean,
    onCopyAnswer: () -> Unit,
    onFollowUpClick: (String) -> Unit,
    onRetryClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val strings = LocalAppStrings.current

    if (message.isError) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = colorScheme.errorContainer,
            border = BorderStroke(1.dp, colorScheme.error.copy(alpha = 0.4f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("error_solution_card")
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = colorScheme.error
                    )
                    Text(
                        text = strings.errorTitle,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = colorScheme.onErrorContainer
                    )
                }
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onErrorContainer
                )
                OutlinedButton(
                    onClick = onRetryClick,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = colorScheme.onErrorContainer
                    ),
                    modifier = Modifier.testTag("retry_solve_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(text = strings.retryLabel)
                }
            }
        }
        return
    }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = colorScheme.surface,
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("assistant_solution_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = colorScheme.primaryContainer
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .padding(6.dp)
                                .size(16.dp)
                        )
                    }
                    Text(
                        text = if (message.isStreaming) {
                            strings.solvingStatus
                        } else {
                            strings.solverAssistantName
                        },
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = colorScheme.primary
                    )
                    if (message.isStreaming) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }

                if (!message.isStreaming && message.text.isNotBlank()) {
                    IconButton(
                        onClick = onCopyAnswer,
                        modifier = Modifier.testTag("copy_answer_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = strings.cdCopyAnswer,
                            tint = colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (message.text.isNotBlank()) {
                MarkdownRichText(markdown = message.text)
            }

            AnimatedVisibility(visible = showFollowUpActions) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    strings.followUps.forEachIndexed { idx, (label, prompt) ->
                        AssistChip(
                            onClick = { onFollowUpClick(prompt) },
                            label = {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            modifier = Modifier.testTag("followup_chip_$idx")
                        )
                    }
                }
            }
        }
    }
}
