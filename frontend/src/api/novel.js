import http from './http'

export const apiGetNovels = (params) => http.get('/novels', { params })
export const apiGetNovelDetail = (id) => http.get(`/novels/${id}`)
export const apiCreateNovel = (dto) => http.post('/novels', dto)
export const apiUpdateNovel = (id, dto) => http.put(`/novels/${id}`, dto)
export const apiDeleteNovel = (id) => http.delete(`/novels/${id}`)
export const apiGetMyNovels = (page = 1, size = 20) =>
  http.get('/novels/my', { params: { page, size } })
