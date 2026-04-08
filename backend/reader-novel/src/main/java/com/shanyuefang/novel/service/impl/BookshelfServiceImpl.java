package com.shanyuefang.novel.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shanyuefang.common.exception.BusinessException;
import com.shanyuefang.common.result.ResultCode;
import com.shanyuefang.common.util.SnowflakeIdUtil;
import com.shanyuefang.novel.domain.dto.AddToShelfDTO;
import com.shanyuefang.novel.domain.dto.UpdateProgressDTO;
import com.shanyuefang.novel.domain.entity.BookshelfBook;
import com.shanyuefang.novel.domain.vo.ShelfBookVO;
import com.shanyuefang.novel.mapper.BookshelfBookMapper;
import com.shanyuefang.novel.service.BookshelfService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class BookshelfServiceImpl extends ServiceImpl<BookshelfBookMapper, BookshelfBook>
        implements BookshelfService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addBook(long userId, AddToShelfDTO dto) {
        // 幂等：已存在则更新封面/作者等元数据
        BookshelfBook existing = lambdaQuery()
                .eq(BookshelfBook::getUserId, userId)
                .eq(BookshelfBook::getBookUrl, dto.getBookUrl())
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
        BookshelfBook book = new BookshelfBook();
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
    public void removeBook(long userId, String bookUrl) {
        boolean removed = lambdaUpdate()
                .eq(BookshelfBook::getUserId, userId)
                .eq(BookshelfBook::getBookUrl, bookUrl)
                .remove();
        if (!removed) throw new BusinessException(ResultCode.NOT_FOUND, "书架中没有该书");
    }

    @Override
    public Page<ShelfBookVO> listMyShelf(long userId, int page, int size) {
        Page<BookshelfBook> raw = lambdaQuery()
                .eq(BookshelfBook::getUserId, userId)
                .orderByDesc(BookshelfBook::getLastReadAt)
                .orderByDesc(BookshelfBook::getCreatedAt)
                .page(new Page<>(page, size));
        Page<ShelfBookVO> result = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        result.setRecords(raw.getRecords().stream().map(b -> {
            ShelfBookVO vo = new ShelfBookVO();
            BeanUtils.copyProperties(b, vo);
            return vo;
        }).toList());
        return result;
    }

    @Override
    public boolean isOnShelf(long userId, String bookUrl) {
        return lambdaQuery()
                .eq(BookshelfBook::getUserId, userId)
                .eq(BookshelfBook::getBookUrl, bookUrl)
                .exists();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProgress(long userId, UpdateProgressDTO dto) {
        var update = lambdaUpdate()
                .eq(BookshelfBook::getUserId, userId)
                .eq(BookshelfBook::getBookUrl, dto.getBookUrl())
                .set(BookshelfBook::getLastChapterName, dto.getChapterName())
                .set(BookshelfBook::getLastChapterUrl, dto.getChapterUrl())
                .set(BookshelfBook::getLastReadAt, LocalDateTime.now());
        if (dto.getChapterIndex() != null) {
            update.set(BookshelfBook::getLastChapterIndex, dto.getChapterIndex());
        }
        if (dto.getTotalChapters() != null && dto.getTotalChapters() > 0) {
            update.set(BookshelfBook::getTotalChapters, dto.getTotalChapters());
        }
        boolean updated = update.update();
        if (!updated) {
            throw new BusinessException(ResultCode.NOT_FOUND, "书架中没有该书，无法更新进度");
        }
    }
}
