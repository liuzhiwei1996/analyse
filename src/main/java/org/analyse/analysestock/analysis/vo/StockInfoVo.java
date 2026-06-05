package org.analyse.analysestock.analysis.vo;

import lombok.Data;

import java.time.LocalDate;

/**
 * @Author: keenan
 * @Description:
 * @Date: create in 2026/6/4 16:15
 */
@Data
public class StockInfoVo {
    private String stockCode;
    private LocalDate tradeDate;
}
