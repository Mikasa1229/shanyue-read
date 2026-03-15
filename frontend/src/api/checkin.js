import http from './http'

export const apiCheckin = (dto) => http.post('/checkins', dto)
export const apiGetCalendar = (year, month) =>
  http.get('/checkins', { params: { year, month } })
export const apiGetStreak = () => http.get('/checkins/streak')
