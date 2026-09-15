package com.alisinan.minicalc;

import java.util.Locale;

public class MathEvaluator {

    public static double eval(String expression, boolean isDeg) throws Exception {
        String expr = prepareExpression(expression);
        Parser parser = new Parser(expr, isDeg);
        return parser.parse();
    }

    private static String prepareExpression(String s) {
        if (s == null) return "0";
        s = s.replace("−", "-")
             .replace("×", "*")
             .replace("÷", "/")
             .replace("π", "(pi)")
             .replace("√", "sqrt")
             .replace("∛", "cbrt");

        // Insert implicit multiplication '*' to make the grammar regular.
        // Never inside identifiers like sin/asin/sqrt, keep scientific notation
        // (1e15, 2e-3) intact, and reject malformed exponents like "5e".
        String t = s.toLowerCase(Locale.US);
        StringBuilder out = new StringBuilder();
        boolean primaryEnd = false; // previous token completed a value (num, ')', constant)
        boolean constEnd = false;   // that completed value was a constant (pi / e)
        boolean lastNumber = false; // previous value was a number literal
        int n = t.length();
        int i = 0;
        while (i < n) {
            char c = t.charAt(i);
            if (Character.isDigit(c) || c == '.') {
                int j = i;
                while (j < n && ((t.charAt(j) >= '0' && t.charAt(j) <= '9') || t.charAt(j) == '.')) j++;
                if (j < n && t.charAt(j) == 'e') {
                    int k = j + 1;
                    if (k < n && (t.charAt(k) == '+' || t.charAt(k) == '-')) k++;
                    if (k < n && Character.isDigit(t.charAt(k))) {
                        while (k < n && Character.isDigit(t.charAt(k))) k++;
                        j = k;
                    }
                }
                if (primaryEnd && !constEnd) out.append('*');
                out.append(t, i, j);
                primaryEnd = true;
                constEnd = false;
                lastNumber = true;
                i = j;
            } else if (c >= 'a' && c <= 'z') {
                int j = i;
                while (j < n && t.charAt(j) >= 'a' && t.charAt(j) <= 'z') j++;
                String id = t.substring(i, j);
                boolean isConstant = id.equals("pi") || id.equals("e");
                // "2e" is malformed scientific notation -> leave it for the parser to reject
                boolean suppress = id.equals("e") && lastNumber;
                if (primaryEnd && !suppress) out.append('*');
                out.append(id);
                primaryEnd = isConstant;
                constEnd = isConstant;
                lastNumber = false;
                i = j;
            } else if (c == '(') {
                if (primaryEnd) out.append('*');
                out.append('(');
                primaryEnd = false;
                constEnd = false;
                lastNumber = false;
                i++;
            } else if (c == ')') {
                out.append(')');
                primaryEnd = true;
                constEnd = false;
                lastNumber = false;
                i++;
            } else {
                out.append(c);
                if (c == '!') {
                    primaryEnd = true;
                    constEnd = false;
                    lastNumber = false;
                } else {
                    primaryEnd = false;
                    constEnd = false;
                    lastNumber = false;
                }
                i++;
            }
        }
        return out.toString();
    }

    public static String formatResult(double val) {
        if (Double.isNaN(val)) return "Error";
        if (Double.isInfinite(val)) return val > 0 ? "Infinity" : "-Infinity";
        if (val == 0) return "0";
        if (val == Math.rint(val) && Math.abs(val) < 1e15) {
            return String.format(Locale.US, "%.0f", val);
        }
        String res = String.format(Locale.US, "%.10f", val);
        res = res.replaceAll("0+$", "").replaceAll("\\.$", "");
        return res;
    }

    private static class Parser {
        private final String str;
        private final boolean isDeg;
        private int pos = -1;
        private int ch;

        Parser(String str, boolean isDeg) {
            this.str = str;
            this.isDeg = isDeg;
        }

        void nextChar() {
            ch = (++pos < str.length()) ? str.charAt(pos) : -1;
        }

        boolean eat(int charToEat) {
            while (ch == ' ') nextChar();
            if (ch == charToEat) {
                nextChar();
                return true;
            }
            return false;
        }

        double parse() throws Exception {
            nextChar();
            double x = parseExpression();
            if (pos < str.length()) throw new RuntimeException("Unexpected: " + (char) ch);
            return x;
        }

        // Grammar:
        // expression = term ('+' term | '-' term)*
        // term = factor ('*' factor | '/' factor | '%' factor)*
        // factor = +factor | -factor | primary ('^' factor | '!')*

        double parseExpression() throws Exception {
            double x = parseTerm();
            for (;;) {
                if (eat('+')) x += parseTerm();
                else if (eat('-')) x -= parseTerm();
                else return x;
            }
        }

        double parseTerm() throws Exception {
            double x = parseFactor();
            for (;;) {
                if (eat('*')) x *= parseFactor();
                else if (eat('/')) {
                    double div = parseFactor();
                    if (div == 0) throw new ArithmeticException("Division by zero");
                    x /= div;
                } else if (eat('%')) {
                    double mod = parseFactor();
                    x %= mod;
                } else return x;
            }
        }

        double parseFactor() throws Exception {
            if (eat('+')) return parseFactor(); // unary plus
            if (eat('-')) return -parseFactor(); // unary minus

            double x;
            int startPos = this.pos;

            if (eat('(')) {
                x = parseExpression();
                if (!eat(')')) throw new RuntimeException("Missing ')'");
            } else if ((ch >= '0' && ch <= '9') || ch == '.') {
                while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                if (ch == 'e' || ch == 'E') {
                    int save = pos;
                    nextChar();
                    if (ch == '+' || ch == '-') nextChar();
                    if (ch >= '0' && ch <= '9') {
                        while (ch >= '0' && ch <= '9') nextChar();
                    } else {
                        pos = save;
                        ch = (save < str.length()) ? str.charAt(save) : -1;
                    }
                }
                x = Double.parseDouble(str.substring(startPos, this.pos));
            } else if (ch >= 'a' && ch <= 'z') {
                while (ch >= 'a' && ch <= 'z') nextChar();
                String func = str.substring(startPos, this.pos);

                if (func.equals("pi")) {
                    x = Math.PI;
                } else if (func.equals("e")) {
                    x = Math.E;
                } else {
                    if (eat('(')) {
                        x = parseExpression();
                        if (!eat(')')) throw new RuntimeException("Missing ')' after function " + func);
                    } else {
                        x = parseFactor();
                    }

                    switch (func) {
                        case "sin":
                            x = Math.sin(isDeg ? Math.toRadians(x) : x);
                            break;
                        case "cos":
                            x = Math.cos(isDeg ? Math.toRadians(x) : x);
                            break;
                        case "tan":
                            x = Math.tan(isDeg ? Math.toRadians(x) : x);
                            break;
                        case "asin":
                            x = Math.asin(x);
                            if (isDeg) x = Math.toDegrees(x);
                            break;
                        case "acos":
                            x = Math.acos(x);
                            if (isDeg) x = Math.toDegrees(x);
                            break;
                        case "atan":
                            x = Math.atan(x);
                            if (isDeg) x = Math.toDegrees(x);
                            break;
                        case "sqrt":
                            if (x < 0) throw new ArithmeticException("Invalid sqrt input");
                            x = Math.sqrt(x);
                            break;
                        case "cbrt":
                            x = Math.cbrt(x);
                            break;
                        case "ln":
                            if (x <= 0) throw new ArithmeticException("Invalid ln input");
                            x = Math.log(x);
                            break;
                        case "log":
                            if (x <= 0) throw new ArithmeticException("Invalid log input");
                            x = Math.log10(x);
                            break;
                        case "abs":
                            x = Math.abs(x);
                            break;
                        default:
                            throw new RuntimeException("Unknown function: " + func);
                    }
                }
            } else {
                throw new RuntimeException("Unexpected character: " + (char) ch);
            }

            // Handle power '^' and factorial '!'
            while (true) {
                if (eat('^')) {
                    double pow = parseFactor();
                    x = Math.pow(x, pow);
                } else if (eat('!')) {
                    x = factorial((long) x);
                } else {
                    break;
                }
            }

            return x;
        }

        private double factorial(long n) {
            if (n < 0) throw new ArithmeticException("Negative factorial");
            if (n > 170) return Double.POSITIVE_INFINITY;
            double res = 1;
            for (int i = 2; i <= n; i++) {
                res *= i;
            }
            return res;
        }
    }
}
