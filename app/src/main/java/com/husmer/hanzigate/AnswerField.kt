package com.husmer.hanzigate

import android.os.LocaleList
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import java.util.Locale

/**
 * A plain EditText, because it can ask the keyboard for a language (imeHintLocales):
 * Gboard switches to Chinese Pinyin for character answers and to English for pinyin answers.
 * Recreate it (with key()) for each question.
 */
@Composable
fun AnswerField(chinese: Boolean, onChange: (String) -> Unit, onSubmit: () -> Unit) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { ctx ->
            EditText(ctx).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
                setTextColor(textColor)
                setSingleLine(true)
                hint = if (chinese) "汉字" else "pinyin, e.g. ni3 hao3"
                imeHintLocales = LocaleList(if (chinese) Locale.SIMPLIFIED_CHINESE else Locale.US)
                inputType = if (chinese) {
                    InputType.TYPE_CLASS_TEXT
                } else {
                    // No autocorrect: it would "fix" pinyin into English words.
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                }
                imeOptions = EditorInfo.IME_ACTION_DONE
                setOnEditorActionListener { _, action, _ ->
                    if (action == EditorInfo.IME_ACTION_DONE) { onSubmit(); true } else false
                }
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                    override fun afterTextChanged(s: Editable?) = onChange(s?.toString().orEmpty())
                })
                post {
                    requestFocus()
                    ctx.getSystemService(InputMethodManager::class.java)
                        .showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
                }
            }
        },
    )
}
