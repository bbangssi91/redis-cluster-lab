package com.example.redisclusterlab.lock.api;

import com.example.redisclusterlab.lock.application.LettuceLockExperimentService;
import com.example.redisclusterlab.lock.application.LettuceLockService;
import com.example.redisclusterlab.lock.dto.AcquireLockRequest;
import com.example.redisclusterlab.lock.dto.AcquireLockResponse;
import com.example.redisclusterlab.lock.dto.LockContendRequest;
import com.example.redisclusterlab.lock.dto.LockContendResponse;
import com.example.redisclusterlab.lock.dto.LockStateResponse;
import com.example.redisclusterlab.lock.dto.LockTtlExpirationRequest;
import com.example.redisclusterlab.lock.dto.LockTtlExpirationResponse;
import com.example.redisclusterlab.lock.dto.ReleaseLockRequest;
import com.example.redisclusterlab.lock.dto.ReleaseLockResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/locks/lettuce")
@RequiredArgsConstructor
public class LettuceLockController {

    private final LettuceLockService lockService;
    private final LettuceLockExperimentService experimentService;

    // 기본 락 API는 service에 바로 위임하고, 입력 검증은 Bean Validation annotation으로 처리한다.
    @PostMapping("/acquire")
    public AcquireLockResponse acquire(@Valid @RequestBody AcquireLockRequest request) {
        return lockService.acquire(request);
    }

    @PostMapping("/release")
    public ReleaseLockResponse release(@Valid @RequestBody ReleaseLockRequest request) {
        return lockService.release(request);
    }

    @GetMapping("/state")
    public LockStateResponse state(@NotBlank(message = "lockKey는 필수입니다.") @RequestParam String lockKey) {
        return lockService.state(lockKey);
    }

    // 경합과 TTL 만료는 실험 성격이 강해서 별도 experiment service가 담당한다.
    @PostMapping("/contend")
    public LockContendResponse contend(@Valid @RequestBody LockContendRequest request) {
        return experimentService.contend(request);
    }

    @PostMapping("/ttl-expiration")
    public LockTtlExpirationResponse ttlExpiration(@Valid @RequestBody LockTtlExpirationRequest request) {
        return experimentService.ttlExpiration(request);
    }
}
