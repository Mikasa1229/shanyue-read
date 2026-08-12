package com.shanyuefang.novel.controller;

import com.shanyuefang.novel.service.BookshelfService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BookshelfControllerTest {
    @Mock private BookshelfService bookshelfService;

    @Test
    void removePassesCanonicalIdentityAndUrlForSourceSwitchCompatibility() {
        controller().remove(7L, 88L, "https://mirror.example/book");

        verify(bookshelfService).removeBook(7L, 88L, "https://mirror.example/book");
    }

    private BookshelfController controller() {
        return new BookshelfController(bookshelfService);
    }
}
