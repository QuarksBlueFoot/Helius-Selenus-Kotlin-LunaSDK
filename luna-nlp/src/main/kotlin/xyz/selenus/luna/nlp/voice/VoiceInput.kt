package xyz.selenus.luna.nlp.voice

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import xyz.selenus.luna.nlp.ParseResult

/**
 * Voice Input Integration - Speech-to-Transaction
 * 
 * Enables voice-first interaction with Solana:
 * - "Hey Solana, send one SOL to alice.sol"
 * - Continuous listening with wake word detection
 * - Multi-language support
 * - Offline voice recognition for privacy
 * 
 * Follows Android 2026 voice assistant patterns
 */

/**
 * Voice Input Controller - Main interface for voice interactions
 */
class VoiceInputController private constructor(
    private val config: VoiceConfig,
    private val speechRecognizer: SpeechRecognizer,
    private val textToSpeech: TextToSpeech?,
    private val wakeWordDetector: WakeWordDetector?
) {
    
    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val state: StateFlow<VoiceState> = _state.asStateFlow()
    
    private val _transcript = MutableStateFlow<String>("")
    val transcript: StateFlow<String> = _transcript.asStateFlow()
    
    companion object {
        fun create(config: VoiceConfig = VoiceConfig()): VoiceInputController {
            return VoiceInputController(
                config = config,
                speechRecognizer = DefaultSpeechRecognizer(),
                textToSpeech = null,
                wakeWordDetector = null
            )
        }
        
        fun create(
            config: VoiceConfig,
            speechRecognizer: SpeechRecognizer,
            textToSpeech: TextToSpeech?,
            wakeWordDetector: WakeWordDetector?
        ): VoiceInputController {
            return VoiceInputController(config, speechRecognizer, textToSpeech, wakeWordDetector)
        }
    }
    
    /**
     * Start listening for voice input
     */
    suspend fun startListening(): Flow<VoiceEvent> = flow {
        _state.value = VoiceState.Listening
        emit(VoiceEvent.Started)
        
        try {
            speechRecognizer.startListening(config.language).collect { result ->
                when (result) {
                    is SpeechResult.Partial -> {
                        _transcript.value = result.text
                        emit(VoiceEvent.PartialResult(result.text))
                    }
                    is SpeechResult.Final -> {
                        _transcript.value = result.text
                        _state.value = VoiceState.Processing
                        emit(VoiceEvent.FinalResult(result.text, result.confidence))
                    }
                    is SpeechResult.Error -> {
                        _state.value = VoiceState.Error(result.error)
                        emit(VoiceEvent.Error(result.error))
                    }
                }
            }
        } finally {
            _state.value = VoiceState.Idle
            emit(VoiceEvent.Stopped)
        }
    }
    
    /**
     * Stop listening
     */
    fun stopListening() {
        speechRecognizer.stopListening()
        _state.value = VoiceState.Idle
    }
    
    /**
     * Start continuous listening with wake word
     */
    suspend fun startContinuousListening(): Flow<VoiceEvent> = flow {
        val detector = wakeWordDetector ?: throw IllegalStateException("Wake word detector not configured")
        
        _state.value = VoiceState.WaitingForWakeWord
        emit(VoiceEvent.WaitingForWakeWord)
        
        detector.startDetecting().collect { detected ->
            if (detected.isWakeWord) {
                emit(VoiceEvent.WakeWordDetected(detected.word))
                _state.value = VoiceState.Listening
                
                // Listen for command
                startListening().collect { event ->
                    emit(event)
                }
                
                // Back to waiting for wake word
                _state.value = VoiceState.WaitingForWakeWord
            }
        }
    }
    
    /**
     * Speak response using text-to-speech
     */
    suspend fun speak(text: String) {
        textToSpeech?.speak(text, config.voiceProfile)
    }
    
    /**
     * Confirm transaction with voice
     */
    suspend fun confirmWithVoice(prompt: String): VoiceConfirmation {
        speak(prompt)
        
        var response: VoiceConfirmation = VoiceConfirmation.Timeout
        
        startListening().collect { event ->
            if (event is VoiceEvent.FinalResult) {
                response = parseConfirmation(event.text)
                stopListening()
            }
        }
        
        return response
    }
    
    private fun parseConfirmation(text: String): VoiceConfirmation {
        val normalized = text.lowercase().trim()
        
        return when {
            normalized.matches(Regex("yes|yeah|yep|confirm|approve|do it|send it|go ahead|okay|ok")) -> {
                VoiceConfirmation.Confirmed
            }
            normalized.matches(Regex("no|nope|cancel|stop|don't|abort|never")) -> {
                VoiceConfirmation.Cancelled
            }
            normalized.contains("change") || normalized.contains("modify") -> {
                VoiceConfirmation.Modify(normalized)
            }
            else -> {
                VoiceConfirmation.Unclear(normalized)
            }
        }
    }
    
    /**
     * Process voice input and execute intent
     */
    suspend fun processVoiceIntent(
        nlpParser: suspend (String) -> ParseResult,
        executor: suspend (ParseResult.Success) -> Unit
    ): Flow<VoiceIntentResult> = flow {
        startListening().collect { event ->
            when (event) {
                is VoiceEvent.FinalResult -> {
                    emit(VoiceIntentResult.Recognized(event.text))
                    
                    // Parse with NLP
                    when (val result = nlpParser(event.text)) {
                        is ParseResult.Success -> {
                            // Speak confirmation request
                            speak(generateConfirmationPrompt(result))
                            emit(VoiceIntentResult.Parsed(result))
                            
                            // Wait for voice confirmation
                            val confirmation = confirmWithVoice("Say yes to confirm or no to cancel")
                            
                            when (confirmation) {
                                is VoiceConfirmation.Confirmed -> {
                                    executor(result)
                                    speak("Transaction sent")
                                    emit(VoiceIntentResult.Executed(result))
                                }
                                is VoiceConfirmation.Cancelled -> {
                                    speak("Cancelled")
                                    emit(VoiceIntentResult.Cancelled)
                                }
                                is VoiceConfirmation.Modify -> {
                                    speak("What would you like to change?")
                                    emit(VoiceIntentResult.ModificationRequested(confirmation.request))
                                }
                                else -> {
                                    speak("I didn't understand. Please try again.")
                                    emit(VoiceIntentResult.ConfirmationUnclear)
                                }
                            }
                        }
                        is ParseResult.NeedsInfo -> {
                            speak(result.suggestion)
                            emit(VoiceIntentResult.NeedsMoreInfo(result.suggestion))
                        }
                        is ParseResult.Unknown -> {
                            speak("I didn't understand that command. Try saying send, swap, or stake.")
                            emit(VoiceIntentResult.NotUnderstood)
                        }
                        is ParseResult.Ambiguous -> {
                            speak("Did you mean ${result.primary.summary()}?")
                            emit(VoiceIntentResult.Ambiguous(result.primary))
                        }
                    }
                    
                    stopListening()
                }
                is VoiceEvent.Error -> {
                    speak("Sorry, I couldn't hear you. Please try again.")
                    emit(VoiceIntentResult.Error(event.error))
                    stopListening()
                }
                else -> {}
            }
        }
    }
    
    private fun generateConfirmationPrompt(result: ParseResult.Success): String {
        return "You want to ${result.intent.summary()}. Is that correct?"
    }
}

/**
 * Voice state
 */
sealed class VoiceState {
    data object Idle : VoiceState()
    data object WaitingForWakeWord : VoiceState()
    data object Listening : VoiceState()
    data object Processing : VoiceState()
    data class Error(val message: String) : VoiceState()
}

/**
 * Voice events
 */
sealed class VoiceEvent {
    data object Started : VoiceEvent()
    data object Stopped : VoiceEvent()
    data object WaitingForWakeWord : VoiceEvent()
    data class WakeWordDetected(val word: String) : VoiceEvent()
    data class PartialResult(val text: String) : VoiceEvent()
    data class FinalResult(val text: String, val confidence: Float) : VoiceEvent()
    data class Error(val error: String) : VoiceEvent()
}

/**
 * Voice confirmation result
 */
sealed class VoiceConfirmation {
    data object Confirmed : VoiceConfirmation()
    data object Cancelled : VoiceConfirmation()
    data class Modify(val request: String) : VoiceConfirmation()
    data class Unclear(val heard: String) : VoiceConfirmation()
    data object Timeout : VoiceConfirmation()
}

/**
 * Voice intent processing result
 */
sealed class VoiceIntentResult {
    data class Recognized(val text: String) : VoiceIntentResult()
    data class Parsed(val result: ParseResult.Success) : VoiceIntentResult()
    data class Executed(val result: ParseResult.Success) : VoiceIntentResult()
    data object Cancelled : VoiceIntentResult()
    data class ModificationRequested(val request: String) : VoiceIntentResult()
    data object ConfirmationUnclear : VoiceIntentResult()
    data class NeedsMoreInfo(val prompt: String) : VoiceIntentResult()
    data object NotUnderstood : VoiceIntentResult()
    data class Ambiguous(val primaryIntent: xyz.selenus.luna.nlp.TransactionIntent) : VoiceIntentResult()
    data class Error(val error: String) : VoiceIntentResult()
}

/**
 * Voice configuration
 */
data class VoiceConfig(
    val language: VoiceLanguage = VoiceLanguage.ENGLISH_US,
    val voiceProfile: VoiceProfile = VoiceProfile.DEFAULT,
    val enableWakeWord: Boolean = false,
    val wakeWords: List<String> = listOf("hey solana", "okay solana", "luna"),
    val confirmationRequired: Boolean = true,
    val speakResponses: Boolean = true,
    val offlineMode: Boolean = false,
    val continuousListening: Boolean = false,
    val listenTimeoutMs: Long = 10000
)

/**
 * Supported voice languages
 */
enum class VoiceLanguage(val code: String, val displayName: String) {
    ENGLISH_US("en-US", "English (US)"),
    ENGLISH_UK("en-GB", "English (UK)"),
    SPANISH("es-ES", "Spanish"),
    PORTUGUESE("pt-BR", "Portuguese (Brazil)"),
    FRENCH("fr-FR", "French"),
    GERMAN("de-DE", "German"),
    CHINESE("zh-CN", "Chinese (Simplified)"),
    JAPANESE("ja-JP", "Japanese"),
    KOREAN("ko-KR", "Korean"),
    HINDI("hi-IN", "Hindi")
}

/**
 * Voice profile for TTS
 */
enum class VoiceProfile {
    DEFAULT,
    FRIENDLY,
    PROFESSIONAL,
    CONCISE
}

/**
 * Speech recognizer interface
 */
interface SpeechRecognizer {
    fun startListening(language: VoiceLanguage): Flow<SpeechResult>
    fun stopListening()
    val isAvailable: Boolean
}

/**
 * Speech recognition result
 */
sealed class SpeechResult {
    data class Partial(val text: String) : SpeechResult()
    data class Final(val text: String, val confidence: Float) : SpeechResult()
    data class Error(val error: String) : SpeechResult()
}

/**
 * Default speech recognizer (placeholder - would use platform API)
 */
class DefaultSpeechRecognizer : SpeechRecognizer {
    override val isAvailable: Boolean = true
    
    override fun startListening(language: VoiceLanguage): Flow<SpeechResult> = flow {
        // Platform-specific implementation would go here
        // This is a placeholder that would integrate with:
        // - Android SpeechRecognizer
        // - iOS Speech Framework
        // - Vosk for offline recognition
        kotlinx.coroutines.delay(100)
        emit(SpeechResult.Error("Speech recognition not implemented - use platform bridge"))
    }
    
    override fun stopListening() {
        // Platform-specific implementation
    }
}

/**
 * Text-to-speech interface
 */
interface TextToSpeech {
    suspend fun speak(text: String, profile: VoiceProfile)
    fun stop()
    val isAvailable: Boolean
}

/**
 * Wake word detector interface
 */
interface WakeWordDetector {
    fun startDetecting(): Flow<WakeWordResult>
    fun stopDetecting()
    val isAvailable: Boolean
}

/**
 * Wake word detection result
 */
data class WakeWordResult(
    val isWakeWord: Boolean,
    val word: String,
    val confidence: Float
)

/**
 * Voice accessibility features
 */
class VoiceAccessibility(
    private val controller: VoiceInputController
) {
    
    /**
     * Read transaction details aloud for visually impaired users
     */
    suspend fun describeTransaction(result: ParseResult.Success) {
        val description = buildAccessibleDescription(result)
        controller.speak(description)
    }
    
    /**
     * Provide audio feedback for state changes
     */
    suspend fun announceState(state: String) {
        controller.speak(state)
    }
    
    /**
     * Read balance with proper currency formatting
     */
    suspend fun readBalance(amount: java.math.BigDecimal, token: String) {
        val spoken = formatAmountForSpeech(amount, token)
        controller.speak(spoken)
    }
    
    private fun buildAccessibleDescription(result: ParseResult.Success): String {
        return buildString {
            append("Transaction summary. ")
            append(result.intent.summary())
            append(". Confidence level: ${(result.confidence * 100).toInt()} percent.")
        }
    }
    
    private fun formatAmountForSpeech(amount: java.math.BigDecimal, token: String): String {
        val amountStr = when {
            amount >= java.math.BigDecimal(1000000) -> "${amount.divide(java.math.BigDecimal(1000000))} million"
            amount >= java.math.BigDecimal(1000) -> "${amount.divide(java.math.BigDecimal(1000))} thousand"
            else -> amount.toPlainString()
        }
        
        return "$amountStr $token"
    }
}

/**
 * Phonetic number parser for voice input
 */
object PhoneticNumberParser {
    
    private val wordToNumber = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4,
        "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9,
        "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13,
        "fourteen" to 14, "fifteen" to 15, "sixteen" to 16, "seventeen" to 17,
        "eighteen" to 18, "nineteen" to 19, "twenty" to 20, "thirty" to 30,
        "forty" to 40, "fifty" to 50, "sixty" to 60, "seventy" to 70,
        "eighty" to 80, "ninety" to 90, "hundred" to 100, "thousand" to 1000,
        "million" to 1000000, "billion" to 1000000000
    )
    
    private val homophones = mapOf(
        "won" to "1", "to" to "2", "too" to "2", "for" to "4",
        "ate" to "8", "niner" to "9"
    )
    
    /**
     * Parse spoken numbers to numeric values
     * "one point five" → 1.5
     * "twenty-five" → 25
     * "a hundred" → 100
     */
    fun parse(spoken: String): java.math.BigDecimal? {
        val normalized = spoken.lowercase()
            .replace("-", " ")
            .replace("point", ".")
            .replace("and", " ")
            .replace("a ", "1 ")
        
        // Replace homophones
        var processed = normalized
        homophones.forEach { (word, digit) ->
            processed = processed.replace(Regex("\\b$word\\b"), digit)
        }
        
        // Try direct numeric parsing first
        processed.replace(" ", "").toBigDecimalOrNull()?.let { return it }
        
        // Parse word numbers
        val parts = processed.split("\\s+".toRegex())
        var result = 0L
        var current = 0L
        var hasDecimal = false
        var decimalPart = StringBuilder()
        
        for (part in parts) {
            when {
                part == "." -> {
                    hasDecimal = true
                    result += current
                    current = 0
                }
                hasDecimal -> {
                    wordToNumber[part]?.let { decimalPart.append(it) }
                        ?: part.toIntOrNull()?.let { decimalPart.append(it) }
                }
                wordToNumber.containsKey(part) -> {
                    val value = wordToNumber[part]!!
                    if (value >= 100) {
                        current = if (current == 0L) value.toLong() else current * value
                    } else {
                        current += value
                    }
                }
                part.toLongOrNull() != null -> {
                    current += part.toLong()
                }
            }
        }
        
        result += current
        
        return if (hasDecimal && decimalPart.isNotEmpty()) {
            java.math.BigDecimal("$result.$decimalPart")
        } else {
            java.math.BigDecimal(result)
        }
    }
}
