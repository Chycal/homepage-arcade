package com.homepage.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 24点验证接口单元测试：表达式求值、用牌规则、安全拦截。
 * 直接实例化控制器（verify 不依赖 Spring 上下文与注入）。
 */
class Game24ControllerTest {

    private final Game24Controller controller = new Game24Controller();

    private Map<String, Object> verify(String expression, Integer... cards) {
        Map<String, Object> body = new HashMap<>();
        body.put("expression", expression);
        body.put("cards", Arrays.asList(cards));
        return controller.verify(body);
    }

    private Map<String, Object> verifyOk(String expression, Integer... cards) {
        Map<String, Object> resp = verify(expression, cards);
        assertThat(resp.get("correct")).as("表达式: %s -> %s", expression, resp).isEqualTo(true);
        return resp;
    }

    @Nested
    @DisplayName("正确求值")
    class CorrectEvaluation {

        @Test
        @DisplayName("经典解法（每张牌恰好一次）")
        void classicSolutions() {
            verifyOk("(10-4)*4*1", 10, 4, 4, 1);   // = 24
            verifyOk("4*6*(2-1)", 4, 6, 2, 1);     // = 24
            verifyOk("(5-1/5)*5", 5, 5, 5, 1);     // = 24，中间结果为小数
            verifyOk("6/(5/4-1)", 6, 5, 4, 1);     // = 24，除数为小数
        }

        @Test
        @DisplayName("运算优先级正确：乘除先于加减")
        void operatorPrecedence() {
            // 2+2*11 = 24；若错误地从左到右计算会得 48
            Map<String, Object> resp = verifyOk("2+2*11", 2, 2, 11, 13);
            assertThat(((Number) resp.get("result")).doubleValue()).isEqualTo(24.0);
        }

        @Test
        @DisplayName("括号改变优先级")
        void parentheses() {
            verifyOk("(2+2)*6*1", 2, 2, 6, 1);
        }

        @Test
        @DisplayName("全角符号自动规范化：× − －")
        void fullwidthNormalization() {
            verifyOk("4×6+9-9", 4, 6, 9, 9);
            verifyOk("(10−4)×4×1", 10, 4, 4, 1);
        }

        @Test
        @DisplayName("空白符被忽略")
        void whitespaceIgnored() {
            verifyOk("4 * 6 * ( 3 - 2 )", 4, 6, 2, 3);
        }

        @Test
        @DisplayName("不等于 24 时 correct=false 并返回实际结果")
        void wrongResultReported() {
            Map<String, Object> resp = verify("4*7-1-1", 4, 7, 1, 1);
            assertThat(resp.get("correct")).isEqualTo(false);
            assertThat(((Number) resp.get("result")).doubleValue()).isEqualTo(26.0);
        }
    }

    @Nested
    @DisplayName("用牌规则")
    class CardUsageRules {

        @Test
        @DisplayName("使用了手牌里没有的数字")
        void rejectForeignNumber() {
            Map<String, Object> resp = verify("4*6", 4, 5, 2, 3);
            assertThat(resp.get("correct")).isEqualTo(false);
            assertThat((String) resp.get("message")).contains("不匹配");
        }

        @Test
        @DisplayName("没有用完四张牌")
        void rejectUnusedCard() {
            Map<String, Object> resp = verify("4*6", 4, 6, 6, 6);
            assertThat(resp.get("correct")).isEqualTo(false);
            assertThat((String) resp.get("message")).contains("没有用完");
        }

        @Test
        @DisplayName("某张牌用了两次（超出多集合）")
        void rejectDuplicateUse() {
            Map<String, Object> resp = verify("6*6", 6, 6, 2, 3);
            assertThat(resp.get("correct")).isEqualTo(false);
        }

        @Test
        @DisplayName("重复数字按多集合匹配：两 张 11 两张 1")
        void multisetMatching() {
            verifyOk("11+11+1+1", 11, 11, 1, 1);   // = 24
            Map<String, Object> bad = verify("11+11+2", 11, 11, 1, 1);
            assertThat(bad.get("correct")).isEqualTo(false);
        }
    }

    @Nested
    @DisplayName("安全与健壮性")
    class SecurityAndRobustness {

        @Test
        @DisplayName("拒绝字母（代码注入尝试）")
        void rejectLetters() {
            Map<String, Object> resp = verify("System.exit(1)", 1, 2, 3, 4);
            assertThat(resp.get("correct")).isEqualTo(false);
            assertThat((String) resp.get("message")).contains("非法字符");
        }

        @Test
        @DisplayName("拒绝其他特殊符号")
        void rejectSpecialChars() {
            for (String expr : new String[]{"4;6", "4|6", "4&6", "4`6`", "4$6", "4>6", "4'6", "4\"6"}) {
                Map<String, Object> resp = verify(expr, 4, 6, 2, 3);
                assertThat(resp.get("correct")).as("表达式: %s -> %s", expr, resp).isEqualTo(false);
            }
        }

        @Test
        @DisplayName("拒绝除以零")
        void rejectDivisionByZero() {
            Map<String, Object> resp = verify("(4*6)/(2-2)", 4, 6, 2, 2);
            assertThat(resp.get("correct")).isEqualTo(false);
            assertThat((String) resp.get("message")).contains("除数不能为0");
        }

        @Test
        @DisplayName("拒绝缺少右括号")
        void rejectUnclosedParen() {
            Map<String, Object> resp = verify("(4*6+2*3", 4, 6, 2, 3);
            assertThat(resp.get("correct")).isEqualTo(false);
        }

        @Test
        @DisplayName("拒绝末尾多余字符")
        void rejectTrailingGarbage() {
            Map<String, Object> resp = verify("4*6*2/3)", 4, 6, 2, 3);
            assertThat(resp.get("correct")).isEqualTo(false);
        }

        @Test
        @DisplayName("参数缺失或不完整")
        void rejectIncompleteParams() {
            assertThat(controller.verify(new HashMap<>()).get("correct")).isEqualTo(false);
            assertThat(verify("", 1, 2, 3, 4).get("correct")).isEqualTo(false);
            assertThat(verify("   ", 1, 2, 3, 4).get("correct")).isEqualTo(false);
            assertThat(verify("4*6", 4, 6).get("correct")).isEqualTo(false);
            Map<String, Object> noCards = new HashMap<>();
            noCards.put("expression", "4*6");
            assertThat(controller.verify(noCards).get("correct")).isEqualTo(false);
        }
    }

    @Nested
    @DisplayName("发牌")
    class Deal {

        @Test
        @DisplayName("每次发4张，取值范围 1-13")
        void dealReturnsFourCardsInRange() {
            for (int i = 0; i < 100; i++) {
                Map<String, Object> resp = controller.deal();
                int[] cards = (int[]) resp.get("cards");
                assertThat(cards).hasSize(4);
                for (int c : cards) {
                    assertThat(c).isBetween(1, 13);
                }
            }
        }
    }
}
