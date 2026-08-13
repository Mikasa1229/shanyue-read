package com.shanyuefang.novel.service.impl;

import com.shanyuefang.novel.domain.entity.BookSource;
import com.shanyuefang.novel.domain.entity.UserBookSourcePreference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookSourceEnablementTest {

    @Test
    void platformDefaultAppliesUntilUserOverridesIt() {
        BookSource source = new BookSource();
        source.setEnabled(true);
        assertTrue(BookSourceServiceImpl.effectiveEnabled(source, null));

        UserBookSourcePreference disabled = new UserBookSourcePreference();
        disabled.setDisabled(true);
        assertFalse(BookSourceServiceImpl.effectiveEnabled(source, disabled));
    }

    @Test
    void userCanEnableAPlatformDisabledSource() {
        BookSource source = new BookSource();
        source.setEnabled(false);
        assertFalse(BookSourceServiceImpl.effectiveEnabled(source, null));

        UserBookSourcePreference enabled = new UserBookSourcePreference();
        enabled.setDisabled(false);
        assertTrue(BookSourceServiceImpl.effectiveEnabled(source, enabled));
    }
}
