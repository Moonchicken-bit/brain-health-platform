import http from '@/api/client'

export interface ScaleInstrument {
  id: number
  name: string
  fullName: string
  version: string
  category: string
  description: string
  scoringRangeMin: number
  scoringRangeMax: number
  cutoffScore?: number
  estimatedDurationMin: number
}

export interface ScaleInstrumentItem {
  id: number
  instrumentId: number
  itemIndex: number
  domainName?: string
  questionText: string
  inputType?: string
  options?: string
  maxScore: number
  scoreType?: string
}

export interface ScaleInstrumentDetail {
  instrument: ScaleInstrument
  items: ScaleInstrumentItem[]
}

export interface ScaleAssessment {
  id?: number
  sessionId: number
  subjectId: number
  instrumentId: number
  examinerId?: number
  assessmentDate: string
  totalScore?: number
  dataEntryStatus: string
  administrationMode: string
  notes?: string
}

export interface ScaleItemResponse {
  itemIndex: number
  questionText: string
  response: string | number | boolean
  score: number
  maxScore: number
  category: string
}

export interface VisitAttachment {
  id: string
  originalName: string
  contentType: string
  size: number
  subjectId: number
  visitCode: string
  fieldCode: string
  createdAt: string
}

export interface AttachmentTextAnalysis {
  attachmentId: string
  method: 'PDF_TEXT' | 'OCR'
  language: string
  text: string
  fields: Record<string, string>
  warnings: string[]
}

export interface ClinicalField {
  code: string
  name: string
  type: string
  options: Array<{ code: string; label: string }>
  unit: string
  required: boolean
  description: string
  category: string
  formCode: string
  visitCode: string
  sourceVersion: string
  active: boolean
}

export const scaleApi = {
  // Instruments
  listInstruments(params?: { category?: string; keyword?: string }) {
    return http.get('/api/v1/scales', { params })
  },

  getInstrument(id: number) {
    return http.get(`/api/v1/scales/${id}`)
  },

  // Assessments
  createAssessment(data: ScaleAssessment) {
    return http.post('/api/v1/assessments', data)
  },

  getAssessment(id: number) {
    return http.get(`/api/v1/assessments/${id}`)
  },

  updateAssessment(id: number, data: Partial<ScaleAssessment>) {
    return http.put(`/api/v1/assessments/${id}`, data)
  },

  submitAssessment(id: number, scores?: Array<{ itemIndex: number; score: number }>) {
    return http.post(`/api/v1/assessments/${id}/submit`, { scores })
  },

  getItemResponses(assessmentId: number) {
    return http.get(`/api/v1/assessments/${assessmentId}/items`)
  },

  saveItemResponses(assessmentId: number, items: ScaleItemResponse[]) {
    return http.post(`/api/v1/assessments/${assessmentId}/items`, { items })
  },

  getHistory(assessmentId: number) {
    return http.get(`/api/v1/assessments/${assessmentId}/history`)
  },

  exportAssessment(assessmentId: number, format: string = 'pdf') {
    return http.get(`/api/v1/assessments/${assessmentId}/export`, {
      params: { format },
      responseType: 'blob',
    })
  },

  uploadVisitAttachment(
    formData: FormData,
    options?: { signal?: AbortSignal; onProgress?: (percent: number) => void },
  ) {
    return http.post<{ code: number; data: VisitAttachment }>('/api/v1/scales/attachments', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      signal: options?.signal,
      timeout: 120000,
      onUploadProgress: (event) => {
        if (event.total && options?.onProgress) {
          options.onProgress(Math.round((event.loaded * 100) / event.total))
        }
      },
    })
  },

  deleteVisitAttachment(id: string) {
    return http.delete(`/api/v1/scales/attachments/${id}`)
  },

  getVisitAttachment(id: string) {
    return http.get<{ code: number; data: VisitAttachment }>(`/api/v1/scales/attachments/${id}`)
  },

  analyzeVisitAttachment(id: string) {
    return http.post<{ code: number; data: AttachmentTextAnalysis }>(
      `/api/v1/scales/attachments/${id}/analyze-text`,
    )
  },

  visitAttachmentDownloadUrl(id: string) {
    const baseUrl = http.defaults.baseURL || ''
    return `${baseUrl}/api/v1/scales/attachments/${id}/content`
  },

  getClinicalFieldSummary() {
    return http.get<{
      code: number
      data: {
        schemaVersion: string
        source: string
        fieldCount: number
        categoryCounts: Record<string, number>
        categories: string[]
      }
    }>('/api/v1/clinical-fields/summary')
  },

  listClinicalFields(params?: {
    category?: string
    formCode?: string
    visitCode?: string
    keyword?: string
    page?: number
    size?: number
  }) {
    return http.get<{
      code: number
      data: { records: ClinicalField[]; total: number; page: number; size: number; totalPages: number }
    }>('/api/v1/clinical-fields', { params })
  },
}
