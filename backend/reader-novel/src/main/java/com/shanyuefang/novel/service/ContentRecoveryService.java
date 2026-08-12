package com.shanyuefang.novel.service;

import com.shanyuefang.novel.domain.entity.ContentRecoveryTask;

public interface ContentRecoveryService {
    ContentRecoveryTask enqueue(long canonicalBookId, int startChapter, int endChapter);

    /** Queues a bounded, server-authorized source fetch for chapters that were never read locally. */
    ContentRecoveryTask enqueuePrefetch(long userId, long canonicalBookId, int startChapter, int endChapter);

    void recover(long taskId);

    ContentRecoveryTask get(long taskId);
}
