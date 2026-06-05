package org.analyse.analysestock.realtimecandidate.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class PercentileUtils {

    /**
     * 计算百分位排名 (0.0 - 1.0)
     * 如果值相同，则排名相同。
     */
    public static <T extends Comparable<T>> List<BigDecimal> calculatePercentileRanks(List<T> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }

        List<T> sortedValues = new ArrayList<>(values);
        sortedValues.removeIf(Objects::isNull);
        Collections.sort(sortedValues);

        List<BigDecimal> ranks = new ArrayList<>(values.size());
        int n = sortedValues.size();

        for (T value : values) {
            if (value == null) {
                ranks.add(null);
                continue;
            }
            // 找到第一个大于等于该值的位置
            int index = Collections.binarySearch(sortedValues, value);
            // 处理重复值，取最大索引
            while (index + 1 < n && sortedValues.get(index + 1).equals(value)) {
                index++;
            }

            if (n <= 1) {
                ranks.add(BigDecimal.ONE);
            } else {
                BigDecimal rank = BigDecimal.valueOf(index).divide(BigDecimal.valueOf(n - 1), 4, RoundingMode.HALF_UP);
                ranks.add(rank);
            }
        }
        return ranks;
    }
}
