package com.shanyuefang.novel.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shanyuefang.common.exception.BusinessException;
import com.shanyuefang.common.result.ResultCode;
import com.shanyuefang.common.util.SnowflakeIdUtil;
import com.shanyuefang.novel.domain.dto.AddFavoriteDTO;
import com.shanyuefang.novel.domain.entity.FavoriteBook;
import com.shanyuefang.novel.domain.vo.FavoriteBookVO;
import com.shanyuefang.novel.mapper.FavoriteBookMapper;
import com.shanyuefang.novel.service.FavoriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl extends ServiceImpl<FavoriteBookMapper, FavoriteBook>
        implements FavoriteService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addFavorite(long userId, AddFavoriteDTO dto) {
        // 幂等：已存在则更新封面/作者等元数据
        FavoriteBook existing = lambdaQuery()
                .eq(FavoriteBook::getUserId, userId)
                .eq(FavoriteBook::getBookUrl, dto.getBookUrl())
                .one();
        if (existing != null) {
            existing.setSourceId(dto.getSourceId());
            existing.setSourceName(dto.getSourceName());
            existing.setBookName(dto.getBookName());
            existing.setAuthor(dto.getAuthor());
            existing.setCoverUrl(dto.getCoverUrl());
            updateById(existing);
            return;
        }
        FavoriteBook book = new FavoriteBook();
        book.setId(SnowflakeIdUtil.next());
        book.setUserId(userId);
        book.setSourceId(dto.getSourceId());
        book.setSourceName(dto.getSourceName());
        book.setBookName(dto.getBookName());
        book.setAuthor(dto.getAuthor());
        book.setCoverUrl(dto.getCoverUrl());
        book.setBookUrl(dto.getBookUrl());
        save(book);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeFavorite(long userId, String bookUrl) {
        boolean removed = lambdaUpdate()
                .eq(FavoriteBook::getUserId, userId)
                .eq(FavoriteBook::getBookUrl, bookUrl)
                .remove();
        if (!removed) {
            throw new BusinessException(ResultCode.NOT_FOUND, "收藏中没有该书");
        }
    }

    @Override
    public Page<FavoriteBookVO> listMyFavorites(long userId, int page, int size) {
        Page<FavoriteBook> raw = lambdaQuery()
                .eq(FavoriteBook::getUserId, userId)
                .orderByDesc(FavoriteBook::getCreatedAt)
                .page(new Page<>(page, size));
        Page<FavoriteBookVO> result = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        result.setRecords(raw.getRecords().stream().map(b -> {
            FavoriteBookVO vo = new FavoriteBookVO();
            BeanUtils.copyProperties(b, vo);
            return vo;
        }).toList());
        return result;
    }

    @Override
    public boolean isFavorited(long userId, String bookUrl) {
        return lambdaQuery()
                .eq(FavoriteBook::getUserId, userId)
                .eq(FavoriteBook::getBookUrl, bookUrl)
                .exists();
    }
}
