package com.example.demo;

import com.example.demo.model.Loss;
import com.example.demo.model.Product;
import com.example.demo.model.User;
import com.example.demo.service.LossService;
import com.example.demo.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Operaciones simultáneas de un mismo usuario sobre sus pérdidas.
// Cada hilo es una petición distinta y no comparte nada en memoria con los demás, igual que
// dos réplicas del backend: lo único que los coordina es la base de datos.
class LossConcurrencyIntegrationTest extends IntegrationTestSupport {

    private static final int ROUNDS = 15;
    private static final int QUERIES_PER_ROUND = 6;

    @Autowired
    private LossService lossService;

    @Autowired
    private ProductService productService;

    private User user;

    @BeforeEach
    void createUser() {
        user = saveUser("Usuario", "concurrencia@example.com");
    }

    // El caso más delicado: mientras se consultan las pérdidas, se elimina el producto vencido.
    // Sin coordinación, la consulta registra una pérdida que el borrado no ve, el borrado crea
    // la suya, y al eliminar el producto quedan dos pérdidas sin producto (o un error 500).
    @Test
    void queryingLossesWhileDeletingTheProductKeepsExactlyOneLoss() throws Exception {
        Long userId = user.getId();
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
        ExecutorService executor = Executors.newFixedThreadPool(QUERIES_PER_ROUND + 1);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                Product product = saveProduct("Producto " + round, user, -1, 2, "100.00");
                CountDownLatch start = new CountDownLatch(1);
                List<Future<?>> tasks = new ArrayList<>();
                for (int i = 0; i < QUERIES_PER_ROUND; i++) {
                    tasks.add(executor.submit(() -> {
                        start.await();
                        return lossService.getLosses(userId, null);
                    }));
                }
                tasks.add(executor.submit(() -> {
                    start.await();
                    productService.delete(userId, product.getId(), null);
                    return null;
                }));

                start.countDown();
                for (Future<?> task : tasks) {
                    try {
                        task.get(30, TimeUnit.SECONDS);
                    } catch (Exception ex) {
                        errors.add(ex.getCause() != null ? ex.getCause() : ex);
                    }
                }
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(errors).as("ninguna petición debe fallar").isEmpty();
        for (int round = 0; round < ROUNDS; round++) {
            String name = "Producto " + round;
            List<Loss> losses = lossRepository.findAll().stream()
                    .filter(loss -> loss.getProductName().equals(name))
                    .toList();
            assertThat(losses).as("pérdidas de %s", name).hasSize(1);
            assertThat(losses.get(0).getProduct()).as("la pérdida de %s queda en el historial", name).isNull();
        }
        assertThat(productRepository.count()).isZero();
    }

    // Muchas consultas a la vez sobre varios productos vencidos: cada uno tiene una sola pérdida
    // y ninguna consulta termina en error.
    @Test
    void manySimultaneousQueriesRecordEachLossOnce() throws Exception {
        for (int i = 0; i < 5; i++) {
            saveProduct("Vencido " + i, user, -1, 1, "10.00");
        }
        Long userId = user.getId();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(12);
        List<Future<?>> tasks = new ArrayList<>();
        try {
            for (int i = 0; i < 24; i++) {
                boolean stats = i % 2 == 0;
                tasks.add(executor.submit(() -> {
                    start.await();
                    return stats ? lossService.getStats(userId, null) : lossService.getLosses(userId, null);
                }));
            }
            start.countDown();
            for (Future<?> task : tasks) {
                task.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(lossRepository.count()).isEqualTo(5);
    }

    // Última línea de defensa: aunque el código fallara, la base rechaza dos pérdidas
    // para el mismo producto (restricción uk_losses_product).
    @Test
    void theDatabaseRejectsTwoLossesForTheSameProduct() {
        Product product = saveProduct("Leche", user, -1, 2, "100.00");
        lossRepository.saveAndFlush(lossFor(product));

        assertThatThrownBy(() -> lossRepository.saveAndFlush(lossFor(product)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Loss lossFor(Product product) {
        return Loss.builder()
                .user(user)
                .product(product)
                .productName(product.getName())
                .quantity(product.getQuantity())
                .unitPrice(product.getUnitPrice())
                .expirationDate(product.getExpirationDate())
                .totalAmount(new BigDecimal("200.00"))
                .recordedAt(Instant.now())
                .build();
    }
}
