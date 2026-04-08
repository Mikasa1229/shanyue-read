import http from './http'

/** 记录本次阅读时长（秒） */
export const apiRecordReading = (seconds) => http.post('/reading/record', { seconds })

/** 获取阅读时长排行榜 */
export const apiGetRanking = (top = 50) => http.get('/reading/ranking', { params: { top } })

/** 获取热门书籍排行榜（按加入书架用户数） */
export const apiGetHotBooks = (top = 20) => http.get('/bookshelf/hot', { params: { top } })
