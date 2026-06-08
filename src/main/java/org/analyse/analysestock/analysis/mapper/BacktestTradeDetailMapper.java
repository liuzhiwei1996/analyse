package org.analyse.analysestock.analysis.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.analyse.analysestock.analysis.entity.BacktestTradeDetail;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@DS("analysis")
/**
 * 回测选股明细表 Mapper。
 */
public interface BacktestTradeDetailMapper extends BaseMapper<BacktestTradeDetail> {

    /**
     * 批量写入选股收益明细。
     */
    int insertBatch(@Param("list") List<BacktestTradeDetail> list);

    /**
     * 删除指定任务的旧明细。
     */
    @Delete("DELETE FROM backtest_trade_detail WHERE task_id = #{taskId}")
    int deleteByTaskId(@Param("taskId") String taskId);
}
