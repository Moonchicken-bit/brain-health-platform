import { searchHttp as http } from '@/api/client'

export interface SearchParams {
  q?: string
  subjectId?: string
  diagnosis?: string
  gene?: string
  scale?: string
  scoreMin?: number
  scoreMax?: number
  modality?: string
  cohort?: string
  institutionId?: number
  dateFrom?: string
  dateTo?: string
  page?: number
  size?: number
}

export interface AdvancedSearchQuery {
  filters: SearchFilter[]
  operator: 'AND' | 'OR'
  page?: number
  size?: number
}

export interface SearchFilter {
  field: string
  operator: string // eq, ne, gt, lt, gte, lte, contains, in
  value: string | number | boolean | string[]
}

export interface SearchResult {
  subjectId: string
  subjectCode?: string
  subjectInfo: {
    sex: string
    dateOfBirth: string
    educationYears: number
  }
  matchedOn: string[]
  highlightFields: Record<string, string>
  score: number
}

export interface SavedSearch {
  id: number
  name: string
  queryJson: string
  createdAt: string
}

export const searchApi = {
  search(params: SearchParams) {
    return http.get('/api/v1/search', { params })
  },

  advancedSearch(query: AdvancedSearchQuery) {
    return http.post('/api/v1/search/advanced', query)
  },

  getSavedSearches() {
    return http.get('/api/v1/search/saved')
  },

  saveSearch(name: string, query: SearchParams | AdvancedSearchQuery) {
    return http.post('/api/v1/search/saved', { name, query })
  },

  deleteSavedSearch(id: number) {
    return http.delete(`/api/v1/search/saved/${id}`)
  },
}
