package com.example.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Biotech
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.HistoryEdu
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.ui.graphics.vector.ImageVector

enum class LanguageMode(
    val displayName: String,
    val shortLabel: String,
    val instructionHint: String
) {
    AUTO(
        displayName = "Auto-Detect (Any Language)",
        shortLabel = "Auto OCR",
        instructionHint = "Automatically detect the exact language(s) in the image or text (such as Khmer, English, French, Vietnamese, Chinese, Japanese, Arabic, Spanish, etc.) and answer in the user's primary language unless instructed otherwise."
    ),
    KHMER(
        displayName = "ភាសាខ្មែរ (Cambodia)",
        shortLabel = "ខ្មែរ (KH)",
        instructionHint = "Read Khmer script (អក្សរខ្មែរ) and any mixed mathematical symbols with extreme precision following Cambodian Ministry of Education (MoEYS) curriculum conventions, and write the explanation clearly in Khmer (ភាសាខ្មែរ)."
    ),
    ENGLISH(
        displayName = "English (International)",
        shortLabel = "English",
        instructionHint = "Read all text and equations accurately and explain the solution clearly in English."
    ),
    FRENCH(
        displayName = "Français",
        shortLabel = "Français",
        instructionHint = "Read the problem accurately and provide the full explanation in French."
    ),
    CHINESE(
        displayName = "中文 (Chinese)",
        shortLabel = "中文",
        instructionHint = "Read Chinese characters and equations accurately and provide the full explanation in Chinese."
    ),
    JAPANESE(
        displayName = "日本語 (Japanese)",
        shortLabel = "日本語",
        instructionHint = "Read Japanese text and equations accurately and provide the full explanation in Japanese."
    ),
    VIETNAMESE(
        displayName = "Tiếng Việt",
        shortLabel = "Tiếng Việt",
        instructionHint = "Read Vietnamese text and equations accurately and provide the full explanation in Vietnamese."
    ),
    SPANISH(
        displayName = "Español",
        shortLabel = "Español",
        instructionHint = "Read Spanish text and equations accurately and provide the full explanation in Spanish."
    ),
    BILINGUAL_KH_EN(
        displayName = "ខ្មែរ + English (Bilingual)",
        shortLabel = "ខ្មែរ / EN",
        instructionHint = "Provide key definitions and final answer in both Khmer (ភាសាខ្មែរ) and English so the student can understand both curricula clearly."
    )
}

enum class SubjectCategory(
    val displayName: String,
    val icon: ImageVector
) {
    ALL("All Subjects", Icons.Rounded.AutoAwesome),
    MATH("Mathematics", Icons.Rounded.Calculate),
    PHYSICS("Physics", Icons.Rounded.Speed),
    CHEMISTRY("Chemistry", Icons.Rounded.Science),
    BIOLOGY("Biology", Icons.Rounded.Biotech),
    LITERATURE("Literature", Icons.Rounded.MenuBook),
    HISTORY("History & Civics", Icons.Rounded.HistoryEdu),
    LANGUAGES("World Languages", Icons.Rounded.Public),
    CODING("Computer Science", Icons.Rounded.Code)
}

enum class MessageRole {
    USER,
    ASSISTANT
}

data class ChatMessage(
    val id: Long = 0L,
    val sessionId: Long = 0L,
    val role: MessageRole,
    val text: String,
    val imagePath: String? = null,
    val languageLabel: String? = null,
    val subjectLabel: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false,
    val isError: Boolean = false
)

data class SolveSessionSummary(
    val id: Long,
    val title: String,
    val subject: SubjectCategory,
    val language: LanguageMode,
    val previewImagePath: String?,
    val updatedAt: Long,
    val messageCount: Int
)

data class SolverUiState(
    val activeSessionId: Long? = null,
    val messages: List<ChatMessage> = emptyList(),
    val sessions: List<SolveSessionSummary> = emptyList(),
    val appLanguage: AppLanguage = AppLanguage.ENGLISH,
    val selectedLanguage: LanguageMode = LanguageMode.AUTO,
    val selectedSubject: SubjectCategory = SubjectCategory.ALL,
    val draftText: String = "",
    val pendingImagePath: String? = null,
    val isGenerating: Boolean = false,
    val errorMessage: String? = null,
    val showHistorySheet: Boolean = false,
    val showSettingsSheet: Boolean = false,
    val previewImagePath: String? = null
)
