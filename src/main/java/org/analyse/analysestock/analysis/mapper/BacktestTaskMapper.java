package org.analyse.analysestock.analysis.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.analyse.analysestock.analysis.entity.BacktestTask;

@DS("analysis")
/**
 * 回测任务主表 Mapper。
 */
public interface BacktestTaskMapper extends BaseMapper<BacktestTask> {
}
