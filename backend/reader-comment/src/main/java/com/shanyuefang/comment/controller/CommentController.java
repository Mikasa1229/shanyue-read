package com.shanyuefang.comment.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shanyuefang.comment.domain.dto.CommentPageDTO;
import com.shanyuefang.comment.domain.dto.CreateCommentDTO;
import com.shanyuefang.comment.domain.vo.CommentVO;
import com.shanyuefang.comment.service.CommentService;
import com.shanyuefang.common.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "点评接口")
@RestController
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @Operation(summary = "提交点评（根评论或回复）")
    @PostMapping("/api/novels/{novelId}/comments")
    public R<CommentVO> createComment(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long novelId,
            @Valid @RequestBody CreateCommentDTO dto) {
        dto.setNovelId(novelId);
        return R.ok(commentService.createComment(userId, dto));
    }

    @Operation(summary = "删除点评")
    @DeleteMapping("/api/comments/{id}")
    public R<Void> deleteComment(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id) {
        commentService.deleteComment(userId, id);
        return R.ok();
    }

    @Operation(summary = "获取小说根评论列表（分页）")
    @GetMapping("/api/novels/{novelId}/comments")
    public R<IPage<CommentVO>> listRootComments(
            @PathVariable Long novelId,
            @Valid CommentPageDTO dto) {
        return R.ok(commentService.listRootComments(novelId, dto));
    }

    @Operation(summary = "获取评论的回复列表（分页）")
    @GetMapping("/api/comments/{rootId}/replies")
    public R<IPage<CommentVO>> listReplies(
            @PathVariable Long rootId,
            @Valid CommentPageDTO dto) {
        return R.ok(commentService.listReplies(rootId, dto));
    }
}
