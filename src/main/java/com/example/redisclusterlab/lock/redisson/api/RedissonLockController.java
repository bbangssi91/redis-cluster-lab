package com.example.redisclusterlab.lock.redisson.api;

import com.example.redisclusterlab.lock.redisson.application.RedissonAopDemoService;
import com.example.redisclusterlab.lock.redisson.application.RedissonLockExperimentService;
import com.example.redisclusterlab.lock.redisson.application.RedissonLockService;
import com.example.redisclusterlab.lock.redisson.dto.RedissonAopBusinessRequest;
import com.example.redisclusterlab.lock.redisson.dto.RedissonAopBusinessResponse;
import com.example.redisclusterlab.lock.redisson.dto.RedissonAopDemoRequest;
import com.example.redisclusterlab.lock.redisson.dto.RedissonAopDemoResponse;
import com.example.redisclusterlab.lock.redisson.dto.RedissonLeaseExpirationRequest;
import com.example.redisclusterlab.lock.redisson.dto.RedissonLeaseExpirationResponse;
import com.example.redisclusterlab.lock.redisson.dto.RedissonLockRequest;
import com.example.redisclusterlab.lock.redisson.dto.RedissonLockResponse;
import com.example.redisclusterlab.lock.redisson.dto.RedissonLockStateResponse;
import com.example.redisclusterlab.lock.redisson.dto.RedissonMultiLockRequest;
import com.example.redisclusterlab.lock.redisson.dto.RedissonMultiLockResponse;
import com.example.redisclusterlab.lock.redisson.dto.RedissonReleaseRequest;
import com.example.redisclusterlab.lock.redisson.dto.RedissonReleaseResponse;
import com.example.redisclusterlab.lock.redisson.dto.RedissonWatchdogRequest;
import com.example.redisclusterlab.lock.redisson.dto.RedissonWatchdogResponse;
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
@RequestMapping("/locks/redisson")
@RequiredArgsConstructor
public class RedissonLockController {

    private final RedissonLockService lockService;
    private final RedissonLockExperimentService experimentService;
    private final RedissonAopDemoService aopDemoService;

    @PostMapping("/acquire")
    public RedissonLockResponse acquire(@Valid @RequestBody RedissonLockRequest request) {
        return lockService.acquire(request);
    }

    @PostMapping("/try-acquire")
    public RedissonLockResponse tryAcquire(@Valid @RequestBody RedissonLockRequest request) {
        return lockService.tryAcquire(request);
    }

    @PostMapping("/release")
    public RedissonReleaseResponse release(@Valid @RequestBody RedissonReleaseRequest request) {
        return lockService.release(request);
    }

    @GetMapping("/state")
    public RedissonLockStateResponse state(@NotBlank(message = "lockKey는 필수입니다.") @RequestParam String lockKey) {
        return lockService.state(lockKey);
    }

    @PostMapping("/watchdog")
    public RedissonWatchdogResponse watchdog(@Valid @RequestBody RedissonWatchdogRequest request) {
        return experimentService.watchdog(request);
    }

    @PostMapping("/lease-expiration")
    public RedissonLeaseExpirationResponse leaseExpiration(
            @Valid @RequestBody RedissonLeaseExpirationRequest request
    ) {
        return experimentService.leaseExpiration(request);
    }

    @PostMapping("/multi-lock")
    public RedissonMultiLockResponse multiLock(@Valid @RequestBody RedissonMultiLockRequest request) {
        return experimentService.multiLock(request);
    }

    @PostMapping("/aop-demo")
    public RedissonAopDemoResponse aopDemo(@Valid @RequestBody RedissonAopDemoRequest request) {
        return aopDemoService.runLocked(request.resourceId(), request.workMillis());
    }

    @PostMapping("/aop-business-demo")
    public RedissonAopBusinessResponse aopBusinessDemo(@Valid @RequestBody RedissonAopBusinessRequest request) {
        return aopDemoService.issueCoupon(request.couponId(), request.userId());
    }
}
