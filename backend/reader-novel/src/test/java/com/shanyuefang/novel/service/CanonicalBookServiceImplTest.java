package com.shanyuefang.novel.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.shanyuefang.novel.domain.dto.ReviewCanonicalMergeDTO;
import com.shanyuefang.novel.domain.entity.CanonicalBook;
import com.shanyuefang.novel.domain.entity.CanonicalMergeReview;
import com.shanyuefang.novel.domain.entity.BookSourceMapping;
import com.shanyuefang.novel.domain.entity.BookshelfBook;
import com.shanyuefang.novel.domain.entity.FavoriteBook;
import com.shanyuefang.novel.mapper.BookSourceMappingMapper;
import com.shanyuefang.novel.mapper.BookshelfBookMapper;
import com.shanyuefang.novel.mapper.CanonicalBookMapper;
import com.shanyuefang.novel.mapper.CanonicalMergeReviewMapper;
import com.shanyuefang.novel.mapper.FavoriteBookMapper;
import com.shanyuefang.novel.mapper.BookContentVersionMapper;
import com.shanyuefang.novel.service.impl.CanonicalBookServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CanonicalBookServiceImplTest {
    @org.junit.jupiter.api.BeforeAll
    static void initializeMyBatisLambdaMetadata() {
        initialize(com.shanyuefang.novel.domain.entity.BookSourceMapping.class);
        initialize(com.shanyuefang.novel.domain.entity.BookshelfBook.class);
        initialize(com.shanyuefang.novel.domain.entity.FavoriteBook.class);
    }

    @Test
    void approvedMergeRedirectsPersonalRecordsToTheRetainedWork() {
        Fixture fixture = fixture();
        CanonicalMergeReview review = new CanonicalMergeReview();
        review.setId(12L); review.setStatus("PENDING"); review.setSourceCanonicalBookId(41L); review.setCandidateCanonicalBookId(42L);
        CanonicalBook source = new CanonicalBook(); source.setId(41L);
        when(fixture.reviews.selectById(12L)).thenReturn(review);
        when(fixture.books.selectById(41L)).thenReturn(source);
        BookshelfBook shelf = new BookshelfBook(); shelf.setId(101L); shelf.setUserId(7L); shelf.setCanonicalBookId(41L);
        FavoriteBook favorite = new FavoriteBook(); favorite.setId(102L); favorite.setUserId(7L); favorite.setCanonicalBookId(41L);
        when(fixture.shelves.selectList(any(Wrapper.class))).thenReturn(List.of(shelf));
        when(fixture.favorites.selectList(any(Wrapper.class))).thenReturn(List.of(favorite));
        ReviewCanonicalMergeDTO request = new ReviewCanonicalMergeDTO(); request.setAction("APPROVE");

        fixture.service.reviewMerge(12L, request);

        assertThat(source.getMergeStatus()).isEqualTo("MERGED");
        assertThat(review.getStatus()).isEqualTo("APPROVED");
        assertThat(shelf.getCanonicalBookId()).isEqualTo(42L);
        assertThat(favorite.getCanonicalBookId()).isEqualTo(42L);
        verify(fixture.mappings).update(org.mockito.ArgumentMatchers.isNull(), any(Wrapper.class));
        verify(fixture.shelves).updateById(shelf);
        verify(fixture.favorites).updateById(favorite);
        verify(fixture.books).updateById(source);
        verify(fixture.reviews).updateById(review);
    }

    @Test
    void approvedMergeRemovesTheOlderDuplicateBeforeChangingCanonicalIdentity() {
        Fixture fixture = fixture();
        CanonicalMergeReview review = new CanonicalMergeReview();
        review.setId(12L); review.setStatus("PENDING"); review.setSourceCanonicalBookId(41L); review.setCandidateCanonicalBookId(42L);
        CanonicalBook source = new CanonicalBook(); source.setId(41L);
        BookshelfBook sourceShelf = new BookshelfBook(); sourceShelf.setId(101L); sourceShelf.setUserId(7L); sourceShelf.setCanonicalBookId(41L);
        sourceShelf.setCreatedAt(java.time.LocalDateTime.now());
        BookshelfBook targetShelf = new BookshelfBook(); targetShelf.setId(102L); targetShelf.setUserId(7L); targetShelf.setCanonicalBookId(42L);
        targetShelf.setCreatedAt(java.time.LocalDateTime.now().minusMinutes(1));
        FavoriteBook sourceFavorite = new FavoriteBook(); sourceFavorite.setId(201L); sourceFavorite.setUserId(7L); sourceFavorite.setCanonicalBookId(41L);
        sourceFavorite.setCreatedAt(java.time.LocalDateTime.now());
        FavoriteBook targetFavorite = new FavoriteBook(); targetFavorite.setId(202L); targetFavorite.setUserId(7L); targetFavorite.setCanonicalBookId(42L);
        targetFavorite.setCreatedAt(java.time.LocalDateTime.now().minusMinutes(1));
        when(fixture.reviews.selectById(12L)).thenReturn(review);
        when(fixture.books.selectById(41L)).thenReturn(source);
        when(fixture.shelves.selectList(any(Wrapper.class))).thenReturn(List.of(sourceShelf));
        when(fixture.shelves.selectOne(any(Wrapper.class))).thenReturn(targetShelf);
        when(fixture.favorites.selectList(any(Wrapper.class))).thenReturn(List.of(sourceFavorite));
        when(fixture.favorites.selectOne(any(Wrapper.class))).thenReturn(targetFavorite);
        ReviewCanonicalMergeDTO request = new ReviewCanonicalMergeDTO(); request.setAction("APPROVE");

        fixture.service.reviewMerge(12L, request);

        verify(fixture.shelves).deleteById(102L);
        verify(fixture.shelves).updateById(sourceShelf);
        verify(fixture.favorites).deleteById(202L);
        verify(fixture.favorites).updateById(sourceFavorite);
    }

    @Test
    void detachingTheLastSourceClearsDanglingPersonalCanonicalReferences() {
        Fixture fixture = fixture();
        BookSourceMapping mapping = new BookSourceMapping(); mapping.setCanonicalBookId(41L);
        when(fixture.mappings.selectList(any(Wrapper.class))).thenReturn(List.of(mapping));
        when(fixture.mappings.selectCount(any(Wrapper.class))).thenReturn(0L);

        List<Long> orphaned = fixture.service.detachSource(9L);

        assertThat(orphaned).containsExactly(41L);
        verify(fixture.mappings).delete(any(Wrapper.class));
        verify(fixture.contentVersions).delete(any(Wrapper.class));
        verify(fixture.books).deleteById(41L);
        verify(fixture.shelves).update(org.mockito.ArgumentMatchers.isNull(), any(Wrapper.class));
        verify(fixture.favorites).update(org.mockito.ArgumentMatchers.isNull(), any(Wrapper.class));
    }

    private Fixture fixture() {
        CanonicalBookMapper books = mock(CanonicalBookMapper.class);
        BookSourceMappingMapper mappings = mock(BookSourceMappingMapper.class);
        CanonicalMergeReviewMapper reviews = mock(CanonicalMergeReviewMapper.class);
        BookshelfBookMapper shelves = mock(BookshelfBookMapper.class);
        FavoriteBookMapper favorites = mock(FavoriteBookMapper.class);
        BookContentVersionMapper contentVersions = mock(BookContentVersionMapper.class);
        return new Fixture(new CanonicalBookServiceImpl(books, mappings, reviews, shelves, favorites, contentVersions), books, mappings, reviews, shelves, favorites, contentVersions);
    }

    private static void initialize(Class<?> entityType) {
        if (TableInfoHelper.getTableInfo(entityType) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), entityType.getName()), entityType);
        }
    }

    private record Fixture(CanonicalBookServiceImpl service, CanonicalBookMapper books, BookSourceMappingMapper mappings,
                           CanonicalMergeReviewMapper reviews, BookshelfBookMapper shelves, FavoriteBookMapper favorites,
                           BookContentVersionMapper contentVersions) { }
}
