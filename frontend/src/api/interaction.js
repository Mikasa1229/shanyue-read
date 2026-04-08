import http from './http'

// 统一切换互动（action: 1=点赞, 2=收藏）
export const apiToggleInteraction = (dto) => http.post('/interactions/toggle', dto)

// 批量查询互动状态（返回 Map<targetId, {active, count}>）
export const apiGetInteractionStatus = (dto) => http.post('/interactions/status', dto)

// 我的收藏列表
export const apiGetMyFavorites = (page = 1, size = 20) =>
  http.get('/interactions/my-favorites', { params: { page, size } })
