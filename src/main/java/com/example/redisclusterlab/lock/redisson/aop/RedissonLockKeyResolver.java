package com.example.redisclusterlab.lock.redisson.aop;

import java.lang.reflect.Method;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;

@Component
public class RedissonLockKeyResolver {

    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    public String resolve(ProceedingJoinPoint joinPoint, String keyExpression) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(
                joinPoint.getTarget(),
                method,
                joinPoint.getArgs(),
                parameterNameDiscoverer
        );
        context.setVariable("methodName", method.getName());
        context.setVariable("targetClass", joinPoint.getTarget().getClass().getSimpleName());

        String lockKey = expressionParser.parseExpression(keyExpression).getValue(context, String.class);
        if (lockKey == null || lockKey.isBlank()) {
            throw new IllegalArgumentException("Redisson lock key expression result must not be blank.");
        }
        return lockKey;
    }
}
