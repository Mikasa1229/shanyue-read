package com.shanyuefang.novel.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shanyuefang.common.exception.BusinessException;
import com.shanyuefang.common.result.ResultCode;
import com.shanyuefang.common.util.SnowflakeIdUtil;
import com.shanyuefang.novel.domain.dto.AddToShelfDTO;
import com.shanyuefang.novel.domain.dto.UpdateProgressDTO;
import com.shanyuefang.novel.domain.entity.BookshelfBook;
import com.shanyuefang.novel.domain.vo.HotBookVO;
import com.shanyuefang.novel.domain.vo.ShelfBookVO;
import com.shanyuefang.novel.mapper.BookshelfBookMapper;
import com.shanyuefang.novel.service.BookshelfService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookshelfServiceImpl extends ServiceImpl<BookshelfBookMapper, BookshelfBook>
        implements BookshelfService {

    private final StringRedisTemplate stringRedisTemplate;

    /** 热门书籍 ZSET：成员=bookUrl，分值=加入书架的用户数 */
    private static final String HOT_BOOKS_ZSET = "ranking:hot_books";

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

        // 新书入架：热门排行 +1
        try {
            stringRedisTemplate.opsForZSet().incrementScore(HOT_BOOKS_ZSET, dto.getBookUrl(), 1);
        } catch (Exception e) {
            log.warn("更新热门书籍 ZSET 失败（不影响主流程）: bookUrl={}, err={}", dto.getBookUrl(), e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeBook(long userId, String bookUrl) {
        boolean removed = lambdaUpdate()
                .eq(BookshelfBook::getUserId, userId)
                .eq(BookshelfBook::getBookUrl, bookUrl)
                .remove();
        if (!removed) throw new BusinessException(ResultCode.NOT_FOUND, "书架中没有该书");

        // 移出书架：热门排行 -1（最低为 0）
        try {
            Double score = stringRedisTemplate.opsForZSet().score(HOT_BOOKS_ZSET, bookUrl);
            if (score != null && score > 0) {
                stringRedisTemplate.opsForZSet().incrementScore(HOT_BOOKS_ZSET, bookUrl, -1);
            }
        } catch (Exception e) {
            log.warn("更新热门书籍 ZSET 失败（不影响主流程）: bookUrl={}, err={}", bookUrl, e.getMessage());
        }
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

    @Override
    public List<HotBookVO> getHotBooks(int top) {
        int limit = Math.min(top, 50);

        // 1. 从 Redis ZSET 获取热门书籍排名
        Set<ZSetOperations.TypedTuple<String>> tuples =
                stringRedisTemplate.opsForZSet().reverseRangeWithScores(HOT_BOOKS_ZSET, 0, limit - 1);

        if (tuples == null || tuples.isEmpty()) {
            // 兜底：从 DB 直接聚合查询
            List<HotBookVO> dbResult = baseMapper.selectHotBooks(limit);
            for (int i = 0; i < dbResult.size(); i++) {
                dbResult.get(i).setRank(i + 1);
            }
            return dbResult;
        }

        // 2. 收集 bookUrl 及分值
        List<String> bookUrls = new ArrayList<>();
        Map<String, Long> countMap = new HashMap<>();
        for (ZSetOperations.TypedTuple<String> t : tuples) {
            String url = t.getValue();
            Double score = t.getScore();
            if (url != null && score != null && score >= 1) {
                bookUrls.add(url);
                countMap.put(url, score.longValue());
            }
        }
        if (bookUrls.isEmpty()) return List.of();

        // 3. 批量查询元数据（每个 bookUrl 取最新一条）
        List<BookshelfBook> metaList = baseMapper.selectMetaByUrls(bookUrls);
        Map<String, BookshelfBook> metaMap = metaList.stream()
                .collect(Collectors.toMap(BookshelfBook::getBookUrl, b -> b, (a, b) -> a));

        // 4. 组装结果
        List<HotBookVO> result = new ArrayList<>();
        int rank = 1;
        for (String bookUrl : bookUrls) {
            HotBookVO vo = new HotBookVO();
            vo.setRank(rank++);
            vo.setBookUrl(bookUrl);
            vo.setShelfCount(countMap.getOrDefault(bookUrl, 0L));

            BookshelfBook meta = metaMap.get(bookUrl);
            if (meta != null) {
                vo.setBookName(meta.getBookName());
                vo.setAuthor(meta.getAuthor());
                vo.setCoverUrl(meta.getCoverUrl());
                vo.setSourceId(meta.getSourceId());
                vo.setSourceName(meta.getSourceName());
            }
            result.add(vo);
        }
        return result;
    }
}
