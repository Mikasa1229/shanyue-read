import http from './http'

export const apiAddToShelf    = (dto)      => http.post('/bookshelf', dto)
export const apiRemoveFromShelf = (book) => http.delete('/bookshelf', {
  params: typeof book === 'string' ? { bookUrl: book } : { canonicalBookId: book?.canonicalBookId, bookUrl: book?.bookUrl }
})
export const apiGetMyShelf    = (page = 1, size = 20) => http.get('/bookshelf', { params: { page, size } })
export const apiCheckOnShelf  = (bookUrl, canonicalBookId)  => http.get('/bookshelf/check', { params: { bookUrl, canonicalBookId } })
export const apiUpdateReadingProgress = (dto) => http.put('/bookshelf/progress', dto)
