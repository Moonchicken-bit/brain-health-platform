import { imagingHttp as http } from '@/api/client'

export interface ImagingSession {
  id?: number
  sessionId: number
  subjectId: number
  scannerId?: number
  modalityId: number
  acquisitionDate?: string
  seriesCount: number
  qcStatus: string
  preprocessingStatus: string
  notes?: string
}

export interface ImagingSeries {
  id?: number
  imagingSessionId: number
  seriesUid?: string
  seriesNumber: number
  seriesDescription?: string
  sequenceName?: string
  echoTime?: number
  repetitionTime?: number
  sliceThickness?: number
  numberOfFiles: number
  fileType: string
  qcStatus: string
}

export const imagingApi = {
  getModalities() {
    return http.get('/api/v1/imaging/modalities')
  },

  // Upload
  uploadFiles(formData: FormData, onProgress?: (percent: number) => void) {
    return http.post('/api/v1/imaging/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 600000, // 10 min for large files
      onUploadProgress: (e) => {
        if (e.total && onProgress) {
          onProgress(Math.round((e.loaded * 100) / e.total))
        }
      },
    })
  },

  // Sessions
  listSessions(params?: {
    subjectId?: number
    modalityId?: number
    qcStatus?: string
    page?: number
    size?: number
  }) {
    return http.get('/api/v1/imaging/sessions', { params })
  },

  getSession(id: number) {
    return http.get(`/api/v1/imaging/sessions/${id}`)
  },

  getSeries(sessionId: number) {
    return http.get(`/api/v1/imaging/sessions/${sessionId}/series`)
  },

  // Series
  getSeriesDetail(id: number) {
    return http.get(`/api/v1/imaging/series/${id}`)
  },

  downloadSeries(id: number) {
    return http.get(`/api/v1/imaging/series/${id}/download`, {
      responseType: 'blob',
    })
  },

  getPreview(id: number) {
    return http.get(`/api/v1/imaging/series/${id}/preview`, {
      responseType: 'blob',
      headers: { 'X-Suppress-404-Toast': 'true' },
    })
  },

  // QC
  updateQC(seriesId: number, qcStatus: string, qcNotes?: string) {
    return http.post(`/api/v1/imaging/series/${seriesId}/qc`, { qcStatus, qcNotes })
  },

  // Preprocessing
  submitPreprocessing(imagingSessionId: number, pipelineId: string) {
    return http.post('/api/v1/imaging/preprocessing', { imagingSessionId, pipelineId })
  },

  getPreprocessingStatus(jobId: string) {
    return http.get(`/api/v1/imaging/preprocessing/${jobId}`)
  },

  // BIDS
  convertToBIDS(imagingSessionId: number) {
    return http.post('/api/v1/imaging/convert-to-bids', { imagingSessionId })
  },

  browseBIDS(subjectId: string, sessionLabel: string) {
    return http.get(`/api/v1/imaging/bids/${subjectId}/${sessionLabel}`)
  },
}
