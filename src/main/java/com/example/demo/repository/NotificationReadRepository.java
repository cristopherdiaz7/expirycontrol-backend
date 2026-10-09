package com.example.demo.repository;

import com.example.demo.model.NotificationRead;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationReadRepository extends JpaRepository<NotificationRead, Long> {

    List<NotificationRead> findByUserId(Long userId);

    Optional<NotificationRead> findByUserIdAndProductId(Long userId, Long productId);

    void deleteByProductId(Long productId);
}
