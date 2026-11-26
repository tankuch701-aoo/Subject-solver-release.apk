package com.example.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.LocalAppStrings
import com.example.model.getAppStrings
import com.example.ui.components.ChatMessageList
import com.example.ui.components.ComposerBar
import com.example.ui.components.HistoryBottomSheet
import com.example.ui.components.ImageViewerDialog
import com.example.ui.components.SettingsBottomSheet
import com.example.ui.components.SolverTopBar
import com.example.ui.components.WelcomeHeroSection
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun SubjectSolverScreen(
    viewModel: SolverViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val appStrings = remember(uiState.appLanguage) { getAppStrings(uiState.appLanguage) }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var pendingCameraFile by remember { mutableStateOf<File?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val capturedFile = pendingCameraFile
        pendingCameraFile = null
        if (success && capturedFile != null) {
            viewModel.onCameraPhotoCaptured(capturedFile)
        } else {
            capturedFile?.delete()
        }
    }

    val launchCamera: () -> Unit = {
        val (uri, file) = viewModel.createCameraOutputTarget()
        pendingCameraFile = file
        takePictureLauncher.launch(uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCamera()
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(appStrings.cameraPermissionRationale)
            }
        }
    }

    val requestTakePhoto: () -> Unit = {
        val hasCameraPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (hasCameraPermission) {
            launchCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.onPhotoPickedFromGallery(uri)
        }
    }

    val requestUploadPhoto: () -> Unit = {
        photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    BackHandler(enabled = uiState.messages.isNotEmpty() && !uiState.isGenerating) {
        viewModel.startNewSession()
    }

    CompositionLocalProvider(LocalAppStrings provides appStrings) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                SolverTopBar(
                    selectedLanguage = uiState.selectedLanguage,
                    selectedSubject = uiState.selectedSubject,
                    sessionCount = uiState.sessions.size,
                    hasActiveMessages = uiState.messages.isNotEmpty(),
                    onLanguageSelected = viewModel::onLanguageSelected,
                    onSubjectSelected = viewModel::onSubjectSelected,
                    onOpenHistory = { viewModel.toggleHistorySheet(true) },
                    onOpenSettings = { viewModel.toggleSettingsSheet(true) },
                    onNewSolve = viewModel::startNewSession
                )
            },
            bottomBar = {
                ComposerBar(
                    draftText = uiState.draftText,
                    pendingImagePath = uiState.pendingImagePath,
                    isGenerating = uiState.isGenerating,
                    hasActiveMessages = uiState.messages.isNotEmpty(),
                    onDraftTextChanged = viewModel::onDraftTextChanged,
                    onTakePhotoClick = requestTakePhoto,
                    onUploadPhotoClick = requestUploadPhoto,
                    onNewSolveClick = viewModel::startNewSession,
                    onClearPendingImage = viewModel::clearPendingImage,
                    onPreviewPendingImage = { path -> viewModel.openImagePreview(path) },
                    onSendClick = { viewModel.submitProblem() }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                AnimatedContent(
                    targetState = uiState.messages.isEmpty(),
                    transitionSpec = {
                        fadeIn(tween(220)) togetherWith fadeOut(tween(160))
                    },
                    label = "hero_chat_transition"
                ) { isEmpty ->
                    if (isEmpty) {
                        WelcomeHeroSection(
                            onTakePhotoClick = requestTakePhoto,
                            onUploadPhotoClick = requestUploadPhoto,
                            onQuickPromptClick = { prompt -> viewModel.submitProblem(overridePrompt = prompt) }
                        )
                    } else {
                        ChatMessageList(
                            messages = uiState.messages,
                            isGenerating = uiState.isGenerating,
                            onImageClick = { path -> viewModel.openImagePreview(path) },
                            onCopyAnswer = { text ->
                                val clipboard =
                                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(
                                    ClipData.newPlainText("Subject Solver Answer", text)
                                )
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(appStrings.copiedToast)
                                }
                            },
                            onFollowUpClick = { followUp ->
                                viewModel.submitProblem(overridePrompt = followUp)
                            },
                            onRetryClick = viewModel::retryLastProblem
                        )
                    }
                }
            }
        }

        if (uiState.showHistorySheet) {
            HistoryBottomSheet(
                sessions = uiState.sessions,
                activeSessionId = uiState.activeSessionId,
                onSelectSession = viewModel::openSession,
                onDeleteSession = viewModel::deleteSession,
                onNewSolve = viewModel::startNewSession,
                onDismiss = { viewModel.toggleHistorySheet(false) }
            )
        }

        if (uiState.showSettingsSheet) {
            SettingsBottomSheet(
                currentAppLanguage = uiState.appLanguage,
                currentOcrLanguage = uiState.selectedLanguage,
                onAppLanguageSelected = viewModel::onAppLanguageSelected,
                onOcrLanguageSelected = viewModel::onLanguageSelected,
                onDismiss = { viewModel.toggleSettingsSheet(false) }
            )
        }

        val previewPath = uiState.previewImagePath
        if (previewPath != null) {
            ImageViewerDialog(
                imagePath = previewPath,
                onDismiss = { viewModel.openImagePreview(null) }
            )
        }
    }
}
