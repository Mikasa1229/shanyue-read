import http from './http'

/** 记录本次阅读时长（秒） */
export const apiRecordReading = (seconds) => http.post('/reading/record', { seconds })

/** 获取阅读时长排行榜 */
export const apiGetRanking = (top = 50) => http.get('/reading/ranking', { params: { top } })
