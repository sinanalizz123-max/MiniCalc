package com.alisinan.minicalc;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.CheckBox;
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
    private static final int MAX_HISTORY = 60;

    private static final List<String> OPS = Arrays.asList("÷", "×", "−", "+", "^", "%");
    private static final List<String> FUNCS = Arrays.asList(
            "sin", "cos", "tan", "asin", "acos", "atan", "sqrt", "cbrt", "ln", "log");

    private TextView tvExpression;
    private TextView tvResult;
    private ScrollView displayScroll;
    private LinearLayout historyArea;
    private LinearLayout sciKeypad;
    private TextView btnDegRad;
    private TextView btnSciToggle;

    private String currentExpr = "";
    private boolean isDeg = true;
    private boolean isSciVisible = false;

    private int themeMode = 0;
    private boolean hapticTap = true;
    private boolean hapticError = true;

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

    private void vibrate(long ms) {
        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null) return;
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(ms);
            }
        } catch (Exception ignored) {
        }
    }

    private void hapticTap() {
        if (hapticTap) vibrate(8L);
    }

    private void hapticError() {
        if (hapticError) vibrate(60L);
    }

    private void loadSettings() {
        themeMode = prefs.getInt(KEY_THEME, 0);
        hapticTap = prefs.getBoolean(KEY_HAPTIC_TAP, true);
        hapticError = prefs.getBoolean(KEY_HAPTIC_ERR, true);
    }

    private void persistSettings() {
        prefs.edit()
                .putInt(KEY_THEME, themeMode)
                .putBoolean(KEY_HAPTIC_TAP, hapticTap)
                .putBoolean(KEY_HAPTIC_ERR, hapticError)
                .apply();
    }

    private int dp(int v) {
        return Math.round(v * density);
    }

    private RippleDrawable roundRect(int fill, int radiusDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(dp(radiusDp));
        shape.setColor(fill);
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), shape, null);
    }

    private TextView key(String label, int bgColor, int fgColor, float sizeSp, boolean bold) {
        TextView t = new TextView(this);
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
        styleChip(btnDegRad, isDeg);
        styleChip(btnSciToggle, isSciVisible);
    }

    private void addKeyRow(LinearLayout parent, String[] keys, boolean sci) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int gap = dp(4);
        row.setPadding(gap, gap, gap, gap);

        for (final String k : keys) {
            TextView b;
            if (sci) {
                b = key(k, sciBg, sciFg, 13f, true);
            } else if ("=".equals(k)) {
                b = key(k, eqBg, eqFg, 26f, true);
            } else if ("÷".equals(k) || "×".equals(k) || "−".equals(k) || "+".equals(k)) {
                b = key(k, opBg, opFg, 22f, true);
            } else if ("C".equals(k) || "⌫".equals(k)) {
                b = key(k, utilBg, dangerFg, 20f, true);
            } else if ("(".equals(k) || ")".equals(k) || "%".equals(k)) {
                b = key(k, utilBg, utilFg, 20f, false);
            } else {
                b = key(k, keyBg, keyFg, 21f, false);
            }

            LinearLayout.LayoutParams lp =
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            lp.setMargins(dp(4), dp(4), dp(4), dp(4));
            b.setLayoutParams(lp);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if ("=".equals(k)) {
                        handleEquals();
                    } else {
                        handleKeyTap(k);
                    }
                }
            });
            row.addView(b);
        }
        parent.addView(row,
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    private void buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER);
        topBar.setPadding(dp(12), dp(12), dp(12), dp(2));

        btnDegRad = chip("DEG");
        btnSciToggle = chip("SCI");
        TextView btnSettings = chip("SET");
        btnDegRad.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleDegRad();
            }
        });
        btnSciToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleSciPanel();
            }
        });
        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSettings();
            }
        });
        refreshChips();

        for (TextView c : new TextView[]{btnDegRad, btnSciToggle, btnSettings}) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(40), 1f);
            lp.setMargins(dp(4), 0, dp(4), 0);
            topBar.addView(c, lp);
        }
        root.addView(topBar,
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        displayScroll = new ScrollView(this);
        displayScroll.setBackgroundColor(bg);
        displayScroll.setVerticalScrollBarEnabled(false);

        LinearLayout displayContent = new LinearLayout(this);
        displayContent.setOrientation(LinearLayout.VERTICAL);
        displayContent.setPadding(dp(20), dp(10), dp(20), dp(10));

        historyArea = new LinearLayout(this);
        historyArea.setOrientation(LinearLayout.VERTICAL);
        displayContent.addView(historyArea,
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

        tvExpression = new TextView(this);
        tvExpression.setText("0");
        tvExpression.setTextColor(textPrimary);
        tvExpression.setTextSize(40f);
        tvExpression.setGravity(Gravity.END);
        tvExpression.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        tvExpression.setMaxLines(3);
        tvExpression.setEllipsize(TextUtils.TruncateAt.END);

        tvResult = new TextView(this);
        tvResult.setText("");
        tvResult.setTextColor(eqBg);
        tvResult.setTextSize(22f);
        tvResult.setGravity(Gravity.END);
        tvResult.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tvResult.setPadding(0, dp(6), 0, 0);

        LinearLayout bottomGroup = new LinearLayout(this);
        bottomGroup.setOrientation(LinearLayout.VERTICAL);
        bottomGroup.setGravity(Gravity.END);
        bottomGroup.setPadding(0, dp(14), 0, 0);
        bottomGroup.addView(tvExpression,
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
        bottomGroup.addView(tvResult,
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
        displayContent.addView(bottomGroup,
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

        displayScroll.addView(displayContent);
        root.addView(displayScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 2f));

        LinearLayout keypadsArea = new LinearLayout(this);
        keypadsArea.setOrientation(LinearLayout.VERTICAL);

        sciKeypad = new LinearLayout(this);
        sciKeypad.setOrientation(LinearLayout.VERTICAL);
        sciKeypad.setVisibility(View.GONE);
        String[][] sciRows = {
                {"sin", "cos", "tan", "π"},
                {"asin", "acos", "atan", "e"},
                {"√", "∛", "x^y", "!"},
                {"ln", "log", "(", ")"}
        };
        for (String[] r : sciRows) addKeyRow(sciKeypad, r, true);
        keypadsArea.addView(sciKeypad, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 2f));

        LinearLayout keypad = new LinearLayout(this);
        keypad.setOrientation(LinearLayout.VERTICAL);
        String[][] rows = {
                {"C", "(", ")", "÷"},
                {"7", "8", "9", "×"},
                {"4", "5", "6", "−"},
                {"1", "2", "3", "+"},
                {"%", "0", ".", "⌫"},
                {"="}
        };
        for (String[] r : rows) addKeyRow(keypad, r, false);
        keypadsArea.addView(keypad, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 5f));

        root.addView(keypadsArea, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 5f));

        setContentView(root);
        updateDisplay();
    }

    private boolean endsWithAny(String s, String... suffixes) {
        for (String suffix : suffixes) {
            if (s.endsWith(suffix)) return true;
        }
        return false;
    }

    private boolean canAppend(String k) {
        String e = currentExpr;
        String last = e.isEmpty() ? "" : e.substring(e.length() - 1);

        if (FUNCS.contains(k)) return e.isEmpty() || !last.equals(")");

        if (OPS.contains(k)) {
            if (e.isEmpty()) return "−".equals(k);
            if (last.equals("(")) return "−".equals(k);
            if (OPS.contains(last)) return "−".equals(k);
            return !last.equals(".");
        }
        if ("0123456789".contains(k)) {
            return !last.equals(")") && !last.equals("!") && !last.equals("π");
        }
        if (".".equals(k)) {
            return !e.isEmpty() && !OPS.contains(last) && !last.equals("(")
                    && !last.equals(")") && !currentNumberContainsDot(e);
        }
        if ("(".equals(k)) {
            return e.isEmpty() || OPS.contains(last) || last.equals("(") || last.equals(")")
                    || last.equals("!") || last.equals("π") || last.equals("e");
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
        } else if ("⌫".equals(k)) {
            if (!currentExpr.isEmpty()) {
                String e = currentExpr;
                if (endsWithAny(e, "asin(", "acos(", "atan(")) {
                    currentExpr = e.substring(0, e.length() - 5);
                } else if (endsWithAny(e, "sqrt(", "cbrt(")) {
                    currentExpr = e.substring(0, e.length() - 5);
                } else if (endsWithAny(e, "sin(", "cos(", "tan(", "log(")) {
                    currentExpr = e.substring(0, e.length() - 4);
                } else if (e.endsWith("ln(")) {
                    currentExpr = e.substring(0, e.length() - 3);
                } else {
                    currentExpr = e.substring(0, e.length() - 1);
                }
            }
        } else if ("x^y".equals(k)) {
            if (canAppend("^")) currentExpr += "^";
        } else if (FUNCS.contains(k)) {
            if (canAppend(k)) currentExpr += k + "(";
        } else {
            if (canAppend(k)) currentExpr += k;
        }
        hapticTap();
        updateDisplay();
        calculatePreview();
    }

    private void calculatePreview() {
        if (currentExpr.trim().isEmpty()) {
            tvResult.setText("");
            return;
        }
        try {
            double res = MathEvaluator.eval(currentExpr, isDeg);
            tvResult.setText("= " + MathEvaluator.formatResult(res));
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
            currentExpr = formatted;
            tvResult.setText("");
            hapticTap();
            updateDisplay();
            rebuildHistory();
            scrollToCurrent();
        } catch (Exception e) {
            tvResult.setText("Error");
            hapticError();
            Toast.makeText(this, "Invalid expression", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateDisplay() {
        tvExpression.setText(currentExpr.isEmpty() ? "0" : currentExpr);
    }

    private void scrollToCurrent() {
        displayScroll.post(new Runnable() {
            @Override
            public void run() {
                displayScroll.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    private void toggleDegRad() {
        isDeg = !isDeg;
        btnDegRad.setText(isDeg ? "DEG" : "RAD");
        refreshChips();
        hapticTap();
        Toast.makeText(this, isDeg ? "Degrees" : "Radians", Toast.LENGTH_SHORT).show();
        calculatePreview();
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
                    updateDisplay();
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

        new AlertDialog.Builder(this)
                .setTitle("Settings")
                .setView(content)
                .setPositiveButton("Done", null)
                .show();
    }
}