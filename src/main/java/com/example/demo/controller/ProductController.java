package com.example.demo.controller;

import com.example.demo.dto.ProductRequest;
import com.example.demo.dto.ProductResponse;
import com.example.demo.dto.ProductStatsResponse;
import com.example.demo.model.User;
import com.example.demo.service.ProductService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    public ResponseEntity<ProductResponse> create(@AuthenticationPrincipal User user,
                                                 @Valid @RequestBody ProductRequest request) {
        ProductResponse response = productService.create(user.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ProductResponse>> getAll(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(productService.getAll(user.getId()));
    }

    @GetMapping("/expired")
    public ResponseEntity<List<ProductResponse>> getExpired(@AuthenticationPrincipal User user,
                                                            @RequestParam(required = false)
                                                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate today) {
        return ResponseEntity.ok(productService.getExpired(user.getId(), today));
    }

    @GetMapping("/expiring")
    public ResponseEntity<List<ProductResponse>> getExpiringSoon(@AuthenticationPrincipal User user,
                                                              @RequestParam(defaultValue = "7") int days,
                                                              @RequestParam(required = false)
                                                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate today) {
        return ResponseEntity.ok(productService.getExpiringSoon(user.getId(), days, today));
    }

    @GetMapping("/stats")
    public ResponseEntity<ProductStatsResponse> getStats(@AuthenticationPrincipal User user,
                                                       @RequestParam(defaultValue = "7") int days,
                                                       @RequestParam(required = false)
                                                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate today) {
        return ResponseEntity.ok(productService.getStats(user.getId(), days, today));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getById(@AuthenticationPrincipal User user,
                                                  @PathVariable Long id) {
        return ResponseEntity.ok(productService.getById(user.getId(), id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> update(@AuthenticationPrincipal User user,
                                                @PathVariable Long id,
                                                @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.ok(productService.update(user.getId(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal User user,
                                      @PathVariable Long id) {
        productService.delete(user.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
