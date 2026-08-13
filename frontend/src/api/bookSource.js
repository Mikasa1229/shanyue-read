import http, { getAuthHeaders } from './http'

// ─── 导入 ───────────────────────────────────────────────────────
export const apiImportByUrl  = (url)  => http.post('/book-sources/import/url',  { url })
export const apiImportByJson = (json) => http.post('/book-sources/import/json', { json })

// ─── 管理 ───────────────────────────────────────────────────────
// The source catalog reads per-user enablement preferences, so keep its
// authentication explicit even if a future request client bypasses interceptors.
export const apiListSources = (page = 1, size = 20) => http.get('/book-sources', {
  params: { page, size },
  headers: getAuthHeaders()
})
export const apiToggleSource   = (id) => http.put(`/book-sources/${id}/status`)
export const apiDeleteSource   = (id) => http.delete(`/book-sources/${id}`)

// ─── 搜索 / 章节 / 正文 ─────────────────────────────────────────
// 聚合搜索（所有启用书源并发）
// Aggregate search waits for several remote book sources; allow it to outlive
// the 10s default used by lightweight API requests.
export const apiAggregateSearch = (keyword, page = 1) => http.get('/book-sources/search', {
  params: { keyword, page },
  timeout: 20000
})
// Canonical aggregation keeps all mirrors of a work instead of discarding duplicate source hits.
export const apiAggregateCanonicalSearch = (keyword, page = 1) => http.get('/book-sources/aggregate-search', {
  params: { keyword, page },
  timeout: 20000
})
export const apiGetCanonicalSources = (canonicalBookId) => http.get(`/book-sources/canonical/${canonicalBookId}/sources`)
// 指定书源搜索
export const apiSearchBooks    = (id, keyword, page = 1) => http.get(`/book-sources/${id}/search`,   { params: { keyword, page } })
export const apiGetBookDetail  = (id, bookUrl)            => http.get(`/book-sources/${id}/detail`,   { params: { bookUrl } })
export const apiGetChapters    = (id, bookUrl)            => http.get(`/book-sources/${id}/chapters`, { params: { bookUrl } })
export const apiGetChaptersPage = (id, bookUrl, offset = 0, limit = 50) =>
	http.get(`/book-sources/${id}/chapters/page`, { params: { bookUrl, offset, limit } })
export const apiGetContent     = (id, chapterUrl, bookUrl, chapterIndex, canonicalBookId) => http.get(`/book-sources/${id}/content`, { params: { chapterUrl, bookUrl, chapterIndex, canonicalBookId } })
export const apiTestSource     = (id)                     => http.get(`/book-sources/${id}/test`)
