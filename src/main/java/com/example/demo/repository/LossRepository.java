package com.example.demo.repository;

import com.example.demo.model.Loss;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LossRepository extends JpaRepository<Loss, Long> {

    List<Loss> findByUserIdOrderByExpirationDateDescIdDesc(Long userId);

    // Pérdidas de productos que todavía existen: son las únicas que se pueden actualizar o quitar.
    List<Loss> findByUserIdAndProductIsNotNull(Long userId);

    Optional<Loss> findByProductId(Long productId);
}
