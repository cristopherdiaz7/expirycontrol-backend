package com.example.demo.service;

import com.example.demo.dto.LossResponse;
import com.example.demo.dto.LossStatsResponse;
import com.example.demo.dto.MonthlyLossResponse;
import com.example.demo.model.Loss;
import com.example.demo.model.Product;
import com.example.demo.repository.LossRepository;
import com.example.demo.repository.ProductRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

// Las pérdidas se registran al consultarlas: en ese momento se comparan con los productos del usuario.
// Un producto vencido y con precio tiene exactamente una pérdida; si deja de estar vencido, se quita.
// Las pérdidas de productos eliminados quedan congeladas y no se vuelven a tocar.
@Service
@RequiredArgsConstructor
public class LossService {

    public static final String CURRENCY = "ARS";

    private final ProductRepository productRepository;
    private final LossRepository lossRepository;
    private final TransactionTemplate transactionTemplate;
    private final UserLocks userLocks;

    public List<LossResponse> getLosses(Long userId, LocalDate clientToday) {
        syncLosses(userId, ClientDates.resolveToday(clientToday));

        return lossRepository.findByUserIdOrderByExpirationDateDescIdDesc(userId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    public LossStatsResponse getStats(Long userId, LocalDate clientToday) {
        syncLosses(userId, ClientDates.resolveToday(clientToday));

        List<Loss> losses = lossRepository.findByUserIdOrderByExpirationDateDescIdDesc(userId);

        Map<YearMonth, List<Loss>> lossesByMonth = losses.stream()
                .collect(Collectors.groupingBy(loss -> YearMonth.from(loss.getExpirationDate())));

        List<MonthlyLossResponse> byMonth = lossesByMonth.entrySet().stream()
                .sorted(Map.Entry.<YearMonth, List<Loss>>comparingByKey(Comparator.reverseOrder()))
                .map(entry -> MonthlyLossResponse.builder()
                        .month(entry.getKey().toString())
                        .totalAmount(sumAmounts(entry.getValue()))
                        .lossCount(entry.getValue().size())
                        .unitsLost(sumUnits(entry.getValue()))
                        .build())
                .toList();

        return LossStatsResponse.builder()
                .currency(CURRENCY)
                .totalAmount(sumAmounts(losses))
                .lossCount(losses.size())
                .unitsLost(sumUnits(losses))
                .byMonth(byMonth)
                .build();
    }

    // Antes de eliminar un producto: deja su pérdida al día y la desvincula para que quede en el historial.
    // Debe llamarse dentro de la transacción que elimina el producto.
    public void freezeBeforeProductDeletion(Product product, LocalDate today) {
        Optional<Loss> existing = lossRepository.findByProductId(product.getId());

        if (isLost(product, today)) {
            Loss loss = existing.orElseGet(() -> newLoss(product));
            applySnapshot(loss, product);
            loss.setProduct(null);
            lossRepository.save(loss);
        } else {
            existing.ifPresent(lossRepository::delete);
        }
    }

    private void syncLosses(Long userId, LocalDate today) {
        synchronized (userLocks.forUser(userId)) {
            transactionTemplate.executeWithoutResult(status -> {
                Map<Long, Loss> lossesByProduct = lossRepository.findByUserIdAndProductIsNotNull(userId).stream()
                        .collect(Collectors.toMap(loss -> loss.getProduct().getId(), Function.identity(), (first, second) -> first));

                for (Product product : productRepository.findByUserId(userId)) {
                    Loss existing = lossesByProduct.get(product.getId());

                    if (isLost(product, today)) {
                        Loss loss = existing != null ? existing : newLoss(product);
                        if (existing == null || differs(loss, product)) {
                            applySnapshot(loss, product);
                            lossRepository.save(loss);
                        }
                    } else if (existing != null) {
                        // La fecha se corrigió y el producto ya no está vencido.
                        lossRepository.delete(existing);
                    }
                }
            });
        }
    }

    // Misma regla de "vencido" que el resto del sistema: la fecha es hoy o anterior.
    private boolean isLost(Product product, LocalDate today) {
        return product.getUnitPrice() != null && !product.getExpirationDate().isAfter(today);
    }

    private Loss newLoss(Product product) {
        return Loss.builder()
                .user(product.getUser())
                .product(product)
                .recordedAt(Instant.now())
                .build();
    }

    private void applySnapshot(Loss loss, Product product) {
        loss.setProductName(product.getName());
        loss.setQuantity(product.getQuantity());
        loss.setUnitPrice(product.getUnitPrice());
        loss.setExpirationDate(product.getExpirationDate());
        loss.setTotalAmount(totalOf(product));
    }

    private boolean differs(Loss loss, Product product) {
        return !loss.getProductName().equals(product.getName())
                || !loss.getQuantity().equals(product.getQuantity())
                || loss.getUnitPrice().compareTo(product.getUnitPrice()) != 0
                || !loss.getExpirationDate().equals(product.getExpirationDate());
    }

    private BigDecimal totalOf(Product product) {
        return product.getUnitPrice()
                .multiply(BigDecimal.valueOf(product.getQuantity()))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal sumAmounts(List<Loss> losses) {
        return losses.stream()
                .map(Loss::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private long sumUnits(List<Loss> losses) {
        return losses.stream().mapToLong(Loss::getQuantity).sum();
    }

    private LossResponse mapToResponse(Loss loss) {
        Product product = loss.getProduct();
        return LossResponse.builder()
                .id(loss.getId())
                .productId(product == null ? null : product.getId())
                .productName(loss.getProductName())
                .quantity(loss.getQuantity())
                .unitPrice(loss.getUnitPrice())
                .expirationDate(loss.getExpirationDate())
                .totalAmount(loss.getTotalAmount())
                .productDeleted(product == null)
                .build();
    }
}
