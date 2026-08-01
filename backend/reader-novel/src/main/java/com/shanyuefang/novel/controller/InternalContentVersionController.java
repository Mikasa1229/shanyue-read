package com.shanyuefang.novel.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shanyuefang.common.result.R;
import com.shanyuefang.common.result.ResultCode;
import com.shanyuefang.novel.domain.dto.ContentVersionStatusDTO;
import com.shanyuefang.novel.domain.entity.BookContentVersion;
import com.shanyuefang.novel.mapper.BookContentVersionMapper;
import com.shanyuefang.novel.service.NovelInternalAccess;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/** Authoritative source-side status ledger for asynchronously indexed chapter versions. */
@RestController
@RequestMapping("/internal/books/content-versions")
@RequiredArgsConstructor
public class InternalContentVersionController {
    private final NovelInternalAccess internalAccess;
    private final BookContentVersionMapper contentVersionMapper;

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
}
