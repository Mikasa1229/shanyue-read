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
import com.shanyuefang.novel.service.CanonicalBookService;
import com.shanyuefang.novel.domain.dto.ResolveCanonicalBookDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookshelfServiceImpl extends ServiceImpl<BookshelfBookMapper, BookshelfBook>
        implements BookshelfService {

    private final StringRedisTemplate stringRedisTemplate;
    private final CanonicalBookService canonicalBookService;

    /** 热门书籍 ZSET：成员为规范作品身份（旧 URL 缓存会在读取时迁移）。 */
    private static final String HOT_BOOKS_ZSET = "ranking:hot_books";
    private static final String CANONICAL_RANKING_PREFIX = "canonical:";

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addBook(long userId, AddToShelfDTO dto) {
        Long canonicalBookId = resolveCanonicalId(dto.getSourceId(), dto.getBookUrl(), dto.getBookName(), dto.getAuthor(), dto.getCoverUrl());
        // Canonical identity is the primary shelf key. URL matching remains a historical fallback.
        BookshelfBook existing = canonicalBookId == null ? null : lambdaQuery().eq(BookshelfBook::getUserId, userId)
                .eq(BookshelfBook::getCanonicalBookId, canonicalBookId).one();
        if (existing == null) existing = lambdaQuery().eq(BookshelfBook::getUserId, userId).eq(BookshelfBook::getBookUrl, dto.getBookUrl()).one();
        if (existing != null) {
            String previousBookUrl = existing.getBookUrl();
            String previousRankingMember = rankingMember(existing.getCanonicalBookId(), previousBookUrl);
            existing.setSourceId(dto.getSourceId());
            existing.setCanonicalBookId(canonicalBookId);
            existing.setSourceName(dto.getSourceName());
            existing.setBookName(dto.getBookName());
            existing.setAuthor(dto.getAuthor());
            existing.setCoverUrl(dto.getCoverUrl());
            existing.setBookUrl(dto.getBookUrl());
            updateById(existing);
            moveHotBookScore(previousRankingMember, rankingMember(canonicalBookId, dto.getBookUrl()));
            return;
        }
        BookshelfBook book = new BookshelfBook();
        book.setId(SnowflakeIdUtil.next());
        book.setUserId(userId);
        book.setSourceId(dto.getSourceId());
        book.setCanonicalBookId(canonicalBookId);
        book.setSourceName(dto.getSourceName());
        book.setBookName(dto.getBookName());
        book.setAuthor(dto.getAuthor());
        book.setCoverUrl(dto.getCoverUrl());
        book.setBookUrl(dto.getBookUrl());
        save(book);

        // 新书入架：热门排行 +1
        try {
            stringRedisTemplate.opsForZSet().incrementScore(HOT_BOOKS_ZSET, rankingMember(canonicalBookId, dto.getBookUrl()), 1);
        } catch (Exception e) {
            log.warn("更新热门书籍 ZSET 失败（不影响主流程）: bookUrl={}, err={}", dto.getBookUrl(), e.getMessage());
        }
    }

    private String rankingMember(Long canonicalBookId, String bookUrl) {
        return canonicalBookId == null ? bookUrl : CANONICAL_RANKING_PREFIX + canonicalBookId;
    }

    // Move an old source-url score only when its logical work identity changes.
    private void moveHotBookScore(String previousMember, String currentMember) {
        if (previousMember == null || previousMember.equals(currentMember)) return;
        try {
            Double score = stringRedisTemplate.opsForZSet().score(HOT_BOOKS_ZSET, previousMember);
            if (score != null && score > 0) {
                stringRedisTemplate.opsForZSet().incrementScore(HOT_BOOKS_ZSET, previousMember, -1);
                stringRedisTemplate.opsForZSet().incrementScore(HOT_BOOKS_ZSET, currentMember, 1);
            }
        } catch (Exception e) {
            log.warn("迁移热门书籍缓存失败（不影响主流程）: from={}, to={}, err={}", previousMember, currentMember, e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeBook(long userId, Long canonicalBookId, String bookUrl) {
        if (canonicalBookId == null && (bookUrl == null || bookUrl.isBlank())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "需要提供规范作品 ID 或书源地址");
        }
        BookshelfBook existing = canonicalBookId == null ? null : lambdaQuery()
                .eq(BookshelfBook::getUserId, userId).eq(BookshelfBook::getCanonicalBookId, canonicalBookId).one();
        if (existing == null && bookUrl != null && !bookUrl.isBlank()) existing = lambdaQuery()
                .eq(BookshelfBook::getUserId, userId).eq(BookshelfBook::getBookUrl, bookUrl).one();
        if (existing == null) throw new BusinessException(ResultCode.NOT_FOUND, "书架中没有该书");
        boolean removed = removeById(existing.getId());
        if (!removed) throw new BusinessException(ResultCode.NOT_FOUND, "书架中没有该书");

        // 移出书架：热门排行 -1（最低为 0）
        try {
            String rankingMember = rankingMember(existing.getCanonicalBookId(), existing.getBookUrl());
            Double score = stringRedisTemplate.opsForZSet().score(HOT_BOOKS_ZSET, rankingMember);
            if (score != null && score > 0) {
                stringRedisTemplate.opsForZSet().incrementScore(HOT_BOOKS_ZSET, rankingMember, -1);
            }
        } catch (Exception e) {
            log.warn("更新热门书籍 ZSET 失败（不影响主流程）: rankingMember={}, err={}", rankingMember(existing.getCanonicalBookId(), existing.getBookUrl()), e.getMessage());
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
            backfillCanonicalId(b);
            ShelfBookVO vo = new ShelfBookVO();
            BeanUtils.copyProperties(b, vo);
            return vo;
        }).toList());
        return result;
    }

    private void backfillCanonicalId(BookshelfBook book) {
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
    public boolean isOnShelf(long userId, String bookUrl) {
        return isOnShelf(userId, null, bookUrl);
    }

    @Override
    public boolean isOnShelf(long userId, Long canonicalBookId, String bookUrl) {
        if (canonicalBookId != null && lambdaQuery().eq(BookshelfBook::getUserId, userId)
                .eq(BookshelfBook::getCanonicalBookId, canonicalBookId).exists()) return true;
        return lambdaQuery()
                .eq(BookshelfBook::getUserId, userId)
                .eq(BookshelfBook::getBookUrl, bookUrl)
                .exists();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProgress(long userId, UpdateProgressDTO dto) {
        BookshelfBook existing = dto.getCanonicalBookId() == null ? null : lambdaQuery()
                .eq(BookshelfBook::getUserId, userId).eq(BookshelfBook::getCanonicalBookId, dto.getCanonicalBookId()).one();
        if (existing == null) existing = lambdaQuery().eq(BookshelfBook::getUserId, userId)
                .eq(BookshelfBook::getBookUrl, dto.getBookUrl()).one();
        if (existing == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "书架中没有该书，无法更新进度");
        }
        String previousRankingMember = rankingMember(existing.getCanonicalBookId(), existing.getBookUrl());
        if (dto.getSourceId() != null) existing.setSourceId(dto.getSourceId());
        if (StringUtils.hasText(dto.getSourceName())) existing.setSourceName(dto.getSourceName());
        if (StringUtils.hasText(dto.getBookUrl())) existing.setBookUrl(dto.getBookUrl());
        existing.setLastChapterName(dto.getChapterName());
        existing.setLastChapterUrl(dto.getChapterUrl());
        existing.setLastReadAt(LocalDateTime.now());
        if (dto.getChapterIndex() != null) existing.setLastChapterIndex(dto.getChapterIndex());
        if (dto.getTotalChapters() != null && dto.getTotalChapters() > 0) existing.setTotalChapters(dto.getTotalChapters());
        if (!updateById(existing)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "书架中没有该书，无法更新进度");
        }
        moveHotBookScore(previousRankingMember, rankingMember(existing.getCanonicalBookId(), existing.getBookUrl()));
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

        // 2. Canonical members are the normal path. URL members are from the pre-canonical
        // cache and are translated using their durable shelf row before being shown.
        List<Long> canonicalIds = new ArrayList<>();
        List<String> legacyUrls = new ArrayList<>();
        Map<String, Long> countMap = new LinkedHashMap<>();
        for (ZSetOperations.TypedTuple<String> t : tuples) {
            String member = t.getValue();
            Double score = t.getScore();
            if (member != null && score != null && score >= 1) {
                if (member.startsWith(CANONICAL_RANKING_PREFIX)) {
                    try {
                        canonicalIds.add(Long.parseLong(member.substring(CANONICAL_RANKING_PREFIX.length())));
                        countMap.put(member, score.longValue());
                    } catch (NumberFormatException ignored) {
                        try {
                            stringRedisTemplate.opsForZSet().remove(HOT_BOOKS_ZSET, member);
                        } catch (Exception e) {
                            log.debug("清理格式错误的热门书籍缓存失败: {}", e.getMessage());
                        }
                    }
                } else {
                    legacyUrls.add(member);
                }
            }
        }
        Map<Long, BookshelfBook> canonicalMeta = (canonicalIds.isEmpty() ? List.<BookshelfBook>of() : baseMapper.selectMetaByCanonicalIds(canonicalIds)).stream()
                .collect(Collectors.toMap(BookshelfBook::getCanonicalBookId, book -> book, (a, b) -> a));
        Map<String, BookshelfBook> legacyMeta = (legacyUrls.isEmpty() ? List.<BookshelfBook>of() : baseMapper.selectMetaByUrls(legacyUrls)).stream()
                .collect(Collectors.toMap(BookshelfBook::getBookUrl, book -> book, (a, b) -> a));
        for (String legacyUrl : legacyUrls) {
            BookshelfBook book = legacyMeta.get(legacyUrl);
            Double score = stringRedisTemplate.opsForZSet().score(HOT_BOOKS_ZSET, legacyUrl);
            if (book == null || score == null || score < 1) continue;
            String member = rankingMember(book.getCanonicalBookId(), legacyUrl);
            if (!member.equals(legacyUrl)) {
                try {
                    stringRedisTemplate.opsForZSet().incrementScore(HOT_BOOKS_ZSET, member, score);
                    stringRedisTemplate.opsForZSet().remove(HOT_BOOKS_ZSET, legacyUrl);
                } catch (Exception e) {
                    log.debug("迁移旧热门书籍缓存失败: {}", e.getMessage());
                }
            }
            countMap.merge(member, score.longValue(), Long::sum);
            if (book.getCanonicalBookId() != null) canonicalMeta.putIfAbsent(book.getCanonicalBookId(), book);
        }
        if (countMap.isEmpty()) return List.of();

        // 3. Redis can retain entries after data removal. Clear entries without a durable row.
        List<String> orphanedMembers = countMap.keySet().stream()
                .filter(member -> {
                    if (member.startsWith(CANONICAL_RANKING_PREFIX)) {
                        try { return !canonicalMeta.containsKey(Long.parseLong(member.substring(CANONICAL_RANKING_PREFIX.length()))); }
                        catch (NumberFormatException ignored) { return true; }
                    }
                    return !legacyMeta.containsKey(member);
                })
                .toList();
        if (!orphanedMembers.isEmpty()) {
            try {
                stringRedisTemplate.opsForZSet().remove(HOT_BOOKS_ZSET, orphanedMembers.toArray());
            } catch (Exception e) {
                log.debug("清理失效热门书籍缓存失败: {}", e.getMessage());
            }
        }

        // 4. 组装结果
        List<HotBookVO> result = new ArrayList<>();
        for (Map.Entry<String, Long> entry : countMap.entrySet()) {
            String member = entry.getKey();
            if (orphanedMembers.contains(member)) continue;
            BookshelfBook meta;
            Long canonicalBookId = null;
            if (member.startsWith(CANONICAL_RANKING_PREFIX)) {
                canonicalBookId = Long.parseLong(member.substring(CANONICAL_RANKING_PREFIX.length()));
                meta = canonicalMeta.get(canonicalBookId);
            } else {
                meta = legacyMeta.get(member);
                canonicalBookId = meta == null ? null : meta.getCanonicalBookId();
            }
            if (meta == null) continue;
            HotBookVO vo = new HotBookVO();
            vo.setCanonicalBookId(canonicalBookId);
            vo.setBookUrl(meta.getBookUrl());
            vo.setShelfCount(entry.getValue());
            vo.setBookName(meta.getBookName());
            vo.setAuthor(meta.getAuthor());
            vo.setCoverUrl(meta.getCoverUrl());
            vo.setSourceId(meta.getSourceId());
            vo.setSourceName(meta.getSourceName());
            result.add(vo);
        }
        // Re-number after orphan filtering so the visible list has contiguous ranks.
        for (int index = 0; index < result.size(); index++) result.get(index).setRank(index + 1);
        if (result.isEmpty()) {
            // A stale Redis ranking may contain only deleted/test rows. Rebuild from the durable
            // bookshelf table instead of showing an empty ranking after cache cleanup.
            List<HotBookVO> dbResult = baseMapper.selectHotBooks(limit);
            for (int index = 0; index < dbResult.size(); index++) dbResult.get(index).setRank(index + 1);
            return dbResult;
        }
        return result;
    }
}
