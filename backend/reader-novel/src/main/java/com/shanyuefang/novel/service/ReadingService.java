package com.shanyuefang.novel.service;

import com.shanyuefang.novel.domain.vo.RankingVO;

import java.util.List;

public interface ReadingService {

    /**
     * 记录用户本次阅读时长，累加到 Redis ZSET 排行榜
     *
     * @param userId  用户 ID
     * @param seconds 本次阅读秒数
     */
    void record(long userId, int seconds);

    /**
     * 获取阅读时长排行榜 Top N
     *
     * @param top 返回前 N 名，最大 100
     * @return 排行列表（含用户信息）
     */
    List<RankingVO> getRanking(int top);
}
