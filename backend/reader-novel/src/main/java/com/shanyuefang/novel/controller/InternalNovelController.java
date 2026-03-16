package com.shanyuefang.novel.controller;

import com.shanyuefang.common.result.R;
import com.shanyuefang.novel.domain.vo.NovelSimpleVO;
import com.shanyuefang.novel.service.NovelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 仅供微服务内部调用，不经过 Gateway 鉴权
 */
@RestController
@RequestMapping("/internal/novels")
@RequiredArgsConstructor
public class InternalNovelController {

    private final NovelService novelService;

    @GetMapping("/batch")
    public R<Map<Long, NovelSimpleVO>> batchGet(@RequestParam List<Long> ids) {
        return R.ok(novelService.batchGet(ids));
    }
}
