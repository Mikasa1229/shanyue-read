import http from './http'

export const apiAddFavorite     = (dto)     => http.post('/favorites', dto)
export const apiRemoveFavorite  = (book) => http.delete('/favorites', {
  params: typeof book === 'string' ? { bookUrl: book } : { canonicalBookId: book?.canonicalBookId, bookUrl: book?.bookUrl }
})
export const apiGetMyFavorites  = (page = 1, size = 20) => http.get('/favorites', { params: { page, size } })
export const apiCheckFavorited  = (bookUrl, canonicalBookId) => http.get('/favorites/check', { params: { bookUrl, canonicalBookId } })
