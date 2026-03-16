package com.shanyuefang.comment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shanyuefang.comment.domain.dto.CreateCommentDTO;
import com.shanyuefang.comment.domain.entity.Comment;
import com.shanyuefang.comment.domain.vo.CommentVO;
import com.shanyuefang.comment.event.CommentEventProducer;
import com.shanyuefang.comment.feign.UserFeignClient;
import com.shanyuefang.comment.mapper.CommentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "spring.config.import=")
@ActiveProfiles("test")
@Testcontainers
@DisplayName("CommentService 集成测试")
class CommentServiceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockBean
    private ConnectionFactory connectionFactory;

    @MockBean
    private UserFeignClient userFeignClient;

    @MockBean
    private CommentEventProducer commentEventProducer;

    @Autowired
    private CommentService commentService;

    @Autowired
    private CommentMapper commentMapper;

    private static final Long TEST_USER_ID  = 200001L;
    private static final Long TEST_NOVEL_ID = 300001L;

    @BeforeEach
    void cleanUp() {
        commentMapper.delete(null);

        // stub Feign to return empty map (graceful degradation path in service)
        when(userFeignClient.batchGetUsers(any())).thenReturn(
            com.shanyuefang.common.result.R.ok(Collections.emptyMap())
        );
        // stub event producer
        doNothing().when(commentEventProducer).sendCommentCreated(any(), any());
        doNothing().when(commentEventProducer).sendCommentDeleted(any(), any());
    }

    @Test
    @DisplayName("创建根评论 - 持久化到数据库")
    void createRootComment_persistsToDB() {
        CreateCommentDTO dto = new CreateCommentDTO();
        dto.setNovelId(TEST_NOVEL_ID);
        dto.setContent("这本书写得真好！");

        CommentVO vo = commentService.createComment(TEST_USER_ID, dto);

        assertThat(vo).isNotNull();
        assertThat(vo.getId()).isNotNull();

        long count = commentMapper.selectCount(
            new LambdaQueryWrapper<Comment>()
                .eq(Comment::getNovelId, TEST_NOVEL_ID)
                .isNull(Comment::getRootId)
        );
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("删除根评论 - 级联软删除所有回复")
    void deleteRootComment_cascadeSoftDeletesReplies() {
        // 创建根评论
        CreateCommentDTO rootDto = new CreateCommentDTO();
        rootDto.setNovelId(TEST_NOVEL_ID);
        rootDto.setContent("根评论");
        CommentVO rootVO = commentService.createComment(TEST_USER_ID, rootDto);
        Long rootId = rootVO.getId();

        // 创建两条回复
        CreateCommentDTO reply1 = new CreateCommentDTO();
        reply1.setNovelId(TEST_NOVEL_ID);
        reply1.setContent("回复1");
        reply1.setParentId(rootId);
        commentService.createComment(TEST_USER_ID, reply1);

        CreateCommentDTO reply2 = new CreateCommentDTO();
        reply2.setNovelId(TEST_NOVEL_ID);
        reply2.setContent("回复2");
        reply2.setParentId(rootId);
        commentService.createComment(TEST_USER_ID, reply2);

        // 删除根评论（调用自定义 XML SQL 级联删除）
        commentService.deleteComment(TEST_USER_ID, rootId);

        // 根评论本身应被软删除（MyBatis-Plus selectById 不返回 deleted=true 的记录）
        Comment rootAfterDelete = commentMapper.selectById(rootId);
        assertThat(rootAfterDelete).isNull();

        // 所有回复（rootId 指向该根评论）也应被软删除
        long replyCount = commentMapper.selectCount(
            new LambdaQueryWrapper<Comment>()
                .eq(Comment::getRootId, rootId)
        );
        assertThat(replyCount).isEqualTo(0);
    }
}
