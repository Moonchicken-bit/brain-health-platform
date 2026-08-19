import { subjectHttp as http } from '@/api/client'
import type { PageResult } from '@/types/api'

export interface SubjectDTO {
  id?: number
  subjectId: string
  externalId?: string
  firstName?: string
  lastName?: string
  namePinyin?: string
  institutionId: number
  projectId: number
  sex: string
  dateOfBirth?: string
  ageAtEnrollment?: number
  ethnicity?: string
  ethnicityCodeId?: number
  educationCodeId?: number
  educationYears?: number
  handedness?: string
  maritalStatusCodeId?: number
  bloodTypeCodeId?: number
  phone?: string
  addressCity?: string
  addressDistrict?: string
  heightCm?: number
  weightKg?: number
  bmi?: number
  enrollmentDate?: string
  enrollmentInstitutionId?: number
  status?: string
  isConsented?: boolean
  consentDate?: string
  remarks?: string
  isActive?: boolean
  createdAt?: string
  updatedAt?: string
}

export interface SubjectSearchParams {
  page?: number
  size?: number
  subjectId?: string
  sex?: string
  ageMin?: number
  ageMax?: number
  institutionId?: number
  projectId?: number
  cohortId?: number
  keyword?: string
}

export interface SubjectBusinessTag {
  id: number
  projectId: number
  name: string
  color: string
  createdBy: number
  isActive: boolean
  createdAt?: string
}

export interface SubjectProjectNote {
  id: number
  subjectId: number
  projectId: number
  revisionNo: number
  content: string
  createdBy: number
  createdAt: string
}

export const subjectApi = {
  list(params: SubjectSearchParams) {
    return http.get<{ code: number; data: PageResult<SubjectDTO> }>('/api/v1/subjects', { params })
  },

  getById(id: number) {
    return http.get<{ code: number; data: SubjectDTO }>(`/api/v1/subjects/${id}`)
  },

  create(data: SubjectDTO) {
    return http.post('/api/v1/subjects', data)
  },

  update(id: number, data: Partial<SubjectDTO>) {
    return http.put(`/api/v1/subjects/${id}`, data)
  },

  delete(id: number) {
    return http.delete(`/api/v1/subjects/${id}`)
  },

  batchImport(formData: FormData) {
    return http.post('/api/v1/subjects/batch-import', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 60000,
    })
  },

  getTimeline(id: number) {
    return http.get(`/api/v1/subjects/${id}/timeline`)
  },

  getSessions(subjectId: number) {
    return http.get(`/api/v1/subjects/${subjectId}/sessions`)
  },

  copyLastSession(subjectId: number) {
    return http.post(`/api/v1/subjects/${subjectId}/copy-last-session`)
  },

  getFavorites() {
    return http.get<{ code: number; data: number[] }>('/api/v1/subjects/favorites')
  },

  setFavorite(subjectId: number, favorite: boolean) {
    return http.put(`/api/v1/subjects/${subjectId}/favorite`, { favorite })
  },

  listProjectTags(projectId: number) {
    return http.get<{ code: number; data: SubjectBusinessTag[] }>(`/api/v1/projects/${projectId}/subject-tags`)
  },

  createProjectTag(projectId: number, name: string, color: string) {
    return http.post<{ code: number; data: SubjectBusinessTag }>(
      `/api/v1/projects/${projectId}/subject-tags`, { name, color },
    )
  },

  getSubjectTags(subjectId: number) {
    return http.get<{ code: number; data: SubjectBusinessTag[] }>(`/api/v1/subjects/${subjectId}/tags`)
  },

  setSubjectTags(subjectId: number, tagIds: number[]) {
    return http.put<{ code: number; data: SubjectBusinessTag[] }>(
      `/api/v1/subjects/${subjectId}/tags`, { tagIds },
    )
  },

  getProjectNoteHistory(subjectId: number) {
    return http.get<{ code: number; data: SubjectProjectNote[] }>(
      `/api/v1/subjects/${subjectId}/project-notes`,
    )
  },

  saveProjectNote(subjectId: number, content: string) {
    return http.post<{ code: number; data: SubjectProjectNote }>(
      `/api/v1/subjects/${subjectId}/project-notes`, { content },
    )
  },
}
