package org.analyse.analysestock.strategy.filter.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("candidate_filter_result")
public class CandidateFilterResult {

    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDate tradeDate;

    private String stockCode;

    private String filterName;

    private Boolean passed;

    private String rejectionReason;

    private String details;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
