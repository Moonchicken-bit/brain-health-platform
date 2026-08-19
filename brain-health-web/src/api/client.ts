import axios from 'axios'
import type {
  AxiosInstance,
  AxiosResponse,
  InternalAxiosRequestConfig,
} from 'axios'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import router from '@/router'

let refreshPromise: Promise<string> | null = null

function generateTraceId(): string {
  return Math.random().toString(36).substring(2, 10)
}

function createHttpClient(baseURL: string): AxiosInstance {
  const http = axios.create({
    baseURL,
    timeout: 30000,
    headers: { 'Content-Type': 'application/json' },
  })

  http.interceptors.request.use(
    (config: InternalAxiosRequestConfig) => {
      const authStore = useAuthStore()
      const latestToken = sessionStorage.getItem('access_token') || authStore.token
      if (latestToken) config.headers.Authorization = `Bearer ${latestToken}`
      config.headers['X-Trace-Id'] = generateTraceId()
      return config
    },
    error => Promise.reject(error),
  )

  http.interceptors.response.use(
    (response: AxiosResponse) => {
      const { data } = response
      if (data.code && data.code !== 200 && data.code !== 201) {
        ElMessage.error(data.message || '请求失败')
        return Promise.reject(new Error(data.message))
      }
      return response
    },
    async (error) => {
      const authStore = useAuthStore()
      const original = error.config as InternalAxiosRequestConfig & { _retried?: boolean }
      const suppress404Toast =
        String(original?.headers?.['X-Suppress-404-Toast']).toLowerCase() === 'true'

      if (error.response?.status === 401 && !original?._retried
          && authStore.refreshToken
          && !String(original?.url || '').includes('/api/v1/auth/refresh')) {
        original._retried = true
        try {
          if (!refreshPromise) {
            refreshPromise = axios.post(`${baseURL}/api/v1/auth/refresh`, {
              refreshToken: authStore.refreshToken,
            }).then((response) => {
              const refreshed = response.data.data
              authStore.token = refreshed.accessToken
              if (refreshed.refreshToken) authStore.refreshToken = refreshed.refreshToken
              sessionStorage.setItem('access_token', authStore.token)
              sessionStorage.setItem('refresh_token', authStore.refreshToken)
              return authStore.token
            }).finally(() => {
              refreshPromise = null
            })
          }
          const newToken = await refreshPromise
          original.headers.Authorization = `Bearer ${newToken}`
          return http.request(original)
        } catch {
          authStore.clearAuth()
          void router.push({ name: 'Login', query: { redirect: router.currentRoute.value.fullPath } })
          ElMessage.error('登录已过期，请重新登录')
          return Promise.reject(error)
        }
      }

      if (error.response) {
        const { status, data } = error.response
        switch (status) {
          case 401:
            authStore.clearAuth()
            void router.push({ name: 'Login', query: { redirect: router.currentRoute.value.fullPath } })
            ElMessage.error('登录已过期，请重新登录')
            break
          case 403:
            ElMessage.error(data?.message || `无权限执行此操作：${original?.url || '未知接口'}`)
            break
          case 404:
            if (!suppress404Toast) ElMessage.error('请求的资源不存在')
            break
          case 500:
            ElMessage.error(data?.message || '服务器内部错误')
            break
          default:
            ElMessage.error(data?.message || `请求失败 (${status})`)
        }
      } else if (error.code === 'ECONNABORTED') {
        ElMessage.error('请求超时，请检查网络')
      } else {
        ElMessage.error('网络连接失败，请检查网络设置')
      }
      return Promise.reject(error)
    },
  )

  return http
}

export const authHttp = createHttpClient(import.meta.env.VITE_AUTH_API_URL || '')
export const subjectHttp = createHttpClient(import.meta.env.VITE_SUBJECT_API_URL || '')
export const scaleHttp = createHttpClient(import.meta.env.VITE_SCALE_API_URL || '')
export const imagingHttp = createHttpClient(import.meta.env.VITE_IMAGING_API_URL || '')
export const geneticsHttp = createHttpClient(import.meta.env.VITE_GENETICS_API_URL || '')
export const labHttp = createHttpClient(import.meta.env.VITE_LAB_API_URL || '')
export const searchHttp = createHttpClient(import.meta.env.VITE_SEARCH_API_URL || '')
export const exportHttp = createHttpClient(import.meta.env.VITE_EXPORT_API_URL || '')
export const adminHttp = createHttpClient(import.meta.env.VITE_ADMIN_API_URL || '')
export const adniHttp = createHttpClient(import.meta.env.VITE_ADNI_API_URL || '')
export const gatewayHttp = createHttpClient(import.meta.env.VITE_GATEWAY_URL || '')

export default gatewayHttp
