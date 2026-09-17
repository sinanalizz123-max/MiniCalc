package com.alisinan.minicalc;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.InputType;
import android.text.TextUtils;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends Activity {

    private static final String PREF_NAME = "MiniCalcPrefs";
    private static final String KEY_HISTORY = "history_data";
    private static final String KEY_THEME = "theme_mode";
    private static final String KEY_HAPTIC_TAP = "haptic_tap";
    private static final String KEY_HAPTIC_ERR = "haptic_error";
    private static final String KEY_ANGLE = "angle_unit";
    private static final String KEY_MEM = "memory_value";
    private static final String KEY_SEP = "group_separator";
    private static final int MAX_HISTORY = 60;
    private static final int MAX_DIGITS_PER_NUMBER = 15;
    private static final int MAX_EXPR_LENGTH = 70;

    private static final List<String> OPS = Arrays.asList("÷", "×", "−", "+", "^", "%");
    private static final List<String> FUNCS = Arrays.asList(
            "sin", "cos", "tan", "asin", "acos", "atan", "sqrt", "cbrt", "ln", "log", "exp", "abs");

    private EditText tvExpression;
    private TextView tvResult;
    private TextView tvMemoryBadge;
    private ScrollView displayScroll;
    private LinearLayout historyArea;
    private LinearLayout sciKeypad;
    private TextView btnSciToggle;

    private String currentExpr = "";
    private boolean isDeg = true;
    private boolean isSciVisible = false;

    private int themeMode = 0;
    private boolean hapticTap = true;
    private boolean hapticError = true;
    private boolean groupSeparators = false;
    private double memoryValue = 0;
    private double lastAnsValue = 0;

    private int bg;
    private int keyBg;
    private int keyFg;
    private int opBg;
    private int opFg;
    private int utilBg;
    private int utilFg;
    private int dangerFg;
    private int eqBg;
    private int eqFg;
    private int sciBg;
    private int sciFg;
    private int textPrimary;
    private int textSecondary;
    private int rippleColor;

    private final List<String> historyList = new ArrayList<>();
    private SharedPreferences prefs;
    private float density;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        loadSettings();
        setTheme(themeRes());
        super.onCreate(savedInstanceState);
        density = getResources().getDisplayMetrics().density;
        resolvePalette();
        applyWindowColors();
        buildUI();
        loadHistoryFromPrefs();
        rebuildHistory();
        scrollToCurrent();
    }

    private boolean isSystemNight() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    private boolean nightMode() {
        if (themeMode == 1) return false;
        if (themeMode == 2) return true;
        return isSystemNight();
    }

    private int themeRes() {
        boolean night = themeMode == 2 || (themeMode == 0 && isSystemNight());
        return night ? R.style.AppTheme_Dark : R.style.AppTheme_Light;
    }

    private void resolvePalette() {
        if (nightMode()) {
            bg = 0xFF121214;
            keyBg = 0xFF1E1E20;
            keyFg = 0xFFF2F2F4;
            opBg = 0xFF23253A;
            opFg = 0xFFA5B4FC;
            utilBg = 0xFF2A2A2E;
            utilFg = 0xFF9A9AA0;
            dangerFg = 0xFFFF8A80;
            eqBg = 0xFF6366F1;
            eqFg = 0xFFFFFFFF;
            sciBg = 0xFF202024;
            sciFg = 0xFFA5B4FC;
            textPrimary = 0xFFF2F2F4;
            textSecondary = 0xFF9A9AA0;
            rippleColor = 0x33FFFFFF;
        } else {
            bg = 0xFFF4F5F8;
            keyBg = 0xFFFFFFFF;
            keyFg = 0xFF1A1A1A;
            opBg = 0xFFE9E9FD;
            opFg = 0xFF4F46E5;
            utilBg = 0xFFE4E6EC;
            utilFg = 0xFF5A5E6B;
            dangerFg = 0xFFD93025;
            eqBg = 0xFF4F46E5;
            eqFg = 0xFFFFFFFF;
            sciBg = 0xFFEDEEF4;
            sciFg = 0xFF5A5E6B;
            textPrimary = 0xFF1A1A1A;
            textSecondary = 0xFF8A8A8E;
            rippleColor = 0x14000000;
        }
    }

    private void applyWindowColors() {
        Window w = getWindow();
        w.setStatusBarColor(bg);
        w.setNavigationBarColor(bg);
        int flags = 0;
        if (!nightMode()) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
        }
        w.getDecorView().setSystemUiVisibility(flags);
    }

    // All vibration runs on a dedicated thread: Vibrator calls are Binder IPC
    // and must never stall the UI thread mid-gesture (that was the slide jank).
    private static android.os.Handler hapticHandler;

    private void vibrate(long ms) {
        vibrate(ms, VibrationEffect.DEFAULT_AMPLITUDE);
    }

    private void vibrate(final long ms, final int amplitude) {
        try {
            final Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null) return;
            if (hapticHandler == null) {
                android.os.HandlerThread t = new android.os.HandlerThread("haptics");
                t.start();
                hapticHandler = new android.os.Handler(t.getLooper());
            }
            hapticHandler.post(new Runnable() {
                @Override public void run() {
                    try {
                        if (!v.hasVibrator()) return;
                        if (Build.VERSION.SDK_INT >= 26) {
                            v.vibrate(VibrationEffect.createOneShot(ms, amplitude));
                        } else {
                            v.vibrate(ms);
                        }
                    } catch (Exception ignored) {
                    }
                }
            });
        } catch (Exception ignored) {
        }
    }

    private void hapticTap() {
        if (hapticTap) vibrate(8L);
    }

    private long lastTickAt = 0;

    // Micro-tick for cursor sliding: short + low amplitude for a crisp subtle
    // tick, rate-limited so fast slides feel like detents, off the UI thread.
    // Honors the tap setting.
    private void hapticTick() {
        if (!hapticTap) return;
        long now = SystemClock.uptimeMillis();
        if (now - lastTickAt < 30) return;
        lastTickAt = now;
        vibrate(8L, 45);
    }

    private void hapticError() {
        if (hapticError) vibrate(60L);
    }

    private void loadSettings() {
        themeMode = prefs.getInt(KEY_THEME, 0);
        hapticTap = prefs.getBoolean(KEY_HAPTIC_TAP, true);
        hapticError = prefs.getBoolean(KEY_HAPTIC_ERR, true);
        isDeg = prefs.getBoolean(KEY_ANGLE, true);
        groupSeparators = prefs.getBoolean(KEY_SEP, false);
        memoryValue = Double.longBitsToDouble(prefs.getLong(KEY_MEM, 0));
        lastAnsValue = Double.longBitsToDouble(prefs.getLong("last_ans", 0));
        MathEvaluator.setLastAns(lastAnsValue);
    }

    private void persistSettings() {
        prefs.edit()
                .putInt(KEY_THEME, themeMode)
                .putBoolean(KEY_HAPTIC_TAP, hapticTap)
                .putBoolean(KEY_HAPTIC_ERR, hapticError)
                .putBoolean(KEY_ANGLE, isDeg)
                .putBoolean(KEY_SEP, groupSeparators)
                .putLong(KEY_MEM, Double.doubleToRawLongBits(memoryValue))
                .putLong("last_ans", Double.doubleToRawLongBits(MathEvaluator.getLastAns()))
                .apply();
    }

    private void persistMemory() {
        prefs.edit().putLong(KEY_MEM, Double.doubleToRawLongBits(memoryValue)).apply();
    }

    private void persistAns() {
        prefs.edit().putLong("last_ans", Double.doubleToRawLongBits(MathEvaluator.getLastAns())).apply();
    }

    private String displayValue(String v) {
        if (!groupSeparators) return v;
        return addSeparators(v);
    }

    private static String addSeparators(String v) {
        if (v == null || v.isEmpty()) return v;
        boolean neg = v.startsWith("-") || v.startsWith("−");
        String s = neg ? v.substring(1) : v;
        // Only plain decimal numbers get separators; anything else
        // (Infinity, Error, E-notation, ...) passes through untouched.
        if (s.isEmpty()) return v;
        int dots = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '.') {
                if (++dots > 1) return v;
            } else if (c < '0' || c > '9') {
                return v;
            }
        }
        int dot = s.indexOf('.');
        String intPart = dot >= 0 ? s.substring(0, dot) : s;
        String decPart = dot >= 0 ? s.substring(dot) : "";
        if (intPart.length() <= 3) return v;
        StringBuilder out = new StringBuilder();
        int r = intPart.length() % 3;
        if (r > 0) out.append(intPart, 0, r);
        for (int i = r; i < intPart.length(); i += 3) {
            if (out.length() > 0) out.append(',');
            out.append(intPart, i, Math.min(i + 3, intPart.length()));
        }
        String res = out.toString() + decPart;
        return neg ? "-" + res : res;
    }

    private int dp(int v) {
        return Math.round(v * density);
    }

    private int adaptiveGapPx() {
        float wDp = getResources().getDisplayMetrics().widthPixels / density;
        float hDp = getResources().getDisplayMetrics().heightPixels / density;
        float s = Math.min(wDp, hDp);
        float g;
        if (s < 300) g = 0.4f;
        else if (s < 340) g = 0.5f;
        else if (s < 380) g = 0.6f;
        else if (s < 420) g = 0.8f;
        else g = 1f;
        return Math.round(g * density);
    }

    private RippleDrawable roundRect(int fill, int radiusDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(dp(radiusDp));
        shape.setColor(fill);
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), shape, null);
    }

    private TextView key(String label, int bgColor, int fgColor, float sizeSp, boolean bold) {
        FitTextView t = new FitTextView(this);
        t.setText(label);
        t.setTextColor(fgColor);
        t.setTextSize(sizeSp);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(Typeface.create("sans-serif-medium", bold ? Typeface.BOLD : Typeface.NORMAL));
        t.setBackground(roundRect(bgColor, 20));
        t.setClickable(true);
        t.setFocusable(true);
        return t;
    }

    private static class FitTextView extends TextView {
        FitTextView(Context c) {
            super(c);
            setSingleLine(true);
            setIncludeFontPadding(false);
            setEllipsize(TextUtils.TruncateAt.END);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            fitTo(w, h);
        }

        @Override
        protected void onTextChanged(CharSequence text, int start, int lengthBefore, int after) {
            super.onTextChanged(text, start, lengthBefore, after);
            fitTo(getWidth(), getHeight());
        }

        private void fitTo(int w, int h) {
            if (w <= 0 || h <= 0) return;
            CharSequence s = getText();
            if (s == null || s.length() == 0) return;
            Paint p = new Paint();
            p.setTypeface(getTypeface());
            float fontPx = getTextSize();
            p.setTextSize(fontPx);
            float tw = p.measureText(s.toString());
            Paint.FontMetrics fm = p.getFontMetrics();
            float th = fm.descent - fm.ascent;
            float maxW = w * 0.84f;
            float maxH = h * 0.72f;
            float scale = 1f;
            if (tw > maxW) scale = Math.min(scale, maxW / tw);
            if (th > maxH) scale = Math.min(scale, maxH / th);
            float floor = 5f * getResources().getDisplayMetrics().density;
            float target = Math.max(floor, fontPx * scale);
            if (target < fontPx) setTextSize(TypedValue.COMPLEX_UNIT_PX, target);
        }
    }

    private TextView chip(String label) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(12f);
        t.setGravity(Gravity.CENTER);
        t.setLetterSpacing(0.08f);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        t.setClickable(true);
        t.setFocusable(true);
        return t;
    }

    private void styleChip(TextView v, boolean active) {
        v.setBackground(roundRect(active ? eqBg : utilBg, 20));
        v.setTextColor(active ? 0xFFFFFFFF : utilFg);
    }

    private void refreshChips() {
        styleChip(btnSciToggle, isSciVisible);
    }

    private void refreshMemoryBadge() {
        if (tvMemoryBadge == null) return;
        boolean hasM = memoryValue != 0;
        tvMemoryBadge.setVisibility(hasM ? View.VISIBLE : View.INVISIBLE);
        if (hasM) {
            tvMemoryBadge.setBackground(roundRect(eqBg, 10));
            tvMemoryBadge.setTextColor(0xFFFFFFFF);
        }
    }

    private void copyToClipboard(String text) {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("result", text));
            Toast.makeText(this, "Copied " + text, Toast.LENGTH_SHORT).show();
            hapticTap();
        } catch (Exception ignored) {}
    }

    private void toggleSign() {
        if (currentExpr.isEmpty()) {
            currentExpr = "−";
            hapticTap(); updateDisplay(); calculatePreview(); return;
        }
        char last = currentExpr.charAt(currentExpr.length() - 1);
        if (last == '−' || last == '+') {
            currentExpr = currentExpr.substring(0, currentExpr.length() - 1)
                    + (last == '−' ? "+" : "−");
            hapticTap(); updateDisplay(); calculatePreview(); return;
        }
        int anchor = -1;
        for (int i = currentExpr.length() - 1; i >= 0; i--) {
            char c = currentExpr.charAt(i);
            if (c == '÷' || c == '×' || c == '^' || c == '%') { anchor = i; break; }
            if (c == '+' || c == '−') {
                if (i == 0) { anchor = -1; break; }
                char prev = currentExpr.charAt(i - 1);
                if (prev == '(' || prev == '÷' || prev == '×' || prev == '+' || prev == '−' || prev == '^' || prev == '%') {
                    continue;
                } else { anchor = i; break; }
            }
            if (c == '(') { anchor = i; break; }
        }
        int numStart = anchor + 1;
        String tail = currentExpr.substring(numStart);
        if (tail.startsWith("−")) {
            currentExpr = currentExpr.substring(0, numStart) + tail.substring(1);
        } else if (tail.startsWith("-")) {
            currentExpr = currentExpr.substring(0, numStart) + tail.substring(1);
        } else if (tail.startsWith("+")) {
            currentExpr = currentExpr.substring(0, numStart) + "−" + tail.substring(1);
        } else if (!tail.isEmpty()) {
            if (tail.startsWith("(") && tail.endsWith(")")) {
                currentExpr = currentExpr.substring(0, numStart) + "−" + tail;
            } else {
                currentExpr = currentExpr.substring(0, numStart) + "−" + tail;
            }
        } else {
            currentExpr += "−";
        }
        hapticTap(); updateDisplay(); calculatePreview();
    }

    private void handleMemory(String k) {
        if ("MC".equals(k)) {
            memoryValue = 0; persistMemory(); refreshMemoryBadge();
            Toast.makeText(this, "Memory cleared", Toast.LENGTH_SHORT).show();
        } else if ("MR".equals(k)) {
            String s = MathEvaluator.formatResult(memoryValue);
            currentExpr = s;
            renderExpr(currentExpr.length(), currentExpr.length());
            calculatePreview();
        } else if ("M+".equals(k)) {
            double v = currentResultValue();
            if (!Double.isNaN(v)) { memoryValue += v; persistMemory(); refreshMemoryBadge();
                Toast.makeText(this, "M+ " + MathEvaluator.formatResult(v), Toast.LENGTH_SHORT).show(); }
        } else if ("M−".equals(k)) {
            double v = currentResultValue();
            if (!Double.isNaN(v)) { memoryValue -= v; persistMemory(); refreshMemoryBadge();
                Toast.makeText(this, "M− " + MathEvaluator.formatResult(v), Toast.LENGTH_SHORT).show(); }
        }
        hapticTap();
    }

    private double currentResultValue() {
        if (currentExpr.trim().isEmpty()) return Double.NaN;
        try { return MathEvaluator.evalPreview(currentExpr, isDeg); } catch (Exception e) { return Double.NaN; }
    }

    private void addKeyRow(LinearLayout parent, String[] keys, boolean sci) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int gap = adaptiveGapPx();
        row.setPadding(0, 0, 0, 0);

        for (final String k : keys) {
            TextView b;
            if (sci) {
                boolean mem = "MC".equals(k) || "MR".equals(k) || "M+".equals(k) || "M−".equals(k);
                if (mem) b = key(k, utilBg, sciFg, 12f, true);
                else b = key(k, sciBg, sciFg, 13f, true);
            } else if ("=".equals(k)) {
                b = key(k, eqBg, eqFg, 26f, true);
            } else if ("÷".equals(k) || "×".equals(k) || "−".equals(k) || "+".equals(k)) {
                b = key(k, opBg, opFg, 22f, true);
            } else if ("C".equals(k) || "⌫".equals(k)) {
                b = key(k, utilBg, dangerFg, 20f, true);
            } else if ("(".equals(k) || ")".equals(k) || "%".equals(k) || "±".equals(k)) {
                b = key(k, utilBg, utilFg, 20f, false);
            } else if ("ANS".equals(k)) {
                b = key(k, utilBg, sciFg, 15f, true);
            } else {
                b = key(k, keyBg, keyFg, 21f, false);
            }

            LinearLayout.LayoutParams lp =
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            boolean isLastRow = keys.length == 3 && "ANS".equals(keys[0]);
            int m = isLastRow ? Math.max(1, gap / 2) : gap;
            int vPad = gap / 2;
            lp.setMargins(m, vPad, m, vPad);
            b.setLayoutParams(lp);
            final String kk = k;
            if ("MC".equals(k) || "MR".equals(k) || "M+".equals(k) || "M−".equals(k)) {
                b.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { handleMemory(kk); }
                });
            } else if ("±".equals(k)) {
                b.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { toggleSign(); }
                });
            } else if ("ANS".equals(k)) {
                b.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { handleAnsTap(); }
                });
            } else {
                b.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if ("=".equals(kk)) handleEquals(); else handleKeyTap(kk);
                    }
                });
            }
            row.addView(b);
        }
        parent.addView(row,
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    private void handleAnsTap() {
        if (canAppend("ANS")) insertAtCursor("ANS");
        hapticTap();
    }

    private void buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(12), dp(10), dp(12), dp(2));

        btnSciToggle = chip("SCI");
        TextView btnSettings = chip("SET");
        btnSciToggle.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleSciPanel(); }
        });
        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showSettings(); }
        });
        refreshChips();

        tvMemoryBadge = new TextView(this);
        tvMemoryBadge.setText("M");
        tvMemoryBadge.setTextSize(11f);
        tvMemoryBadge.setGravity(Gravity.CENTER);
        tvMemoryBadge.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        tvMemoryBadge.setPadding(dp(10), dp(6), dp(10), dp(6));
        tvMemoryBadge.setVisibility(View.INVISIBLE);

        LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(0, dp(40), 1f);
        chipLp.setMargins(dp(4), 0, dp(4), 0);
        topBar.addView(btnSciToggle, chipLp);
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(dp(36), dp(28));
        badgeLp.setMargins(dp(4), 0, dp(4), 0);
        topBar.addView(tvMemoryBadge, badgeLp);
        topBar.addView(btnSettings, chipLp);
        root.addView(topBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        refreshMemoryBadge();

        displayScroll = new ScrollView(this);
        displayScroll.setBackgroundColor(bg);
        displayScroll.setVerticalScrollBarEnabled(false);

        LinearLayout displayContent = new LinearLayout(this);
        displayContent.setOrientation(LinearLayout.VERTICAL);
        displayContent.setPadding(dp(20), dp(10), dp(20), dp(10));

        historyArea = new LinearLayout(this);
        historyArea.setOrientation(LinearLayout.VERTICAL);
        displayContent.addView(historyArea,
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        tvExpression = new EditText(this);
        tvExpression.setText("");
        tvExpression.setHint("0");
        tvExpression.setHintTextColor(textSecondary);
        tvExpression.setTextColor(textPrimary);
        tvExpression.setTextSize(40f);
        tvExpression.setGravity(Gravity.END);
        tvExpression.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        tvExpression.setMaxLines(3);
        tvExpression.setPadding(0, 0, 0, 0);
        tvExpression.setBackground(null);
        tvExpression.setFocusable(true);
        tvExpression.setFocusableInTouchMode(true);
        tvExpression.setCursorVisible(true);
        // Our keypad is the only input: never show the system keyboard.
        tvExpression.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        tvExpression.setShowSoftInputOnFocus(false);
        // No system copy/paste bars either: keys stay the only way text
        // changes, long-press copies the whole expression (as before).
        ActionMode.Callback noBar = new ActionMode.Callback() {
            @Override public boolean onCreateActionMode(ActionMode m, Menu menu) { return false; }
            @Override public boolean onPrepareActionMode(ActionMode m, Menu menu) { return false; }
            @Override public boolean onActionItemClicked(ActionMode m, MenuItem i) { return false; }
            @Override public void onDestroyActionMode(ActionMode m) {}
        };
        tvExpression.setCustomSelectionActionModeCallback(noBar);
        tvExpression.setCustomInsertionActionModeCallback(noBar);
        tvExpression.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                if (!currentExpr.isEmpty()) copyToClipboard(currentExpr);
                return true;
            }
        });

        tvResult = new TextView(this);
        tvResult.setText("");
        tvResult.setTextColor(eqBg);
        tvResult.setTextSize(22f);
        tvResult.setGravity(Gravity.END);
        tvResult.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tvResult.setPadding(0, dp(6), 0, 0);
        tvResult.setClickable(true);
        tvResult.setFocusable(true);
        tvResult.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String t = tvResult.getText().toString().replace("= ", "").trim();
                if (!t.isEmpty() && !t.equals("Error")) copyToClipboard(t);
            }
        });
        tvResult.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                String t = tvResult.getText().toString().replace("= ", "").trim();
                if (!t.isEmpty() && !t.equals("Error")) copyToClipboard(t);
                return true;
            }
        });

        LinearLayout bottomGroup = new LinearLayout(this);
        bottomGroup.setOrientation(LinearLayout.VERTICAL);
        bottomGroup.setGravity(Gravity.END);
        bottomGroup.setPadding(0, dp(14), 0, 0);
        bottomGroup.addView(tvExpression, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        bottomGroup.addView(tvResult, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        displayContent.addView(bottomGroup, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        displayScroll.addView(displayContent);
        root.addView(displayScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 2f));

        // Slide left/right anywhere on the keys to glide the cursor
        // (Gboard spacebar-style, but over the whole keypad). The gesture is
        // only claimed once it is clearly a horizontal slide, so normal taps
        // still click their keys; claiming it sends CANCEL to the key.
        LinearLayout keypadsArea = new LinearLayout(this) {
            float downX;
            float lastStepX;
            boolean sliding;
            int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
            float stepPx;

            // Claiming only: once claimed, the framework sends the rest of
            // this finger's moves to onTouchEvent below, which drives them.
            // Keeping this side-effect-free after the claim avoids double steps.
            @Override public boolean onInterceptTouchEvent(MotionEvent ev) {
                switch (ev.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = ev.getX();
                        lastStepX = downX;
                        sliding = false;
                        return false;
                    case MotionEvent.ACTION_MOVE:
                        if (sliding) return true;
                        if (Math.abs(ev.getX() - downX) < slop) return false;
                        sliding = true;
                        lastStepX = ev.getX();
                        hapticTap();
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        sliding = false;
                        return false;
                }
                return false;
            }

            // Drives a claimed slide until the finger lifts: every move keeps
            // gliding the cursor, so the gesture never freezes mid-touch.
            @Override public boolean onTouchEvent(MotionEvent ev) {
                switch (ev.getActionMasked()) {
                    case MotionEvent.ACTION_MOVE:
                        if (!sliding) return super.onTouchEvent(ev);
                        if (stepPx == 0) stepPx = dp(12);
                        float dx = ev.getX() - lastStepX;
                        int steps = (int) (dx / stepPx);
                        if (steps != 0) {
                            lastStepX += steps * stepPx;
                            slideCursor(steps);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        sliding = false;
                        return super.onTouchEvent(ev);
                }
                return super.onTouchEvent(ev);
            }
        };
        keypadsArea.setOrientation(LinearLayout.VERTICAL);

        sciKeypad = new LinearLayout(this);
        sciKeypad.setOrientation(LinearLayout.VERTICAL);
        sciKeypad.setVisibility(View.GONE);
        String[][] sciRows = {
                {"MC", "MR", "M+", "M−"},
                {"sin", "cos", "tan", "π"},
                {"asin", "acos", "atan", "e"},
                {"√", "∛", "x^y", "!"},
                {"ln", "log", "exp", "abs"}
        };
        for (String[] r : sciRows) addKeyRow(sciKeypad, r, true);
        keypadsArea.addView(sciKeypad, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 5f));

        LinearLayout keypad = new LinearLayout(this);
        keypad.setOrientation(LinearLayout.VERTICAL);
        String[][] rows = {
                {"C", "(", ")", "÷"},
                {"7", "8", "9", "×"},
                {"4", "5", "6", "−"},
                {"1", "2", "3", "+"},
                {"±", "0", ".", "⌫"},
                {"ANS", "%", "="}
        };
        for (String[] r : rows) addKeyRow(keypad, r, false);
        keypadsArea.addView(keypad, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 6f));

        root.addView(keypadsArea, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 5f));

        setContentView(root);
        updateDisplay();
    }

    private boolean endsWithAny(String s, String... suffixes) {
        for (String suffix : suffixes) {
            if (s.endsWith(suffix)) return true;
        }
        return false;
    }

    private int digitsInCurrentNumber(String e) {
        int c = 0;
        for (int i = e.length() - 1; i >= 0; i--) {
            char ch = e.charAt(i);
            if (ch >= '0' && ch <= '9') c++;
            else if (ch == '.') continue;
            else break;
        }
        return c;
    }

    private boolean canAppend(String k) {
        if (currentExpr.length() >= MAX_EXPR_LENGTH && !"⌫".equals(k) && !"C".equals(k)) return false;
        // Validate against the text before the cursor, so rules work mid-expression.
        int[] sel = internalSelection();
        String e = currentExpr.substring(0, sel[0]);
        String last = e.isEmpty() ? "" : e.substring(e.length() - 1);
        if ("ANS".equals(k)) {
            if (e.isEmpty()) return true;
            if (last.equals(".")) return false;
            if (e.endsWith("ANS")) return false;
            return true;
        }
        if (FUNCS.contains(k)) return e.isEmpty() || !last.equals(")");

        if (OPS.contains(k)) {
            if ("%".equals(k)) {
                if (e.isEmpty()) return false;
                return last.matches("[0-9%)!πe]") || e.endsWith("ANS");
            }
            if (e.isEmpty()) return "−".equals(k);
            if (last.equals("(")) return "−".equals(k);
            if (OPS.contains(last) && !"%".equals(last) && !"!".equals(last) && !")".equals(last)) return "−".equals(k);
            return !last.equals(".");
        }
        if ("0123456789".contains(k)) {
            if (last.equals(")") || last.equals("!") || last.equals("%") || last.equals("π")) return false;
            if (e.endsWith("ANS")) return false;
            return digitsInCurrentNumber(e) < MAX_DIGITS_PER_NUMBER;
        }
        if (".".equals(k)) {
            return !e.isEmpty() && !OPS.contains(last) && !last.equals("(")
                    && !last.equals(")") && !currentNumberContainsDot(e);
        }
        if ("(".equals(k)) {
            return e.isEmpty() || OPS.contains(last) || last.equals("(") || last.equals(")")
                    || last.equals("!") || last.equals("π") || last.equals("e") || e.endsWith("ANS") || last.equals("%");
        }
        if (")".equals(k)) return countOpenParens(e) > 0;
        if ("π".equals(k) || "e".equals(k)) return !last.equals(".");
        return true;
    }

    private boolean currentNumberContainsDot(String e) {
        for (int i = e.length() - 1; i >= 0; i--) {
            char c = e.charAt(i);
            if (c == '.') return true;
            if (c == '÷' || c == '×' || c == '−' || c == '+' || c == '^') return false;
        }
        return false;
    }

    private int countOpenParens(String e) {
        int open = 0;
        int closed = 0;
        for (int i = 0; i < e.length(); i++) {
            char c = e.charAt(i);
            if (c == '(') open++;
            else if (c == ')') closed++;
        }
        return open - closed;
    }

    private void handleKeyTap(String k) {
        if ("C".equals(k)) {
            currentExpr = "";
            tvResult.setText("");
            renderExpr(0, 0);
        } else if ("⌫".equals(k)) {
            backspaceAtCursor();
        } else if ("x^y".equals(k)) {
            if (canAppend("^")) insertAtCursor("^");
        } else if (FUNCS.contains(k)) {
            if (canAppend(k)) insertAtCursor(k + "(");
        } else {
            if (canAppend(k)) insertAtCursor(k);
        }
        hapticTap();
    }

    private void calculatePreview() {
        if (currentExpr.trim().isEmpty()) {
            tvResult.setText("");
            return;
        }
        try {
            double res = MathEvaluator.evalPreview(currentExpr, isDeg);
            tvResult.setText("= " + displayValue(MathEvaluator.formatResult(res)));
        } catch (Exception e) {
            tvResult.setText("");
        }
    }

    private void handleEquals() {
        if (currentExpr.trim().isEmpty()) return;
        try {
            double res = MathEvaluator.eval(currentExpr, isDeg);
            String formatted = MathEvaluator.formatResult(res);
            saveHistoryEntry(currentExpr + " = " + formatted);
            persistAns();
            currentExpr = formatted;
            tvResult.setText("");
            hapticTap();
            renderExpr(currentExpr.length(), currentExpr.length());
            rebuildHistory();
            scrollToCurrent();
        } catch (Exception e) {
            tvResult.setText("Error");
            hapticError();
            Toast.makeText(this, "Invalid expression", Toast.LENGTH_SHORT).show();
        }
    }

    // ---------------- Cursor editing ----------------
    // The expression field is an EditText so the user can tap to place the
    // cursor (or select a span like the "5" in "542") and edit anywhere.
    // currentExpr stays the source of truth (comma-free); the widget may show
    // grouped digits, so selections are mapped between widget and internal
    // coordinates on every operation.

    private int clampPos(int p, int len) {
        if (p < 0) return 0;
        if (p > len) return len;
        return p;
    }

    private int countCommasBefore(String s, int pos) {
        int c = 0;
        int lim = Math.min(pos, s.length());
        for (int i = 0; i < lim; i++) if (s.charAt(i) == ',') c++;
        return c;
    }

    private boolean isAllNumeric(String raw) {
        if (raw.isEmpty()) return false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if ((c < '0' || c > '9') && c != '.' && c != '-' && c != '−' && c != ',') return false;
        }
        return true;
    }

    // Widget selection -> internal (comma-free) selection.
    private int[] internalSelection() {
        int len = currentExpr.length();
        String disp = tvExpression.getText().toString();
        int a = tvExpression.getSelectionStart();
        int b = tvExpression.getSelectionEnd();
        if (a < 0 || b < 0) return new int[]{len, len};
        a = clampPos(a - countCommasBefore(disp, a), len);
        b = clampPos(b - countCommasBefore(disp, b), len);
        if (a > b) { int t = a; a = b; b = t; }
        return new int[]{a, b};
    }

    // Internal offset -> widget offset (grouping only inserts commas).
    private int mapToDisplay(int internalPos, boolean grouped, int dispLen) {
        if (!grouped) return clampPos(internalPos, dispLen);
        String raw = currentExpr;
        int start = (raw.startsWith("-") || raw.startsWith("−")) ? 1 : 0;
        int dot = raw.indexOf('.');
        int intEnd = dot >= 0 ? dot : raw.length();
        int commas = 0;
        if (intEnd - start > 3) {
            int k = start + ((intEnd - start) % 3);
            if (k == start) k += 3;
            for (; k < intEnd; k += 3) if (k <= internalPos) commas++;
        }
        return clampPos(internalPos + commas, dispLen);
    }

    private void renderExpr(int cs, int ce) {
        String raw = currentExpr;
        boolean grouped = groupSeparators && isAllNumeric(raw);
        String disp = grouped ? displayValue(raw.replace(",", "")) : raw;
        int s0 = tvExpression.getSelectionStart();
        int s1 = tvExpression.getSelectionEnd();
        if (!tvExpression.getText().toString().equals(disp)) tvExpression.setText(disp);
        int dl = disp.length();
        if (cs < 0) {
            tvExpression.setSelection(s0 < 0 ? dl : clampPos(s0, dl),
                    s1 < 0 ? dl : clampPos(s1, dl));
        } else {
            tvExpression.setSelection(mapToDisplay(cs, grouped, dl), mapToDisplay(ce, grouped, dl));
        }
        fitExpression();
    }

    private void insertAtCursor(String s) {
        int[] sel = internalSelection();
        StringBuilder b = new StringBuilder(currentExpr);
        b.replace(sel[0], sel[1], s);
        currentExpr = b.toString();
        renderExpr(sel[0] + s.length(), sel[0] + s.length());
        calculatePreview();
    }

    // Slide-to-move-cursor (Gboard spacebar-style): the keypad area reports
    // accumulated horizontal travel; a selection collapses toward the slide
    // direction first, then the cursor glides with the finger.
    private void slideCursor(int steps) {
        if (steps == 0 || currentExpr.isEmpty()) return;
        if (!tvExpression.isFocused()) tvExpression.requestFocus();
        int[] sel = internalSelection();
        int from = (sel[0] != sel[1]) ? (steps < 0 ? sel[0] : sel[1]) : sel[0];
        int pos = clampPos(from + steps, currentExpr.length());
        if (pos == from) return; // stuck at an end: no move, no tick
        // Text never changes while sliding, so move only the selection —
        // no setText / font-fit / relayout, which is what made it janky.
        String raw = currentExpr;
        boolean grouped = groupSeparators && isAllNumeric(raw);
        String disp = grouped ? displayValue(raw.replace(",", "")) : raw;
        if (!tvExpression.getText().toString().equals(disp)) {
            renderExpr(pos, pos);
        } else {
            int dp = mapToDisplay(pos, grouped, disp.length());
            tvExpression.setSelection(dp, dp);
        }
        hapticTick();
    }

    private void backspaceAtCursor() {
        int[] sel = internalSelection();
        if (sel[0] != sel[1]) {
            StringBuilder b = new StringBuilder(currentExpr);
            b.delete(sel[0], sel[1]);
            currentExpr = b.toString();
            renderExpr(sel[0], sel[0]);
        } else if (sel[0] > 0) {
            String before = currentExpr.substring(0, sel[0]);
            int drop = 1;
            if (before.endsWith("ANS")) drop = 3;
            else if (endsWithAny(before, "asin(", "acos(", "atan(", "sqrt(", "cbrt(")) drop = 5;
            else if (endsWithAny(before, "sin(", "cos(", "tan(", "log(", "exp(", "abs(")) drop = 4;
            else if (before.endsWith("ln(")) drop = 3;
            StringBuilder b = new StringBuilder(currentExpr);
            b.delete(sel[0] - drop, sel[0]);
            currentExpr = b.toString();
            renderExpr(sel[0] - drop, sel[0] - drop);
        }
        calculatePreview();
    }

    private void updateDisplay() {
        renderExpr(-1, -1);
    }

    private void fitExpression() {
        if (tvExpression == null) return;
        tvExpression.post(new Runnable() {
            @Override
            public void run() {
                int w = tvExpression.getWidth();
                if (w <= 0) return;
                int avail = w - tvExpression.getPaddingLeft() - tvExpression.getPaddingRight();
                if (avail <= 0) return;
                String s = tvExpression.getText().toString();
                if (s.isEmpty()) return;
                Paint p = new Paint();
                p.setTypeface(tvExpression.getTypeface());
                float baseSp = 40f;
                float minSp = 16f;
                p.setTextSize(baseSp * getResources().getDisplayMetrics().scaledDensity);
                float tw = p.measureText(s);
                float targetSp = baseSp;
                if (tw > avail * 0.98f) {
                    targetSp = baseSp * (avail * 0.98f / tw);
                    if (targetSp < minSp) targetSp = minSp;
                }
                tvExpression.setTextSize(targetSp);
            }
        });
    }

    private void scrollToCurrent() {
        displayScroll.post(new Runnable() {
            @Override
            public void run() {
                displayScroll.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    private void toggleSciPanel() {
        isSciVisible = !isSciVisible;
        sciKeypad.setVisibility(isSciVisible ? View.VISIBLE : View.GONE);
        refreshChips();
        hapticTap();
    }

    private void saveHistoryEntry(String entry) {
        historyList.add(0, entry);
        if (historyList.size() > MAX_HISTORY) {
            historyList.remove(historyList.size() - 1);
        }
        persistHistory();
    }

    private void persistHistory() {
        JSONArray arr = new JSONArray();
        for (String s : historyList) arr.put(s);
        prefs.edit().putString(KEY_HISTORY, arr.toString()).apply();
    }

    private void loadHistoryFromPrefs() {
        historyList.clear();
        String json = prefs.getString(KEY_HISTORY, null);
        if (json == null) return;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) historyList.add(arr.getString(i));
        } catch (Exception ignored) {
        }
    }

    private void rebuildHistory() {
        historyArea.removeAllViews();
        if (historyList.isEmpty()) return;

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("HISTORY");
        title.setTextSize(11f);
        title.setLetterSpacing(0.08f);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        title.setTextColor(textSecondary);
        header.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView clear = key("CLEAR", utilBg, dangerFg, 11f, true);
        clear.setPadding(dp(16), 0, dp(16), 0);
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearHistory();
            }
        });
        header.addView(clear, new LinearLayout.LayoutParams(dp(84), dp(34)));

        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        hl.setMargins(0, dp(4), 0, dp(6));
        historyArea.addView(header, hl);

        for (final String entry : historyList) {
            String[] parts = entry.split(" = ");
            final String exprPart = parts[0];
            String resPart = parts.length > 1 ? parts[1] : "";

            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setBackground(roundRect(keyBg, 14));
            item.setPadding(dp(16), dp(12), dp(16), dp(12));
            item.setClickable(true);
            item.setFocusable(true);

            TextView e1 = new TextView(this);
            e1.setText(exprPart);
            e1.setTextColor(textSecondary);
            e1.setTextSize(14f);
            e1.setMaxLines(2);
            e1.setEllipsize(TextUtils.TruncateAt.END);
            item.addView(e1);

            TextView e2 = new TextView(this);
            e2.setText("= " + resPart);
            e2.setTextColor(textPrimary);
            e2.setTextSize(20f);
            e2.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            e2.setPadding(0, dp(2), 0, 0);
            item.addView(e2);

            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    hapticTap();
                    currentExpr = exprPart;
                    tvResult.setText("");
                    renderExpr(currentExpr.length(), currentExpr.length());
                    calculatePreview();
                    scrollToCurrent();
                }
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, dp(4), 0, dp(4));
            historyArea.addView(item, lp);
        }
    }

    private void clearHistory() {
        historyList.clear();
        persistHistory();
        rebuildHistory();
        hapticTap();
        scrollToCurrent();
        Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show();
    }

    private void showSettings() {
        hapticTap();

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(16), dp(24), dp(4));

        TextView label = new TextView(this);
        label.setText("THEME");
        label.setTextSize(11f);
        label.setLetterSpacing(0.08f);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        label.setTextColor(textSecondary);
        label.setPadding(0, 0, 0, dp(6));
        content.addView(label);

        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.VERTICAL);
        String[] names = {"System", "Light", "Dark"};
        final int[] ids = new int[3];
        for (int i = 0; i < 3; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(names[i]);
            rb.setTextSize(16f);
            rb.setId(View.generateViewId());
            rb.setPadding(dp(4), dp(8), 0, dp(8));
            ids[i] = rb.getId();
            group.addView(rb, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        group.check(ids[themeMode]);
        group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup g, int checkedId) {
                int mode = -1;
                for (int i = 0; i < ids.length; i++) {
                    if (ids[i] == checkedId) mode = i;
                }
                if (mode >= 0 && mode != themeMode) {
                    themeMode = mode;
                    persistSettings();
                    recreate();
                }
            }
        });
        content.addView(group);

        TextView angleLabel = new TextView(this);
        angleLabel.setText("ANGLES");
        angleLabel.setTextSize(11f);
        angleLabel.setLetterSpacing(0.08f);
        angleLabel.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        angleLabel.setTextColor(textSecondary);
        angleLabel.setPadding(0, dp(14), 0, dp(6));
        content.addView(angleLabel);

        RadioGroup angleGroup = new RadioGroup(this);
        angleGroup.setOrientation(RadioGroup.VERTICAL);
        String[] angleNames = {"Degrees", "Radians"};
        final int[] angleIds = new int[2];
        for (int i = 0; i < 2; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(angleNames[i]);
            rb.setTextSize(16f);
            rb.setId(View.generateViewId());
            rb.setPadding(dp(4), dp(8), 0, dp(8));
            angleIds[i] = rb.getId();
            angleGroup.addView(rb, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        angleGroup.check(angleIds[isDeg ? 0 : 1]);
        angleGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup g, int checkedId) {
                boolean wantDeg = checkedId == angleIds[0];
                if (wantDeg != isDeg) {
                    isDeg = wantDeg;
                    persistSettings();
                    hapticTap();
                    calculatePreview();
                }
            }
        });
        content.addView(angleGroup);

        CheckBox tapBox = new CheckBox(this);
        tapBox.setText("Vibrate on tap");
        tapBox.setTextSize(16f);
        tapBox.setChecked(hapticTap);
        tapBox.setPadding(dp(4), dp(8), 0, dp(8));
        tapBox.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton b, boolean checked) {
                hapticTap = checked;
                persistSettings();
                if (checked) hapticTap();
            }
        });
        content.addView(tapBox);

        CheckBox errBox = new CheckBox(this);
        errBox.setText("Vibrate on error");
        errBox.setTextSize(16f);
        errBox.setChecked(hapticError);
        errBox.setPadding(dp(4), dp(8), 0, dp(8));
        errBox.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton b, boolean checked) {
                hapticError = checked;
                persistSettings();
                if (checked) hapticError();
            }
        });
        content.addView(errBox);

        CheckBox sepBox = new CheckBox(this);
        sepBox.setText("Thousands separator (1,000)");
        sepBox.setTextSize(16f);
        sepBox.setChecked(groupSeparators);
        sepBox.setPadding(dp(4), dp(8), 0, dp(8));
        sepBox.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton b, boolean checked) {
                groupSeparators = checked;
                persistSettings();
                hapticTap();
                updateDisplay();
                calculatePreview();
            }
        });
        content.addView(sepBox);

        if (memoryValue != 0) {
            TextView memInfo = new TextView(this);
            memInfo.setText("Memory: " + MathEvaluator.formatResult(memoryValue));
            memInfo.setTextSize(13f);
            memInfo.setTextColor(textSecondary);
            memInfo.setPadding(0, dp(8), 0, 0);
            content.addView(memInfo);
        }

        new AlertDialog.Builder(this)
                .setTitle("Settings")
                .setView(content)
                .setPositiveButton("Done", null)
                .show();
    }
}