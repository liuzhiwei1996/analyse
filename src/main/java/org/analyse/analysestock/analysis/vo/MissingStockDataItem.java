package org.analyse.analysestock.analysis.vo;

import lombok.Data;

import java.time.LocalDate;

/**
 * 缺失数据的股票条目
 */
@Data
public class MissingStockDataItem {
    /** 股票代码 */
    private String stockCode;
    /** 股票名称 */
    private String stockName;
    /** 交易日期 */
    private LocalDate tradeDate;
    /** 是否缺失日K数据 */
    private boolean missingDaily;
    /** 是否缺失分时数据 */
    private boolean missingMinute;
}
