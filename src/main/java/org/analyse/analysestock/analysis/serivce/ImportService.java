package org.analyse.analysestock.analysis.serivce;

import java.time.LocalDate;

/**
 * <p>
 * VIEW Mapper 接口
 * </p>
 *
 * @author kennan
 * @since 2022-03-14
 */
public interface ImportService {

    /**
     * 导入个股分时数据(rds接口只能拿到当日的数据)
     * @return
     */
    Integer importStockMinuteData(String stockCode, LocalDate tradeDate);

    /**
     * 导入个股日线数据
     * @return
     */
    Integer importStockDailyData(String stockCode, LocalDate tradeDate);
}
