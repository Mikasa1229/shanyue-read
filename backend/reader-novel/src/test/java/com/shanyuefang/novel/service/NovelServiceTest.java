package com.shanyuefang.novel.service;

import com.shanyuefang.common.exception.BusinessException;
import com.shanyuefang.novel.domain.dto.CreateNovelDTO;
import com.shanyuefang.novel.domain.dto.UpdateNovelDTO;
import com.shanyuefang.novel.domain.entity.Novel;
import com.shanyuefang.novel.domain.vo.NovelSimpleVO;
import com.shanyuefang.novel.domain.vo.NovelVO;
import com.shanyuefang.novel.mapper.NovelMapper;
import com.shanyuefang.novel.service.impl.NovelServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("小说服务单元测试")
class NovelServiceTest {

    @Mock NovelMapper novelMapper;
    @Mock RedisTemplate<String, Object> redisTemplate;
    @Mock ValueOperations<String, Object> valueOps;

    NovelServiceImpl novelService;

    @BeforeEach
    void setUp() {
        novelService = new NovelServiceImpl(redisTemplate);
        ReflectionTestUtils.setField(novelService, "baseMapper", novelMapper);
        ReflectionTestUtils.setField(novelService, "entityClass", Novel.class);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // ── create ────────────────────────────────────────────────

    @Test
    @DisplayName("创建小说 - 保存成功并返回 VO")
    void create_savesNovelAndReturnsVO() {
        CreateNovelDTO dto = new CreateNovelDTO();
        dto.setTitle("测试小说");
        dto.setAuthorName("作者甲");
        dto.setCategory("都市");
        dto.setStatus(1);

        when(novelMapper.insert(any(Novel.class))).thenReturn(1);

        NovelVO vo = novelService.create(1L, dto);

        assertThat(vo.getTitle()).isEqualTo("测试小说");
        assertThat(vo.getUserId()).isEqualTo(1L);
        verify(novelMapper).insert(argThat((Novel n) -> "测试小说".equals(n.getTitle()) && Long.valueOf(1L).equals(n.getUserId())));
    }

    // ── update ────────────────────────────────────────────────

    @Test
    @DisplayName("更新小说 - 操作者非作者时抛出 FORBIDDEN")
    void update_whenNotOwner_throwsForbidden() {
        Novel novel = mockNovel(10L, 99L);
        when(novelMapper.selectById(10L)).thenReturn(novel);

        UpdateNovelDTO dto = new UpdateNovelDTO();
        dto.setTitle("新标题");

        assertThatThrownBy(() -> novelService.update(1L, 10L, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权操作");
    }

    @Test
    @DisplayName("更新小说 - 操作者是作者时更新成功并清除缓存")
    void update_whenOwner_updatesAndEvictsCache() {
        Novel novel = mockNovel(10L, 1L);
        when(novelMapper.selectById(10L)).thenReturn(novel);
        when(novelMapper.updateById(any(Novel.class))).thenReturn(1);

        UpdateNovelDTO dto = new UpdateNovelDTO();
        dto.setTitle("新标题");

        NovelVO vo = novelService.update(1L, 10L, dto);

        assertThat(vo.getTitle()).isEqualTo("新标题");
        verify(redisTemplate).delete("novel:detail:10");
    }

    // ── delete ────────────────────────────────────────────────

    @Test
    @DisplayName("删除小说 - 小说不存在时抛出 NOT_FOUND")
    void delete_whenNotFound_throwsNotFound() {
        when(novelMapper.selectById(any())).thenReturn(null);

        assertThatThrownBy(() -> novelService.delete(1L, 999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("小说不存在");
    }

    @Test
    @DisplayName("删除小说 - 操作者是作者时删除成功并清除缓存")
    void delete_whenOwner_deletesAndEvictsCache() {
        Novel novel = mockNovel(10L, 1L);
        when(novelMapper.selectById(10L)).thenReturn(novel);
        when(novelMapper.deleteById(any(Long.class))).thenReturn(1);

        novelService.delete(1L, 10L);

        verify(novelMapper).deleteById(10L);
        verify(redisTemplate).delete("novel:detail:10");
    }

    // ── getDetail ─────────────────────────────────────────────

    @Test
    @DisplayName("获取详情 - 缓存命中时直接返回，不查 DB")
    void getDetail_whenCacheHit_returnsCached() {
        NovelVO cached = new NovelVO();
        cached.setId(5L);
        when(valueOps.get("novel:detail:5")).thenReturn(cached);

        NovelVO result = novelService.getDetail(5L);

        assertThat(result).isSameAs(cached);
        verify(novelMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("获取详情 - 缓存未命中时查 DB 并回填缓存")
    void getDetail_whenCacheMiss_queriesDbAndCaches() {
        Novel novel = mockNovel(5L, 1L);
        when(valueOps.get("novel:detail:5")).thenReturn(null);
        when(novelMapper.selectById(5L)).thenReturn(novel);

        NovelVO result = novelService.getDetail(5L);

        assertThat(result.getId()).isEqualTo(5L);
        verify(valueOps).set(eq("novel:detail:5"), any(), eq(10L), eq(TimeUnit.MINUTES));
    }

    @Test
    @DisplayName("获取详情 - 小说不存在时抛出 NOT_FOUND")
    void getDetail_whenNotFound_throwsNotFound() {
        when(valueOps.get(any())).thenReturn(null);
        when(novelMapper.selectById(any())).thenReturn(null);

        assertThatThrownBy(() -> novelService.getDetail(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("小说不存在");
    }

    // ── batchGet ──────────────────────────────────────────────

    @Test
    @DisplayName("批量获取 - 空列表时不查 DB 直接返回空 Map")
    void batchGet_whenEmptyIds_returnsEmptyMap() {
        Map<Long, NovelSimpleVO> result = novelService.batchGet(Collections.emptyList());

        assertThat(result).isEmpty();
        verify(novelMapper, never()).selectBatchIds(any());
    }

    @Test
    @DisplayName("批量获取 - 返回有效小说且过滤已删除")
    void batchGet_filtersDeletedNovels() {
        Novel active = mockNovel(1L, 10L);
        Novel deleted = mockNovel(2L, 10L);
        deleted.setDeleted(true);

        when(novelMapper.selectBatchIds(anyList())).thenReturn(java.util.List.of(active, deleted));

        Map<Long, NovelSimpleVO> result = novelService.batchGet(java.util.List.of(1L, 2L));

        assertThat(result).containsKey(1L).doesNotContainKey(2L);
    }

    // ── updateLikeCount ───────────────────────────────────────

    @Test
    @DisplayName("更新点赞数 - 调用 mapper 并清除缓存")
    void updateLikeCount_updatesAndEvictsCache() {
        doNothing().when(novelMapper).updateLikeCount(10L, 1);

        novelService.updateLikeCount(10L, 1);

        verify(novelMapper).updateLikeCount(10L, 1);
        verify(redisTemplate).delete("novel:detail:10");
    }

    // ── helper ────────────────────────────────────────────────

    private Novel mockNovel(Long id, Long userId) {
        Novel n = new Novel();
        n.setId(id);
        n.setUserId(userId);
        n.setTitle("默认标题");
        n.setAuthorName("作者");
        n.setStatus(1);
        n.setWordCount(0L);
        n.setViewCount(0L);
        n.setLikeCount(0L);
        n.setFavoriteCount(0L);
        n.setDeleted(false);
        return n;
    }
}
