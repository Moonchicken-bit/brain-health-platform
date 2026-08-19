import { adniHttp as http } from '@/api/client'
import type { PageResult } from '@/types/api'

export interface ADNISubjectDTO {
  id?: number
  adniSubjectId: string
  diagnosis: string // CN, MCI, AD, or other
  age?: number
  sex?: string
  educationYears?: number
  apoeGenotype?: string
  hasImaging: boolean
  hasGenetics: boolean
  localSubjectId?: number // linked local subject
  importedAt?: string
  updatedAt?: string
}

export interface ADNISubjectSearchParams {
  page?: number
  size?: number
  diagnosis?: string
  ageMin?: number
  ageMax?: number
  sex?: string
  apoeStatus?: string // e.g. 'E2E3','E3E3','E3E4','E4E4'
}

export interface ADNIStatistics {
  totalSubjects: number
  cnCount: number
  mciCount: number
  adCount: number
  otherCount: number
}

export const adniApi = {
  /** List ADNI subjects with pagination and filters */
  list(params: ADNISubjectSearchParams) {
    return http.get<{ code: number; data: PageResult<ADNISubjectDTO> }>('/api/v1/adni/subjects', { params })
  },

  /** Get summary statistics */
  statistics() {
    return http.get<{ code: number; data: ADNIStatistics }>('/api/v1/adni/statistics')
  },

  /** Link an ADNI subject to a local subject */
  linkToLocalSubject(adniSubjectId: number, localSubjectId: number) {
    return http.post(`/api/v1/adni/subjects/${adniSubjectId}/link`, { localSubjectId })
  },

  /** Trigger ADNI data import (admin only) */
  triggerImport(file: File) {
    const form = new FormData()
    form.append('file', file)
    return http.post('/api/v1/adni/import', form)
  },
}
