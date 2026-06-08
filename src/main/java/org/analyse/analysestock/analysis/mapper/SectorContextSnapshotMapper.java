package org.analyse.analysestock.analysis.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.analyse.analysestock.analysis.entity.SectorContextSnapshot;

@DS("analysis")
public interface SectorContextSnapshotMapper extends BaseMapper<SectorContextSnapshot> {
    int insertBatch(@org.apache.ibatis.annotations.Param("list") java.util.List<SectorContextSnapshot> list);

    @org.apache.ibatis.annotations.Delete("DELETE FROM sector_context_snapshot WHERE trade_date = #{tradeDate}")
    int deleteByTradeDate(@org.apache.ibatis.annotations.Param("tradeDate") java.time.LocalDate tradeDate);
}
