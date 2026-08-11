package com.shanyuefang.novel.controller;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.shanyuefang.novel.domain.dto.ContentVersionStatusDTO;
import com.shanyuefang.novel.domain.entity.BookContentVersion;
import com.shanyuefang.novel.service.ContentRecoveryService;
import com.shanyuefang.novel.mapper.BookContentVersionMapper;
import com.shanyuefang.novel.service.NovelInternalAccess;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InternalContentVersionControllerTest {
    @org.junit.jupiter.api.BeforeAll
    static void initializeMyBatisLambdaMetadata() {
        if (TableInfoHelper.getTableInfo(BookContentVersion.class) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), BookContentVersion.class.getName()), BookContentVersion.class);
        }
    }

    @Test
    void missingLedgerVersionIsReportedRatherThanSilentlyAcknowledged() {
        NovelInternalAccess access = mock(NovelInternalAccess.class);
        BookContentVersionMapper versions = mock(BookContentVersionMapper.class);
        when(versions.update(org.mockito.ArgumentMatchers.isNull(), any(Wrapper.class))).thenReturn(0);

        int code = new InternalContentVersionController(access, versions, mock(ContentRecoveryService.class)).updateStatus("internal", request()).getCode();

        assertThat(code).isEqualTo(404);
        verify(access).require("internal");
    }

    @Test
    void existingLedgerVersionAcknowledgesTheStatusTransition() {
        NovelInternalAccess access = mock(NovelInternalAccess.class);
        BookContentVersionMapper versions = mock(BookContentVersionMapper.class);
        when(versions.update(org.mockito.ArgumentMatchers.isNull(), any(Wrapper.class))).thenReturn(1);

        int code = new InternalContentVersionController(access, versions, mock(ContentRecoveryService.class)).updateStatus("internal", request()).getCode();

        assertThat(code).isEqualTo(200);
    }

    private ContentVersionStatusDTO request() {
        ContentVersionStatusDTO dto = new ContentVersionStatusDTO();
        dto.setCanonicalBookId(88L); dto.setChapterIndex(4); dto.setContentHash("hash-v1"); dto.setIndexStatus("ready");
        return dto;
    }
}
