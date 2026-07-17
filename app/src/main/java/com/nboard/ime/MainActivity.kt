package com.nboard.ime

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var languageValue: TextView
    private lateinit var keyboardValue: TextView
    private lateinit var uploadedLayoutNoticeCard: View
    private lateinit var uploadedLayoutNoticeText: TextView
    private lateinit var numberRowSettingValue: TextView
    private lateinit var autoSpaceAfterPunctuationValue: TextView
    private lateinit var autoCapitalizeAfterPunctuationValue: TextView
    private lateinit var returnToLettersAfterNumberSpaceValue: TextView
    private lateinit var wordPredictionValue: TextView
    private lateinit var swipeTypingValue: TextView
    private lateinit var swipeTrailValue: TextView
    private lateinit var voiceInputValue: TextView
    private lateinit var swipeTrailRow: View
    private lateinit var swipeTrailDivider: View
    private lateinit var leftKeyModesRow: View
    private lateinit var rightKeyModesRow: View
    private lateinit var leftKeyModesValue: TextView
    private lateinit var rightKeyModesValue: TextView
    private lateinit var deleteLayoutPackRow: View
    private lateinit var deleteLayoutPackValue: TextView
    private lateinit var themeValue: TextView
    private lateinit var fontValue: TextView
    private val importLayoutPackLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            importLayoutPackFromUri(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val themeMode = KeyboardModeSettings.loadThemeMode(this)
        setTheme(themeStyleFor(themeMode))
        applyThemePreference(themeMode)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        languageValue = findViewById(R.id.languageValue)
        keyboardValue = findViewById(R.id.keyboardValue)
        uploadedLayoutNoticeCard = findViewById(R.id.uploadedLayoutNoticeCard)
        uploadedLayoutNoticeText = findViewById(R.id.uploadedLayoutNoticeText)
        numberRowSettingValue = findViewById(R.id.numberRowSettingValue)
        autoSpaceAfterPunctuationValue = findViewById(R.id.autoSpaceAfterPunctuationValue)
        autoCapitalizeAfterPunctuationValue = findViewById(R.id.autoCapitalizeAfterPunctuationValue)
        returnToLettersAfterNumberSpaceValue = findViewById(R.id.returnToLettersAfterNumberSpaceValue)
        wordPredictionValue = findViewById(R.id.wordPredictionValue)
        swipeTypingValue = findViewById(R.id.swipeTypingValue)
        swipeTrailValue = findViewById(R.id.swipeTrailValue)
        voiceInputValue = findViewById(R.id.voiceInputValue)
        swipeTrailRow = findViewById(R.id.swipeTrailRow)
        swipeTrailDivider = findViewById(R.id.swipeTrailDivider)
        leftKeyModesRow = findViewById(R.id.leftKeyModesRow)
        rightKeyModesRow = findViewById(R.id.rightKeyModesRow)
        leftKeyModesValue = findViewById(R.id.leftKeyModesValue)
        rightKeyModesValue = findViewById(R.id.rightKeyModesValue)
        deleteLayoutPackRow = findViewById(R.id.deleteLayoutPackRow)
        deleteLayoutPackValue = findViewById(R.id.deleteLayoutPackValue)
        themeValue = findViewById(R.id.themeValue)
        fontValue = findViewById(R.id.fontValue)

        applyStatusBarInset()
        bindActions()
        refreshValues()
        maybeShowFirstLaunchOnboarding()
    }

    override fun onResume() {
        super.onResume()
        refreshValues()
    }

    private fun applyStatusBarInset() {
        val content = findViewById<View>(R.id.settingsContent)
        val baseTop = content.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val topInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(view.paddingLeft, baseTop + topInset, view.paddingRight, view.paddingBottom)
            insets
        }
        ViewCompat.requestApplyInsets(content)
    }

    private fun bindActions() {
        findViewById<View>(R.id.makeDefaultRow).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }

        findViewById<View>(R.id.replayOnboardingRow).setOnClickListener {
            startActivity(Intent(this, OnboardingActivity::class.java).apply {
                putExtra(OnboardingActivity.EXTRA_REPLAY, true)
            })
        }

        findViewById<View>(R.id.keyboardRow).setOnClickListener {
            showKeyboardLayoutDialog()
        }

        findViewById<View>(R.id.moreLayoutsRow).setOnClickListener {
            openLink("https://github.com/MathieuDvv/Nboard/tree/main/Community%20Layouts")
        }

        findViewById<View>(R.id.importLayoutPackRow).setOnClickListener {
            launchLayoutPackImportPicker()
        }

        deleteLayoutPackRow.setOnClickListener {
            showDeleteLayoutPackDialog()
        }

        findViewById<View>(R.id.numberRowSettingRow).setOnClickListener {
            showNumberRowDialog()
        }

        findViewById<View>(R.id.autoSpaceAfterPunctuationRow).setOnClickListener {
            showAutoSpaceAfterPunctuationDialog()
        }

        findViewById<View>(R.id.autoCapitalizeAfterPunctuationRow).setOnClickListener {
            showAutoCapitalizeAfterPunctuationDialog()
        }

        findViewById<View>(R.id.returnToLettersAfterNumberSpaceRow).setOnClickListener {
            showReturnToLettersAfterNumberSpaceDialog()
        }

        findViewById<View>(R.id.languageRow).setOnClickListener {
            showLanguageDialog()
        }

        findViewById<View>(R.id.apiKeyRow).setOnClickListener {
            showApiKeyDialog()
        }

                findViewById<View>(R.id.searxngUrlRow).setOnClickListener {
            showSearxngUrlDialog()
        }

        findViewById<View>(R.id.wordPredictionRow).setOnClickListener {
            showWordPredictionDialog()
        }

        findViewById<View>(R.id.swipeTypingRow).setOnClickListener {
            showSwipeTypingDialog()
        }

        swipeTrailRow.setOnClickListener {
            showSwipeTrailDialog()
        }

        findViewById<View>(R.id.voiceInputRow).setOnClickListener {
            showVoiceInputDialog()
        }

        leftKeyModesRow.setOnClickListener {
            if (LayoutPackManager.resolveActive(this).isGboardStyle()) {
                Toast.makeText(this, "Tool slots are managed by Gboard layout", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showBottomKeyOptionsDialog(isLeftSlot = true)
        }

        rightKeyModesRow.setOnClickListener {
            if (LayoutPackManager.resolveActive(this).isGboardStyle()) {
                Toast.makeText(this, "Tool slots are managed by Gboard layout", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showBottomKeyOptionsDialog(isLeftSlot = false)
        }

        findViewById<View>(R.id.themeRow).setOnClickListener {
            showThemeDialog()
        }

        findViewById<View>(R.id.fontRow).setOnClickListener {
            showFontDialog()
        }

        findViewById<View>(R.id.koFiButton).setOnClickListener {
            openLink("https://ko-fi.com/dotslimy")
        }

        findViewById<View>(R.id.librariesRow).setOnClickListener {
            showLibrariesDialog()
        }

        findViewById<View>(R.id.authorRow).setOnClickListener {
            openLink("https://github.com/MathieuDvv")
        }
    }

    private fun showKeyboardLayoutDialog() {
        val installed = LayoutPackManager.listInstalled(this)
        if (installed.isEmpty()) {
            Toast.makeText(this, "No layout packs found", Toast.LENGTH_SHORT).show()
            return
        }
        val activePackId = KeyboardModeSettings.loadActiveLayoutPackId(this)
        val labels = installed.map { formatLayoutPackLabel(it) }.toTypedArray()
        val selected = installed.indexOfFirst { it.id == activePackId }.coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle("Keyboard layout")
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                val selectedPack = installed[which]
                LayoutPackManager.setActive(this, selectedPack.id)
                refreshValues()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteLayoutPackDialog() {
        val imported = LayoutPackManager.listImported(this)
        if (imported.isEmpty()) {
            Toast.makeText(this, "No imported layout packs to delete", Toast.LENGTH_SHORT).show()
            return
        }

        val labels = imported.map { "${it.displayName} (${it.id})" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Delete layout pack")
            .setItems(labels) { _, which ->
                val selected = imported[which]
                AlertDialog.Builder(this)
                    .setTitle("Delete ${selected.displayName}?")
                    .setMessage("This removes the imported file from this device.")
                    .setPositiveButton("Delete") { _, _ ->
                        val deleted = LayoutPackManager.deleteImportedPack(this, selected.id)
                        if (deleted) {
                            refreshValues()
                            Toast.makeText(this, "Layout pack deleted", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Could not delete this pack", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun launchLayoutPackImportPicker() {
        importLayoutPackLauncher.launch(arrayOf("text/xml", "application/xml", "*/*"))
    }

    private fun importLayoutPackFromUri(uri: Uri) {
        val imported = runCatching {
            LayoutPackManager.importFromUri(this, uri)
        }.getOrElse { error ->
            val message = error.message?.takeIf { it.isNotBlank() } ?: "Invalid layout file"
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            return
        }

        LayoutPackManager.setActive(this, imported.id)
        refreshValues()
        Toast.makeText(this, "Imported: ${imported.displayName}", Toast.LENGTH_SHORT).show()
    }

    private fun formatLayoutPackLabel(pack: LayoutPack): String {
        val sourceLabel = if (pack.source == LayoutPackSource.BUILTIN) "Built-in" else "Imported"
        val bottomLabel = if (pack.isGboardStyle()) "Gboard" else "Classic"
        return "${pack.displayName} ($bottomLabel, $sourceLabel)"
    }

    private fun keyboardSummary(pack: LayoutPack): String {
        val bottom = if (pack.isGboardStyle()) "Gboard" else "Classic"
        return "${pack.displayName} • $bottom"
    }

    private fun showLanguageDialog() {
        val current = KeyboardModeSettings.loadLanguageMode(this)
        val options = arrayOf("French", "English", "French + English", "Disabled")
        val selected = when (current) {
            KeyboardLanguageMode.FRENCH -> 0
            KeyboardLanguageMode.ENGLISH -> 1
            KeyboardLanguageMode.BOTH -> 2
            KeyboardLanguageMode.DISABLED -> 3
        }

        AlertDialog.Builder(this)
            .setTitle("Basic autocorrect")
            .setSingleChoiceItems(options, selected) { dialog, which ->
                val mode = when (which) {
                    1 -> KeyboardLanguageMode.ENGLISH
                    2 -> KeyboardLanguageMode.BOTH
                    3 -> KeyboardLanguageMode.DISABLED
                    else -> KeyboardLanguageMode.FRENCH
                }
                KeyboardModeSettings.saveLanguageMode(this, mode)
                refreshValues()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showApiKeyDialog() {
        val currentProvider = KeyboardModeSettings.loadAiProvider(this)
        val options = arrayOf("Gemini", "OpenRouter")
        val selected = if (currentProvider == AiProvider.OPENROUTER) 1 else 0

        AlertDialog.Builder(this)
            .setTitle("Select AI Provider")
            .setSingleChoiceItems(options, selected) { dialog, which ->
                val provider = if (which == 1) AiProvider.OPENROUTER else AiProvider.GEMINI
                KeyboardModeSettings.saveAiProvider(this, provider)
                dialog.dismiss()
                showApiKeyInputDialog(provider)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showApiKeyInputDialog(provider: AiProvider) {
        val isGemini = provider == AiProvider.GEMINI
        val currentKey = if (isGemini) KeyboardModeSettings.loadGeminiApiKey(this) else KeyboardModeSettings.loadOpenRouterApiKey(this)
        
        val input = EditText(this).apply {
            setText(currentKey)
            hint = if (isGemini) "Paste Gemini API key" else "Paste OpenRouter API key"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            setSelection(text.length)
        }

        AlertDialog.Builder(this)
            .setTitle(if (isGemini) "Set Gemini API key" else "Set OpenRouter API key")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newKey = input.text?.toString().orEmpty()
                if (isGemini) {
                    KeyboardModeSettings.saveGeminiApiKey(this, newKey)
                    refreshValues()
                } else {
                    KeyboardModeSettings.saveOpenRouterApiKey(this, newKey)
                    showOpenRouterModelDialog()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showOpenRouterModelDialog() {
        val currentModel = KeyboardModeSettings.loadOpenRouterModel(this)
        val input = EditText(this).apply {
            setText(currentModel)
            hint = "e.g. google/gemini-2.5-flash"
            inputType = InputType.TYPE_CLASS_TEXT
            setSelection(text.length)
        }

        AlertDialog.Builder(this)
            .setTitle("Set OpenRouter Model (Optional)")
            .setMessage("Leave blank to use default fallback models.")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                KeyboardModeSettings.saveOpenRouterModel(this, input.text?.toString().orEmpty())
                refreshValues()
            }
            .setNegativeButton("Cancel") { _, _ -> 
                refreshValues()
            }
            .show()
    }

    private fun showThemeDialog() {
        val current = KeyboardModeSettings.loadThemeMode(this)
        val options = arrayOf("System", "Light", "Dark", "AMOLED Black")
        val selected = when (current) {
            AppThemeMode.SYSTEM -> 0
            AppThemeMode.LIGHT -> 1
            AppThemeMode.DARK -> 2
            AppThemeMode.AMOLED -> 3
        }

        AlertDialog.Builder(this)
            .setTitle("Theme")
            .setSingleChoiceItems(options, selected) { dialog, which ->
                val mode = when (which) {
                    1 -> AppThemeMode.LIGHT
                    2 -> AppThemeMode.DARK
                    3 -> AppThemeMode.AMOLED
                    else -> AppThemeMode.SYSTEM
                }
                KeyboardModeSettings.saveThemeMode(this, mode)
                applyThemePreference(mode)
                refreshValues()
                dialog.dismiss()
                recreate()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showBottomKeyOptionsDialog(isLeftSlot: Boolean) {
        val options = listOf(
            BottomKeyPairOption(BottomKeyMode.AI, BottomKeyMode.EMOJI),
            BottomKeyPairOption(BottomKeyMode.AI, BottomKeyMode.CLIPBOARD),
            BottomKeyPairOption(BottomKeyMode.CLIPBOARD, BottomKeyMode.EMOJI)
        )
        val labels = options.map { formatBottomModePairLabel(it.first, it.second) }.toTypedArray()
        val (leftOptions, rightOptions) = KeyboardModeSettings.loadBottomSlotOptions(this)
        val currentSet = if (isLeftSlot) leftOptions.toSet() else rightOptions.toSet()
        val selected = options.indexOfFirst { setOf(it.first, it.second) == currentSet }.coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(if (isLeftSlot) "Left key options" else "Right key options")
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                val selectedPair = options[which]
                if (isLeftSlot) {
                    KeyboardModeSettings.saveLeftSlotOptions(this, selectedPair.first, selectedPair.second)
                } else {
                    KeyboardModeSettings.saveRightSlotOptions(this, selectedPair.first, selectedPair.second)
                }
                refreshValues()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSearxngUrlDialog() {
        val input = android.widget.EditText(this).apply {
            setText(KeyboardModeSettings.loadSearxngUrl(this@MainActivity))
            hint = "https://searx.be"
            setPadding(
                (16 * resources.displayMetrics.density).toInt(),
                (16 * resources.displayMetrics.density).toInt(),
                (16 * resources.displayMetrics.density).toInt(),
                (16 * resources.displayMetrics.density).toInt()
            )
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("SearXNG URL")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                KeyboardModeSettings.saveSearxngUrl(this, input.text.toString().trim())
                refreshValues()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showWordPredictionDialog() {
        val enabled = KeyboardModeSettings.loadWordPredictionEnabled(this)
        val options = arrayOf("Enabled", "Disabled")
        val selected = if (enabled) 0 else 1

        AlertDialog.Builder(this)
            .setTitle("Word prediction")
            .setSingleChoiceItems(options, selected) { dialog, which ->
                KeyboardModeSettings.saveWordPredictionEnabled(this, which == 0)
                refreshValues()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showNumberRowDialog() {
        val enabled = KeyboardModeSettings.loadNumberRowEnabled(this)
        val options = arrayOf("Enabled", "Disabled")
        val selected = if (enabled) 0 else 1

        AlertDialog.Builder(this)
            .setTitle("Number row")
            .setSingleChoiceItems(options, selected) { dialog, which ->
                KeyboardModeSettings.saveNumberRowEnabled(this, which == 0)
                refreshValues()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAutoSpaceAfterPunctuationDialog() {
        val enabled = KeyboardModeSettings.loadAutoSpaceAfterPunctuationEnabled(this)
        val options = arrayOf("Enabled", "Disabled")
        val selected = if (enabled) 0 else 1

        AlertDialog.Builder(this)
            .setTitle("Auto-space after punctuation")
            .setSingleChoiceItems(options, selected) { dialog, which ->
                KeyboardModeSettings.saveAutoSpaceAfterPunctuationEnabled(this, which == 0)
                refreshValues()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAutoCapitalizeAfterPunctuationDialog() {
        val enabled = KeyboardModeSettings.loadAutoCapitalizeAfterPunctuationEnabled(this)
        val options = arrayOf("Enabled", "Disabled")
        val selected = if (enabled) 0 else 1

        AlertDialog.Builder(this)
            .setTitle("Auto-capitalize after punctuation")
            .setSingleChoiceItems(options, selected) { dialog, which ->
                KeyboardModeSettings.saveAutoCapitalizeAfterPunctuationEnabled(this, which == 0)
                refreshValues()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showReturnToLettersAfterNumberSpaceDialog() {
        val enabled = KeyboardModeSettings.loadReturnToLettersAfterNumberSpaceEnabled(this)
        val options = arrayOf("Enabled", "Disabled")
        val selected = if (enabled) 0 else 1

        AlertDialog.Builder(this)
            .setTitle("Return to letters after numbers")
            .setSingleChoiceItems(options, selected) { dialog, which ->
                KeyboardModeSettings.saveReturnToLettersAfterNumberSpaceEnabled(this, which == 0)
                refreshValues()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSwipeTypingDialog() {
        val enabled = KeyboardModeSettings.loadSwipeTypingEnabled(this)
        val options = arrayOf("Enabled", "Disabled")
        val selected = if (enabled) 0 else 1

        AlertDialog.Builder(this)
            .setTitle("Swipe typing")
            .setSingleChoiceItems(options, selected) { dialog, which ->
                KeyboardModeSettings.saveSwipeTypingEnabled(this, which == 0)
                refreshValues()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showFontDialog() {
        val current = KeyboardModeSettings.loadFontMode(this)
        val options = arrayOf("Inter", "Roboto")
        val selected = if (current == KeyboardFontMode.ROBOTO) 1 else 0

        AlertDialog.Builder(this)
            .setTitle("Keyboard font")
            .setSingleChoiceItems(options, selected) { dialog, which ->
                val mode = if (which == 1) KeyboardFontMode.ROBOTO else KeyboardFontMode.INTER
                KeyboardModeSettings.saveFontMode(this, mode)
                refreshValues()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showVoiceInputDialog() {
        val enabled = KeyboardModeSettings.loadVoiceInputEnabled(this)
        val options = arrayOf("Enabled", "Disabled")
        val selected = if (enabled) 0 else 1

        AlertDialog.Builder(this)
            .setTitle("Voice input")
            .setSingleChoiceItems(options, selected) { dialog, which ->
                val shouldEnable = which == 0
                KeyboardModeSettings.saveVoiceInputEnabled(this, shouldEnable)
                if (shouldEnable && !hasRecordAudioPermission()) {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.RECORD_AUDIO),
                        REQUEST_RECORD_AUDIO_PERMISSION
                    )
                }
                refreshValues()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSwipeTrailDialog() {
        val enabled = KeyboardModeSettings.loadSwipeTrailEnabled(this)
        val options = arrayOf("Enabled", "Disabled")
        val selected = if (enabled) 0 else 1

        AlertDialog.Builder(this)
            .setTitle("Showing the trail")
            .setSingleChoiceItems(options, selected) { dialog, which ->
                KeyboardModeSettings.saveSwipeTrailEnabled(this, which == 0)
                refreshValues()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun refreshValues() {
        val activeLayoutPack = LayoutPackManager.resolveActive(this)
        val isGboardLayout = activeLayoutPack.isGboardStyle()
        val provider = KeyboardModeSettings.loadAiProvider(this)
        val apiKey = if (provider == AiProvider.OPENROUTER) KeyboardModeSettings.loadOpenRouterApiKey(this) else KeyboardModeSettings.loadGeminiApiKey(this)
        statusText.text = "${if (provider == AiProvider.OPENROUTER) "OpenRouter" else "Gemini"}: " + maskApiKeyForDisplay(apiKey)
        languageValue.text = when (KeyboardModeSettings.loadLanguageMode(this)) {
            KeyboardLanguageMode.FRENCH -> "French"
            KeyboardLanguageMode.ENGLISH -> "English"
            KeyboardLanguageMode.BOTH -> "French + English"
            KeyboardLanguageMode.DISABLED -> "Disabled"
        }
        keyboardValue.text = keyboardSummary(activeLayoutPack)
        val hasImportedActiveLayout = activeLayoutPack.source == LayoutPackSource.IMPORTED
        uploadedLayoutNoticeCard.visibility = if (hasImportedActiveLayout) View.VISIBLE else View.GONE
        if (hasImportedActiveLayout) {
            uploadedLayoutNoticeText.text =
                "Custom layout active (${activeLayoutPack.displayName}). Word prediction and autocorrect still use English/French only; only the layout changed."
        }
        numberRowSettingValue.text = if (KeyboardModeSettings.loadNumberRowEnabled(this)) {
            "Enabled"
        } else {
            "Disabled"
        }
        autoSpaceAfterPunctuationValue.text = if (KeyboardModeSettings.loadAutoSpaceAfterPunctuationEnabled(this)) {
            "Enabled"
        } else {
            "Disabled"
        }
        autoCapitalizeAfterPunctuationValue.text =
            if (KeyboardModeSettings.loadAutoCapitalizeAfterPunctuationEnabled(this)) {
                "Enabled"
            } else {
                "Disabled"
            }
        returnToLettersAfterNumberSpaceValue.text =
            if (KeyboardModeSettings.loadReturnToLettersAfterNumberSpaceEnabled(this)) {
                "Enabled"
            } else {
                "Disabled"
            }
        wordPredictionValue.text = if (KeyboardModeSettings.loadWordPredictionEnabled(this)) {
            "Enabled"
        } else {
            "Disabled"
        }
        val swipeTypingEnabled = KeyboardModeSettings.loadSwipeTypingEnabled(this)
        swipeTypingValue.text = if (swipeTypingEnabled) {
            "Enabled"
        } else {
            "Disabled"
        }
        swipeTrailValue.text = if (KeyboardModeSettings.loadSwipeTrailEnabled(this)) {
            "Enabled"
        } else {
            "Disabled"
        }
        swipeTrailRow.visibility = if (swipeTypingEnabled) View.VISIBLE else View.GONE
        swipeTrailDivider.visibility = if (swipeTypingEnabled) View.VISIBLE else View.GONE
        voiceInputValue.text = if (KeyboardModeSettings.loadVoiceInputEnabled(this)) {
            "Enabled"
        } else {
            "Disabled"
        }
        val (leftOptions, rightOptions) = KeyboardModeSettings.loadBottomSlotOptions(this)
        val importedCount = LayoutPackManager.listImported(this).size
        deleteLayoutPackValue.text = if (importedCount <= 0) "None" else "$importedCount imported"
        deleteLayoutPackRow.isEnabled = importedCount > 0
        deleteLayoutPackRow.alpha = if (importedCount > 0) 1f else 0.5f
        leftKeyModesValue.text = if (isGboardLayout) {
            "Single key (hold AI for AI/Clipboard/Emoji)"
        } else {
            formatBottomModePairLabel(leftOptions[0], leftOptions[1])
        }
        rightKeyModesValue.text = if (isGboardLayout) {
            "Disabled in Gboard layout"
        } else {
            formatBottomModePairLabel(rightOptions[0], rightOptions[1])
        }
        leftKeyModesRow.isEnabled = !isGboardLayout
        rightKeyModesRow.isEnabled = !isGboardLayout
        leftKeyModesRow.alpha = if (isGboardLayout) 0.5f else 1f
        rightKeyModesRow.alpha = if (isGboardLayout) 0.5f else 1f
        themeValue.text = when (KeyboardModeSettings.loadThemeMode(this)) {
            AppThemeMode.SYSTEM -> "System"
            AppThemeMode.LIGHT -> "Light"
            AppThemeMode.DARK -> "Dark"
            AppThemeMode.AMOLED -> "AMOLED Black"
        }
        fontValue.text = when (KeyboardModeSettings.loadFontMode(this)) {
            KeyboardFontMode.INTER -> "Inter"
            KeyboardFontMode.ROBOTO -> "Roboto"
        }
    }

    private fun maskApiKeyForDisplay(key: String): String {
        if (key.isBlank()) {
            return "Not set"
        }
        val visible = key.takeLast(4)
        return "••••••••••••$visible"
    }

    private fun formatBottomModePairLabel(first: BottomKeyMode, second: BottomKeyMode): String {
        return "${formatBottomModeLabel(first)} + ${formatBottomModeLabel(second)}"
    }

    private fun formatBottomModeLabel(mode: BottomKeyMode): String {
        return when (mode) {
            BottomKeyMode.AI -> "AI"
            BottomKeyMode.CLIPBOARD -> "Clipboard"
            BottomKeyMode.EMOJI -> "Emoji"
        }
    }

    private fun showLibrariesDialog() {
        val message = """
            • AndroidX Core KTX — Apache License 2.0
            • AndroidX AppCompat — Apache License 2.0
            • AndroidX RecyclerView — Apache License 2.0
            • Material Components for Android — Apache License 2.0
            • Kotlin Coroutines (Android) — Apache License 2.0
            • OkHttp — Apache License 2.0
            • Kotlin Standard Library — Apache License 2.0

            Design note:
            Nboard is heavily inspired by Nothing and its aesthetic (Nothing Technology Limited).
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Libraries & licences")
            .setMessage(message)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun applyThemePreference(mode: AppThemeMode) {
        val nightMode = when (mode) {
            AppThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            AppThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            AppThemeMode.DARK,
            AppThemeMode.AMOLED -> AppCompatDelegate.MODE_NIGHT_YES
        }
        if (AppCompatDelegate.getDefaultNightMode() != nightMode) {
            AppCompatDelegate.setDefaultNightMode(nightMode)
        }
    }

    private fun themeStyleFor(mode: AppThemeMode): Int {
        return if (mode == AppThemeMode.AMOLED) {
            R.style.Theme_Nboard_Amoled
        } else {
            R.style.Theme_Nboard
        }
    }

    private fun openLink(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    private fun maybeShowFirstLaunchOnboarding() {
        if (!KeyboardModeSettings.loadOnboardingCompleted(this)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
        }
    }

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_RECORD_AUDIO_PERMISSION) {
            return
        }
        val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            KeyboardModeSettings.saveVoiceInputEnabled(this, false)
            refreshValues()
            Toast.makeText(
                this,
                "Microphone permission denied. Voice input disabled.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    companion object {
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 1004
    }
}

private data class BottomKeyPairOption(
    val first: BottomKeyMode,
    val second: BottomKeyMode
)
