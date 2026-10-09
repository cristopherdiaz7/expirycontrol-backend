package com.example.demo.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
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
public class LossResponse {

    private Long id;
    // null cuando el producto ya fue eliminado.
    private Long productId;
    private String productName;
    private Integer quantity;
    private BigDecimal unitPrice;
    private LocalDate expirationDate;
    private BigDecimal totalAmount;
    private boolean productDeleted;
}
