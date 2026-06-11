package org.analyse.analysestock.analysis.serivce;

import org.analyse.analysestock.analysis.vo.MissingStockDataItem;

import java.time.LocalDate;
import java.util.List;

public interface ImportService {

    Integer importStockMinuteData(String stockCode, LocalDate tradeDate);

    Integer importStockDailyData(String stockCode, LocalDate tradeDate);

    List<MissingStockDataItem> checkMissingData(String stockCode, LocalDate startDate, LocalDate endDate);
}
