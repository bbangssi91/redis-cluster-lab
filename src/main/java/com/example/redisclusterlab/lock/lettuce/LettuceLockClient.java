package com.example.redisclusterlab.lock.lettuce;

import com.example.redisclusterlab.cluster.common.RedisClusterConnectionProvider;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.SetArgs;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
// Lettuce baseline이 실제 Redis 명령을 어떻게 호출하는지 드러내기 위한 얇은 client wrapper다.
public class LettuceLockClient {

    // GET과 DEL을 하나의 Lua script로 묶어 다른 owner의 락을 지우는 일을 막는다.
    private static final String RELEASE_SCRIPT = """
            if redis.call("get", KEYS[1]) == ARGV[1] then
              return redis.call("del", KEYS[1])
            else
              return 0
            end
            """;

    private final RedisClusterConnectionProvider connectionProvider;

    // Redis SET NX PX 명령을 Lettuce API로 직접 호출한다.
    public boolean acquire(String lockKey, String token, long ttlMillis) {
        String result = connectionProvider.commands()
                .set(lockKey, token, SetArgs.Builder.nx().px(ttlMillis));
        return "OK".equalsIgnoreCase(result);
    }

    // Lua script 결과가 1이면 현재 token과 일치해 삭제가 수행된 것이다.
    public boolean release(String lockKey, String token) {
        Long deleted = connectionProvider.commands()
                .eval(RELEASE_SCRIPT, ScriptOutputType.INTEGER, new String[]{lockKey}, token);
        return deleted != null && deleted > 0;
    }

    public String get(String lockKey) {
        return connectionProvider.commands().get(lockKey);
    }

    // Redis PTTL의 -2는 key 없음, -1은 TTL 없음이라는 의미를 그대로 전달한다.
    public long pttl(String lockKey) {
        Long ttlMillis = connectionProvider.commands().pttl(lockKey);
        return ttlMillis == null ? -2 : ttlMillis;
    }
}
