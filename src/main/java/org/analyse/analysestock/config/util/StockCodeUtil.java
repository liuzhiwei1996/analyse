package org.analyse.analysestock.config.util;

import java.util.Locale;
import java.util.regex.Pattern;

public final class StockCodeUtil {

    private static final String SHENZHEN_PREFIX = "A";
    private static final String SHANGHAI_PREFIX = "B";

    private static final Pattern STOCK_CODE_PATTERN = Pattern.compile("\\d{6}");
    private static final Pattern SHENZHEN_STOCK_PATTERN =
            Pattern.compile("(?:000|001|002|003|300|301)\\d{3}");
    private static final Pattern SHANGHAI_STOCK_PATTERN =
            Pattern.compile("(?:600|601|603|605|688|689)\\d{3}");
    private static final Pattern PREFIXED_STOCK_CODE_PATTERN = Pattern.compile("[AB]\\d{6}");

    private StockCodeUtil() {
    }

    public static String addMarketPrefix(String stockCode) {
        if (stockCode == null) {
            throw new IllegalArgumentException("股票代码不能为空");
        }

        String normalizedCode = stockCode.trim().toUpperCase(Locale.ROOT);
        if (PREFIXED_STOCK_CODE_PATTERN.matcher(normalizedCode).matches()) {
            String expectedCode = addMarketPrefix(normalizedCode.substring(1));
            if (expectedCode.equals(normalizedCode)) {
                return normalizedCode;
            }
            throw new IllegalArgumentException("股票代码前缀与市场不匹配: " + stockCode);
        }
        if (!STOCK_CODE_PATTERN.matcher(normalizedCode).matches()) {
            throw new IllegalArgumentException("股票代码必须是6位数字: " + stockCode);
        }
        if (SHENZHEN_STOCK_PATTERN.matcher(normalizedCode).matches()) {
            return SHENZHEN_PREFIX + normalizedCode;
        }
        if (SHANGHAI_STOCK_PATTERN.matcher(normalizedCode).matches()) {
            return SHANGHAI_PREFIX + normalizedCode;
        }

        throw new IllegalArgumentException("无法识别的沪深A股代码: " + stockCode);
    }
}
