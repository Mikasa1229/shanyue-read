package com.shanyuefang.common.util;

/**
 * 雪花算法 ID 生成器（64 位）
 * 结构：1位符号位 | 41位时间戳 | 10位机器ID | 12位序列号
 */
public class SnowflakeIdUtil {

    private static final long EPOCH = 1700000000000L; // 自定义起始时间戳（2023-11-15）

    private static final long MACHINE_ID_BITS = 10L;
    private static final long SEQUENCE_BITS = 12L;

    private static final long MAX_MACHINE_ID = ~(-1L << MACHINE_ID_BITS);   // 1023
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);        // 4095

    private static final long MACHINE_ID_SHIFT = SEQUENCE_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + MACHINE_ID_BITS;

    private final long machineId;
    private long lastTimestamp = -1L;
    private long sequence = 0L;

    private static volatile SnowflakeIdUtil instance;

    private SnowflakeIdUtil(long machineId) {
        if (machineId < 0 || machineId > MAX_MACHINE_ID) {
            throw new IllegalArgumentException("machineId 必须在 0~" + MAX_MACHINE_ID + " 之间");
        }
        this.machineId = machineId;
    }

    public static SnowflakeIdUtil getInstance() {
        if (instance == null) {
            synchronized (SnowflakeIdUtil.class) {
                if (instance == null) {
                    instance = new SnowflakeIdUtil(1L); // 默认机器 ID=1，生产环境从配置读取
                }
            }
        }
        return instance;
    }

    public static SnowflakeIdUtil getInstance(long machineId) {
        if (instance == null) {
            synchronized (SnowflakeIdUtil.class) {
                if (instance == null) {
                    instance = new SnowflakeIdUtil(machineId);
                }
            }
        }
        return instance;
    }

    public synchronized long nextId() {
        long timestamp = currentTime();
        if (timestamp < lastTimestamp) {
            throw new RuntimeException("系统时钟回拨，拒绝生成 ID");
        }
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                timestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;
        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
                | (machineId << MACHINE_ID_SHIFT)
                | sequence;
    }

    /** 快捷方法：直接获取下一个 ID */
    public static long next() {
        return getInstance().nextId();
    }

    private long currentTime() {
        return System.currentTimeMillis();
    }

    private long waitNextMillis(long lastTimestamp) {
        long timestamp = currentTime();
        while (timestamp <= lastTimestamp) {
            timestamp = currentTime();
        }
        return timestamp;
    }
}
