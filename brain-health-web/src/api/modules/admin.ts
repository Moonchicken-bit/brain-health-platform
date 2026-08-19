import { adminHttp as http } from '@/api/client'
import type { PageResult } from '@/types/api'

export interface UserDTO {
  id?: number
  username: string
  realName: string
  email: string
  phone: string
  institutionId: number
  department: string
  title: string
  isActive: boolean
  roles: string[]
  projectIds?: number[]
}

export interface RoleDTO {
  id?: number
  name: string
  code: string
  description: string
  permissions: number[]
}

export interface PermissionDTO {
  id: number
  code: string
  name: string
  resource: string
  action: string
}

export interface ProjectDTO {
  id?: number
  name: string
  alias: string
  pi: string
  piContact: string
  institutionId: number
  startDate: string
  endDate: string
  recruitmentTarget: number
  status: 'PLANNING' | 'ACTIVE' | 'COMPLETED' | 'SUSPENDED'
  description: string
  createdAt?: string
  updatedAt?: string
}

export interface InstitutionDTO {
  id?: number
  name: string
  alias?: string
  city: string
  contact: string
  contactPhone?: string
  address?: string
  isActive: boolean
  createdAt?: string
  updatedAt?: string
}

export interface AuditLogEntry {
  id: number
  username: string
  action: string
  resourceType: string
  resourceId: number
  detail: string
  ipAddress: string
  status: string
  createdAt: string
}

export const adminApi = {
  // Projects
  listProjects(params?: { page?: number; size?: number; keyword?: string; status?: string }) {
    return http.get<{ code: number; data: PageResult<ProjectDTO> }>('/api/v1/admin/projects', { params })
  },

  getProject(id: number) {
    return http.get<{ code: number; data: ProjectDTO }>(`/api/v1/admin/projects/${id}`)
  },

  createProject(data: ProjectDTO) {
    return http.post('/api/v1/admin/projects', data)
  },

  updateProject(id: number, data: Partial<ProjectDTO>) {
    return http.put(`/api/v1/admin/projects/${id}`, data)
  },

  deleteProject(id: number) {
    return http.delete(`/api/v1/admin/projects/${id}`)
  },

  // Institutions
  listInstitutions(params?: { page?: number; size?: number; keyword?: string; city?: string; isActive?: boolean }) {
    return http.get<{ code: number; data: PageResult<InstitutionDTO> }>('/api/v1/admin/institutions', { params })
  },

  getInstitution(id: number) {
    return http.get<{ code: number; data: InstitutionDTO }>(`/api/v1/admin/institutions/${id}`)
  },

  createInstitution(data: InstitutionDTO) {
    return http.post('/api/v1/admin/institutions', data)
  },

  updateInstitution(id: number, data: Partial<InstitutionDTO>) {
    return http.put(`/api/v1/admin/institutions/${id}`, data)
  },

  deleteInstitution(id: number) {
    return http.delete(`/api/v1/admin/institutions/${id}`)
  },

  toggleInstitutionStatus(id: number, active: boolean) {
    return http.put(`/api/v1/admin/institutions/${id}/status`, { isActive: active })
  },

  // Users
  listUsers(params?: { page?: number; size?: number; keyword?: string }) {
    return http.get<{ code: number; data: PageResult<UserDTO> }>('/api/v1/admin/users', { params })
  },

  createUser(data: UserDTO & { password: string }) {
    return http.post('/api/v1/admin/users', data)
  },

  updateUser(id: number, data: Partial<UserDTO>) {
    return http.put(`/api/v1/admin/users/${id}`, data)
  },

  toggleUserStatus(id: number, active: boolean) {
    return http.put(`/api/v1/admin/users/${id}/status`, { isActive: active })
  },

  resetPassword(id: number, newPassword: string) {
    return http.put(`/api/v1/admin/users/${id}/reset-password`, { newPassword })
  },

  getUserRoles(id: number) {
    return http.get(`/api/v1/admin/users/${id}/roles`)
  },

  assignRole(userId: number, roleId: number, institutionId?: number, projectId?: number) {
    return http.post(`/api/v1/admin/users/${userId}/roles`, { roleId, institutionId, projectId })
  },

  removeRole(userId: number, roleId: number) {
    return http.delete(`/api/v1/admin/users/${userId}/roles/${roleId}`)
  },

  // Roles
  listRoles() {
    return http.get('/api/v1/admin/roles')
  },

  createRole(data: RoleDTO) {
    return http.post('/api/v1/admin/roles', data)
  },

  updateRole(id: number, data: Partial<RoleDTO>) {
    return http.put(`/api/v1/admin/roles/${id}`, data)
  },

  deleteRole(id: number) {
    return http.delete(`/api/v1/admin/roles/${id}`)
  },

  getRolePermissions(id: number) {
    return http.get(`/api/v1/admin/roles/${id}/permissions`)
  },

  // Permissions
  listPermissions() {
    return http.get('/api/v1/admin/permissions')
  },

  // Audit logs
  listAuditLogs(params: {
    page?: number
    size?: number
    userId?: number
    action?: string
    resourceType?: string
    status?: string
    dateFrom?: string
    dateTo?: string
  }) {
    return http.get<{ code: number; data: PageResult<AuditLogEntry> }>('/api/v1/admin/audit-logs', { params })
  },
}
