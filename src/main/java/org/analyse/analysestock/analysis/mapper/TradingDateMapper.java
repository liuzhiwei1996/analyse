package org.analyse.analysestock.analysis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.analyse.analysestock.analysis.entity.TradingDate;
import org.apache.ibatis.annotations.Param;
import org.springframework.cache.annotation.Cacheable;

import java.time.LocalDate;
import java.util.List;

/**
 * <p>
 * 交易日期表
 * </p>
 *
 * @author dengzhiqiang
 * @since 2022-02-28
 */
public interface TradingDateMapper extends BaseMapper<TradingDate> {


    /**
     * 查询上一个交易日期或当前
     *
     * @return
     */
    LocalDate getNewsetTradingDate(@Param("holdingDate") LocalDate holdingDate);

    /**
     * SqlServer 个股列表数据导入查询 交易日期 最早为20210101
     *
     * @param holdingDate
     * @param num
     * @return
     */
    LocalDate findTradingDateSqlServerStockCode(@Param("holdingDate") LocalDate holdingDate, @Param("num") int num);

    int isTradeDate(@Param("tradeDate") LocalDate tradeDate);

    /**
     * 获取前面21个交易日
     * @return
     */
    List<LocalDate> findByMinuteTradeDate();
}
