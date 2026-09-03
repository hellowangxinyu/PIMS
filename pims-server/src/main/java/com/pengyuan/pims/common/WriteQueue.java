package com.pengyuan.pims.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * 全局写锁 —— SQLite 写并发保护
 * 所有写操作排队执行，3-5 人并发下几乎无感知
 */
@Component
public class WriteQueue {

    private static final Logger log = LoggerFactory.getLogger(WriteQueue.class);

    private final ReentrantLock lock = new ReentrantLock(true); // 公平锁
    private final TransactionTemplate txTemplate;

    public WriteQueue(PlatformTransactionManager txManager) {
        this.txTemplate = new TransactionTemplate(txManager);
    }

    public <T> T execute(Supplier<T> task) {
        lock.lock();
        try {
            return task.get();
        } finally {
            lock.unlock();
        }
    }

    public void execute(Runnable task) {
        lock.lock();
        try {
            task.run();
        } finally {
            lock.unlock();
        }
    }

    /**
     * v6.1（高#12）：锁内包事务——取号类写操作的事务在锁内开启、提交后才放锁。
     * 旧模式「@Transactional 方法内再 execute(锁)」的时序是 开事务→抢锁→写→放锁→提交，
     * 放锁到提交之间他人可抢锁读 MAX(序号)（未提交的插入不可见）→ 撞号。
     * 迁移注意：调用方方法须去掉 @Transactional（否则外层事务先于锁开启，等价旧时序）。
     */
    public <T> T executeTx(Supplier<T> task) {
        lock.lock();
        try {
            return txTemplate.execute(status -> task.get());
        } finally {
            lock.unlock();
        }
    }

    /** 同 {@link #executeTx(Supplier)}，无返回值版本 */
    public void executeTx(Runnable task) {
        lock.lock();
        try {
            txTemplate.executeWithoutResult(status -> task.run());
        } finally {
            lock.unlock();
        }
    }
}
