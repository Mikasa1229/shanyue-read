import http from './http'

// ─── 导入 ───────────────────────────────────────────────────────
export const apiImportByUrl  = (url)  => http.post('/book-sources/import/url',  { url })
export const apiImportByJson = (json) => http.post('/book-sources/import/json', { json })

// ─── 管理 ───────────────────────────────────────────────────────
export const apiListSources    = (page = 1, size = 20) => http.get('/book-sources', { params: { page, size } })
export const apiToggleSource   = (id) => http.put(`/book-sources/${id}/status`)
export const apiDeleteSource   = (id) => http.delete(`/book-sources/${id}`)

// ─── 搜索 / 章节 / 正文 ─────────────────────────────────────────
// 聚合搜索（所有启用书源并发）
export const apiAggregateSearch = (keyword, page = 1) => http.get('/book-sources/search', { params: { keyword, page } })
// 指定书源搜索
export const apiSearchBooks    = (id, keyword, page = 1) => http.get(`/book-sources/${id}/search`,   { params: { keyword, page } })
export const apiGetChapters    = (id, bookUrl)            => http.get(`/book-sources/${id}/chapters`, { params: { bookUrl } })
export const apiGetContent     = (id, chapterUrl)         => http.get(`/book-sources/${id}/content`,  { params: { chapterUrl } })
export const apiTestSource     = (id)                     => http.get(`/book-sources/${id}/test`)
