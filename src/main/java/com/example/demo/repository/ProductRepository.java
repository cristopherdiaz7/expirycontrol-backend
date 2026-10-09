package com.example.demo.repository;

import com.example.demo.model.Product;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    // Orden fijo por alta: sin esto la base no garantiza el orden y un producto editado cambia de lugar.
    @Query("select p from Product p where p.user.id = :userId order by p.id")
    List<Product> findByUserId(@Param("userId") Long userId);

    Optional<Product> findByIdAndUserId(Long id, Long userId);
}
