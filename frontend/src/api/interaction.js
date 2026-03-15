import http from './http'

export const apiToggleInteraction = (dto) => http.post('/interactions/toggle', dto)
export const apiGetInteractionStatus = (dto) => http.post('/interactions/status/batch', dto)
