package org.analyse.analysestock.analysis.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.analyse.analysestock.analysis.entity.SectorContextSnapshotV3;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@DS("analysis")
public interface SectorContextSnapshotV3Mapper extends BaseMapper<SectorContextSnapshotV3> {

    int insertBatch(@Param("list") List<SectorContextSnapshotV3> list);

    @Delete("DELETE FROM sector_context_snapshot_v3 WHERE trade_date = #{tradeDate}")
    int deleteByTradeDate(@Param("tradeDate") LocalDate tradeDate);
}
