package com.homepage.controller;

import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 24点游戏控制器 - 随机发牌 + 表达式验证
 */
@RestController
@RequestMapping("/api/game24")
public class Game24Controller {

    private final Random rng = new Random();

    /** 发牌：返回4张随机牌 (1-13, A=1 J=11 Q=12 K=13) */
    @GetMapping("/deal")
    public Map<String, Object> deal() {
        int[] cards = new int[4];
        for (int i = 0; i < 4; i++) {
            cards[i] = rng.nextInt(13) + 1;
        }
        Map<String, Object> result = new HashMap<>();
        result.put("cards", cards);
        return result;
    }

    /** 验证表达式：检查是否用尽了四张牌且结果等于24 */
    @PostMapping("/verify")
    @SuppressWarnings("unchecked")
    public Map<String, Object> verify(@RequestBody Map<String, Object> body) {
        String raw = (String) body.get("expression");
        List<Integer> cardList = (List<Integer>) body.get("cards");

        if (raw == null || raw.trim().isEmpty() || cardList == null || cardList.size() != 4) {
            return error("参数不完整");
        }

        // 0. 规范化：全角符号 → 半角
        String expr = raw.replace('\u00D7', '*')   // × → *
                         .replace('\u00F7', '/')   // ÷ → /
                         .replace('\u2212', '-')   // − → -
                         .replace('\uFF0D', '-')   // － → -
                         .replaceAll("\\s+", "");

        // 1. 提取表达式中的数字
        List<Integer> exprNums = extractNumbers(expr);
        if (exprNums == null) {
            return error("表达式中包含非法字符，仅允许数字和 + - * / ( )");
        }

        // 2. 检查是否使用了全部四张牌（多集合相等）
        List<Integer> cardsCopy = new ArrayList<>(cardList);
        for (int n : exprNums) {
            if (!cardsCopy.remove(Integer.valueOf(n))) {
                return error("使用的数字与手牌不匹配（请使用全部四张牌，每张恰好一次）");
            }
        }
        if (!cardsCopy.isEmpty()) {
            return error("没有用完四张牌，每张牌必须使用恰好一次");
        }

        // 3. 计算表达式结果
        double result;
        try {
            ExprEvaluator ev = new ExprEvaluator(expr);
            result = ev.eval();
        } catch (Exception e) {
            return error("算式格式错误: " + e.getMessage());
        }

        boolean ok = Math.abs(result - 24.0) < 1e-9;
        Map<String, Object> resp = new HashMap<>();
        resp.put("correct", ok);
        resp.put("result", result);
        resp.put("message", ok ? "恭喜，等于 24！" : ("结果 = " + nice(result) + "，不等于 24"));
        return resp;
    }

    // ==================== 独立的递归下降求值器（每次 new，线程安全）====================

    private static class ExprEvaluator {
        private final String s;
        private int pos;
        private char ch;

        ExprEvaluator(String s) {
            this.s = s;
            this.pos = 0;
            nextChar();
        }

        double eval() {
            double v = parseExpression();
            if (pos < s.length()) {
                throw new ArithmeticException("存在未处理的字符 '" + s.substring(pos).charAt(0) + "'");
            }
            return v;
        }

        private void nextChar() {
            ch = pos < s.length() ? s.charAt(pos++) : (char) -1;
        }

        private boolean eat(char c) {
            if (ch == c) { nextChar(); return true; }
            return false;
        }

        private double parseExpression() {
            double x = parseTerm();
            while (true) {
                if (eat('+')) x += parseTerm();
                else if (eat('-')) x -= parseTerm();
                else return x;
            }
        }

        private double parseTerm() {
            double x = parseFactor();
            while (true) {
                if (eat('*')) x *= parseFactor();
                else if (eat('/')) {
                    double d = parseFactor();
                    if (Math.abs(d) < 1e-15) throw new ArithmeticException("除数不能为0");
                    x /= d;
                } else return x;
            }
        }

        private double parseFactor() {
            if (eat('+')) return parseFactor();
            if (eat('-')) return -parseFactor();
            if (eat('(')) {
                double x = parseExpression();
                if (!eat(')')) throw new ArithmeticException("缺少右括号");
                return x;
            }
            return parseNumber();
        }

        private double parseNumber() {
            int start = pos - 1;
            if (ch < '0' || ch > '9') {
                throw new ArithmeticException("此处需要数字，但遇到了 '" + (ch == (char)-1 ? "末尾" : String.valueOf(ch)) + "'");
            }
            while (ch >= '0' && ch <= '9') nextChar();
            // 当数字在表达式末尾时 ch==EOF，pos 未被 nextChar 递增，需修正 endIndex
            int endIndex = (ch == (char) -1) ? pos : pos - 1;
            String numStr = s.substring(start, endIndex);
            if (numStr.isEmpty()) {
                throw new ArithmeticException("无法识别数字");
            }
            try {
                return Double.parseDouble(numStr);
            } catch (NumberFormatException e) {
                throw new ArithmeticException("无效数字: " + numStr);
            }
        }
    }

    // ==================== 数字提取 ====================

    private List<Integer> extractNumbers(String expr) {
        List<Integer> nums = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < expr.length(); i++) {
            char c = expr.charAt(i);
            if (c >= '0' && c <= '9') {
                buf.append(c);
            } else if ("+-*/()".indexOf(c) >= 0) {
                if (buf.length() > 0) {
                    nums.add(Integer.parseInt(buf.toString()));
                    buf.setLength(0);
                }
            } else {
                return null; // 非法字符
            }
        }
        if (buf.length() > 0) nums.add(Integer.parseInt(buf.toString()));
        return nums;
    }

    // ==================== 辅助 ====================

    private Map<String, Object> error(String msg) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("correct", false);
        resp.put("message", msg);
        return resp;
    }

    private static String nice(double v) {
        return v == (long) v ? String.valueOf((long) v) : String.valueOf(v);
    }
}
