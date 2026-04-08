import http from './http'

export const apiAddFavorite     = (dto)     => http.post('/favorites', dto)
export const apiRemoveFavorite  = (bookUrl) => http.delete('/favorites', { params: { bookUrl } })
export const apiGetMyFavorites  = (page = 1, size = 20) => http.get('/favorites', { params: { page, size } })
export const apiCheckFavorited  = (bookUrl) => http.get('/favorites/check', { params: { bookUrl } })
