package com.example.redisclusterlab.lock.redisson.api;

import com.example.redisclusterlab.lock.redisson.aop.RedissonLockAcquireException;
import com.example.redisclusterlab.lock.redisson.dto.RedissonLockErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = RedissonLockController.class)
public class RedissonLockExceptionHandler {

    @ExceptionHandler(RedissonLockAcquireException.class)
    public ResponseEntity<RedissonLockErrorResponse> handleLockAcquireFailure(RedissonLockAcquireException ex) {
        return ResponseEntity.status(HttpStatus.LOCKED)
                .body(new RedissonLockErrorResponse(ex.lockKey(), ex.waitMillis(), ex.getMessage()));
    }
}
