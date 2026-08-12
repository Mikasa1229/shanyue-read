package com.shanyuefang.agent.service;

import com.shanyuefang.agent.domain.dto.SaveShelfGroupDTO;
import java.util.List;
import java.util.Map;

public interface ShelfGroupService {
    List<Map<String, Object>> groups(long userId);
    void save(long userId, SaveShelfGroupDTO dto);
}
