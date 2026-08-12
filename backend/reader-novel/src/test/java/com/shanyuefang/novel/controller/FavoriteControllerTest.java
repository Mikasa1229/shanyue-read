package com.shanyuefang.novel.controller;

import com.shanyuefang.novel.service.FavoriteService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FavoriteControllerTest {
    @Mock private FavoriteService favoriteService;

    @Test
    void removePassesCanonicalIdentityAndUrlForSourceSwitchCompatibility() {
        controller().remove(7L, 88L, "https://mirror.example/book");

        verify(favoriteService).removeFavorite(7L, 88L, "https://mirror.example/book");
    }

    private FavoriteController controller() {
        return new FavoriteController(favoriteService);
    }
}
