package org.analyse.analysestock.analysis.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.analyse.analysestock.analysis.entity.BacktestDailySummary;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@DS("analysis")
/**
 * 回测每日汇总表 Mapper。
 */
public interface BacktestDailySummaryMapper extends BaseMapper<BacktestDailySummary> {

    /**
     * 批量写入每日组合收益。
     */
    int insertBatch(@Param("list") List<BacktestDailySummary> list);

    /**
     * 删除指定任务的旧每日汇总。
     */
    @Delete("DELETE FROM backtest_daily_summary WHERE task_id = #{taskId}")
    int deleteByTaskId(@Param("taskId") String taskId);
}
