package com.nboard.ime

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import androidx.core.view.isVisible
import com.nboard.ime.ai.SearxngClient
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        var contextText = sourceText
        if (action == QuickAiAction.SEARCH_WEB || action == QuickAiAction.FACT_CHECK) {
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                toast("Searching web...")
            }
            val searxngUrl = KeyboardModeSettings.loadSearxngUrl(this@runQuickAiAction)
            val searchResults = SearxngClient.search(searxngUrl, sourceText)
            contextText = "User Input/Query: $sourceText\n\nWeb Search Results Context:\n$searchResults"
        }

        val prompt = when (action) {
            QuickAiAction.SUMMARIZE -> buildLanguagePreservingSelectionPrompt(
                instruction = "Summarize this text. Preserve meaning. Return a short, concise version.",
                selectedText = contextText
            )
            QuickAiAction.FIX_GRAMMAR -> buildLanguagePreservingSelectionPrompt(
                instruction = "Fix grammar, punctuation, and spelling while keeping the same meaning and original tone.",
                selectedText = contextText
            )
            QuickAiAction.EXPAND -> buildLanguagePreservingSelectionPrompt(
                instruction = "Expand this text naturally with more detail. Improve the flow.",
                selectedText = contextText
            )
            QuickAiAction.REWRITE -> buildLanguagePreservingSelectionPrompt(
                instruction = "Rewrite this naturally to improve readability. Do not change the meaning.",
                selectedText = contextText
            )
            QuickAiAction.SHORTEN -> buildLanguagePreservingSelectionPrompt(
                instruction = "Reduce the length of this text. Preserve important information.",
                selectedText = contextText
            )
            QuickAiAction.TRANSLATE -> buildLanguagePreservingSelectionPrompt(
                instruction = "Translate this text into English (or if it is already English, translate it into the user's apparent native language or widely spoken language based on context).",
                selectedText = contextText
            )
            QuickAiAction.IMPROVE_WRITING -> buildLanguagePreservingSelectionPrompt(
                instruction = "Improve the writing. Use better vocabulary, better sentence structure, and make it sound more natural.",
                selectedText = contextText
            )
            QuickAiAction.PROFESSIONAL -> buildLanguagePreservingSelectionPrompt(
                instruction = "Rewrite this text to sound highly professional, polite, and formal.",
                selectedText = contextText
            )
            QuickAiAction.CASUAL -> buildLanguagePreservingSelectionPrompt(
                instruction = "Rewrite this text to sound casual, friendly, and conversational.",
                selectedText = contextText
            )
            QuickAiAction.EXPLAIN_SIMPLER -> buildLanguagePreservingSelectionPrompt(
                instruction = "Explain this text in simpler terms so it is easier to understand.",
                selectedText = contextText
            )
            QuickAiAction.DEBUG_CODE -> buildLanguagePreservingSelectionPrompt(
                instruction = "Find and fix bugs in this code. Return only the corrected code if possible.",
                selectedText = contextText
            )
            QuickAiAction.OPTIMIZE_CODE -> buildLanguagePreservingSelectionPrompt(
                instruction = "Optimize this code for performance and readability. Return the optimized code.",
                selectedText = contextText
            )
            QuickAiAction.EXPLAIN_CODE -> buildLanguagePreservingSelectionPrompt(
                instruction = "Add comments and explain what this code does.",
                selectedText = contextText
            )
            QuickAiAction.SOLVE_STEP_BY_STEP -> buildLanguagePreservingSelectionPrompt(
                instruction = "Solve this problem step-by-step and show the final answer clearly.",
                selectedText = contextText
            )
            QuickAiAction.CREATE_FLASHCARDS -> buildLanguagePreservingSelectionPrompt(
                instruction = "Convert this text into a set of question-and-answer flashcards.",
                selectedText = contextText
            )
            QuickAiAction.GENERATE_QUIZ -> buildLanguagePreservingSelectionPrompt(
                instruction = "Generate a short multiple choice quiz based on this text.",
                selectedText = contextText
            )
            QuickAiAction.CONTINUE_STORY -> buildLanguagePreservingSelectionPrompt(
                instruction = "Continue this story naturally.",
                selectedText = contextText
            )
            QuickAiAction.IMPROVE_STORY -> buildLanguagePreservingSelectionPrompt(
                instruction = "Improve the narrative flow, descriptions, and dialogue in this story.",
                selectedText = contextText
            )
            QuickAiAction.CHANGE_TONE -> buildLanguagePreservingSelectionPrompt(
                instruction = "Rewrite this with a different, engaging tone.",
                selectedText = contextText
            )
            QuickAiAction.FIX_AND_HUMANIZE -> buildLanguagePreservingSelectionPrompt(
                instruction = "Fix all grammar issues and rewrite the text so it sounds completely natural and human, hiding any AI tone.",
                selectedText = contextText
            )
            QuickAiAction.PROFESSIONAL_BULLETS -> buildLanguagePreservingSelectionPrompt(
                instruction = "Rewrite this into a professional, concise bulleted list.",
                selectedText = contextText
            )
            // Code
            QuickAiAction.FIND_SECURITY_ISSUES -> buildLanguagePreservingSelectionPrompt(
                instruction = "Analyze this code and find any security vulnerabilities or bad practices. Return the secure code.",
                selectedText = contextText
            )
            QuickAiAction.GENERATE_TESTS -> buildLanguagePreservingSelectionPrompt(
                instruction = "Write unit tests for this code.",
                selectedText = contextText
            )
            QuickAiAction.CONVERT_LANGUAGE -> buildLanguagePreservingSelectionPrompt(
                instruction = "Convert this code into another popular programming language.",
                selectedText = contextText
            )
            QuickAiAction.ADD_COMMENTS -> buildLanguagePreservingSelectionPrompt(
                instruction = "Add inline comments explaining the logic of this code.",
                selectedText = contextText
            )
            QuickAiAction.GENERATE_DOCS -> buildLanguagePreservingSelectionPrompt(
                instruction = "Generate formal documentation for this code.",
                selectedText = contextText
            )

            // Student
            QuickAiAction.EXPLAIN_LIKE_10 -> buildLanguagePreservingSelectionPrompt(
                instruction = "Explain this topic as if I am 10 years old.",
                selectedText = contextText
            )
            QuickAiAction.MIND_MAP -> buildLanguagePreservingSelectionPrompt(
                instruction = "Create a structured text-based mind map of these concepts.",
                selectedText = contextText
            )
            QuickAiAction.CORNELL_NOTES -> buildLanguagePreservingSelectionPrompt(
                instruction = "Organize this text into the Cornell Notes format (Cues, Notes, Summary).",
                selectedText = contextText
            )
            QuickAiAction.IMPORTANT_QUESTIONS -> buildLanguagePreservingSelectionPrompt(
                instruction = "Generate the most important exam questions based on this text.",
                selectedText = contextText
            )
            QuickAiAction.FORMULA_SHEET -> buildLanguagePreservingSelectionPrompt(
                instruction = "Extract all mathematical or scientific formulas and definitions into a concise sheet.",
                selectedText = contextText
            )

            // Startup
            QuickAiAction.SWOT_ANALYSIS -> buildLanguagePreservingSelectionPrompt(
                instruction = "Perform a SWOT Analysis (Strengths, Weaknesses, Opportunities, Threats) on this idea.",
                selectedText = contextText
            )
            QuickAiAction.LEAN_CANVAS -> buildLanguagePreservingSelectionPrompt(
                instruction = "Draft a Lean Canvas model for this business idea.",
                selectedText = contextText
            )
            QuickAiAction.BUSINESS_MODEL -> buildLanguagePreservingSelectionPrompt(
                instruction = "Outline a solid business model for this concept.",
                selectedText = contextText
            )
            QuickAiAction.REVENUE_IDEAS -> buildLanguagePreservingSelectionPrompt(
                instruction = "Suggest 5 creative ways to generate revenue from this idea.",
                selectedText = contextText
            )
            QuickAiAction.INVESTOR_PITCH -> buildLanguagePreservingSelectionPrompt(
                instruction = "Rewrite this into a compelling pitch for angel investors or VCs.",
                selectedText = contextText
            )
            QuickAiAction.ELEVATOR_PITCH -> buildLanguagePreservingSelectionPrompt(
                instruction = "Condense this into a powerful 30-second elevator pitch.",
                selectedText = contextText
            )
            QuickAiAction.MARKET_RESEARCH -> buildLanguagePreservingSelectionPrompt(
                instruction = "Outline the target market and demographics for this idea.",
                selectedText = contextText
            )
            QuickAiAction.COMPETITOR_ANALYSIS -> buildLanguagePreservingSelectionPrompt(
                instruction = "Identify potential competitors and how this idea can differentiate itself.",
                selectedText = contextText
            )

            // Chat
            QuickAiAction.SMART_REPLY -> buildLanguagePreservingSelectionPrompt(
                instruction = "Generate a smart, contextual reply to this message.",
                selectedText = contextText
            )
            QuickAiAction.FUNNY_REPLY -> buildLanguagePreservingSelectionPrompt(
                instruction = "Generate a funny, witty reply to this message.",
                selectedText = contextText
            )
            QuickAiAction.PROFESSIONAL_REPLY -> buildLanguagePreservingSelectionPrompt(
                instruction = "Generate a highly professional and polite reply.",
                selectedText = contextText
            )
            QuickAiAction.POLITE_DECLINE -> buildLanguagePreservingSelectionPrompt(
                instruction = "Politely decline this request or offer.",
                selectedText = contextText
            )
            QuickAiAction.CONTINUE_CONVERSATION -> buildLanguagePreservingSelectionPrompt(
                instruction = "Suggest a follow-up message to keep this conversation going.",
                selectedText = contextText
            )

            // Universal
            QuickAiAction.FACT_CHECK -> buildLanguagePreservingSelectionPrompt(
                instruction = "Fact-check this text. Point out any inaccuracies and provide the correct information.",
                selectedText = contextText
            )
            QuickAiAction.SEARCH_WEB -> buildLanguagePreservingSelectionPrompt(
                instruction = "Search the web internally and provide a summary of the latest information regarding this topic.",
                selectedText = contextText
            )

            // Creative / Writing
            QuickAiAction.MAKE_MORE_PERSUASIVE -> buildLanguagePreservingSelectionPrompt(
                instruction = "Rewrite this to be highly persuasive and convincing.",
                selectedText = contextText
            )
            QuickAiAction.HUMANIZE -> buildLanguagePreservingSelectionPrompt(
                instruction = "Rewrite this so it sounds completely human, natural, and free of AI-like phrasing.",
                selectedText = contextText
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
    if (!isAiSmartActionsInitialized()) return
    val currentText = currentInputConnection?.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)?.text?.toString().orEmpty()
    
    if (currentText.isBlank() && lastAiOriginalText == null) {
        aiSmartActionsScroll.isVisible = false
        return
    }

    // Intent Prediction / Living Quick Actions
    val actions = mutableListOf<QuickAiAction>()
    val lower = currentText.lowercase()
    
    // Code context (Developer Mode)
    if (currentText.contains("{") && (currentText.contains("def ") || currentText.contains("fun ") || currentText.contains("var ") || currentText.contains("let ") || currentText.contains("class "))) {
        actions.add(QuickAiAction.DEBUG_CODE)
        actions.add(QuickAiAction.EXPLAIN_CODE)
        actions.add(QuickAiAction.OPTIMIZE_CODE)
        actions.add(QuickAiAction.FIND_SECURITY_ISSUES)
        actions.add(QuickAiAction.GENERATE_TESTS)
        actions.add(QuickAiAction.CONVERT_LANGUAGE)
        actions.add(QuickAiAction.ADD_COMMENTS)
        actions.add(QuickAiAction.GENERATE_DOCS)
    } 
    // Homework / Question context (Student Mode)
    else if (lower.contains("calculate") || lower.contains("theorem") || lower.contains("what is") || lower.contains("how to") || (lower.contains("explain") && currentText.contains("?"))) {
        actions.add(QuickAiAction.SOLVE_STEP_BY_STEP)
        actions.add(QuickAiAction.EXPLAIN_LIKE_10)
        actions.add(QuickAiAction.CREATE_FLASHCARDS)
        actions.add(QuickAiAction.GENERATE_QUIZ)
        actions.add(QuickAiAction.MIND_MAP)
        actions.add(QuickAiAction.CORNELL_NOTES)
        actions.add(QuickAiAction.IMPORTANT_QUESTIONS)
        actions.add(QuickAiAction.FORMULA_SHEET)
    }
    // Startup / Business context (Startup Mode)
    else if (lower.contains("startup") || lower.contains("business") || lower.contains("market") || lower.contains("revenue") || lower.contains("pitch")) {
        actions.add(QuickAiAction.SWOT_ANALYSIS)
        actions.add(QuickAiAction.LEAN_CANVAS)
        actions.add(QuickAiAction.BUSINESS_MODEL)
        actions.add(QuickAiAction.REVENUE_IDEAS)
        actions.add(QuickAiAction.INVESTOR_PITCH)
        actions.add(QuickAiAction.ELEVATOR_PITCH)
        actions.add(QuickAiAction.MARKET_RESEARCH)
        actions.add(QuickAiAction.COMPETITOR_ANALYSIS)
    }
    // Story / Creative context (Writing Mode)
    else if (lower.contains("once upon a time") || (lower.contains(" he ") && lower.contains(" she ") && lower.contains(" said "))) {
        actions.add(QuickAiAction.CONTINUE_STORY)
        actions.add(QuickAiAction.IMPROVE_STORY)
        actions.add(QuickAiAction.CHANGE_TONE)
        actions.add(QuickAiAction.MAKE_MORE_PERSUASIVE)
        actions.add(QuickAiAction.HUMANIZE)
    }
    // Chat context (Chat Mode)
    else if (currentText.length < 150 && !currentText.contains("\n\n")) {
        actions.add(QuickAiAction.SMART_REPLY)
        actions.add(QuickAiAction.FUNNY_REPLY)
        actions.add(QuickAiAction.PROFESSIONAL_REPLY)
        actions.add(QuickAiAction.POLITE_DECLINE)
        actions.add(QuickAiAction.CONTINUE_CONVERSATION)
        actions.add(QuickAiAction.CASUAL)
    }
    // Default / Professional / Long form context
    else {
        actions.add(QuickAiAction.SUMMARIZE)
        actions.add(QuickAiAction.PROFESSIONAL_BULLETS)
        actions.add(QuickAiAction.FIX_AND_HUMANIZE)
        actions.add(QuickAiAction.SHORTEN)
        actions.add(QuickAiAction.EXPAND)
        actions.add(QuickAiAction.PROFESSIONAL)
    }

    // Always add universal actions at the end if they aren't there
    if (!actions.contains(QuickAiAction.FACT_CHECK)) actions.add(QuickAiAction.FACT_CHECK)
    if (!actions.contains(QuickAiAction.SEARCH_WEB)) actions.add(QuickAiAction.SEARCH_WEB)

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
