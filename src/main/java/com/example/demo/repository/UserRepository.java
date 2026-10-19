package com.example.demo.repository;

import com.example.demo.model.User;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    // Bloquea la fila del usuario hasta que termina la transacción (SELECT ... FOR UPDATE).
    // Otra transacción que pida el mismo bloqueo espera, aunque venga de otra réplica:
    // así las operaciones de pérdidas de un mismo usuario se ejecutan de a una.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> lockById(@Param("id") Long id);
}
