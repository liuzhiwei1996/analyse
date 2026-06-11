package org.analyse.analysestock.monitor.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.analyse.analysestock.monitor.entity.ShadowRecord;

@DS("analysis")
public interface ShadowRecordMapper extends BaseMapper<ShadowRecord> {
}
