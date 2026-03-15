import http from './http'

export const apiCreateComment = (dto) => http.post('/comments', dto)
export const apiDeleteComment = (id) => http.delete(`/comments/${id}`)
export const apiGetComments = (novelId, page = 1, size = 20) =>
  http.get('/comments', { params: { novelId, page, size } })
