import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { LoginRequest, UserInfo } from '@/types/user'
import { authApi } from '@/api/modules/auth'
import router from '@/router'

export const useAuthStore = defineStore('auth', () => {
  // Tokens are tab-scoped so admin, doctor, and patient sessions can be open
  // simultaneously without the last login overwriting the other identities.
  const legacyToken = localStorage.getItem('access_token') || ''
  const legacyRefreshToken = localStorage.getItem('refresh_token') || ''
  const token = ref(sessionStorage.getItem('access_token') || legacyToken)
  const refreshToken = ref(sessionStorage.getItem('refresh_token') || legacyRefreshToken)
  const userInfo = ref<UserInfo | null>(null)
  const permissions = ref<string[]>([])

  if (token.value) sessionStorage.setItem('access_token', token.value)
  if (refreshToken.value) sessionStorage.setItem('refresh_token', refreshToken.value)
  localStorage.removeItem('access_token')
  localStorage.removeItem('refresh_token')

  const isAuthenticated = computed(() => Boolean(token.value))
  const username = computed(() => userInfo.value?.username || '')
  const realName = computed(() => userInfo.value?.realName || username.value)

  async function login(credentials: LoginRequest) {
    const response = await authApi.login(credentials)
    const loginData = response.data.data
    token.value = loginData.accessToken
    refreshToken.value = loginData.refreshToken
    sessionStorage.setItem('access_token', token.value)
    sessionStorage.setItem('refresh_token', refreshToken.value)
    await fetchUserInfo()
  }

  async function fetchUserInfo() {
    try {
      const response = await authApi.getCurrentUser()
      const profile = response.data.data
      userInfo.value = profile.user
      permissions.value = profile.permissions || []
    } catch {
      userInfo.value = null
      permissions.value = []
    }
  }

  async function logout() {
    try {
      await authApi.logout()
    } finally {
      clearAuth()
      void router.push({ name: 'Login' })
    }
  }

  function clearAuth() {
    token.value = ''
    refreshToken.value = ''
    userInfo.value = null
    permissions.value = []
    sessionStorage.removeItem('access_token')
    sessionStorage.removeItem('refresh_token')
    localStorage.removeItem('access_token')
    localStorage.removeItem('refresh_token')
  }

  function hasPermission(permission: string): boolean {
    if (permissions.value.includes('*')
        || userInfo.value?.roles?.some(role => ['admin', 'ADMIN', '系统管理员'].includes(role))) {
      return true
    }
    return permissions.value.includes(permission)
  }

  function hasAnyRole(roles: string[]): boolean {
    return userInfo.value?.roles?.some(role => roles.includes(role)) || false
  }

  return {
    token,
    refreshToken,
    userInfo,
    permissions,
    isAuthenticated,
    username,
    realName,
    login,
    logout,
    fetchUserInfo,
    clearAuth,
    hasPermission,
    hasAnyRole,
  }
})
