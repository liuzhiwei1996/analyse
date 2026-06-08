package org.analyse.analysestock.analysis.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.analyse.analysestock.analysis.entity.BacktestTopkSummary;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@DS("analysis")
/**
 * 回测 TopK 汇总表 Mapper。
 */
public interface BacktestTopkSummaryMapper extends BaseMapper<BacktestTopkSummary> {

    /**
     * 批量写入 TopK 汇总结果。
     */
    int insertBatch(@Param("list") List<BacktestTopkSummary> list);

    /**
     * 删除指定任务的旧汇总，支持任务重跑时保持幂等。
     */
    @Delete("DELETE FROM backtest_topk_summary WHERE task_id = #{taskId}")
    int deleteByTaskId(@Param("taskId") String taskId);
}
