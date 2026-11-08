package org.fdroid.ui.utils

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlinx.coroutines.launch
import org.fdroid.R
import org.fdroid.ui.details.DescriptionTranslator
import org.fdroid.ui.details.TranslationResult

/** The language translations are produced in: the system language, e.g. "zh-CN" or "en". */
fun translationTargetLanguage(): String =
  Locale.getDefault().let { if (it.language == "zh") "zh-CN" else it.language }

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
) {
  var translatedText by mutableStateOf<String?>(null)
    private set
  var isShowingTranslation by mutableStateOf(false)
    private set
  var isTranslating by mutableStateOf(false)
    private set
  var hasFailed by mutableStateOf(false)
    private set

  /** Hide the button when there is nothing to translate or text is already in system language. */
  val isTranslatable: Boolean =
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
    when (val result = DescriptionTranslator.translate(originalText, targetLanguage)) {
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
  val targetLanguage = remember { translationTargetLanguage() }
  return remember(text) { TranslationState(text, targetLanguage) }
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
