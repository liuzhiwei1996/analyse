package org.analyse.analysestock.analysis.vo;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class GenerationMissingDateItem {

    private LocalDate tradeDate;

    private Boolean factorGenerated;

    private Boolean scoreGenerated;

    private List<String> missingFactorItems;
}
