// Common API type definitions

/** Generic API response wrapper */
export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
  traceId?: string
  timestamp: number
}

/** Paginated result */
export interface PageResult<T> {
  page: number
  size: number
  total: number
  totalPages: number
  records: T[]
}

/** Pagination query parameters */
export interface PageParams {
  page?: number
  size?: number
  sortBy?: string
  sortOrder?: 'asc' | 'desc'
}
