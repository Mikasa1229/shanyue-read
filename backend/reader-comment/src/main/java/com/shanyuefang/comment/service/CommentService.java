package com.shanyuefang.comment.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shanyuefang.comment.domain.dto.CommentPageDTO;
import com.shanyuefang.comment.domain.dto.CreateCommentDTO;
import com.shanyuefang.comment.domain.vo.CommentVO;

public interface CommentService {

    /** 提交点评（根评论或回复） */
    CommentVO createComment(Long userId, CreateCommentDTO dto);

    /** 删除点评（仅作者可操作，根评论级联删除回复） */
    void deleteComment(Long userId, Long commentId);

    /** 分页查询小说的根评论列表 */
    IPage<CommentVO> listRootComments(Long novelId, CommentPageDTO dto);

    /** 分页查询某根评论的回复列表 */
    IPage<CommentVO> listReplies(Long rootId, CommentPageDTO dto);
}
