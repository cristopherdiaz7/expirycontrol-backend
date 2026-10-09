package com.example.demo.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LossStatsResponse {

    private String currency;
    private BigDecimal totalAmount;
    private long lossCount;
    private long unitsLost;
    private List<MonthlyLossResponse> byMonth;
}
