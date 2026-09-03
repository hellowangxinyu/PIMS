package com.pengyuan.pims.config;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * v6.1.3：mustChangePwd 拦截检查的轻量缓存。
 * 拦截器对每个 API 请求校验一次，无缓存时等于每请求一次 sys_user 查库（N+1）；
 * 这里做 60 秒 TTL 的内存缓存，改密/重置时主动失效，保证改完立即可用。
 */
@Component
public class MustChangePwdCache {

    private static final long TTL_MS = 60_000;
    private final ConcurrentHashMap<Long, long[]> cache = new ConcurrentHashMap<>();   // userId -> [flag, expireAt]

    public boolean flagged(Long userId, java.util.function.BooleanSupplier loader) {
        long now = System.currentTimeMillis();
        long[] hit = cache.get(userId);
        if (hit != null && now < hit[1]) return hit[0] == 1;
        boolean flag = loader.getAsBoolean();
        cache.put(userId, new long[]{flag ? 1L : 0L, now + TTL_MS});
        return flag;
    }

    public void invalidate(Long userId) {
        cache.remove(userId);
    }
}
