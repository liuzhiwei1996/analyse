package org.analyse.analysestock.realtimecandidate.calculator;

import org.analyse.analysestock.analysis.entity.PubStockInfo;
import org.analyse.analysestock.realtimecandidate.dto.FactorSnapshot;
import org.analyse.analysestock.realtimecandidate.dto.MarketContext;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MarketContextCalculator {

    public MarketContext calculate(
            List<FactorSnapshot> validSnapshots,
            List<PubStockInfo> securityInfos
    ) {
        MarketContext context = new MarketContext();

        if (validSnapshots == null || validSnapshots.isEmpty()) {
            return context;
        }

        Map<String, PubStockInfo> infoMap = securityInfos.stream()
                .collect(Collectors.toMap(PubStockInfo::getSymbol, i -> i, (a, b) -> a));

        // 9.1 市场宽度
        long upCount = validSnapshots.stream()
                .filter(s -> s.getPrice1430().compareTo(s.getClosePrevious()) > 0)
                .count();
        context.setMarketBreadth(BigDecimal.valueOf(upCount).divide(BigDecimal.valueOf(validSnapshots.size()), 4, RoundingMode.HALF_UP));

        // 9.2 市场平均涨幅
        BigDecimal totalReturn = validSnapshots.stream()
                .map(FactorSnapshot::getReturnTo1430)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        context.setMarketReturn1430(totalReturn.divide(BigDecimal.valueOf(validSnapshots.size()), 6, RoundingMode.HALF_UP));

        // 9.3 板块强度 & 9.4 板块宽度
        Map<String, List<FactorSnapshot>> sectorGroups = validSnapshots.stream()
                .filter(s -> infoMap.containsKey(s.getStockCode()))
                .collect(Collectors.groupingBy(s -> {
                    String sector = infoMap.get(s.getStockCode()).getSector();
                    return sector == null ? "UNKNOWN" : sector;
                }));

        Map<String, BigDecimal> sectorStrength = new HashMap<>();
        Map<String, BigDecimal> sectorBreadth = new HashMap<>();

        for (Map.Entry<String, List<FactorSnapshot>> entry : sectorGroups.entrySet()) {
            List<FactorSnapshot> sectorStocks = entry.getValue();
            BigDecimal sectorTotalReturn = sectorStocks.stream()
                    .map(FactorSnapshot::getReturnTo1430)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            sectorStrength.put(entry.getKey(), sectorTotalReturn.divide(BigDecimal.valueOf(sectorStocks.size()), 6, RoundingMode.HALF_UP));

            long sectorUpCount = sectorStocks.stream()
                    .filter(s -> s.getPrice1430().compareTo(s.getClosePrevious()) > 0)
                    .count();
            sectorBreadth.put(entry.getKey(), BigDecimal.valueOf(sectorUpCount).divide(BigDecimal.valueOf(sectorStocks.size()), 4, RoundingMode.HALF_UP));
        }

        context.setSectorStrength(sectorStrength);
        context.setSectorBreadth(sectorBreadth);

        // 9.6 市场环境评分 regimeScore (简化实现，因为归一化比较复杂，暂定为市场宽度的线性映射或简单组合)
        // 文档中要求: 0.4 * marketBreadthNorm + 0.3 * sectorBreadthNorm + 0.2 * marketReturnNorm + 0.1 * relativeStrengthNorm
        // 这里由于是实时计算，Norm 可能需要历史分布。暂时简单返回 marketBreadth 作为 regimeScore 的基础。
        context.setRegimeScore(context.getMarketBreadth());

        return context;
    }
}
