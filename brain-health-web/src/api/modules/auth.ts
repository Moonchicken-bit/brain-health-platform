import { authHttp as http } from '@/api/client'

export interface LoginRequest {
  username: string
  password: string
  captcha?: string
  otpCode?: string
}

export interface LoginResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number
}

export interface UserInfo {
  id: number
  username: string
  realName: string
  email: string
  phone: string
  institutionId: number
  subjectId?: number
  institutionName: string
  department: string
  roles: string[]
  permissions: string[]
}

export interface UserProfileResponse {
  user: UserInfo
  permissions: string[]
}

export const authApi = {
  login(data: LoginRequest) {
    return http.post<{ code: number; message: string; data: LoginResponse }>('/api/v1/auth/login', data)
  },

  logout() {
    return http.post('/api/v1/auth/logout')
  },

  refreshToken(refreshToken: string) {
    return http.post('/api/v1/auth/refresh', { refreshToken })
  },

  getCurrentUser() {
    return http.get<{ code: number; message: string; data: UserProfileResponse }>('/api/v1/auth/me')
  },

  changePassword(data: { oldPassword: string; newPassword: string }) {
    return http.post('/api/v1/auth/change-password', data)
  },

  setup2FA() {
    return http.post<{ code: number; data: { secret: string; otpauthUri: string; recoveryCodes: string[] } }>('/api/v1/auth/2fa/setup')
  },

  verify2FA(code: string) {
    return http.post('/api/v1/auth/2fa/verify', { code })
  },

  forgotPassword(data: { email: string }) {
    return http.post('/api/v1/auth/forgot-password', data)
  },

  resetPassword(data: { email: string; code: string; newPassword: string }) {
    return http.post('/api/v1/auth/reset-password', data)
  },
}
