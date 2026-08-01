package com.shanyuefang.comment.service;

import com.shanyuefang.comment.domain.dto.CreateCommentDTO;
import com.shanyuefang.comment.domain.entity.Comment;
import com.shanyuefang.comment.domain.vo.CommentVO;
import com.shanyuefang.comment.event.CommentEventProducer;
import com.shanyuefang.comment.feign.UserFeignClient;
import com.shanyuefang.comment.mapper.CommentMapper;
import com.shanyuefang.comment.service.impl.CommentServiceImpl;
import com.shanyuefang.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("点评服务单元测试")
class CommentServiceTest {

    @Mock CommentMapper commentMapper;
    @Mock CommentEventProducer eventProducer;
    @Mock UserFeignClient userFeignClient;

    CommentServiceImpl commentService;

    @BeforeEach
    void setUp() {
        commentService = new CommentServiceImpl(eventProducer, userFeignClient);
        ReflectionTestUtils.setField(commentService, "baseMapper", commentMapper);
        ReflectionTestUtils.setField(commentService, "entityClass", Comment.class);
    }

    // ── createComment ─────────────────────────────────────────

    @Test
    @DisplayName("创建根评论 - 无 parentId 时保存成功并发送事件")
    void createComment_rootComment_savesAndSendsEvent() {
        CreateCommentDTO dto = new CreateCommentDTO();
        dto.setNovelId(100L);
        dto.setContent("这本书很好看");
        dto.setScore(5);

        when(commentMapper.insert(any(Comment.class))).thenReturn(1);
        when(userFeignClient.batchGetUsers(any())).thenReturn(null);

        CommentVO vo = commentService.createComment(1L, dto);

        assertThat(vo.getContent()).isEqualTo("这本书很好看");
        assertThat(vo.getNovelId()).isEqualTo(100L);
        verify(commentMapper).insert(argThat((Comment c) -> c.getRootId() == null && c.getParentId() == null));
        verify(eventProducer).sendCommentCreated(eq(100L), anyLong());
    }

    @Test
    @DisplayName("创建回复 - 父评论不存在时抛出 NOT_FOUND")
    void createComment_whenParentNotFound_throwsNotFound() {
        CreateCommentDTO dto = new CreateCommentDTO();
        dto.setNovelId(100L);
        dto.setContent("回复内容");
        dto.setParentId(999L);

        when(commentMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> commentService.createComment(1L, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("被回复的评论不存在");
    }

    @Test
    @DisplayName("创建回复 - 父评论属于不同小说时抛出参数错误")
    void createComment_whenParentBelongsToDifferentNovel_throwsParamError() {
        CreateCommentDTO dto = new CreateCommentDTO();
        dto.setNovelId(100L);
        dto.setContent("回复内容");
        dto.setParentId(50L);

        Comment parent = mockComment(50L, 200L, 1L, null, null); // parent belongs to novel 200, not 100
        when(commentMapper.selectById(50L)).thenReturn(parent);

        assertThatThrownBy(() -> commentService.createComment(1L, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("评论不属于该小说");
    }

    @Test
    @DisplayName("创建回复 - 父评论是根评论时 rootId = parentId")
    void createComment_replyToRoot_setsRootIdCorrectly() {
        CreateCommentDTO dto = new CreateCommentDTO();
        dto.setNovelId(100L);
        dto.setContent("回复根评论");
        dto.setParentId(50L);

        Comment parent = mockComment(50L, 100L, 1L, null, null); // rootId = null means parent is root
        when(commentMapper.selectById(50L)).thenReturn(parent);
        when(commentMapper.insert(any(Comment.class))).thenReturn(1);
        when(userFeignClient.batchGetUsers(any())).thenReturn(null);

        CommentVO vo = commentService.createComment(2L, dto);

        assertThat(vo.getParentId()).isEqualTo(50L);
        verify(commentMapper).insert(argThat((Comment c) -> Long.valueOf(50L).equals(c.getRootId())));
    }

    @Test
    @DisplayName("创建回复 - 父评论是子评论时 rootId 继承父的 rootId")
    void createComment_replyToReply_inheritsRootId() {
        CreateCommentDTO dto = new CreateCommentDTO();
        dto.setNovelId(100L);
        dto.setContent("二级回复");
        dto.setParentId(60L);

        Comment parent = mockComment(60L, 100L, 1L, 50L, 50L); // parent is a reply, rootId = 50
        when(commentMapper.selectById(60L)).thenReturn(parent);
        when(commentMapper.insert(any(Comment.class))).thenReturn(1);
        when(userFeignClient.batchGetUsers(any())).thenReturn(null);

        commentService.createComment(3L, dto);

        verify(commentMapper).insert(argThat((Comment c) -> Long.valueOf(50L).equals(c.getRootId())));
        // 二级回复不发送事件（不是根评论）
        verify(eventProducer, never()).sendCommentCreated(any(), any());
    }

    // ── deleteComment ─────────────────────────────────────────

    @Test
    @DisplayName("删除评论 - 评论不存在时抛出 NOT_FOUND")
    void deleteComment_whenNotFound_throwsNotFound() {
        when(commentMapper.selectById(any())).thenReturn(null);

        assertThatThrownBy(() -> commentService.deleteComment(1L, 999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("点评不存在");
    }

    @Test
    @DisplayName("删除评论 - 删除他人评论时抛出 FORBIDDEN")
    void deleteComment_whenNotOwner_throwsForbidden() {
        Comment comment = mockComment(10L, 100L, 99L, null, null); // owner = 99
        when(commentMapper.selectById(10L)).thenReturn(comment);

        assertThatThrownBy(() -> commentService.deleteComment(1L, 10L)) // userId = 1
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权删除他人点评");
    }

    @Test
    @DisplayName("删除根评论 - 软删除成功并级联删除回复、发送事件")
    void deleteComment_rootComment_cascadesAndSendsEvent() {
        Comment comment = mockComment(10L, 100L, 1L, null, null); // rootId = null → is root
        when(commentMapper.selectById(10L)).thenReturn(comment);
        when(commentMapper.deleteById(10L)).thenReturn(1);
        when(commentMapper.softDeleteRepliesByRootId(10L)).thenReturn(3);

        commentService.deleteComment(1L, 10L);

        verify(commentMapper).deleteById(10L);
        verify(commentMapper).softDeleteRepliesByRootId(10L);
        verify(eventProducer).sendCommentDeleted(100L, 10L);
    }

    @Test
    @DisplayName("删除回复评论 - 仅软删除本条，不级联不发事件")
    void deleteComment_replyComment_noCascadeNoEvent() {
        Comment comment = mockComment(20L, 100L, 1L, 10L, 10L); // has rootId → is reply
        when(commentMapper.selectById(20L)).thenReturn(comment);
        when(commentMapper.deleteById(20L)).thenReturn(1);

        commentService.deleteComment(1L, 20L);

        verify(commentMapper).deleteById(20L);
        verify(commentMapper, never()).softDeleteRepliesByRootId(any());
        verify(eventProducer, never()).sendCommentDeleted(any(), any());
    }

    // ── helper ────────────────────────────────────────────────

    private Comment mockComment(Long id, Long novelId, Long userId, Long parentId, Long rootId) {
        Comment c = new Comment();
        c.setId(id);
        c.setNovelId(novelId);
        c.setUserId(userId);
        c.setParentId(parentId);
        c.setRootId(rootId);
        c.setContent("内容");
        c.setLikeCount(0);
        c.setStatus(1);
        c.setDeleted(false);
        return c;
    }
}
