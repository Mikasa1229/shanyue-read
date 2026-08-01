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
import com.shanyuefang.novel.service.CanonicalBookService;
import com.shanyuefang.novel.domain.dto.ResolveCanonicalBookDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl extends ServiceImpl<FavoriteBookMapper, FavoriteBook>
        implements FavoriteService {
    private final CanonicalBookService canonicalBookService;

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
            existing.setCanonicalBookId(resolveCanonicalId(dto.getSourceId(), dto.getBookUrl(), dto.getBookName(), dto.getAuthor(), dto.getCoverUrl()));
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
        book.setCanonicalBookId(resolveCanonicalId(dto.getSourceId(), dto.getBookUrl(), dto.getBookName(), dto.getAuthor(), dto.getCoverUrl()));
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
            backfillCanonicalId(b);
            FavoriteBookVO vo = new FavoriteBookVO();
            BeanUtils.copyProperties(b, vo);
            return vo;
        }).toList());
        return result;
    }

    private void backfillCanonicalId(FavoriteBook book) {
        if (book.getCanonicalBookId() != null) return;
        Long canonicalBookId = resolveCanonicalId(book.getSourceId(), book.getBookUrl(), book.getBookName(), book.getAuthor(), book.getCoverUrl());
        if (canonicalBookId == null) return;
        book.setCanonicalBookId(canonicalBookId);
        updateById(book);
    }

    // Canonical identity is always resolved from server-side source metadata, never from client input.
    private Long resolveCanonicalId(Long sourceId, String bookUrl, String title, String author, String coverUrl) {
        if (sourceId == null || bookUrl == null || bookUrl.isBlank() || title == null || title.isBlank()) return null;
        ResolveCanonicalBookDTO resolve = new ResolveCanonicalBookDTO();
        resolve.setSourceId(sourceId); resolve.setBookUrl(bookUrl); resolve.setTitle(title); resolve.setAuthor(author); resolve.setCoverUrl(coverUrl);
        return canonicalBookService.resolve(resolve).getCanonicalBookId();
    }

    @Override
    public boolean isFavorited(long userId, String bookUrl) {
        return lambdaQuery()
                .eq(FavoriteBook::getUserId, userId)
                .eq(FavoriteBook::getBookUrl, bookUrl)
                .exists();
    }
}
