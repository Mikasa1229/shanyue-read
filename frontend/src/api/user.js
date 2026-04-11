import http from './http'

export const apiRegister = (dto) => http.post('/auth/register', dto)
export const apiLogin = (dto) => http.post('/auth/login', dto)
export const apiLogout = () => http.post('/auth/logout')

export const apiGetProfile = () => http.get('/users/me')
export const apiUpdateProfile = (dto) => http.put('/users/me', dto)
export const apiUpdatePassword = (dto) => http.put('/users/me/password', dto)
export const apiUploadAvatar = (formData) => http.post('/users/me/avatar', formData, {
  headers: { 'Content-Type': 'multipart/form-data' }
})

export const apiGetMyLevel = () => http.get('/users/me/level')
export const apiRecordLevelAction = (actionType, value = 1) =>
  http.post('/users/me/level/action', { actionType, value })
