package com.shanyuefang.comment.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shanyuefang.comment.domain.dto.CommentPageDTO;
import com.shanyuefang.comment.domain.dto.CreateCommentDTO;
import com.shanyuefang.comment.domain.entity.Comment;
import com.shanyuefang.comment.domain.vo.CommentVO;
import com.shanyuefang.comment.event.CommentEventProducer;
import com.shanyuefang.comment.feign.UserFeignClient;
import com.shanyuefang.comment.feign.vo.UserSimpleVO;
import com.shanyuefang.comment.mapper.CommentMapper;
import com.shanyuefang.comment.service.CommentService;
import com.shanyuefang.common.exception.BusinessException;
import com.shanyuefang.common.result.R;
import com.shanyuefang.common.result.ResultCode;
import com.shanyuefang.common.util.SnowflakeIdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommentServiceImpl extends ServiceImpl<CommentMapper, Comment> implements CommentService {

    private final CommentEventProducer eventProducer;
    private final UserFeignClient userFeignClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommentVO createComment(Long userId, CreateCommentDTO dto) {
        Comment comment = new Comment();
        comment.setId(SnowflakeIdUtil.next());
        comment.setNovelId(dto.getNovelId());
        comment.setUserId(userId);
        comment.setContent(dto.getContent());
        comment.setLikeCount(0);
        comment.setStatus(1);

        if (dto.getParentId() != null) {
            // 回复：需找到父评论确认存在且属于同一小说
            Comment parent = getById(dto.getParentId());
            if (parent == null || Boolean.TRUE.equals(parent.getDeleted())) {
                throw new BusinessException(ResultCode.NOT_FOUND, "被回复的评论不存在");
            }
            if (!parent.getNovelId().equals(dto.getNovelId())) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "评论不属于该小说");
            }
            comment.setParentId(parent.getId());
            // rootId：若父评论本身是根评论，则 rootId = parentId；否则继承父的 rootId
            comment.setRootId(parent.getRootId() == null ? parent.getId() : parent.getRootId());
        }

        save(comment);

        // 异步通知小说服务更新 comment_count（只统计根评论）
        if (comment.getRootId() == null) {
            eventProducer.sendCommentCreated(dto.getNovelId(), comment.getId());
        }

        log.info("点评提交成功: commentId={}, novelId={}, userId={}", comment.getId(), dto.getNovelId(), userId);
        return assembleVO(comment, fetchUserMap(List.of(userId)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(Long userId, Long commentId) {
        Comment comment = getById(commentId);
        if (comment == null || Boolean.TRUE.equals(comment.getDeleted())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "点评不存在");
        }
        if (!comment.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权删除他人点评");
        }

        // 软删除本条评论（MBP removeById 触发 @TableLogic UPDATE）
        removeById(commentId);

        // 若是根评论，级联软删除所有回复
        boolean isRoot = comment.getRootId() == null;
        if (isRoot) {
            baseMapper.softDeleteRepliesByRootId(commentId);
            eventProducer.sendCommentDeleted(comment.getNovelId(), commentId);
        }

        log.info("点评删除成功: commentId={}, isRoot={}, userId={}", commentId, isRoot, userId);
    }

    @Override
    public IPage<CommentVO> listRootComments(Long novelId, CommentPageDTO dto) {
        Page<Comment> page = lambdaQuery()
                .eq(Comment::getNovelId, novelId)
                .isNull(Comment::getRootId)          // 只查根评论
                .eq(Comment::getStatus, 1)
                .orderByDesc(Comment::getCreatedAt)
                .page(new Page<>(dto.getPage(), dto.getSize()));

        return toVOPage(page);
    }

    @Override
    public IPage<CommentVO> listReplies(Long rootId, CommentPageDTO dto) {
        // 验证根评论存在
        Comment root = getById(rootId);
        if (root == null || Boolean.TRUE.equals(root.getDeleted())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "评论不存在");
        }

        Page<Comment> page = lambdaQuery()
                .eq(Comment::getRootId, rootId)
                .eq(Comment::getStatus, 1)
                .orderByAsc(Comment::getCreatedAt)   // 回复按时间正序
                .page(new Page<>(dto.getPage(), dto.getSize()));

        return toVOPage(page);
    }

    @Override
    public IPage<CommentVO> listRecentComments(int page, int size) {
        Page<Comment> result = lambdaQuery()
                .isNull(Comment::getRootId)          // 只查根评论
                .eq(Comment::getStatus, 1)
                .orderByDesc(Comment::getCreatedAt)
                .page(new Page<>(page, size));
        return toVOPage(result);
    }

    // ─────────── 私有方法 ───────────

    /** 将分页 Comment 转换为分页 CommentVO，并批量填充用户信息 */
    private IPage<CommentVO> toVOPage(Page<Comment> page) {
        List<Comment> records = page.getRecords();
        if (records.isEmpty()) {
            Page<CommentVO> empty = new Page<>(page.getCurrent(), page.getSize(), 0);
            empty.setRecords(Collections.emptyList());
            return empty;
        }

        // 批量 Feign 获取用户信息（一次调用，避免 N+1）
        List<Long> userIds = records.stream()
                .map(Comment::getUserId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, UserSimpleVO> userMap = fetchUserMap(userIds);

        List<CommentVO> voList = records.stream()
                .map(c -> assembleVO(c, userMap))
                .collect(Collectors.toList());

        Page<CommentVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(voList);
        return voPage;
    }

    /** 批量获取用户信息 Map，Feign 失败时返回空 Map（降级） */
    private Map<Long, UserSimpleVO> fetchUserMap(List<Long> userIds) {
        try {
            R<Map<Long, UserSimpleVO>> resp = userFeignClient.batchGetUsers(userIds);
            if (resp != null && resp.getData() != null) {
                return resp.getData();
            }
        } catch (Exception e) {
            log.warn("Feign 获取用户信息失败，降级为空: {}", e.getMessage());
        }
        return Collections.emptyMap();
    }

    /** Comment + UserMap → CommentVO */
    private CommentVO assembleVO(Comment comment, Map<Long, UserSimpleVO> userMap) {
        CommentVO vo = new CommentVO();
        BeanUtils.copyProperties(comment, vo);

        UserSimpleVO user = userMap.get(comment.getUserId());
        if (user != null) {
            vo.setUserNickname(user.getNickname());
            vo.setUserAvatar(user.getAvatar());
        }
        return vo;
    }
}
