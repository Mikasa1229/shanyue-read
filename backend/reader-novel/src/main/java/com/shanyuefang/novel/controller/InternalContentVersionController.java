package com.shanyuefang.novel.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shanyuefang.common.result.R;
import com.shanyuefang.common.result.ResultCode;
import com.shanyuefang.novel.domain.dto.ContentVersionStatusDTO;
import com.shanyuefang.novel.domain.entity.ContentRecoveryTask;
import com.shanyuefang.novel.domain.entity.BookContentVersion;
import com.shanyuefang.novel.mapper.BookContentVersionMapper;
import com.shanyuefang.novel.service.NovelInternalAccess;
import com.shanyuefang.novel.service.ContentRecoveryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Set;

/** Authoritative source-side status ledger for asynchronously indexed chapter versions. */
@RestController
@RequestMapping("/internal/books/content-versions")
@RequiredArgsConstructor
public class InternalContentVersionController {
    private final NovelInternalAccess internalAccess;
    private final BookContentVersionMapper contentVersionMapper;
    private final ContentRecoveryService contentRecoveryService;

    @PutMapping("/status")
    public R<Void> updateStatus(@RequestHeader("X-Agent-Internal-Token") String token, @Valid @RequestBody ContentVersionStatusDTO dto) {
        internalAccess.require(token);
        String status = dto.getIndexStatus().trim().toUpperCase(java.util.Locale.ROOT);
        if (!Set.of("PENDING", "READY", "FAILED").contains(status)) throw new IllegalArgumentException("Unsupported content index status");
        int updated = contentVersionMapper.update(null, Wrappers.<BookContentVersion>lambdaUpdate()
                .eq(BookContentVersion::getCanonicalBookId, dto.getCanonicalBookId())
                .eq(BookContentVersion::getChapterIndex, dto.getChapterIndex())
                .eq(BookContentVersion::getContentHash, dto.getContentHash())
                .set(BookContentVersion::getIndexStatus, status));
        if (updated == 0) return R.fail(ResultCode.NOT_FOUND, "Chapter content version does not exist");
        return R.ok();
    }

    /** Internal only: repairs a bounded range after a historic downstream full-delete. */
    @PostMapping("/recovery/{canonicalBookId}")
    public R<ContentRecoveryTask> recover(@RequestHeader("X-Agent-Internal-Token") String token,
                                          @PathVariable long canonicalBookId,
                                          @RequestParam int startChapter,
                                          @RequestParam int endChapter) {
        internalAccess.require(token);
        return R.ok(contentRecoveryService.enqueue(canonicalBookId, startChapter, endChapter));
    }

    /** Fetches chapters absent from the source-side ledger using the requester's current shelf source. */
    @PostMapping("/prefetch/{canonicalBookId}")
    public R<ContentRecoveryTask> prefetch(@RequestHeader("X-Agent-Internal-Token") String token,
                                           @PathVariable long canonicalBookId,
                                           @RequestParam long userId,
                                           @RequestParam int startChapter,
                                           @RequestParam int endChapter) {
        internalAccess.require(token);
        return R.ok(contentRecoveryService.enqueuePrefetch(userId, canonicalBookId, startChapter, endChapter));
    }

    @GetMapping("/prefetch/tasks/{taskId}")
    public R<ContentRecoveryTask> prefetchTask(@RequestHeader("X-Agent-Internal-Token") String token,
                                                @PathVariable long taskId, @RequestParam long userId) {
        internalAccess.require(token);
        ContentRecoveryTask task = contentRecoveryService.get(taskId);
        if (task == null || !"PREFETCH".equals(task.getTaskType()) || !Long.valueOf(userId).equals(task.getRequesterUserId())) {
            return R.fail(ResultCode.NOT_FOUND, "正文补齐任务不存在");
        }
        return R.ok(task);
    }
}
