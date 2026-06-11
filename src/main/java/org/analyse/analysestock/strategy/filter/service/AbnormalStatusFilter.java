package org.analyse.analysestock.strategy.filter.service;

import org.analyse.analysestock.analysis.entity.PubStockInfo;
import org.analyse.analysestock.analysis.entity.StockDataDailyAll;
import org.analyse.analysestock.analysis.entity.StockMinuteData;
import org.analyse.analysestock.strategy.filter.entity.CandidateFilterResult;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

@Component
public class AbnormalStatusFilter extends AbstractStockFilter {

    @Override
    public String getFilterName() {
        return "ABNORMAL_STATUS";
    }

    @Override
    public CandidateFilterResult check(LocalDate tradeDate, PubStockInfo stockInfo, List<StockDataDailyAll> dailyBars, List<StockMinuteData> minuteBars) {
        if (stockInfo == null) {
            return FilterResultFactory.fail(tradeDate, null, getFilterName(), "缺少证券基础信息", null);
        }
        if (stockInfo.getListingDate() != null && tradeDate.isBefore(stockInfo.getListingDate())) {
            return FilterResultFactory.fail(tradeDate, stockCode(stockInfo), getFilterName(), "交易日在上市日前", "listingDate=" + stockInfo.getListingDate());
        }
        if (stockInfo.getDelistDate() != null && !tradeDate.isBefore(stockInfo.getDelistDate())) {
            return FilterResultFactory.fail(tradeDate, stockCode(stockInfo), getFilterName(), "股票已退市", "delistDate=" + stockInfo.getDelistDate());
        }
        if (StringUtils.hasText(stockInfo.getStStatus()) && !"0".equals(stockInfo.getStStatus())) {
            return FilterResultFactory.fail(tradeDate, stockCode(stockInfo), getFilterName(), "ST状态股票", "stStatus=" + stockInfo.getStStatus());
        }
        if (StringUtils.hasText(stockInfo.getListedStatus()) && stockInfo.getListedStatus().contains("退")) {
            return FilterResultFactory.fail(tradeDate, stockCode(stockInfo), getFilterName(), "上市状态异常", "listedStatus=" + stockInfo.getListedStatus());
        }
        return FilterResultFactory.pass(tradeDate, stockCode(stockInfo), getFilterName(), "listedStatus=" + stockInfo.getListedStatus());
    }
}
