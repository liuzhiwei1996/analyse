package org.analyse.analysestock.strategy.filter.service;

import org.analyse.analysestock.analysis.entity.PubStockInfo;
import org.analyse.analysestock.analysis.entity.StockDataDailyAll;
import org.analyse.analysestock.analysis.entity.StockMinuteData;
import org.analyse.analysestock.strategy.filter.entity.CandidateFilterResult;

import java.time.LocalDate;
import java.util.List;

public interface StockFilter {

    String getFilterName();

    CandidateFilterResult check(LocalDate tradeDate, PubStockInfo stockInfo, List<StockDataDailyAll> dailyBars, List<StockMinuteData> minuteBars);
}
