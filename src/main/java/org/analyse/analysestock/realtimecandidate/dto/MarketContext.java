package org.analyse.analysestock.realtimecandidate.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.Map;

@Data
public class MarketContext {
    private BigDecimal marketBreadth;
    private BigDecimal marketReturn1430;
    private Map<String, BigDecimal> sectorStrength;
    private Map<String, BigDecimal> sectorBreadth;
    private BigDecimal regimeScore;
}
