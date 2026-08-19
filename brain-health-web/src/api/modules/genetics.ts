import { geneticsHttp as http } from '@/api/client'
import type { PageResult } from '@/types/api'

// ---- Genetics Sample ----

export interface GeneticsSample {
  id?: number
  subjectId: number
  subjectIdDisplay?: string
  sampleType: string
  platform: string
  qcStatus: string
  variantCount: number
  vcfFileName?: string
  notes?: string
  createdAt?: string
  updatedAt?: string
}

// ---- Genetics Variant ----

export interface GeneticsVariant {
  id?: number
  sampleId: number
  geneSymbol: string
  variantType: string
  clinicalSignificance: string
  chromosome: string
  position: number
  ref: string
  alt: string
  rsId?: string
  impact?: string
  description?: string
  alleleFrequency?: number
  readDepth?: number
  genotype?: string
}

// ---- Variant Summary (parsing result) ----

export interface VariantSummary {
  totalVariants: number
  snpCount: number
  indelCount: number
  cnvCount: number
  svCount: number
  tiTvRatio?: number
  hetHomRatio?: number
  dbSnpOverlap?: number
  novelVariants?: number
  qcFiltersApplied: QcFilterResult[]
  chromosomes: ChromosomeVariantCount[]
}

export interface QcFilterResult {
  filterName: string
  description: string
  variantsRemoved: number
  variantsPassed: number
  status: 'PASS' | 'WARN' | 'FAIL'
}

export interface ChromosomeVariantCount {
  chromosome: string
  variantCount: number
}

// ---- Platform / Reference Genome ----

export interface PlatformOption {
  id: number
  name: string
  label: string
  description?: string
}

export interface ReferenceGenomeOption {
  id: number
  name: string
  label: string
  description?: string
}

// ---- Search params ----

export interface GeneticsSampleSearchParams {
  subjectId?: string | number
  sessionId?: number
  geneSymbol?: string
  variantType?: string
  clinicalSignificance?: string
  sampleType?: string
  platform?: string
  qcStatus?: string
  page?: number
  size?: number
}

// ---- API ----

export const geneticsApi = {
  // Sample CRUD
  listSamples(params?: GeneticsSampleSearchParams) {
    return http.get<{ code: number; data: PageResult<GeneticsSample> }>('/api/v1/genetics/samples', { params })
  },

  getSample(id: number) {
    return http.get<{ code: number; data: GeneticsSample }>(`/api/v1/genetics/samples/${id}`)
  },

  deleteSample(id: number) {
    return http.delete(`/api/v1/genetics/samples/${id}`)
  },

  // VCF upload (direct, for files < chunk threshold)
  uploadVcf(formData: FormData) {
    return http.post<{ code: number; data: GeneticsSample }>('/api/v1/genetics/samples/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 600000,
    })
  },

  // Chunked upload (single chunk)
  uploadChunk(formData: FormData) {
    return http.post('/api/v1/genetics/upload/chunk', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 300000,
    })
  },

  // Merge chunks after all uploaded
  mergeChunks(data: {
    uploadId: string
    fileName: string
    totalChunks: number
    fileSize: number
    subjectId: number
    sessionId: number
    platform: string
    referenceGenome: string
  }) {
    return http.post('/api/v1/genetics/upload/merge', data, { timeout: 120000 })
  },

  // Trigger VCF parsing on server
  triggerParsing(sampleId: number) {
    return http.post(`/api/v1/genetics/samples/${sampleId}/parse`)
  },

  // Get variant summary after parsing
  getVariantSummary(sampleId: number) {
    return http.get<{ code: number; data: VariantSummary }>(
      `/api/v1/genetics/samples/${sampleId}/variant-summary`
    )
  },

  // Get supported sequencing platforms
  getPlatforms() {
    return http.get<{ code: number; data: PlatformOption[] }>('/api/v1/genetics/platforms')
  },

  // Get supported reference genomes
  getReferenceGenomes() {
    return http.get<{ code: number; data: ReferenceGenomeOption[] }>('/api/v1/genetics/reference-genomes')
  },

  // Variants — list
  listVariants(params: {
    sampleId: number
    geneSymbol?: string
    variantType?: string
    clinicalSignificance?: string
    page?: number
    size?: number
  }) {
    return http.get<{ code: number; data: PageResult<GeneticsVariant> }>(
      `/api/v1/genetics/samples/${params.sampleId}/variants`,
      { params }
    )
  },

  // Variants — single
  getVariant(sampleId: number, variantId: number) {
    return http.get<{ code: number; data: GeneticsVariant }>(
      `/api/v1/genetics/samples/${sampleId}/variants/${variantId}`
    )
  },
}
