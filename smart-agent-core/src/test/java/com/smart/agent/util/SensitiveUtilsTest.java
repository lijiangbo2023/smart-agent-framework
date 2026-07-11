package com.smart.agent.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class SensitiveUtilsTest {

    // ========== null / empty ==========

    @Test
    void mask_null_returnsNull() {
        assertNull(SensitiveUtils.mask(null));
    }

    @Test
    void mask_empty_returnsEmpty() {
        assertEquals("", SensitiveUtils.mask(""));
    }

    @Test
    void mask_noSensitiveData_unchanged() {
        String text = "今天天气不错，温度25度";
        assertEquals(text, SensitiveUtils.mask(text));
    }

    // ========== phone masking ==========

    @Test
    void mask_phone_basic() {
        assertEquals("我的手机号是138****5678", SensitiveUtils.mask("我的手机号是13812345678"));
    }

    @Test
    void mask_phone_allPrefixes() {
        assertEquals("130****0000", SensitiveUtils.mask("13000000000"));
        assertEquals("155****1234", SensitiveUtils.mask("15512341234"));
        assertEquals("189****9999", SensitiveUtils.mask("18999999999"));
        assertEquals("166****8888", SensitiveUtils.mask("16688888888"));
        assertEquals("177****7777", SensitiveUtils.mask("17777777777"));
    }

    @Test
    void mask_phone_multiple() {
        String input = "联系人A: 13800001111, 联系人B: 15900002222";
        String expected = "联系人A: 138****1111, 联系人B: 159****2222";
        assertEquals(expected, SensitiveUtils.mask(input));
    }

    @Test
    void mask_phone_notStartWith1_noMask() {
        assertEquals("电话: 23456789012", SensitiveUtils.mask("电话: 23456789012"));
    }

    // ========== ID card masking ==========

    @Test
    void mask_idCard_18digits() {
        assertEquals("身份证: 110101**********1234", SensitiveUtils.mask("身份证: 110101199001011234"));
    }

    @Test
    void mask_idCard_endsWithX() {
        assertEquals("证件号 320106**********123X", SensitiveUtils.mask("证件号 32010619900101123X"));
    }

    @Test
    void mask_idCard_endsWithLowerX() {
        assertEquals("证件号 320106**********123x", SensitiveUtils.mask("证件号 32010619900101123x"));
    }

    @Test
    void mask_idCard_multiple() {
        String input = "甲: 110101199001011234 乙: 32010619900101123X";
        String expected = "甲: 110101**********1234 乙: 320106**********123X";
        assertEquals(expected, SensitiveUtils.mask(input));
    }

    // ========== mixed ==========

    @Test
    void mask_phoneAndIdCard_together() {
        String input = "姓名张三，手机13812345678，身份证110101199001011234";
        String expected = "姓名张三，手机138****5678，身份证110101**********1234";
        assertEquals(expected, SensitiveUtils.mask(input));
    }

    // ========== bank card masking ==========

    @Test
    void mask_bankCard_16digits() {
        String input = "银行卡号: 6228480088889999";
        String result = SensitiveUtils.mask(input);
        assertTrue(result.startsWith("银行卡号: 622848"));
        assertTrue(result.contains("****"));
        assertTrue(result.endsWith("9999"));
    }

    @Test
    void mask_bankCard_19digits() {
        String input = "卡号 6222021234567890123";
        String result = SensitiveUtils.mask(input);
        assertTrue(result.startsWith("卡号 622202"));
        assertTrue(result.contains("****"));
        assertTrue(result.endsWith("0123"));
    }

    // ========== email masking ==========

    @Test
    void mask_email_basic() {
        String input = "邮箱: zhangsan@example.com";
        String result = SensitiveUtils.mask(input);
        assertTrue(result.contains("z***n@example.com"));
    }

    @Test
    void mask_email_shortLocal() {
        String input = "邮箱: ab@test.com";
        String result = SensitiveUtils.mask(input);
        assertTrue(result.contains("a***@test.com"));
    }

    @Test
    void mask_email_multiple() {
        String input = "联系 foo@bar.com 或 admin@site.org";
        String result = SensitiveUtils.mask(input);
        assertTrue(result.contains("f***o@bar.com"));
        assertTrue(result.contains("a***n@site.org"));
    }

    // ========== token masking ==========

    @Test
    void mask_token_skPrefix() {
        String input = "API Key: sk-abcdefghijklmnopqrstuvwxyz1234";
        String result = SensitiveUtils.mask(input);
        assertTrue(result.contains("sk-a****1234"));
    }

    @Test
    void mask_token_bearerPrefix() {
        // Bearer token without space (pattern matches Bearer<token>)
        String input = "token: Bearerabcdefghijklmnopqrstuvwxyz";
        String result = SensitiveUtils.mask(input);
        assertTrue(result.contains("Bear****wxyz"), "Expected Bear****wxyz but got: " + result);
    }

    // ========== edge cases ==========

    @Test
    void mask_partialPhone_noMask() {
        assertEquals("号码 1381234", SensitiveUtils.mask("号码 1381234"));
    }

    @Test
    void mask_partialIdCard_noMask() {
        assertEquals("证件 11010119900101", SensitiveUtils.mask("证件 11010119900101"));
    }
}
