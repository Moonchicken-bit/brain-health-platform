import { subjectHttp as http } from '@/api/client'
import type { PageResult } from '@/types/api'

export interface SessionDTO {
  id: number
  subjectId: number
  visitLabel: string
  visitDate: string
  status: 'PLANNED' | 'IN_PROGRESS' | 'COMPLETED' | 'WITHDRAWN'
  notes?: string
  subject?: {
    id: number
    subjectId: string
    sex: string
    dateOfBirth?: string
  }
  createdAt?: string
  updatedAt?: string
}

export interface SessionAssessmentSummary {
  id: number
  instrumentId: number
  instrumentName: string
  totalScore?: number
  dataEntryStatus: string
  assessmentDate: string
}

export interface SessionImagingSummary {
  id: number
  modalityId: number
  modalityName: string
  seriesCount: number
  qcStatus: string
}

export interface SessionLabSummary {
  id: number
  labTestId: number
  labTestName: string
  result?: string
  referenceRange?: string
  isAbnormal: boolean
  collectionDate: string
}

export const sessionApi = {
  getById(id: number) {
    return http.get<{ code: number; data: SessionDTO }>(`/api/v1/sessions/${id}`)
  },

  update(id: number, data: { status?: string; visitLabel?: string; visitDate?: string; notes?: string }) {
    return http.put<{ code: number; data: SessionDTO }>(`/api/v1/sessions/${id}`, data)
  },

  updateStatus(id: number, status: string) {
    return http.patch<{ code: number; data: SessionDTO }>(`/api/v1/sessions/${id}/status`, { status })
  },

  getAssessments(sessionId: number) {
    return http.get<{ code: number; data: SessionAssessmentSummary[] }>(`/api/v1/sessions/${sessionId}/assessments`)
  },

  getImaging(sessionId: number) {
    return http.get<{ code: number; data: SessionImagingSummary[] }>(`/api/v1/sessions/${sessionId}/imaging`)
  },

  getLabTests(sessionId: number) {
    return http.get<{ code: number; data: SessionLabSummary[] }>(`/api/v1/sessions/${sessionId}/lab-tests`)
  },

  copyFromLast(sessionId: number) {
    return http.post<{ code: number; data: SessionDTO }>(`/api/v1/sessions/${sessionId}/copy-from-last`)
  },

  list(params?: { subjectId?: number; status?: string; page?: number; size?: number }) {
    return http.get<{ code: number; data: PageResult<SessionDTO> }>('/api/v1/sessions', { params })
  },
}
