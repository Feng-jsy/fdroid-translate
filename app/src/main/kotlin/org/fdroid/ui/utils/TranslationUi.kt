package org.fdroid.ui.utils

import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.fdroid.R
import org.fdroid.settings.SettingsConstants.PREF_DEFAULT_TRANSLATION_AUTO
import org.fdroid.settings.SettingsConstants.PREF_DEFAULT_TRANSLATION_ENABLED
import org.fdroid.settings.SettingsConstants.PREF_DEFAULT_TRANSLATION_ENGINE
import org.fdroid.settings.SettingsConstants.PREF_KEY_TRANSLATION_AUTO
import org.fdroid.settings.SettingsConstants.PREF_KEY_TRANSLATION_ENABLED
import org.fdroid.settings.SettingsConstants.PREF_KEY_TRANSLATION_ENGINE
import org.fdroid.settings.SettingsConstants.TranslationEngineValues
import org.fdroid.ui.details.DescriptionTranslator
import org.fdroid.ui.details.TranslationEngine
import org.fdroid.ui.details.TranslationResult

/** The language translations are produced in: the system language, e.g. "zh-CN" or "en". */
fun translationTargetLanguage(): String =
  Locale.getDefault().let { if (it.language == "zh") "zh-CN" else it.language }

/** Translation preferences as chosen in the settings screen. */
data class TranslationSettings(
  val enabled: Boolean = PREF_DEFAULT_TRANSLATION_ENABLED,
  val autoTranslate: Boolean = PREF_DEFAULT_TRANSLATION_AUTO,
  val engine: TranslationEngine = TranslationEngine.Auto,
)

/**
 * Exposes the translation preferences to Compose without threading them through every caller.
 *
 * It reads and listens to the same SharedPreferences file that [org.fdroid.settings.SettingsManager]
 * writes, so toggling a preference in the settings screen takes effect immediately.
 */
object TranslationPrefs {

  private var listener: OnSharedPreferenceChangeListener? = null
  private val _settings = MutableStateFlow(TranslationSettings())
  val settings: StateFlow<TranslationSettings> = _settings.asStateFlow()

  fun init(context: Context) {
    if (listener != null) return
    val prefs =
      context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
    _settings.value = prefs.readSettings()
    // SharedPreferences keeps listeners weakly, so hold a strong reference to ours.
    val newListener = OnSharedPreferenceChangeListener { p, _ -> _settings.value = p.readSettings() }
    listener = newListener
    prefs.registerOnSharedPreferenceChangeListener(newListener)
  }

  private fun SharedPreferences.readSettings(): TranslationSettings =
    TranslationSettings(
      enabled = getBoolean(PREF_KEY_TRANSLATION_ENABLED, PREF_DEFAULT_TRANSLATION_ENABLED),
      autoTranslate = getBoolean(PREF_KEY_TRANSLATION_AUTO, PREF_DEFAULT_TRANSLATION_AUTO),
      engine =
        when (getString(PREF_KEY_TRANSLATION_ENGINE, PREF_DEFAULT_TRANSLATION_ENGINE)) {
          TranslationEngineValues.Tencent.name -> TranslationEngine.Tencent
          TranslationEngineValues.Google.name -> TranslationEngine.Google
          TranslationEngineValues.MyMemory.name -> TranslationEngine.MyMemory
          else -> TranslationEngine.Auto
        },
    )
}

/** Current translation preferences, kept up to date while the app is running. */
@Composable
fun translationSettings(): TranslationSettings {
  val context = LocalContext.current
  remember(context) { TranslationPrefs.init(context.applicationContext) }
  return TranslationPrefs.settings.collectAsState().value
}

/**
 * State of one translatable block of text.
 *
 * [displayedText] is either the original text or the translation, [isShowingTranslation] tells
 * which one it is. Translations are cached in [DescriptionTranslator], so switching back and forth
 * or scrolling a list does not trigger new requests.
 */
@Stable
class TranslationState internal constructor(
  val originalText: String,
  private val targetLanguage: String,
  private val settings: TranslationSettings,
) {
  var translatedText by mutableStateOf<String?>(null)
    private set
  var isShowingTranslation by mutableStateOf(false)
    private set
  var isTranslating by mutableStateOf(false)
    private set
  var hasFailed by mutableStateOf(false)
    private set

  /** Hide the button when translation is off, there is nothing to translate, or already translated. */
  val isTranslatable: Boolean =
    settings.enabled &&
      originalText.isNotBlank() &&
      !DescriptionTranslator.isAlreadyInTargetLanguage(originalText, targetLanguage)

  val displayedText: String
    get() = if (isShowingTranslation) translatedText ?: originalText else originalText

  suspend fun toggleTranslation() {
    if (translatedText != null) {
      isShowingTranslation = !isShowingTranslation
      hasFailed = false
      return
    }
    isTranslating = true
    hasFailed = false
    val result =
      DescriptionTranslator.translate(originalText, targetLanguage, settings.engine)
    when (result) {
      is TranslationResult.Success -> {
        translatedText = result.text
        isShowingTranslation = true
      }
      is TranslationResult.Error -> hasFailed = true
    }
    isTranslating = false
  }
}

/** Remembers a [TranslationState] for [text], resetting it whenever the text changes. */
@Composable
fun rememberTranslationState(text: String): TranslationState {
  val settings = translationSettings()
  val targetLanguage = remember { translationTargetLanguage() }
  val state = remember(text, settings) { TranslationState(text, targetLanguage, settings) }
  // "Translate automatically" is opt-in, so only fire when the user asked for it.
  if (settings.autoTranslate && state.isTranslatable) {
    LaunchedEffect(state) { state.toggleTranslation() }
  }
  return state
}

/** The "Translate" / "Show original" button belonging to a [TranslationState]. */
@Composable
fun TranslateTextButton(state: TranslationState, modifier: Modifier = Modifier) {
  val scope = rememberCoroutineScope()
  TextButton(
    onClick = { scope.launch { state.toggleTranslation() } },
    enabled = !state.isTranslating,
    modifier = modifier,
  ) {
    if (state.isTranslating) {
      CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
      Spacer(Modifier.size(8.dp))
    }
    Text(
      text =
        when {
          state.isTranslating -> stringResource(R.string.translating)
          state.hasFailed -> stringResource(R.string.translation_failed_retry)
          state.isShowingTranslation -> stringResource(R.string.show_original)
          else -> stringResource(R.string.translate_description)
        }
    )
  }
}

/** Right-aligned row holding a [TranslateTextButton], for use below a block of text. */
@Composable
fun TranslateButtonRow(state: TranslationState, modifier: Modifier = Modifier) {
  if (!state.isTranslatable) return
  Row(horizontalArrangement = Arrangement.End, modifier = modifier) {
    TranslateTextButton(state)
  }
}
