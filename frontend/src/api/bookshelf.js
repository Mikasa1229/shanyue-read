import http from './http'

export const apiAddToShelf    = (dto)      => http.post('/bookshelf', dto)
export const apiRemoveFromShelf = (bookUrl) => http.delete('/bookshelf', { params: { bookUrl } })
export const apiGetMyShelf    = (page = 1, size = 20) => http.get('/bookshelf', { params: { page, size } })
export const apiCheckOnShelf  = (bookUrl)  => http.get('/bookshelf/check', { params: { bookUrl } })
export const apiUpdateReadingProgress = (dto) => http.put('/bookshelf/progress', dto)
