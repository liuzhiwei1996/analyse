package org.analyse.analysestock.analysis.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.analyse.analysestock.analysis.entity.PubStockInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@DS("analysis")
public interface PubStockInfoMapper extends BaseMapper<PubStockInfo> {
    /**
     * 根据股票代码查询上市日期
     *
     * @param stockCode
     * @return
     */
    LocalDate findIpoDateByStockCode(String stockCode);

    /**
     * 根据股票代码查询股票信息
     *
     * @param stockCode
     * @return
     */
    List<PubStockInfo> findByStockCode(@Param("stockCode") String stockCode);
}
