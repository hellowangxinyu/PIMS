package com.pengyuan.pims.common;

/**
 * 名称拼音首字母简称工具（v5.27）
 * 用于合同号/单号生成：中文取拼音首字母，英文取首字母，如「青岛海博」→ QDHB
 */
public final class PinyinShortCode {

    private PinyinShortCode() {}

    /** 中文拼音首字母映射表（GBK 区位序） */
    private static final String[] PINYIN = {"A","B","C","D","E","F","G","H","J","K","L","M","N","O","P","Q","R","S","T","W","X","Y","Z"};
    private static final int[] RANGE = {0xB0A1,0xB0C5,0xB2C1,0xB4EE,0xB6EA,0xB7A2,0xB8C1,0xB9FE,0xBBF7,0xBFA6,0xC0AC,0xC2E8,0xC4C3,0xC5B6,0xC5BE,0xC6DA,0xC8BB,0xC8F6,0xCBFA,0xCDDA,0xCEF4,0xD1B9,0xD4D1};

    /**
     * 取名称简称：中文拼音首字母 + 英文大写字母（最多 4 位）
     * 全英文/数字名称退化为前 2 位大写
     */
    public static String shortCode(String name) {
        if (name == null || name.isBlank()) return "XX";
        StringBuilder sb = new StringBuilder();
        for (char c : name.toCharArray()) {
            if (c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z') sb.append(Character.toUpperCase(c));
            else if (c >= 0x4e00 && c <= 0x9fff) sb.append(initial(c));
        }
        String result = sb.toString().replaceAll("[^A-Z]", "");
        if (result.length() >= 2) return result.substring(0, Math.min(4, result.length()));
        return name.substring(0, Math.min(2, name.length())).toUpperCase();
    }

    /** 单个汉字的拼音首字母（简化映射，多音字取常用音） */
    private static char initial(char c) {
        try {
            byte[] gbk = String.valueOf(c).getBytes("GBK");
            if (gbk.length != 2) return 'X';
            int code = ((gbk[0] & 0xFF) << 8) | (gbk[1] & 0xFF);
            for (int i = 0; i < RANGE.length; i++) {
                if (code < RANGE[i]) return PINYIN[i].charAt(0);
            }
            return 'Z';
        } catch (Exception e) {
            return 'X';
        }
    }
}
