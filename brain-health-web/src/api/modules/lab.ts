import { labHttp as http } from '@/api/client'
import type { PageResult } from '@/types/api'

export interface LabResult {
  id?: number
  sessionId: number
  subjectId: number
  labTestId: number
  labTestName?: string
  result: string
  unit?: string
  referenceRange?: string
  isAbnormal: boolean
  collectionDate: string
  technicianId?: number
  notes?: string
  createdAt?: string
}

export interface LabTest {
  id: number
  name: string
  category: string
  unit: string
  referenceMin?: number
  referenceMax?: number
  description: string
}

export interface LabReportUpload {
  id: string
  subjectId: number
  sessionId: number
  originalName: string
  contentType?: string
  fileSize: number
  status: 'UPLOADED' | 'PARSING' | 'PENDING_CONFIRMATION' | 'CONFIRMED' | 'FAILED'
  createdAt?: string
}

export interface LabReportCandidate {
  labTestId?: number
  sourceName?: string
  matchedName?: string
  value?: string
  unit?: string
  referenceRange?: string
  abnormalFlag?: string
  collectionDate?: string
  confidence: number
}

export interface LabReportPreview {
  uploadId: string
  tableCount: number
  candidates: LabReportCandidate[]
  warnings: string[]
}

export const labApi = {
  listResults(params?: {
    sessionId?: number
    subjectId?: number
    labTestId?: number
    isAbnormal?: boolean
    page?: number
    size?: number
  }) {
    return http.get<{ code: number; data: PageResult<LabResult> }>('/api/v1/lab/results', { params })
  },

  getResult(id: number) {
    return http.get<{ code: number; data: LabResult }>(`/api/v1/lab/results/${id}`)
  },

  createResult(data: Partial<LabResult>) {
    return http.post<{ code: number; data: LabResult }>('/api/v1/lab/results', data)
  },

  updateResult(id: number, data: Partial<LabResult>) {
    return http.put<{ code: number; data: LabResult }>(`/api/v1/lab/results/${id}`, data)
  },

  deleteResult(id: number) {
    return http.delete(`/api/v1/lab/results/${id}`)
  },

  batchCreate(sessionId: number, results: Partial<LabResult>[]) {
    return http.post<{ code: number; data: LabResult[] }>(`/api/v1/lab/results/batch`, { sessionId, results })
  },

  listTests(params?: { category?: string; keyword?: string }) {
    return http.get<{ code: number; data: LabTest[] }>('/api/v1/lab/tests', { params })
  },

  getTest(id: number) {
    return http.get<{ code: number; data: LabTest }>(`/api/v1/lab/tests/${id}`)
  },

  uploadReport(file: File, subjectId: number, sessionId: number, onProgress?: (percent: number) => void) {
    const form = new FormData()
    form.append('file', file)
    form.append('subjectId', String(subjectId))
    form.append('sessionId', String(sessionId))
    return http.post<{ code: number; data: LabReportUpload }>('/api/v1/lab/report-uploads', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 600000,
      onUploadProgress: (event) => {
        if (event.total) onProgress?.(Math.round(event.loaded * 100 / event.total))
      },
    })
  },

  listReportUploads(subjectId: number, sessionId: number) {
    return http.get<{ code: number; data: LabReportUpload[] }>('/api/v1/lab/report-uploads', {
      params: { subjectId, sessionId },
    })
  },

  previewReport(id: string) {
    return http.post<{ code: number; data: LabReportPreview }>(`/api/v1/lab/report-uploads/${id}/preview`)
  },

  confirmReport(id: string, candidates: LabReportCandidate[]) {
    return http.post<{ code: number; data: LabResult[] }>(`/api/v1/lab/report-uploads/${id}/confirm`, candidates)
  },
}
