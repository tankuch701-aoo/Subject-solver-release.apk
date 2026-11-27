package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.model.AppLanguage
import com.example.model.ChatMessage
import com.example.model.LanguageMode
import com.example.model.MessageRole
import com.example.model.SubjectCategory
import com.google.android.gms.tasks.Task
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.appcheck.AppCheckToken
import com.google.firebase.appcheck.FirebaseAppCheck
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "SubjectSolverRepo"
private const val AI_TIMEOUT_MS = 60_000L
private const val DUMMY_ERROR_APP_CHECK_TOKEN = "eyJlcnJvciI6IlVOS05PV05fRVJST1IifQ=="

class SubjectSolverRepository {

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                temperature = 0.2f
            },
            systemInstruction = content {
                text(
                    """
                    You are Subject Solver, an elite, patient, multi-language academic AI tutor and high-precision OCR reader.
                    
                    CORE CAPABILITIES:
                    1. Multi-Language OCR & Reading:
                       - Accurately read printed or handwritten problems from photos in Khmer (ភាសាខ្មែរ - Cambodian school & BacII / Grade 12 / Grade 9 subjects), English, French, Vietnamese, Chinese, Japanese, Korean, Spanish, Arabic, and mixed-language textbooks or worksheets.
                       - Pay special attention to Khmer vowels, subscripts (ជើងអក្សរ), math symbols, fractions, exponents, chemical equations, physics units, and multi-choice options (ក ខ គ ឃ or A B C D).
                       - If an image contains multiple numbered questions and the user specified one in chat (e.g. "solve #2"), focus on that question. If none is specified and multiple short items exist, solve them clearly in order.
                    2. Clear, Accurate Solutions:
                       - When an image is attached, start with a short `### Recognized Problem` section transcribing what you read so the user can verify the OCR.
                       - Next, provide `### Final Answer` clearly highlighted in bold so the user immediately sees the correct answer.
                       - Then provide `### Step-by-Step Explanation` breaking down formulas, reasoning, grammar, or historical/scientific facts cleanly.
                       - Double-check all arithmetic, algebra, calculus, stoichiometry, and logic before answering.
                    3. Language & Curriculum Fidelity:
                       - Follow the user's selected Language Mode or explicit chat instructions (e.g., if the problem is in Khmer or the user writes in Khmer or selects Khmer mode, respond in natural, grammatically accurate Khmer; if Bilingual, give key answers in both Khmer and English).
                       - If the user asks follow-up questions ("explain step 2 simpler", "give another example", "check my work"), use the conversation history including previously uploaded photos.
                    4. Honesty & Clarity:
                       - If a photo is too blurry or cropped to read a crucial number or character, politely state exactly which part is unreadable and ask for a clearer photo or typed clarification instead of guessing blindly.
                    """.trimIndent()
                )
            }
        )
    }

    fun solveStream(
        historyMessages: List<ChatMessage>,
        historyBitmaps: Map<String, Bitmap>,
        userPrompt: String,
        attachedBitmap: Bitmap?,
        languageMode: LanguageMode,
        appLanguage: AppLanguage,
        subjectCategory: SubjectCategory
    ): Flow<String> = flow {
        val chatHistory: List<Content> = historyMessages
            .filter { !it.isError && it.text.isNotBlank() }
            .map { msg ->
                val role = if (msg.role == MessageRole.USER) "user" else "model"
                content(role) {
                    val pastBitmap = msg.imagePath?.let { historyBitmaps[it] }
                    if (pastBitmap != null) {
                        image(pastBitmap)
                    }
                    text(msg.text)
                }
            }

        val contextualDirective = buildString {
            append("[Context: Subject=${subjectCategory.displayName}, AppLanguage=${appLanguage.englishName}, OCRLanguage=${languageMode.displayName}. ")
            append(languageMode.instructionHint)
            if (languageMode == LanguageMode.AUTO) {
                append(" Note: The user's interface language is ${appLanguage.englishName} (${appLanguage.nativeName}); prefer explaining in ${appLanguage.englishName} when appropriate unless the problem/user requests another language.")
            }
            append("]\n")
            if (userPrompt.isNotBlank()) {
                append(userPrompt.trim())
            } else if (attachedBitmap != null) {
                append("Please perform OCR to read the problem in this image accurately and solve it step-by-step.")
            }
        }

        val messageContent = content("user") {
            if (attachedBitmap != null) {
                image(attachedBitmap)
            }
            text(contextualDirective)
        }

        try {
            val accumulated = StringBuilder()
            withTimeout(AI_TIMEOUT_MS) {
                ensureValidAppCheckToken(forceRefresh = false)

                suspend fun streamAttempt() {
                    val chat = model.startChat(history = chatHistory)
                    chat.sendMessageStream(messageContent).collect { chunk ->
                        val piece = chunk.text.orEmpty()
                        if (piece.isNotEmpty()) {
                            accumulated.append(piece)
                            emit(accumulated.toString())
                        }
                    }
                }

                try {
                    streamAttempt()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (accumulated.isEmpty() && isAppCheckError(e)) {
                        Log.w(TAG, "App Check token rejected; force-refreshing token and retrying once.", e)
                        ensureValidAppCheckToken(forceRefresh = true)
                        streamAttempt()
                    } else {
                        throw e
                    }
                }
            }
            if (accumulated.isBlank()) {
                throw IllegalStateException("The AI solver returned an empty response. Please try again.")
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI solver timed out after 60s", e)
            throw IllegalStateException(
                "Solving timed out after 60 seconds. Please check your internet connection or try a clearer cropped photo."
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI solver failed", e)
            throw e
        }
    }

    private suspend fun ensureValidAppCheckToken(forceRefresh: Boolean) {
        val appCheck = FirebaseAppCheck.getInstance()
        val token: AppCheckToken = try {
            appCheck.getAppCheckToken(forceRefresh).awaitTask()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!forceRefresh) {
                Log.w(TAG, "Initial App Check token check failed; retrying with forceRefresh=true.", e)
                try {
                    appCheck.getAppCheckToken(true).awaitTask()
                } catch (retryErr: CancellationException) {
                    throw retryErr
                } catch (retryErr: Exception) {
                    val detail = retryErr.localizedMessage ?: retryErr.javaClass.simpleName
                    throw IllegalStateException(
                        "Firebase App Check verification failed: $detail. Please check your internet connection and Google Play services."
                    )
                }
            } else {
                val detail = e.localizedMessage ?: e.javaClass.simpleName
                throw IllegalStateException(
                    "Firebase App Check verification failed: $detail. Please check your internet connection and Google Play services."
                )
            }
        }

        val tokenValue = token.token
        if (tokenValue.isBlank() || tokenValue == DUMMY_ERROR_APP_CHECK_TOKEN) {
            if (!forceRefresh) {
                ensureValidAppCheckToken(forceRefresh = true)
                return
            }
            throw IllegalStateException(
                "Firebase App Check returned an invalid attestation token. Please check your internet connection and Google Play services."
            )
        }
    }

    private fun isAppCheckError(error: Throwable): Boolean {
        val msg = error.message.orEmpty()
        return msg.contains("App Check", ignoreCase = true) ||
            msg.contains("appcheck", ignoreCase = true) ||
            msg.contains("403", ignoreCase = true) ||
            msg.contains("UNAUTHENTICATED", ignoreCase = true)
    }

    private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result ->
            if (cont.isActive) cont.resume(result)
        }
        addOnFailureListener { exc ->
            if (cont.isActive) cont.resumeWithException(exc)
        }
        addOnCanceledListener {
            cont.cancel()
        }
    }
}
