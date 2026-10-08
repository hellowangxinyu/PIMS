package com.pengyuan.pims.common;

/**
 * 期间（YYYY-MM）统一严格校验。
 * v11.8 修复：原各服务用 \\d{4}-\\d{2} 正则，放行 2026-13 / 2026-99 等非法月份——
 * 工资单可按 2026-13 建单；折旧/月度成本传非法月份时 LocalDate 越界直接 500 裸崩。
 */
public final class PeriodValidator {

    private PeriodValidator() {}

    /**
     * 校验期间格式与月份范围（01-12），非法时抛 IllegalArgumentException（走全局 400）。
     *
     * @param period 期间字符串
     * @param label  报错文案前缀（如「期间」「月份」），保持各接口原有文案
     * @return 校验通过的期间
     */
    public static String requireValid(String period, String label) {
        if (period == null || !period.matches("\\d{4}-(0[1-9]|1[0-2])")) {
            throw new IllegalArgumentException(label + "格式应为 YYYY-MM");
        }
        return period;
    }
}
