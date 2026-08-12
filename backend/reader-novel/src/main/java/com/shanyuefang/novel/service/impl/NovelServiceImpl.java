package com.shanyuefang.novel.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shanyuefang.common.exception.BusinessException;
import com.shanyuefang.common.result.ResultCode;
import com.shanyuefang.common.util.SnowflakeIdUtil;
import com.shanyuefang.novel.domain.dto.CreateNovelDTO;
import com.shanyuefang.novel.domain.dto.NovelPageDTO;
import com.shanyuefang.novel.domain.dto.UpdateNovelDTO;
import com.shanyuefang.novel.domain.entity.Novel;
import com.shanyuefang.novel.domain.vo.NovelSimpleVO;
import com.shanyuefang.novel.domain.vo.NovelVO;
import com.shanyuefang.novel.mapper.NovelMapper;
import com.shanyuefang.novel.service.NovelService;
import com.shanyuefang.novel.util.CoverSnapshotUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NovelServiceImpl extends ServiceImpl<NovelMapper, Novel> implements NovelService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final CoverSnapshotUtil coverSnapshotUtil;

    /** 小说详情缓存 key: novel:detail:{id} */
    private static final String DETAIL_KEY = "novel:detail:%d";
    /** 浏览量防刷：novel:viewed:{ip_hash}:{id}，TTL 10分钟 */
    private static final String VIEW_KEY   = "novel:viewed:%s:%d";

    // ─── 写操作 ───────────────────────────────────────────────

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NovelVO create(Long userId, CreateNovelDTO dto) {
        Novel novel = new Novel();
        novel.setId(SnowflakeIdUtil.next());
        novel.setUserId(userId);
        novel.setTitle(dto.getTitle());
        novel.setAuthorName(dto.getAuthorName());
        novel.setCategory(dto.getCategory());
        novel.setCoverUrl(coverSnapshotUtil.snapshot(dto.getCoverUrl()));
        novel.setSummary(dto.getSummary());
        novel.setStatus(dto.getStatus());
        novel.setWordCount(0L);
        novel.setViewCount(0L);
        novel.setLikeCount(0L);
        novel.setFavoriteCount(0L);
        save(novel);
        log.info("发布小说: userId={}, title={}", userId, dto.getTitle());
        return toVO(novel);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NovelVO update(Long userId, Long id, UpdateNovelDTO dto) {
        Novel novel = getAndCheckOwner(id, userId);
        if (StringUtils.hasText(dto.getTitle()))      novel.setTitle(dto.getTitle());
        if (StringUtils.hasText(dto.getAuthorName())) novel.setAuthorName(dto.getAuthorName());
        if (StringUtils.hasText(dto.getCategory()))   novel.setCategory(dto.getCategory());
        if (dto.getCoverUrl() != null)                novel.setCoverUrl(coverSnapshotUtil.snapshot(dto.getCoverUrl()));
        if (dto.getSummary()  != null)                novel.setSummary(dto.getSummary());
        if (dto.getStatus()   != null)                novel.setStatus(dto.getStatus());
        updateById(novel);
        evictCache(id);
        return toVO(novel);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long id) {
        getAndCheckOwner(id, userId);
        removeById(id);
        evictCache(id);
    }

    // ─── 读操作 ───────────────────────────────────────────────

    @Override
    public Page<NovelVO> getPage(NovelPageDTO dto) {
        LambdaQueryWrapper<Novel> wrapper = new LambdaQueryWrapper<Novel>()
                .eq(Novel::getDeleted, false)
                .orderByDesc(Novel::getCreatedAt);

        if (StringUtils.hasText(dto.getCategory())) {
            wrapper.eq(Novel::getCategory, dto.getCategory());
        }
        if (StringUtils.hasText(dto.getKeyword())) {
            String kw = "%" + dto.getKeyword() + "%";
            wrapper.and(w -> w.like(Novel::getTitle, dto.getKeyword())
                              .or().like(Novel::getAuthorName, dto.getKeyword()));
        }

        Page<Novel> rawPage = page(new Page<>(dto.getPage(), dto.getSize()), wrapper);
        Page<NovelVO> result = new Page<>(rawPage.getCurrent(), rawPage.getSize(), rawPage.getTotal());
        result.setRecords(rawPage.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
        return result;
    }

    @Override
    public NovelVO getDetail(Long id) {
        String key = String.format(DETAIL_KEY, id);

        // L2 Redis 缓存
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached instanceof NovelVO vo) {
            incrementViewAsync(id);
            return vo;
        }

        Novel novel = getById(id);
        if (novel == null || Boolean.TRUE.equals(novel.getDeleted())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "小说不存在");
        }

        NovelVO vo = toVO(novel);
        redisTemplate.opsForValue().set(key, vo, 10, TimeUnit.MINUTES);

        incrementViewAsync(id);
        return vo;
    }

    @Override
    public Page<NovelVO> getMyNovels(Long userId, int page, int size) {
        Page<Novel> rawPage = lambdaQuery()
                .eq(Novel::getUserId, userId)
                .eq(Novel::getDeleted, false)
                .orderByDesc(Novel::getCreatedAt)
                .page(new Page<>(page, size));
        Page<NovelVO> result = new Page<>(rawPage.getCurrent(), rawPage.getSize(), rawPage.getTotal());
        result.setRecords(rawPage.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
        return result;
    }

    @Override
    public Map<Long, NovelSimpleVO> batchGet(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return Collections.emptyMap();
        return listByIds(ids).stream()
                .filter(n -> !Boolean.TRUE.equals(n.getDeleted()))
                .collect(Collectors.toMap(Novel::getId, this::toSimpleVO));
    }

    // ─── 计数更新（MQ 回调） ──────────────────────────────────

    @Override
    public void updateLikeCount(Long novelId, int delta) {
        baseMapper.updateLikeCount(novelId, delta);
        evictCache(novelId);
    }

    @Override
    public void updateFavoriteCount(Long novelId, int delta) {
        baseMapper.updateFavoriteCount(novelId, delta);
        evictCache(novelId);
    }

    // ─── 私有方法 ─────────────────────────────────────────────

    private Novel getAndCheckOwner(Long id, Long userId) {
        Novel novel = getById(id);
        if (novel == null || Boolean.TRUE.equals(novel.getDeleted())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "小说不存在");
        }
        if (!novel.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作");
        }
        return novel;
    }

    /** 异步累加浏览量，用 Redis 对同一小说每分钟限频 */
    private void incrementViewAsync(Long id) {
        try {
            String throttleKey = "novel:view:throttle:" + id;
            Boolean absent = redisTemplate.opsForValue().setIfAbsent(throttleKey, 1, 1, TimeUnit.MINUTES);
            if (Boolean.TRUE.equals(absent)) {
                baseMapper.incrementViewCount(id);
            }
        } catch (Exception e) {
            log.warn("浏览量累加异常: novelId={}", id, e);
        }
    }

    private void evictCache(Long id) {
        redisTemplate.delete(String.format(DETAIL_KEY, id));
    }

    private NovelVO toVO(Novel n) {
        NovelVO vo = new NovelVO();
        vo.setId(n.getId());
        vo.setUserId(n.getUserId());
        vo.setTitle(n.getTitle());
        vo.setAuthorName(n.getAuthorName());
        vo.setCategory(n.getCategory());
        vo.setCoverUrl(n.getCoverUrl());
        vo.setSummary(n.getSummary());
        vo.setStatus(n.getStatus());
        vo.setStatusLabel(n.getStatus() != null && n.getStatus() == 2 ? "已完结" : "连载中");
        vo.setWordCount(n.getWordCount());
        vo.setViewCount(n.getViewCount());
        vo.setLikeCount(n.getLikeCount());
        vo.setFavoriteCount(n.getFavoriteCount());
        vo.setCreatedAt(n.getCreatedAt());
        vo.setUpdatedAt(n.getUpdatedAt());
        return vo;
    }

    private NovelSimpleVO toSimpleVO(Novel n) {
        NovelSimpleVO vo = new NovelSimpleVO();
        vo.setId(n.getId());
        vo.setTitle(n.getTitle());
        vo.setAuthorName(n.getAuthorName());
        vo.setCategory(n.getCategory());
        vo.setCoverUrl(n.getCoverUrl());
        vo.setViewCount(n.getViewCount());
        return vo;
    }
}
