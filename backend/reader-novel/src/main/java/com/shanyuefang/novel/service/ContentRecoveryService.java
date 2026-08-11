package com.shanyuefang.novel.service;

import com.shanyuefang.novel.domain.entity.ContentRecoveryTask;

public interface ContentRecoveryService {
    ContentRecoveryTask enqueue(long canonicalBookId, int startChapter, int endChapter);

    void recover(long taskId);
}
