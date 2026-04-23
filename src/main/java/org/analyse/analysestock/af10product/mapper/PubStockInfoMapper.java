package org.analyse.analysestock.af10product.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.analyse.analysestock.af10product.entity.PubStockInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 股票基本信息表 Mapper 接口
 */
@DS("af10product")
public interface PubStockInfoMapper extends BaseMapper<PubStockInfo> {
}
