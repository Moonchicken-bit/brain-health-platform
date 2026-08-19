// User-related type definitions

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
  title?: string
  roles: string[]
}

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
