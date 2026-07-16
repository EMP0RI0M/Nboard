package com.nboard.ime

import android.view.View
import androidx.appcompat.widget.AppCompatTextView

internal enum class ShiftMode {
    OFF,
    ONE_SHOT,
    CAPS_LOCK
}

internal enum class InlineInputTarget {
    NONE,
    AI_PROMPT,
    EMOJI_SEARCH
}

internal enum class QuickAiAction(val label: String) {
    SUMMARIZE("Summarize"),
    FIX_GRAMMAR("Fix Grammar"),
    EXPAND("Expand"),
    REWRITE("Rewrite"),
    SHORTEN("Shorten"),
    TRANSLATE("Translate"),
    IMPROVE_WRITING("Improve Writing"),
    PROFESSIONAL("Professional"),
    CASUAL("Casual"),
    EXPLAIN_SIMPLER("Explain Simpler"),

    // Developer Mode
    DEBUG_CODE("Debug"),
    OPTIMIZE_CODE("Optimize"),
    EXPLAIN_CODE("Explain Code"),
    FIND_SECURITY_ISSUES("Find Security Issues"),
    GENERATE_TESTS("Generate Tests"),
    CONVERT_LANGUAGE("Convert Language"),
    ADD_COMMENTS("Add Comments"),
    GENERATE_DOCS("Generate Documentation"),

    // Student Mode
    SOLVE_STEP_BY_STEP("Solve Step-by-Step"),
    EXPLAIN_LIKE_10("Explain Like I'm 10"),
    CREATE_FLASHCARDS("Create Flashcards"),
    GENERATE_QUIZ("Generate Quiz"),
    MIND_MAP("Mind Map"),
    CORNELL_NOTES("Cornell Notes"),
    IMPORTANT_QUESTIONS("Important Questions"),
    FORMULA_SHEET("Formula Sheet"),

    // Startup Mode
    SWOT_ANALYSIS("SWOT Analysis"),
    LEAN_CANVAS("Lean Canvas"),
    BUSINESS_MODEL("Business Model"),
    REVENUE_IDEAS("Revenue Ideas"),
    INVESTOR_PITCH("Investor Pitch"),
    ELEVATOR_PITCH("Elevator Pitch"),
    MARKET_RESEARCH("Market Research"),
    COMPETITOR_ANALYSIS("Competitor Analysis"),

    // Chat Mode
    SMART_REPLY("Smart Reply"),
    FUNNY_REPLY("Funny Reply"),
    PROFESSIONAL_REPLY("Professional Reply"),
    POLITE_DECLINE("Polite Decline"),
    CONTINUE_CONVERSATION("Continue Conversation"),

    // Universal
    FACT_CHECK("Fact Check"),
    SEARCH_WEB("Search Web"),

    // Creative / Writing
    CONTINUE_STORY("Continue Story"),
    IMPROVE_STORY("Improve Story"),
    CHANGE_TONE("Change Tone"),
    MAKE_MORE_PERSUASIVE("Make More Persuasive"),
    HUMANIZE("Humanize"),

    // Workflow Chains
    FIX_AND_HUMANIZE("Fix + Humanize"),
    PROFESSIONAL_BULLETS("Pro Bullets")
}

internal data class ModeOption(val mode: BottomKeyMode, val iconRes: Int)

internal data class Lexicon(
    val words: Set<String>,
    val foldedWords: Set<String>,
    val byFirst: Map<Char, List<String>>,
    val byPrefix2: Map<String, List<String>>,
    val foldedToWord: Map<String, String>
) {
    companion object {
        fun empty() = Lexicon(
            words = emptySet(),
            foldedWords = emptySet(),
            byFirst = emptyMap(),
            byPrefix2 = emptyMap(),
            foldedToWord = emptyMap()
        )
    }
}

internal data class VariantSelectionSession(
    val options: List<String>,
    val optionViews: List<AppCompatTextView>,
    val replacePreviousChar: Boolean,
    val shiftAware: Boolean,
    var selectedIndex: Int
)

internal data class SwipePopupSession(
    val optionViews: List<View>,
    val optionActions: List<(() -> Unit)?>,
    val optionEnabled: List<Boolean>,
    var selectedIndex: Int
)

internal data class SwipeTypingSession(
    val ownerView: View,
    val rawStartX: Float,
    val rawStartY: Float,
    val tokens: MutableList<String>,
    val dwellDurationsMs: MutableList<Long>,
    val trailPoints: MutableList<SwipeTrailView.TrailPoint>,
    var lastTokenEnteredAtMs: Long,
    var isSwiping: Boolean
)

internal data class AutoCorrectionVariant(
    val word: String,
    val penalty: Int
)

internal data class DictionaryCorrectionCandidate(
    val word: String,
    val score: Int,
    val language: KeyboardLanguageMode?,
    val variantPenalty: Int,
    val editDistance: Int,
    val prefixLength: Int
)

internal data class AutoCorrectionResult(
    val originalWord: String,
    val correctedWord: String
)

internal data class AutoCorrectionUndo(
    val originalWord: String,
    val correctedWord: String,
    val committedSuffix: String
)

internal data class PredictionRenderCache(
    val contextKey: String,
    val words: List<String>
)
