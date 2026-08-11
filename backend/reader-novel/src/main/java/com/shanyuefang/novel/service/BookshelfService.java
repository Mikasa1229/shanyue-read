package com.shanyuefang.novel.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shanyuefang.novel.domain.dto.AddToShelfDTO;
import com.shanyuefang.novel.domain.dto.UpdateProgressDTO;
import com.shanyuefang.novel.domain.vo.HotBookVO;
import com.shanyuefang.novel.domain.vo.ShelfBookVO;

import java.util.List;

public interface BookshelfService {
    void addBook(long userId, AddToShelfDTO dto);
    void removeBook(long userId, String bookUrl);
    Page<ShelfBookVO> listMyShelf(long userId, int page, int size);
    boolean isOnShelf(long userId, String bookUrl);
    boolean isOnShelf(long userId, Long canonicalBookId, String bookUrl);
    void updateProgress(long userId, UpdateProgressDTO dto);
    List<HotBookVO> getHotBooks(int top);
}
