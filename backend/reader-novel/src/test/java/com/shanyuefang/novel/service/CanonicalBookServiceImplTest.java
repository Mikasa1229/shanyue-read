package com.shanyuefang.novel.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.shanyuefang.novel.domain.dto.ReviewCanonicalMergeDTO;
import com.shanyuefang.novel.domain.entity.CanonicalBook;
import com.shanyuefang.novel.domain.entity.CanonicalMergeReview;
import com.shanyuefang.novel.domain.entity.BookSourceMapping;
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
        ReviewCanonicalMergeDTO request = new ReviewCanonicalMergeDTO(); request.setAction("APPROVE");

        fixture.service.reviewMerge(12L, request);

        assertThat(source.getMergeStatus()).isEqualTo("MERGED");
        assertThat(review.getStatus()).isEqualTo("APPROVED");
        verify(fixture.mappings).update(org.mockito.ArgumentMatchers.isNull(), any(Wrapper.class));
        verify(fixture.shelves).update(org.mockito.ArgumentMatchers.isNull(), any(Wrapper.class));
        verify(fixture.favorites).update(org.mockito.ArgumentMatchers.isNull(), any(Wrapper.class));
        verify(fixture.books).updateById(source);
        verify(fixture.reviews).updateById(review);
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
