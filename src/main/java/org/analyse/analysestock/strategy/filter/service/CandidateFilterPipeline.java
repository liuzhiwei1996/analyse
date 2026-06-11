package org.analyse.analysestock.strategy.filter.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.analyse.analysestock.analysis.entity.PubStockInfo;
import org.analyse.analysestock.analysis.entity.StockDataDailyAll;
import org.analyse.analysestock.analysis.entity.StockMinuteData;
import org.analyse.analysestock.analysis.mapper.PubStockInfoMapper;
import org.analyse.analysestock.analysis.mapper.StockDataDailyAllMapper;
import org.analyse.analysestock.analysis.mapper.StockMinuteDataMapper;
import org.analyse.analysestock.strategy.filter.entity.CandidateFilterResult;
import org.analyse.analysestock.strategy.filter.mapper.CandidateFilterResultMapper;
import org.analyse.analysestock.strategy.market.entity.MarketRegime;
import org.analyse.analysestock.strategy.market.entity.MarketRegimeSnapshot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CandidateFilterPipeline {

    @Autowired
    private PubStockInfoMapper pubStockInfoMapper;

    @Autowired
    private StockDataDailyAllMapper stockDataDailyAllMapper;

    @Autowired
    private StockMinuteDataMapper stockMinuteDataMapper;

    @Autowired
    private CandidateFilterResultMapper candidateFilterResultMapper;

    @Autowired
    private List<StockFilter> filters;

    public List<CandidateFilterResult> run(LocalDate tradeDate, MarketRegimeSnapshot regimeSnapshot) {
        if (regimeSnapshot != null && MarketRegime.WEAK.name().equals(regimeSnapshot.getRegime())) {
            return Collections.emptyList();
        }
        List<PubStockInfo> stockInfos = pubStockInfoMapper.selectList(new LambdaQueryWrapper<PubStockInfo>());
        if (CollectionUtils.isEmpty(stockInfos)) {
            return Collections.emptyList();
        }
        Set<String> stockCodes = stockInfos.stream()
                .map(PubStockInfo::getSymbol)
                .filter(code -> code != null && code.length() == 6)
                .collect(Collectors.toCollection(HashSet::new));
        if (stockCodes.isEmpty()) {
            return Collections.emptyList();
        }

        LocalDate startDate = tradeDate.minusDays(40);
        Map<String, List<StockDataDailyAll>> dailyBarsByStock = stockDataDailyAllMapper.selectList(new LambdaQueryWrapper<StockDataDailyAll>()
                        .in(StockDataDailyAll::getStockCode, stockCodes)
                        .between(StockDataDailyAll::getTradeDate, startDate, tradeDate))
                .stream()
                .collect(Collectors.groupingBy(StockDataDailyAll::getStockCode));
        Map<String, List<StockMinuteData>> minuteBarsByStock = stockMinuteDataMapper.selectList(new LambdaQueryWrapper<StockMinuteData>()
                        .in(StockMinuteData::getStockCode, stockCodes.stream().map(Integer::valueOf).collect(Collectors.toList()))
                        .eq(StockMinuteData::getTradeDate, tradeDate))
                .stream()
                .collect(Collectors.groupingBy(bar -> String.format("%06d", bar.getStockCode())));

        List<CandidateFilterResult> results = new ArrayList<>();
        List<StockFilter> orderedFilters = filters.stream()
                .sorted(Comparator.comparing(StockFilter::getFilterName))
                .collect(Collectors.toList());
        for (PubStockInfo stockInfo : stockInfos) {
            if (stockInfo.getSymbol() == null || stockInfo.getSymbol().length() != 6) {
                continue;
            }
            List<StockDataDailyAll> dailyBars = dailyBarsByStock.getOrDefault(stockInfo.getSymbol(), Collections.emptyList());
            List<StockMinuteData> minuteBars = minuteBarsByStock.getOrDefault(stockInfo.getSymbol(), Collections.emptyList());
            for (StockFilter filter : orderedFilters) {
                CandidateFilterResult result = filter.check(tradeDate, stockInfo, dailyBars, minuteBars);
                results.add(result);
                candidateFilterResultMapper.insert(result);
                if (Boolean.FALSE.equals(result.getPassed())) {
                    break;
                }
            }
        }
        return results;
    }

    public List<String> passedStockCodes(LocalDate tradeDate) {
        List<CandidateFilterResult> results = candidateFilterResultMapper.selectList(new LambdaQueryWrapper<CandidateFilterResult>()
                .eq(CandidateFilterResult::getTradeDate, tradeDate));
        Map<String, List<CandidateFilterResult>> byStock = results.stream()
                .collect(Collectors.groupingBy(CandidateFilterResult::getStockCode));
        return byStock.entrySet().stream()
                .filter(entry -> entry.getValue().stream().allMatch(CandidateFilterResult::getPassed))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    public List<CandidateFilterResult> list(LocalDate tradeDate) {
        return candidateFilterResultMapper.selectList(new LambdaQueryWrapper<CandidateFilterResult>()
                .eq(CandidateFilterResult::getTradeDate, tradeDate)
                .orderByAsc(CandidateFilterResult::getStockCode, CandidateFilterResult::getFilterName));
    }
}
