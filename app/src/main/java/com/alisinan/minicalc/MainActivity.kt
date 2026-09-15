package com.alisinan.minicalc

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import org.json.JSONArray

class MainActivity : Activity() {

    private lateinit var tvExpression: TextView
    private lateinit var tvResult: TextView
    private lateinit var displayScroll: ScrollView
    private lateinit var displayContent: LinearLayout
    private lateinit var historyArea: LinearLayout
    private lateinit var keypadContainer: LinearLayout
    private lateinit var sciKeypadContainer: LinearLayout
    private lateinit var keypadsArea: LinearLayout
    private lateinit var btnDegRad: MaterialButton
    private lateinit var btnSciToggle: MaterialButton
    private lateinit var btnSettings: MaterialButton

    private var currentExpr = ""
    private var isDeg = true
    private var isSciVisible = false

    // Settings
    private var themeMode = 0   // 0 system, 1 light, 2 dark
    private var hapticTap = true
    private var hapticError = true

    // Color palette (resolved from theme)
    private var bg = 0xFF1C1B1F.toInt()
    private var surface = 0xFF2B2930.toInt()
    private var operatorBg = 0xFF4A4458.toInt()
    private var operatorFg = 0xFFD0BCFF.toInt()
    private var clearFg = 0xFFFFB4AB.toInt()
    private var textPrimary = 0xFFE6E1E5.toInt()
    private var secondaryFg = 0xFF938F99.toInt()

    private val historyList = ArrayList<String>()
    private lateinit var prefs: SharedPreferences

    companion object {
        private const val PREF_NAME = "MiniCalcPrefs"
        private const val KEY_HISTORY = "history_data"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_HAPTIC_TAP = "haptic_tap"
        private const val KEY_HAPTIC_ERR = "haptic_error"
        private const val MAX_HISTORY = 60
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        loadSettings()
        setTheme(themeRes())
        super.onCreate(savedInstanceState)
        resolvePalette()
        buildUI()
        rebuildHistory()
        scrollToCurrent()
    }

    private fun themeRes(): Int = when (themeMode) {
        1 -> R.style.AppTheme_Light
        2 -> R.style.AppTheme_Dark
        else -> R.style.AppTheme
    }

    private fun resolvePalette() {
        val night = when (themeMode) {
            1 -> false
            2 -> true
            else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
        if (night) {
            bg = 0xFF1C1B1F.toInt()
            surface = 0xFF2B2930.toInt()
            operatorBg = 0xFF4A4458.toInt()
            operatorFg = 0xFFD0BCFF.toInt()
            clearFg = 0xFFFFB4AB.toInt()
            textPrimary = 0xFFE6E1E5.toInt()
            secondaryFg = 0xFF938F99.toInt()
        } else {
            bg = 0xFFFEF7FF.toInt()
            surface = 0xFFE7E0EC.toInt()
            operatorBg = 0xFFE8DEF8.toInt()
            operatorFg = 0xFF4F378B.toInt()
            clearFg = 0xFFB3261E.toInt()
            textPrimary = 0xFF1C1B1F.toInt()
            secondaryFg = 0xFF6F6E77.toInt()
        }
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = bg
        val nightFlags = if (night) 0 else {
            var f = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            if (Build.VERSION.SDK_INT >= 26) f = f or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            f
        }
        window.decorView.systemUiVisibility = nightFlags
    }

    // ---------------- Haptics ----------------

    private fun vibrate(ms: Long) {
        try {
            val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(ms)
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun hapticTap() { if (hapticTap) vibrate(8L) }
    private fun hapticError() { if (hapticError) vibrate(60L) }

    // ---------------- Settings ----------------

    private fun loadSettings() {
        themeMode = prefs.getInt(KEY_THEME, 0)
        hapticTap = prefs.getBoolean(KEY_HAPTIC_TAP, true)
        hapticError = prefs.getBoolean(KEY_HAPTIC_ERR, true)
    }

    private fun persistSettings() {
        prefs.edit().putInt(KEY_THEME, themeMode)
            .putBoolean(KEY_HAPTIC_TAP, hapticTap)
            .putBoolean(KEY_HAPTIC_ERR, hapticError)
            .apply()
    }

    private fun showSettings() {
        hapticTap()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(8))
        }

        val themeLabel = TextView(this).apply {
            text = "Theme"
            textSize = 14f
            setTextColor(textPrimary)
            setPadding(0, 0, 0, dp(8))
        }
        val themeGroup = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        val themeNames = arrayOf("System", "Light", "Dark")
        val themeIds = IntArray(3)
        themeNames.forEachIndexed { i, name ->
            val rb = RadioButton(this).apply {
                text = name
                id = View.generateViewId()
                setTextColor(textPrimary)
            }
            themeIds[i] = rb.id
            themeGroup.addView(rb, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(2), 0, dp(2)) })
        }
        themeGroup.setOnCheckedChangeListener { _, checkedId ->
            val mode = themeIds.indexOf(checkedId)
            if (mode >= 0 && mode != themeMode) {
                themeMode = mode
                persistSettings()
                recreate()
            }
        }
        themeGroup.check(themeIds[themeMode])

        val tapBox = CheckBox(this).apply {
            text = "Vibrate on tap"
            textSize = 15f
            isChecked = hapticTap
            setTextColor(textPrimary)
        }
        tapBox.setOnCheckedChangeListener { _, b ->
            hapticTap = b
            persistSettings()
            if (b) hapticTap()
        }

        val errBox = CheckBox(this).apply {
            text = "Vibrate on error"
            textSize = 15f
            isChecked = hapticError
            setTextColor(textPrimary)
        }
        errBox.setOnCheckedChangeListener { _, b ->
            hapticError = b
            persistSettings()
            if (b) hapticError()
        }

        content.addView(themeLabel)
        content.addView(themeGroup)
        content.addView(tapBox, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(12), 0, 0) })
        content.addView(errBox)

        AlertDialog.Builder(this)
            .setTitle("Settings")
            .setView(content)
            .setPositiveButton("Close", null)
            .show()
    }

    // ---------------- UI helpers ----------------

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun mbutton(text: String, bgColor: Int, fg: Int, sizeSp: Float): MaterialButton {
        return MaterialButton(this).apply {
            this.text = text
            setTextColor(fg)
            textSize = sizeSp
            backgroundTintList = ColorStateList.valueOf(bgColor)
            cornerRadius = dp(28)
            elevation = 0f
            insetTop = 0
            insetBottom = 0
            setPadding(0, 0, 0, 0)
            strokeWidth = 0
        }
    }

    private fun row(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
    }

    private fun keyRow(parent: LinearLayout, keys: Array<String>) {
        val r = row()
        val m = dp(6)
        r.setPadding(m, m / 2, m, m / 2)
        keys.forEach { key ->
            val bbg: Int
            val bfg: Int
            when {
                key == "=" -> { bbg = 0xFF6750A4.toInt(); bfg = 0xFFFFFFFF.toInt() }
                "÷×−+".contains(key) -> { bbg = operatorBg; bfg = operatorFg }
                key == "C" || key == "⌫" -> { bbg = surface; bfg = clearFg }
                else -> { bbg = surface; bfg = textPrimary }
            }
            val b = mbutton(key, bbg, bfg, 20f)
            b.typeface = if (key == "=") Typeface.create("sans-serif-medium", Typeface.BOLD)
                         else Typeface.create("sans-serif-medium", Typeface.NORMAL)
            b.setOnClickListener {
                if (key == "=") handleEquals() else handleKeyTap(key)
            }
            r.addView(b, LinearLayout.LayoutParams(0, -1, 1f).apply {
                setMargins(dp(5), dp(4), dp(5), dp(4))
            })
        }
        parent.addView(r, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    private fun sciKeyRow(parent: LinearLayout, keys: Array<String>) {
        val r = row()
        val m = dp(5)
        r.setPadding(m, m / 2, m, m / 2)
        keys.forEach { key ->
            val b = mbutton(key, surface, operatorFg, 14f)
            b.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            b.setOnClickListener { handleKeyTap(key) }
            r.addView(b, LinearLayout.LayoutParams(0, -1, 1f).apply {
                setMargins(dp(4), dp(3), dp(4), dp(3))
            })
        }
        parent.addView(r, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    // ---------------- Main layout ----------------

    private fun buildUI() {
        val main = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            // Keep content just below the status bar / above the nav bar
            setOnApplyWindowInsetsListener { v, insets ->
                v.setPadding(0, insets.systemWindowInsetTop, 0, insets.systemWindowInsetBottom)
                insets
            }
        }

        // Top bar: DEG | SCI | SET
        val topBar = row()
        btnDegRad = mbutton("DEG", surface, operatorFg, 12f).apply {
            cornerRadius = dp(20)
            setOnClickListener { toggleDegRad() }
        }
        btnSciToggle = mbutton("SCI", surface, textPrimary, 12f).apply {
            cornerRadius = dp(20)
            setOnClickListener { toggleSciPanel() }
        }
        btnSettings = mbutton("SET", surface, textPrimary, 12f).apply {
            cornerRadius = dp(20)
            setOnClickListener { showSettings() }
        }
        listOf(btnDegRad, btnSciToggle, btnSettings).forEach { b ->
            topBar.addView(b, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                setMargins(dp(4), 0, dp(4), 0)
            })
        }
        main.addView(topBar, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(dp(8), dp(10), dp(8), dp(4))
        })

        // Scrollable display: [ history… ] then expression + result on top of the viewport
        displayScroll = ScrollView(this).apply {
            setBackgroundColor(bg)
            isVerticalScrollBarEnabled = false
        }
        displayContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(12), dp(22), dp(12))
        }
        historyArea = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        displayContent.addView(historyArea, LinearLayout.LayoutParams(-1, -2))

        tvExpression = TextView(this).apply {
            text = "0"
            setTextColor(textPrimary)
            setTextSize(42f)
            gravity = Gravity.END
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            maxLines = 3
            isSingleLine = false
            ellipsize = TextUtils.TruncateAt.END
        }
        tvResult = TextView(this).apply {
            text = ""
            setTextColor(operatorFg)
            setTextSize(24f)
            gravity = Gravity.END
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(0, dp(8), 0, 0)
        }
        val bottomGroup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            setPadding(0, dp(14), 0, 0)
        }
        bottomGroup.addView(tvExpression, LinearLayout.LayoutParams(-1, -2))
        bottomGroup.addView(tvResult, LinearLayout.LayoutParams(-1, -2))
        displayContent.addView(bottomGroup, LinearLayout.LayoutParams(-1, -2))

        displayScroll.addView(displayContent)
        main.addView(displayScroll, LinearLayout.LayoutParams(-1, 0, 2f))

        // Keypads
        keypadsArea = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        sciKeypadContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        arrayOf(
            arrayOf("sin", "cos", "tan", "π"),
            arrayOf("asin", "acos", "atan", "e"),
            arrayOf("√", "∛", "x^y", "!"),
            arrayOf("ln", "log", "(", ")")
        ).forEach { sciKeyRow(sciKeypadContainer, it) }
        keypadsArea.addView(sciKeypadContainer, LinearLayout.LayoutParams(-1, 0, 2f))

        keypadContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        arrayOf(
            arrayOf("C", "(", ")", "÷"),
            arrayOf("7", "8", "9", "×"),
            arrayOf("4", "5", "6", "−"),
            arrayOf("1", "2", "3", "+"),
            arrayOf("%", "0", ".", "⌫"),
            arrayOf("=")
        ).forEach { keyRow(keypadContainer, it) }
        keypadsArea.addView(keypadContainer, LinearLayout.LayoutParams(-1, 0, 5f))

        main.addView(keypadsArea, LinearLayout.LayoutParams(-1, 0, 5f))

        setContentView(main)
        updateDisplay()
    }

    // ---------------- Interactions ----------------

    private val ops = listOf("÷", "×", "−", "+", "^", "%")
    private val funcs = listOf("sin", "cos", "tan", "asin", "acos", "atan", "sqrt", "cbrt", "ln", "log")

    // Prevent building invalid expressions (inbuilt error prevention).
    private fun canAppend(k: String): Boolean {
        val e = currentExpr
        val last = if (e.isEmpty()) "" else e.last().toString()
        if (funcs.contains(k)) return e.isEmpty() || last != ")"   // funcs always usable except after ")"
        return when (k) {
            // Operators
            in ops -> {
                if (e.isEmpty()) k == "−"                          // allow leading unary minus only
                else if (last == "(") k == "−"                     // "-" directly after "("
                else if (last in ops) k == "−"                      // "5×−3": minus is unary
                else last != "."
            }
            // Digits (allow after "e"/"E" for scientific notation like 2e15)
            in "0123456789" -> last != ")" && last != "!" && last != "π"
            // Decimal point
            "." -> e.isNotEmpty() && last !in ops && last != "(" && last != ")" &&
                   !currentNumberContainsDot(e)
            // Open paren: also after a value for implicit multiplication: 2(3), (2)(3), π(, 2!(
            "(" -> e.isEmpty() || last in ops || last == "(" || last == ")" || last == "!" ||
                   last == "π" || last == "e"
            // Close paren: only when there is an unmatched open paren
            ")" -> countOpenParens(e) > 0
            // Constants: valid at start, after "(", after operators, or after a value (implicit mult)
            "π", "e" -> last != "."
            else -> true
        }
    }

    private fun currentNumberContainsDot(e: String): Boolean {
        var i = e.length
        while (i > 0) {
            val c = e[--i]
            if (c == '.' ) return true
            if (c in "÷×−+^") return false
        }
        return false
    }

    private fun countOpenParens(e: String): Int {
        var open = 0
        var closed = 0
        e.forEach { c ->
            if (c == '(') open++
            else if (c == ')') closed++
        }
        return open - closed
    }

    private fun handleKeyTap(k: String) {
        when (k) {
            "C" -> { currentExpr = ""; tvResult.text = "" }
            "⌫" -> {
                if (currentExpr.isNotEmpty()) {
                    val e = currentExpr
                    currentExpr = when {
                        e.endsWith("asin(") || e.endsWith("acos(") || e.endsWith("atan(") -> e.dropLast(5)
                        e.endsWith("sqrt(") || e.endsWith("cbrt(") -> e.dropLast(5)
                        e.endsWith("sin(") || e.endsWith("cos(") || e.endsWith("tan(") || e.endsWith("log(") -> e.dropLast(4)
                        e.endsWith("ln(") -> e.dropLast(3)
                        else -> e.dropLast(1)
                    }
                }
            }
            "x^y" -> if (canAppend("^")) currentExpr += "^"
            in funcs -> if (canAppend(k)) currentExpr += "$k("
            else -> if (canAppend(k)) currentExpr += k
        }
        hapticTap()
        updateDisplay()
        calculatePreview()
    }

    private fun calculatePreview() {
        if (currentExpr.trim().isEmpty()) { tvResult.text = ""; return }
        try {
            val res = MathEvaluator.eval(currentExpr, isDeg)
            tvResult.text = "= ${MathEvaluator.formatResult(res)}"
        } catch (e: Exception) {
            tvResult.text = ""
        }
    }

    private fun handleEquals() {
        if (currentExpr.trim().isEmpty()) return
        try {
            val res = MathEvaluator.eval(currentExpr, isDeg)
            val formatted = MathEvaluator.formatResult(res)
            saveHistoryEntry("$currentExpr = $formatted")
            currentExpr = formatted
            tvResult.text = ""
            hapticTap()
            updateDisplay()
            rebuildHistory()
            scrollToCurrent()
        } catch (e: Exception) {
            tvResult.text = "Error"
            hapticError()
            Toast.makeText(this, "Invalid expression", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateDisplay() {
        tvExpression.text = currentExpr.ifEmpty { "0" }
    }

    private fun scrollToCurrent() {
        displayScroll.post { displayScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun toggleDegRad() {
        isDeg = !isDeg
        btnDegRad.text = if (isDeg) "DEG" else "RAD"
        hapticTap()
        Toast.makeText(this, if (isDeg) "Degrees" else "Radians", Toast.LENGTH_SHORT).show()
        calculatePreview()
    }

    private fun toggleSciPanel() {
        isSciVisible = !isSciVisible
        sciKeypadContainer.visibility = if (isSciVisible) View.VISIBLE else View.GONE
        btnSciToggle.setTextColor(if (isSciVisible) operatorFg else textPrimary)
        hapticTap()
    }

    // ---------------- History ----------------

    private fun saveHistoryEntry(entry: String) {
        historyList.add(0, entry)
        if (historyList.size > MAX_HISTORY) historyList.removeAt(historyList.size - 1)
        persistHistory()
    }

    private fun persistHistory() {
        val arr = JSONArray()
        historyList.forEach { arr.put(it) }
        prefs.edit().putString(KEY_HISTORY, arr.toString()).apply()
    }

    private fun loadHistoryFromPrefs() {
        historyList.clear()
        val json = prefs.getString(KEY_HISTORY, null) ?: return
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) historyList.add(arr.getString(i))
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun rebuildHistory() {
        historyArea.removeAllViews()
        if (historyList.isEmpty()) return

        // Header: HISTORY + CLEAR
        val header = row().apply { gravity = Gravity.CENTER_VERTICAL }
        val title = TextView(this).apply {
            text = "History"
            setTextColor(secondaryFg)
            textSize = 13f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        val btnClear = mbutton("CLEAR", surface, clearFg, 10f).apply {
            cornerRadius = dp(18)
            setOnClickListener { clearHistory() }
        }
        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(btnClear, LinearLayout.LayoutParams(-2, dp(38)).apply { setMargins(dp(6), 0, 0, 0) })
        historyArea.addView(header, LinearLayout.LayoutParams(-1, dp(44)).apply {
            setMargins(0, dp(6), 0, dp(6))
        })

        historyList.forEach { entry ->
            val parts = entry.split(" = ")
            val exprPart = parts[0]
            val resPart = parts.getOrElse(1) { "" }

            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(surface)
                setPadding(dp(18), dp(12), dp(18), dp(12))
                isClickable = true
                isFocusable = true
            }
            historyArea.addView(item, LinearLayout.LayoutParams(-1, -2).apply {
                setMargins(0, dp(5), 0, dp(5))
            })

            item.addView(TextView(this).apply {
                text = exprPart
                setTextColor(secondaryFg)
                textSize = 15f
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
            })
            item.addView(TextView(this).apply {
                text = "= $resPart"
                setTextColor(textPrimary)
                textSize = 20f
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                setPadding(0, dp(2), 0, 0)
            })
            item.setOnClickListener {
                hapticTap()
                currentExpr = exprPart
                tvResult.text = ""
                updateDisplay()
                scrollToCurrent()
                Toast.makeText(this, "Loaded calculation", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun clearHistory() {
        historyList.clear()
        persistHistory()
        rebuildHistory()
        hapticTap()
        scrollToCurrent()
        Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show()
    }
}