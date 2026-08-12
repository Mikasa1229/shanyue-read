import http from './http'

/** 服务端验证阅读会话：排行与积分只采纳可见页面心跳。 */
export const apiStartReadingSession = (bookUrl) => http.post('/reading/sessions', { bookUrl })
export const apiReadingHeartbeat = (sessionToken, pageVisible) => http.post('/reading/sessions/heartbeat', { sessionToken, pageVisible })

/** 获取阅读时长排行榜 */
export const apiGetRanking = (top = 50) => http.get('/reading/ranking', { params: { top } })

/** 获取热门书籍排行榜（按加入书架用户数） */
export const apiGetHotBooks = (top = 20) => http.get('/bookshelf/hot', { params: { top } })
