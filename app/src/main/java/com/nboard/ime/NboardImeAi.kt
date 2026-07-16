package com.nboard.ime

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import androidx.core.view.isVisible
import kotlinx.coroutines.launch

internal fun NboardImeService.syncAiProcessingAnimations() {
    val shouldAnimate = isGenerating && isAiMode && aiPromptRow.isVisible
    if (shouldAnimate) {
        startAiProcessingAnimations()
    } else {
        stopAiProcessingAnimations()
    }
}

internal fun NboardImeService.startAiProcessingAnimations() {
    if (!isAiPromptShimmerInitialized() || !isAiPromptInputInitialized()) {
        return
    }

    if (aiPillShimmerAnimator == null) {
        aiPromptShimmer.post {
            val stripWidth = aiPromptShimmer.width.toFloat().takeIf { it > 0f } ?: dp(84).toFloat()
            val travel = aiPromptRow.width.toFloat().takeIf { it > 0f } ?: return@post
            aiPromptShimmer.layoutParams = aiPromptShimmer.layoutParams.apply {
                height = aiPromptRow.height
            }
            aiPromptShimmer.isVisible = true
            aiPillShimmerAnimator?.cancel()
            aiPillShimmerAnimator = ValueAnimator.ofFloat(-stripWidth, travel + stripWidth).apply {
                duration = 1100L
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                addUpdateListener { animator ->
                    aiPromptShimmer.translationX = animator.animatedValue as Float
                }
                start()
            }
        }
    }

    if (aiTextPulseAnimator == null) {
        val baseColor = uiColor(R.color.ai_text)
        val pulseColor = uiColor(R.color.ai_text_shine)
        aiTextPulseAnimator = ValueAnimator.ofObject(ArgbEvaluator(), baseColor, pulseColor, baseColor).apply {
            duration = 900L
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animator ->
                aiPromptInput.setTextColor((animator.animatedValue as Int))
            }
            start()
        }
    }
}

internal fun NboardImeService.stopAiProcessingAnimations() {
    aiPillShimmerAnimator?.cancel()
    aiPillShimmerAnimator = null
    if (isAiPromptShimmerInitialized()) {
        aiPromptShimmer.isVisible = false
        aiPromptShimmer.translationX = 0f
    }

    aiTextPulseAnimator?.cancel()
    aiTextPulseAnimator = null
    if (isAiPromptInputInitialized()) {
        aiPromptInput.setTextColor(uiColor(R.color.ai_text))
    }
}

internal fun NboardImeService.animateAiResultText() {
    if (!isAiPromptInputInitialized()) {
        return
    }

    aiTextPulseAnimator?.cancel()
    aiTextPulseAnimator = null

    val baseColor = uiColor(R.color.ai_text)
    val flashColor = uiColor(R.color.ai_text_shine)
    ValueAnimator.ofObject(ArgbEvaluator(), flashColor, baseColor).apply {
        duration = 420L
        addUpdateListener { animator ->
            aiPromptInput.setTextColor((animator.animatedValue as Int))
        }
        start()
    }
}


internal fun NboardImeService.submitAiPrompt() {
    if (isGenerating) {
        return
    }
    if (!isAiAllowedInCurrentContext()) {
        return
    }

    val prompt = aiPromptInput.text?.toString()?.trim().orEmpty()
    if (prompt.isBlank()) {
        toast("Enter a prompt first")
        return
    }

    val selectedText = currentInputConnection
        ?.getSelectedText(0)
        ?.toString()
        ?.trim()
        .orEmpty()
    val resolvedPrompt = if (selectedText.isBlank()) {
        prompt
    } else {
        buildLanguagePreservingSelectionPrompt(
            instruction = prompt,
            selectedText = selectedText
        )
    }

    if (!aiClient.isConfigured) {
        toast("API key missing. AI is disabled")
        return
    }

    aiPromptInput.error = null
    setGenerating(true)
    serviceScope.launch {
        val result = aiClient.generateText(
            prompt = resolvedPrompt,
            systemInstruction = AI_PROMPT_SYSTEM_INSTRUCTION,
            outputCharLimit = AI_REPLY_CHAR_LIMIT
        )
        setGenerating(false)
        result
            .onSuccess { responseText ->
                val connection = currentInputConnection
                if (connection != null) {
                    connection.commitText(responseText, 1)
                } else {
                    aiPromptInput.error = "No text field focused"
                }
                aiPromptInput.text?.clear()
                animateAiResultText()
            }
            .onFailure { error ->
                val message = error.message ?: "AI request failed"
                aiPromptInput.error = message
                toast(message)
            }
    }
}

internal fun NboardImeService.runQuickAiAction(action: QuickAiAction) {
    if (isGenerating) {
        return
    }
    if (!isAiAllowedInCurrentContext()) {
        return
    }

    if (!aiClient.isConfigured) {
        toast("API key missing. AI is disabled")
        return
    }

    val connection = currentInputConnection ?: return
    var sourceText = connection.getSelectedText(0)?.toString()?.trim().orEmpty()
    var isFullTextReplacement = false

    if (sourceText.isBlank()) {
        val extracted = connection.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)
        sourceText = extracted?.text?.toString()?.trim().orEmpty()
        isFullTextReplacement = true
    }

    if (sourceText.isBlank()) {
        toast("No text found to process")
        return
    }
    
    lastAiOriginalText = sourceText

    aiPromptInput.error = null
    setGenerating(true)
    serviceScope.launch {
        val prompt = when (action) {
            QuickAiAction.SUMMARIZE -> buildLanguagePreservingSelectionPrompt(
                instruction = "Summarize this text. Preserve meaning. Return a short, concise version.",
                selectedText = sourceText
            )
            QuickAiAction.FIX_GRAMMAR -> buildLanguagePreservingSelectionPrompt(
                instruction = "Fix grammar, punctuation, and spelling while keeping the same meaning and original tone.",
                selectedText = sourceText
            )
            QuickAiAction.EXPAND -> buildLanguagePreservingSelectionPrompt(
                instruction = "Expand this text naturally with more detail. Improve the flow.",
                selectedText = sourceText
            )
            QuickAiAction.REWRITE -> buildLanguagePreservingSelectionPrompt(
                instruction = "Rewrite this naturally to improve readability. Do not change the meaning.",
                selectedText = sourceText
            )
            QuickAiAction.SHORTEN -> buildLanguagePreservingSelectionPrompt(
                instruction = "Reduce the length of this text. Preserve important information.",
                selectedText = sourceText
            )
            QuickAiAction.TRANSLATE -> buildLanguagePreservingSelectionPrompt(
                instruction = "Translate this text into English (or if it is already English, translate it into the user's apparent native language or widely spoken language based on context).",
                selectedText = sourceText
            )
            QuickAiAction.IMPROVE_WRITING -> buildLanguagePreservingSelectionPrompt(
                instruction = "Improve the writing. Use better vocabulary, better sentence structure, and make it sound more natural.",
                selectedText = sourceText
            )
            QuickAiAction.PROFESSIONAL -> buildLanguagePreservingSelectionPrompt(
                instruction = "Rewrite this text to sound highly professional, polite, and formal.",
                selectedText = sourceText
            )
            QuickAiAction.CASUAL -> buildLanguagePreservingSelectionPrompt(
                instruction = "Rewrite this text to sound casual, friendly, and conversational.",
                selectedText = sourceText
            )
            QuickAiAction.EXPLAIN_SIMPLER -> buildLanguagePreservingSelectionPrompt(
                instruction = "Explain this text in simpler terms so it is easier to understand.",
                selectedText = sourceText
            )
            QuickAiAction.DEBUG_CODE -> buildLanguagePreservingSelectionPrompt(
                instruction = "Find and fix bugs in this code. Return only the corrected code if possible.",
                selectedText = sourceText
            )
            QuickAiAction.OPTIMIZE_CODE -> buildLanguagePreservingSelectionPrompt(
                instruction = "Optimize this code for performance and readability. Return the optimized code.",
                selectedText = sourceText
            )
            QuickAiAction.EXPLAIN_CODE -> buildLanguagePreservingSelectionPrompt(
                instruction = "Add comments and explain what this code does.",
                selectedText = sourceText
            )
            QuickAiAction.SOLVE_STEP_BY_STEP -> buildLanguagePreservingSelectionPrompt(
                instruction = "Solve this problem step-by-step and show the final answer clearly.",
                selectedText = sourceText
            )
            QuickAiAction.CREATE_FLASHCARDS -> buildLanguagePreservingSelectionPrompt(
                instruction = "Convert this text into a set of question-and-answer flashcards.",
                selectedText = sourceText
            )
            QuickAiAction.GENERATE_QUIZ -> buildLanguagePreservingSelectionPrompt(
                instruction = "Generate a short multiple choice quiz based on this text.",
                selectedText = sourceText
            )
            QuickAiAction.CONTINUE_STORY -> buildLanguagePreservingSelectionPrompt(
                instruction = "Continue this story naturally.",
                selectedText = sourceText
            )
            QuickAiAction.IMPROVE_STORY -> buildLanguagePreservingSelectionPrompt(
                instruction = "Improve the narrative flow, descriptions, and dialogue in this story.",
                selectedText = sourceText
            )
            QuickAiAction.CHANGE_TONE -> buildLanguagePreservingSelectionPrompt(
                instruction = "Rewrite this with a different, engaging tone.",
                selectedText = sourceText
            )
            QuickAiAction.FIX_AND_HUMANIZE -> buildLanguagePreservingSelectionPrompt(
                instruction = "Fix all grammar issues and rewrite the text so it sounds completely natural and human, hiding any AI tone.",
                selectedText = sourceText
            )
            QuickAiAction.PROFESSIONAL_BULLETS -> buildLanguagePreservingSelectionPrompt(
                instruction = "Rewrite this into a professional, concise bulleted list.",
                selectedText = sourceText
            )
        }

        val result = aiClient.generateText(
            prompt = prompt,
            systemInstruction = AI_QUICK_ACTION_SYSTEM_INSTRUCTION,
            outputCharLimit = AI_PILL_CHAR_LIMIT
        )
        setGenerating(false)
        result
            .onSuccess { responseText ->
                aiPromptInput.setText(responseText)
                aiPromptInput.setSelection(aiPromptInput.text?.length ?: 0)
                animateAiResultText()

                val inputConnection = currentInputConnection
                if (isFullTextReplacement && inputConnection != null) {
                    val extracted = inputConnection.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)
                    val len = extracted?.text?.length ?: 0
                    inputConnection.setSelection(0, len)
                    inputConnection.commitText(responseText, 1)
                } else {
                    inputConnection?.commitText(responseText, 1)
                }
            }
            .onFailure { error ->
                val message = error.message ?: "AI request failed"
                aiPromptInput.error = message
                toast(message)
            }
    }
}

internal fun NboardImeService.buildLanguagePreservingSelectionPrompt(
    instruction: String,
    selectedText: String
): String {
    return buildString {
        append("Apply this instruction to the selected text and return only the transformed result.\n")
        append("Keep the output in the same language as the selected text.\n")
        append("Do not translate unless the instruction explicitly asks for translation.\n")
        append("Instruction: ")
        append(instruction.trim())
        append("\n\nSelected text:\n")
        append(selectedText)
    }
}


internal fun NboardImeService.updateAiSmartActions() {
    if (!::aiSmartActionsScroll.isInitialized) return
    val text = currentInputConnection?.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)?.text?.toString().orEmpty()
    
    if (text.isBlank() && lastAiOriginalText == null) {
        aiSmartActionsScroll.isVisible = false
        return
    }

    // Intent Prediction / Living Quick Actions
    val actions = mutableListOf<QuickAiAction>()
    val lower = text.lowercase()
    
    // Code context
    if (text.contains("{") && (text.contains("def ") || text.contains("fun ") || text.contains("var ") || text.contains("let ") || text.contains("class "))) {
        actions.add(QuickAiAction.DEBUG_CODE)
        actions.add(QuickAiAction.OPTIMIZE_CODE)
        actions.add(QuickAiAction.EXPLAIN_CODE)
    } 
    // Homework / Question context
    else if ((lower.contains("what is") || lower.contains("how to") || lower.contains("explain")) && text.contains("?")) {
        actions.add(QuickAiAction.SOLVE_STEP_BY_STEP)
        actions.add(QuickAiAction.CREATE_FLASHCARDS)
        actions.add(QuickAiAction.GENERATE_QUIZ)
    } 
    // Story / Creative context
    else if (lower.contains("once upon a time") || (lower.contains(" he ") && lower.contains(" she ") && lower.contains(" said "))) {
        actions.add(QuickAiAction.CONTINUE_STORY)
        actions.add(QuickAiAction.IMPROVE_STORY)
        actions.add(QuickAiAction.CHANGE_TONE)
    }
    // Professional context
    else if (lower.contains("dear ") || lower.contains("sincerely") || lower.contains("best regards") || lower.contains("attached")) {
        actions.add(QuickAiAction.PROFESSIONAL)
        actions.add(QuickAiAction.PROFESSIONAL_BULLETS)
        actions.add(QuickAiAction.FIX_GRAMMAR)
    } 
    // Casual context
    else if (lower.contains("hey") || lower.contains("lol") || lower.contains("haha") || lower.contains("brb") || lower.contains("lmao")) {
        actions.add(QuickAiAction.CASUAL)
        actions.add(QuickAiAction.REWRITE)
        actions.add(QuickAiAction.FIX_AND_HUMANIZE)
    } 
    // List / Summary context
    else if (text.contains("•") || text.contains("- ") || lower.length > 200) {
        actions.add(QuickAiAction.SUMMARIZE)
        actions.add(QuickAiAction.PROFESSIONAL_BULLETS)
        actions.add(QuickAiAction.SHORTEN)
    } 
    // Default context
    else {
        actions.add(QuickAiAction.FIX_AND_HUMANIZE)
        actions.add(QuickAiAction.FIX_GRAMMAR)
        actions.add(QuickAiAction.REWRITE)
        actions.add(QuickAiAction.EXPAND)
    }

    // Add remaining distinct actions
    for (action in QuickAiAction.values()) {
        if (!actions.contains(action)) {
            actions.add(action)
        }
    }

    aiSmartActionsRow.removeAllViews()

    if (lastAiOriginalText != null) {
        val undoBtn = androidx.appcompat.widget.AppCompatButton(keyboardUiContext).apply {
            background = uiDrawable(R.drawable.bg_ai_quick_action)
            isAllCaps = false
            setTextColor(uiColor(R.color.ai_text_shine))
            textSize = 13f
            applySerifTypeface(this)
            text = "↩ Undo AI"
            setPadding(dp(16), 0, dp(16), 0)
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(36)
            ).apply {
                marginEnd = dp(6)
            }
            setOnClickListener {
                val inputConnection = currentInputConnection ?: return@setOnClickListener
                val extracted = inputConnection.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)
                val len = extracted?.text?.length ?: 0
                inputConnection.setSelection(0, len)
                inputConnection.commitText(lastAiOriginalText, 1)
                lastAiOriginalText = null
                updateAiSmartActions()
            }
        }
        aiSmartActionsRow.addView(undoBtn)
    }

    for (action in actions) {
        val btn = androidx.appcompat.widget.AppCompatButton(keyboardUiContext).apply {
            background = uiDrawable(R.drawable.bg_ai_quick_action)
            isAllCaps = false
            setTextColor(uiColor(R.color.ai_text))
            textSize = 13f
            applySerifTypeface(this)
            text = action.label
            setPadding(dp(16), 0, dp(16), 0)
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(36)
            ).apply {
                marginEnd = dp(6)
            }
            setOnClickListener {
                runQuickAiAction(action)
            }
        }
        aiSmartActionsRow.addView(btn)
    }
    
    aiSmartActionsScroll.isVisible = true
}
