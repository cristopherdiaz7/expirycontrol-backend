package com.example.demo.controller;

import com.example.demo.dto.LossResponse;
import com.example.demo.dto.LossStatsResponse;
import com.example.demo.model.User;
import com.example.demo.service.LossService;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/losses")
@RequiredArgsConstructor
public class LossController {

    private final LossService lossService;

    @GetMapping
    public ResponseEntity<List<LossResponse>> getAll(@AuthenticationPrincipal User user,
                                                     @RequestParam(required = false)
                                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate today) {
        return ResponseEntity.ok(lossService.getLosses(user.getId(), today));
    }

    @GetMapping("/stats")
    public ResponseEntity<LossStatsResponse> getStats(@AuthenticationPrincipal User user,
                                                      @RequestParam(required = false)
                                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate today) {
        return ResponseEntity.ok(lossService.getStats(user.getId(), today));
    }
}
