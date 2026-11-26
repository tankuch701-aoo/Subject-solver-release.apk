package com.example.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.SubjectSolverRepository
import com.example.data.ImageStorageHelper
import com.example.data.local.ChatMessageEntity
import com.example.data.local.SolveSessionEntity
import com.example.data.local.SolverDatabase
import com.example.model.AppLanguage
import com.example.model.ChatMessage
import com.example.model.LanguageMode
import com.example.model.MessageRole
import com.example.model.SolveSessionSummary
import com.example.model.SolverUiState
import com.example.model.SubjectCategory
import com.example.model.getAppStrings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "SolverViewModel"
private const val PREFS_NAME = "subject_solver_prefs"
private const val KEY_APP_LANGUAGE = "app_language"
private const val KEY_OCR_LANGUAGE = "ocr_language"
private const val STREAM_UI_INTERVAL_MS = 45L

class SolverViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val dao = SolverDatabase.getInstance(application).solverDao()
    private val imageHelper = ImageStorageHelper(application)
    private val repository = SubjectSolverRepository()

    private val initialAppLanguage = prefs.getString(KEY_APP_LANGUAGE, AppLanguage.ENGLISH.name)
        ?.let { name -> runCatching { AppLanguage.valueOf(name) }.getOrNull() }
        ?: AppLanguage.ENGLISH

    private val initialOcrLanguage = prefs.getString(KEY_OCR_LANGUAGE, LanguageMode.AUTO.name)
        ?.let { name -> runCatching { LanguageMode.valueOf(name) }.getOrNull() }
        ?: LanguageMode.AUTO

    private val _uiState = MutableStateFlow(
        SolverUiState(
            appLanguage = initialAppLanguage,
            selectedLanguage = initialOcrLanguage
        )
    )
    val uiState: StateFlow<SolverUiState> = _uiState.asStateFlow()

    private var activeSolveJob: Job? = null
    private var lastSubmittedPrompt: String = ""
    private var lastSubmittedImagePath: String? = null

    init {
        viewModelScope.launch {
            dao.observeSessions().collect { rows ->
                val summaries = rows.map { row ->
                    SolveSessionSummary(
                        id = row.id,
                        title = row.title,
                        subject = runCatching { SubjectCategory.valueOf(row.subjectName) }
                            .getOrDefault(SubjectCategory.ALL),
                        language = runCatching { LanguageMode.valueOf(row.languageName) }
                            .getOrDefault(LanguageMode.AUTO),
                        previewImagePath = row.previewImagePath,
                        updatedAt = row.updatedAt,
                        messageCount = row.messageCount
                    )
                }
                _uiState.update { it.copy(sessions = summaries) }
            }
        }
    }

    fun onDraftTextChanged(text: String) {
        _uiState.update { it.copy(draftText = text, errorMessage = null) }
    }

    fun onAppLanguageSelected(appLanguage: AppLanguage) {
        prefs.edit().putString(KEY_APP_LANGUAGE, appLanguage.name).apply()
        _uiState.update { it.copy(appLanguage = appLanguage) }
    }

    fun onLanguageSelected(language: LanguageMode) {
        prefs.edit().putString(KEY_OCR_LANGUAGE, language.name).apply()
        _uiState.update { it.copy(selectedLanguage = language) }
    }

    fun onSubjectSelected(subject: SubjectCategory) {
        _uiState.update { it.copy(selectedSubject = subject) }
    }

    fun createCameraOutputTarget(): Pair<Uri, File> = imageHelper.createCameraOutputUri()

    fun onPhotoPickedFromGallery(uri: Uri) {
        val strings = getAppStrings(_uiState.value.appLanguage)
        viewModelScope.launch {
            try {
                val savedPath = imageHelper.persistNormalizedImageFromUri(uri)
                if (savedPath != null) {
                    _uiState.update { it.copy(pendingImagePath = savedPath, errorMessage = null) }
                } else {
                    _uiState.update { it.copy(errorMessage = strings.imageLoadError) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load picked image", e)
                _uiState.update {
                    it.copy(errorMessage = e.localizedMessage ?: strings.imageLoadError)
                }
            }
        }
    }

    fun onCameraPhotoCaptured(tempFile: File) {
        val strings = getAppStrings(_uiState.value.appLanguage)
        viewModelScope.launch {
            try {
                val savedPath = imageHelper.persistNormalizedImageFromFile(tempFile)
                if (savedPath != null) {
                    _uiState.update { it.copy(pendingImagePath = savedPath, errorMessage = null) }
                } else {
                    _uiState.update { it.copy(errorMessage = strings.emptyCaptureError) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process captured photo", e)
                _uiState.update {
                    it.copy(errorMessage = e.localizedMessage ?: strings.emptyCaptureError)
                }
            }
        }
    }

    fun clearPendingImage() {
        _uiState.update { it.copy(pendingImagePath = null) }
    }

    fun toggleHistorySheet(show: Boolean) {
        _uiState.update { it.copy(showHistorySheet = show) }
    }

    fun toggleSettingsSheet(show: Boolean) {
        _uiState.update { it.copy(showSettingsSheet = show) }
    }

    fun openImagePreview(path: String?) {
        _uiState.update { it.copy(previewImagePath = path) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun startNewSession() {
        activeSolveJob?.cancel()
        _uiState.update {
            it.copy(
                activeSessionId = null,
                messages = emptyList(),
                draftText = "",
                pendingImagePath = null,
                isGenerating = false,
                errorMessage = null,
                showHistorySheet = false
            )
        }
    }

    fun openSession(sessionId: Long) {
        activeSolveJob?.cancel()
        viewModelScope.launch {
            val entities = dao.getMessagesForSession(sessionId)
            val session = _uiState.value.sessions.find { it.id == sessionId }
            val loadedMessages = entities.map { entity ->
                ChatMessage(
                    id = entity.id,
                    sessionId = entity.sessionId,
                    role = if (entity.role == MessageRole.USER.name) MessageRole.USER else MessageRole.ASSISTANT,
                    text = entity.text,
                    imagePath = entity.imagePath,
                    languageLabel = entity.languageLabel,
                    subjectLabel = entity.subjectLabel,
                    timestamp = entity.timestamp
                )
            }
            _uiState.update { state ->
                state.copy(
                    activeSessionId = sessionId,
                    messages = loadedMessages,
                    selectedLanguage = session?.language ?: state.selectedLanguage,
                    selectedSubject = session?.subject ?: state.selectedSubject,
                    isGenerating = false,
                    errorMessage = null,
                    showHistorySheet = false
                )
            }
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            dao.deleteSession(sessionId)
            if (_uiState.value.activeSessionId == sessionId) {
                startNewSession()
            }
        }
    }

    fun submitProblem(overridePrompt: String? = null) {
        val currentState = _uiState.value
        if (currentState.isGenerating) return

        val promptText = (overridePrompt ?: currentState.draftText).trim()
        val imagePath = if (overridePrompt != null) null else currentState.pendingImagePath

        if (promptText.isBlank() && imagePath == null) return

        val appLanguage = currentState.appLanguage
        val strings = getAppStrings(appLanguage)
        val language = currentState.selectedLanguage
        val subject = currentState.selectedSubject
        val priorMessages = currentState.messages.filter { !it.isError }

        lastSubmittedPrompt = promptText
        lastSubmittedImagePath = imagePath

        activeSolveJob = viewModelScope.launch {
            val now = System.currentTimeMillis()
            var sessionId = currentState.activeSessionId
            if (sessionId == null) {
                val sessionTitle = when {
                    promptText.isNotBlank() -> promptText.take(48)
                    else -> "${strings.subjectName(subject)} Photo"
                }
                sessionId = dao.insertSession(
                    SolveSessionEntity(
                        title = sessionTitle,
                        subjectName = subject.name,
                        languageName = language.name,
                        previewImagePath = imagePath,
                        updatedAt = now
                    )
                )
            } else {
                dao.updateSessionMetadata(
                    sessionId = sessionId,
                    updatedAt = now,
                    subjectName = subject.name,
                    languageName = language.name
                )
            }

            val displayUserText = promptText.ifBlank { strings.defaultPhotoPrompt }

            val userMsgId = dao.insertMessage(
                ChatMessageEntity(
                    sessionId = sessionId,
                    role = MessageRole.USER.name,
                    text = displayUserText,
                    imagePath = imagePath,
                    languageLabel = language.shortLabel,
                    subjectLabel = strings.subjectName(subject),
                    timestamp = now
                )
            )

            val userMessage = ChatMessage(
                id = userMsgId,
                sessionId = sessionId,
                role = MessageRole.USER,
                text = displayUserText,
                imagePath = imagePath,
                languageLabel = language.shortLabel,
                subjectLabel = strings.subjectName(subject),
                timestamp = now
            )

            val streamingPlaceholder = ChatMessage(
                id = -now,
                sessionId = sessionId,
                role = MessageRole.ASSISTANT,
                text = "",
                languageLabel = language.shortLabel,
                subjectLabel = strings.subjectName(subject),
                timestamp = now + 1,
                isStreaming = true
            )

            _uiState.update { state ->
                state.copy(
                    activeSessionId = sessionId,
                    messages = priorMessages + userMessage + streamingPlaceholder,
                    draftText = if (overridePrompt == null) "" else state.draftText,
                    pendingImagePath = if (overridePrompt == null) null else state.pendingImagePath,
                    isGenerating = true,
                    errorMessage = null
                )
            }

            try {
                val attachedBitmap = imagePath?.let { imageHelper.loadBitmapForAi(it) }
                val historyBitmaps = mutableMapOf<String, Bitmap>()
                for (msg in priorMessages) {
                    val pastPath = msg.imagePath
                    if (pastPath != null && !historyBitmaps.containsKey(pastPath)) {
                        imageHelper.loadBitmapForAi(pastPath)?.let { bmp ->
                            historyBitmaps[pastPath] = bmp
                        }
                    }
                }

                var finalText = ""
                var lastUiPushTime = 0L
                repository.solveStream(
                    historyMessages = priorMessages,
                    historyBitmaps = historyBitmaps,
                    userPrompt = promptText,
                    attachedBitmap = attachedBitmap,
                    languageMode = language,
                    appLanguage = appLanguage,
                    subjectCategory = subject
                ).collect { partial ->
                    finalText = partial
                    val elapsed = SystemClock.elapsedRealtime() - lastUiPushTime
                    if (elapsed >= STREAM_UI_INTERVAL_MS) {
                        lastUiPushTime = SystemClock.elapsedRealtime()
                        _uiState.update { state ->
                            val updated = state.messages.map { m ->
                                if (m.id == streamingPlaceholder.id) {
                                    m.copy(text = partial, isStreaming = true)
                                } else {
                                    m
                                }
                            }
                            state.copy(messages = updated)
                        }
                    }
                }

                val savedAssistantId = dao.insertMessage(
                    ChatMessageEntity(
                        sessionId = sessionId,
                        role = MessageRole.ASSISTANT.name,
                        text = finalText,
                        languageLabel = language.shortLabel,
                        subjectLabel = strings.subjectName(subject),
                        timestamp = System.currentTimeMillis()
                    )
                )

                _uiState.update { state ->
                    val updated = state.messages.map { m ->
                        if (m.id == streamingPlaceholder.id) {
                            m.copy(id = savedAssistantId, text = finalText, isStreaming = false)
                        } else {
                            m
                        }
                    }
                    state.copy(messages = updated, isGenerating = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Problem solving failed", e)
                val friendlyError = e.localizedMessage
                    ?: "Unable to solve right now. Please check your connection and try again."
                _uiState.update { state ->
                    val updated = state.messages.map { m ->
                        if (m.id == streamingPlaceholder.id) {
                            m.copy(
                                text = friendlyError,
                                isStreaming = false,
                                isError = true
                            )
                        } else {
                            m
                        }
                    }
                    state.copy(
                        messages = updated,
                        isGenerating = false,
                        errorMessage = friendlyError
                    )
                }
            }
        }
    }

    fun retryLastProblem() {
        val currentState = _uiState.value
        if (currentState.isGenerating) return
        val cleaned = currentState.messages.dropLastWhile { it.isError }
        _uiState.update {
            it.copy(
                messages = cleaned,
                errorMessage = null,
                draftText = lastSubmittedPrompt,
                pendingImagePath = lastSubmittedImagePath
            )
        }
        submitProblem()
    }
}
