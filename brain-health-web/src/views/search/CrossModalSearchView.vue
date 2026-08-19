<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, RefreshRight, Star, StarFilled } from '@element-plus/icons-vue'
import {
  searchApi,
  type SearchParams,
  type AdvancedSearchQuery,
  type SearchFilter,
  type SearchResult,
  type SavedSearch,
} from '@/api/modules/search'
import { scaleApi, type ScaleInstrument } from '@/api/modules/scale'
import { labApi, type LabTest } from '@/api/modules/lab'
import http from '@/api/client'

const router = useRouter()

// ======================== Search filter state ========================

const filterForm = reactive({
  // Keyword (cross-modal)
  keyword: '',

  // Demographics
  ageMin: undefined as number | undefined,
  ageMax: undefined as number | undefined,
  sex: '' as string,
  educationMin: undefined as number | undefined,
  educationMax: undefined as number | undefined,
  cohort: '',

  // Scale scores
  instrumentId: undefined as number | undefined,
  scoreMin: undefined as number | undefined,
  scoreMax: undefined as number | undefined,

  // Genetics
  geneSymbol: '',
  variantType: '',

  // Imaging
  modality: '',

  // Lab
  labTestId: undefined as number | undefined,
  isAbnormal: undefined as boolean | undefined,
})

// ======================== Dropdown / reference data ========================

const instrumentOptions = ref<ScaleInstrument[]>([])
const instrumentsLoading = ref(false)

const labTestOptions = ref<LabTest[]>([])
const labTestsLoading = ref(false)

interface CohortOption {
  id: number
  name: string
}
const cohortOptions = ref<CohortOption[]>([])

const VARIANT_TYPE_OPTIONS = [
  { label: '全部', value: '' },
  { label: 'SNP', value: 'SNP' },
  { label: 'INDEL', value: 'INDEL' },
  { label: 'CNV', value: 'CNV' },
  { label: 'SV', value: 'SV' },
]

const MODALITY_OPTIONS = [
  { label: '全部', value: '' },
  { label: 'T1w MRI', value: 'T1w' },
  { label: 'T2w MRI', value: 'T2w' },
  { label: 'FLAIR', value: 'FLAIR' },
  { label: 'fMRI', value: 'fMRI' },
  { label: 'DTI', value: 'DTI' },
  { label: 'PET', value: 'PET' },
  { label: 'CT', value: 'CT' },
  { label: 'EEG', value: 'EEG' },
  { label: 'MEG', value: 'MEG' },
]

const SEX_OPTIONS = [
  { label: '全部', value: '' },
  { label: '男', value: 'M' },
  { label: '女', value: 'F' },
]

// ======================== Results state ========================

const results = ref<SearchResult[]>([])
const loading = ref(false)
const searched = ref(false)
const pagination = reactive({
  page: 1,
  size: 12,
  total: 0,
})

// ======================== Saved searches ========================

const savedSearches = ref<SavedSearch[]>([])
const saveDialogVisible = ref(false)
const saveName = ref('')
const saving = ref(false)

// ======================== API calls ========================

async function fetchInstruments() {
  instrumentsLoading.value = true
  try {
    const res = await scaleApi.listInstruments()
    if (res.data.code === 200) {
      instrumentOptions.value = res.data.data as ScaleInstrument[]
    }
  } catch {
    // silent
  } finally {
    instrumentsLoading.value = false
  }
}

async function fetchLabTests() {
  labTestsLoading.value = true
  try {
    const res = await labApi.listTests()
    if (res.data.code === 200) {
      labTestOptions.value = res.data.data
    }
  } catch {
    // silent
  } finally {
    labTestsLoading.value = false
  }
}

async function fetchCohorts() {
  try {
    const res = await http.get<{ code: number; data: CohortOption[] }>('/api/v1/cohorts')
    if (res.data.code === 200 && res.data.data) {
      cohortOptions.value = res.data.data
    }
  } catch {
    // silent
  }
}

async function fetchSavedSearches() {
  try {
    const res = await searchApi.getSavedSearches()
    if (res.data.code === 200) {
      savedSearches.value = res.data.data as SavedSearch[]
    }
  } catch {
    // silent
  }
}

function buildSearchParams(): SearchParams {
  const params: SearchParams = {
    page: pagination.page,
    size: pagination.size,
  }

  if (filterForm.keyword) params.q = filterForm.keyword
  if (filterForm.sex) params.subjectId = undefined // sex not in simple params, goes to advanced
  if (filterForm.cohort) params.cohort = filterForm.cohort
  if (filterForm.instrumentId) {
    const inst = instrumentOptions.value.find((i) => i.id === filterForm.instrumentId)
    if (inst) params.scale = inst.name
  }
  if (filterForm.scoreMin !== undefined) params.scoreMin = filterForm.scoreMin
  if (filterForm.scoreMax !== undefined) params.scoreMax = filterForm.scoreMax
  if (filterForm.geneSymbol) params.gene = filterForm.geneSymbol
  if (filterForm.modality) params.modality = filterForm.modality

  return params
}

function buildAdvancedFilters(): SearchFilter[] {
  const filters: SearchFilter[] = []

  if (filterForm.sex) {
    filters.push({ field: 'sex', operator: 'eq', value: filterForm.sex })
  }
  if (filterForm.ageMin !== undefined) {
    filters.push({ field: 'age', operator: 'gte', value: filterForm.ageMin })
  }
  if (filterForm.ageMax !== undefined) {
    filters.push({ field: 'age', operator: 'lte', value: filterForm.ageMax })
  }
  if (filterForm.educationMin !== undefined) {
    filters.push({ field: 'educationYears', operator: 'gte', value: filterForm.educationMin })
  }
  if (filterForm.educationMax !== undefined) {
    filters.push({ field: 'educationYears', operator: 'lte', value: filterForm.educationMax })
  }
  if (filterForm.variantType) {
    filters.push({ field: 'variantType', operator: 'eq', value: filterForm.variantType })
  }
  if (filterForm.labTestId !== undefined) {
    filters.push({ field: 'labTestId', operator: 'eq', value: filterForm.labTestId })
  }
  if (filterForm.isAbnormal !== undefined) {
    filters.push({ field: 'isAbnormal', operator: 'eq', value: filterForm.isAbnormal })
  }

  return filters
}

function hasAdvancedFilters(): boolean {
  return (
    filterForm.sex !== '' ||
    filterForm.ageMin !== undefined ||
    filterForm.ageMax !== undefined ||
    filterForm.educationMin !== undefined ||
    filterForm.educationMax !== undefined ||
    filterForm.variantType !== '' ||
    filterForm.labTestId !== undefined ||
    filterForm.isAbnormal !== undefined
  )
}

async function doSearch() {
  loading.value = true
  searched.value = true
  try {
    const advancedFilters = buildAdvancedFilters()

    if (advancedFilters.length > 0) {
      // Use advanced search when extra filters are present
      const baseParams = buildSearchParams()
      const simpleFilters: SearchFilter[] = []

      if (baseParams.q) simpleFilters.push({ field: 'keyword', operator: 'contains', value: baseParams.q! })
      if (baseParams.cohort) simpleFilters.push({ field: 'cohort', operator: 'eq', value: baseParams.cohort! })
      if (baseParams.scale) simpleFilters.push({ field: 'scale', operator: 'eq', value: baseParams.scale! })
      if (baseParams.scoreMin !== undefined) simpleFilters.push({ field: 'score', operator: 'gte', value: baseParams.scoreMin! })
      if (baseParams.scoreMax !== undefined) simpleFilters.push({ field: 'score', operator: 'lte', value: baseParams.scoreMax! })
      if (baseParams.gene) simpleFilters.push({ field: 'gene', operator: 'contains', value: baseParams.gene! })
      if (baseParams.modality) simpleFilters.push({ field: 'modality', operator: 'eq', value: baseParams.modality! })

      const query: AdvancedSearchQuery = {
        filters: [...simpleFilters, ...advancedFilters],
        operator: 'AND',
        page: pagination.page,
        size: pagination.size,
      }
      const res = await searchApi.advancedSearch(query)
      if (res.data.code === 200) {
        results.value = (res.data.data.records ?? res.data.data) as SearchResult[]
        pagination.total = res.data.data.total ?? results.value.length
      }
    } else {
      // Use simple search
      const params = buildSearchParams()
      const res = await searchApi.search(params)
      if (res.data.code === 200) {
        results.value = (res.data.data.records ?? res.data.data) as SearchResult[]
        pagination.total = res.data.data.total ?? results.value.length
      }
    }
  } catch {
    results.value = []
    pagination.total = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.page = 1
  doSearch()
}

function handleReset() {
  filterForm.keyword = ''
  filterForm.ageMin = undefined
  filterForm.ageMax = undefined
  filterForm.sex = ''
  filterForm.educationMin = undefined
  filterForm.educationMax = undefined
  filterForm.cohort = ''
  filterForm.instrumentId = undefined
  filterForm.scoreMin = undefined
  filterForm.scoreMax = undefined
  filterForm.geneSymbol = ''
  filterForm.variantType = ''
  filterForm.modality = ''
  filterForm.labTestId = undefined
  filterForm.isAbnormal = undefined
  pagination.page = 1
  searched.value = false
  results.value = []
  pagination.total = 0
}

function handlePageChange(page: number) {
  pagination.page = page
  doSearch()
}

function handleSizeChange(size: number) {
  pagination.size = size
  pagination.page = 1
  doSearch()
}

// ======================== Result navigation ========================

function handleViewResult(result: SearchResult) {
  router.push(`/subjects/${result.subjectId}`)
}

function handleViewSubject(subjectId: string) {
  router.push(`/subjects/${subjectId}`)
}

// ======================== Saved search ========================

function openSaveDialog() {
  saveName.value = ''
  saveDialogVisible.value = true
}

async function handleSaveSearch() {
  if (!saveName.value.trim()) {
    ElMessage.warning('请输入保存名称')
    return
  }
  saving.value = true
  try {
    if (hasAdvancedFilters()) {
      const simpleFilters = buildSearchParams()
      const advancedFilters = buildAdvancedFilters()
      const allFilters: SearchFilter[] = [
        ...(simpleFilters.q ? [{ field: 'keyword', operator: 'contains' as const, value: simpleFilters.q! }] : []),
        ...(simpleFilters.cohort ? [{ field: 'cohort', operator: 'eq' as const, value: simpleFilters.cohort! }] : []),
        ...(simpleFilters.scale ? [{ field: 'scale', operator: 'eq' as const, value: simpleFilters.scale! }] : []),
        ...(simpleFilters.scoreMin !== undefined ? [{ field: 'score', operator: 'gte' as const, value: simpleFilters.scoreMin! }] : []),
        ...(simpleFilters.scoreMax !== undefined ? [{ field: 'score', operator: 'lte' as const, value: simpleFilters.scoreMax! }] : []),
        ...(simpleFilters.gene ? [{ field: 'gene', operator: 'contains' as const, value: simpleFilters.gene! }] : []),
        ...(simpleFilters.modality ? [{ field: 'modality', operator: 'eq' as const, value: simpleFilters.modality! }] : []),
        ...advancedFilters,
      ]
      const query: AdvancedSearchQuery = { filters: allFilters, operator: 'AND' }
      await searchApi.saveSearch(saveName.value.trim(), query)
    } else {
      const params = buildSearchParams()
      await searchApi.saveSearch(saveName.value.trim(), params)
    }
    ElMessage.success('搜索条件已保存')
    saveDialogVisible.value = false
    fetchSavedSearches()
  } catch {
    // error handled by interceptor
  } finally {
    saving.value = false
  }
}

async function handleLoadSavedSearch(saved: SavedSearch) {
  try {
    const query = JSON.parse(saved.queryJson) as SearchParams | AdvancedSearchQuery
    resetAllFilters()

    if ('filters' in query && Array.isArray(query.filters)) {
      // Advanced search format
      for (const f of query.filters) {
        applyFilter(f)
      }
    } else {
      // Simple search format
      const p = query as SearchParams
      if (p.q) filterForm.keyword = p.q
      if (p.cohort) filterForm.cohort = p.cohort
      if (p.scale) {
        const inst = instrumentOptions.value.find((i) => i.name === p.scale)
        if (inst) filterForm.instrumentId = inst.id
      }
      if (p.scoreMin !== undefined) filterForm.scoreMin = p.scoreMin
      if (p.scoreMax !== undefined) filterForm.scoreMax = p.scoreMax
      if (p.gene) filterForm.geneSymbol = p.gene
      if (p.modality) filterForm.modality = p.modality
    }

    handleSearch()
  } catch {
    ElMessage.error('无法解析保存的搜索条件')
  }
}

function applyFilter(f: SearchFilter) {
  switch (f.field) {
    case 'keyword':
    case 'q':
      filterForm.keyword = String(f.value)
      break
    case 'sex':
      filterForm.sex = String(f.value)
      break
    case 'age':
      if (f.operator === 'gte' || f.operator === 'gt') filterForm.ageMin = Number(f.value)
      if (f.operator === 'lte' || f.operator === 'lt') filterForm.ageMax = Number(f.value)
      break
    case 'educationYears':
      if (f.operator === 'gte' || f.operator === 'gt') filterForm.educationMin = Number(f.value)
      if (f.operator === 'lte' || f.operator === 'lt') filterForm.educationMax = Number(f.value)
      break
    case 'cohort':
      filterForm.cohort = String(f.value)
      break
    case 'scale':
      {
        const inst = instrumentOptions.value.find((i) => i.name === String(f.value))
        if (inst) filterForm.instrumentId = inst.id
      }
      break
    case 'score':
      if (f.operator === 'gte' || f.operator === 'gt') filterForm.scoreMin = Number(f.value)
      if (f.operator === 'lte' || f.operator === 'lt') filterForm.scoreMax = Number(f.value)
      break
    case 'gene':
      filterForm.geneSymbol = String(f.value)
      break
    case 'variantType':
      filterForm.variantType = String(f.value)
      break
    case 'modality':
      filterForm.modality = String(f.value)
      break
    case 'labTestId':
      filterForm.labTestId = Number(f.value)
      break
    case 'isAbnormal':
      filterForm.isAbnormal = Boolean(f.value)
      break
  }
}

async function handleDeleteSavedSearch(id: number) {
  try {
    await searchApi.deleteSavedSearch(id)
    ElMessage.success('已删除')
    fetchSavedSearches()
  } catch {
    // error handled by interceptor
  }
}

function resetAllFilters() {
  filterForm.keyword = ''
  filterForm.ageMin = undefined
  filterForm.ageMax = undefined
  filterForm.sex = ''
  filterForm.educationMin = undefined
  filterForm.educationMax = undefined
  filterForm.cohort = ''
  filterForm.instrumentId = undefined
  filterForm.scoreMin = undefined
  filterForm.scoreMax = undefined
  filterForm.geneSymbol = ''
  filterForm.variantType = ''
  filterForm.modality = ''
  filterForm.labTestId = undefined
  filterForm.isAbnormal = undefined
}

// ======================== Display helpers ========================

function formatDate(dateStr?: string): string {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  if (isNaN(d.getTime())) return dateStr
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

function getSexLabel(sex: string): string {
  if (sex === 'M') return '男'
  if (sex === 'F') return '女'
  return sex || '-'
}

function getEducationLabel(years?: number): string {
  if (years === undefined || years === null) return '-'
  if (years <= 6) return '小学'
  if (years <= 9) return '初中'
  if (years <= 12) return '高中/中专'
  if (years <= 16) return '本科/大专'
  return '研究生及以上'
}

function getMatchedLabel(field: string): string {
  const map: Record<string, string> = {
    keyword: '关键词',
    q: '关键词',
    sex: '性别',
    age: '年龄',
    educationYears: '教育年限',
    cohort: '队列',
    scale: '量表',
    score: '评分',
    gene: '基因',
    variantType: '变异类型',
    modality: '影像模态',
    labTestId: '检验项目',
    isAbnormal: '异常标记',
    subjectId: '受试者ID',
    diagnosis: '诊断',
  }
  return map[field] || field
}

function getMatchedTagType(_field: string): '' | 'success' | 'info' | 'warning' | 'danger' {
  return 'success'
}

function activeFilterCount(): number {
  let count = 0
  if (filterForm.keyword) count++
  if (filterForm.sex) count++
  if (filterForm.ageMin !== undefined || filterForm.ageMax !== undefined) count++
  if (filterForm.educationMin !== undefined || filterForm.educationMax !== undefined) count++
  if (filterForm.cohort) count++
  if (filterForm.instrumentId !== undefined) count++
  if (filterForm.scoreMin !== undefined || filterForm.scoreMax !== undefined) count++
  if (filterForm.geneSymbol) count++
  if (filterForm.variantType) count++
  if (filterForm.modality) count++
  if (filterForm.labTestId !== undefined) count++
  if (filterForm.isAbnormal !== undefined) count++
  return count
}

// ======================== Init ========================

onMounted(() => {
  fetchInstruments()
  fetchLabTests()
  fetchCohorts()
  fetchSavedSearches()
})
</script>

<template>
  <div class="cross-modal-search">
    <!-- ================ Left Panel: Search Filters ================ -->
    <aside class="search-sidebar">
      <div class="sidebar-header">
        <h3 class="sidebar-title">跨模态检索</h3>
        <span v-if="activeFilterCount() > 0" class="active-filter-badge">
          {{ activeFilterCount() }} 项筛选
        </span>
      </div>

      <div class="filter-sections">
        <!-- Keyword -->
        <div class="filter-section">
          <div class="section-label">关键词搜索</div>
          <el-input
            v-model="filterForm.keyword"
            placeholder="搜索受试者ID、诊断、备注等..."
            clearable
            :prefix-icon="Search"
            @keyup.enter="handleSearch"
          />
        </div>

        <!-- Demographics -->
        <div class="filter-section">
          <div class="section-label">人口学信息</div>

          <div class="filter-row">
            <span class="filter-sub-label">年龄范围</span>
            <div class="range-inputs">
              <el-input-number
                v-model="filterForm.ageMin"
                :min="0"
                :max="150"
                placeholder="最小"
                controls-position="right"
                size="small"
                style="width: 100%"
              />
              <span class="range-sep">—</span>
              <el-input-number
                v-model="filterForm.ageMax"
                :min="0"
                :max="150"
                placeholder="最大"
                controls-position="right"
                size="small"
                style="width: 100%"
              />
            </div>
          </div>

          <div class="filter-row">
            <span class="filter-sub-label">性别</span>
            <el-select v-model="filterForm.sex" placeholder="全部" clearable size="small" style="width: 100%">
              <el-option
                v-for="opt in SEX_OPTIONS"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
          </div>

          <div class="filter-row">
            <span class="filter-sub-label">教育年限</span>
            <div class="range-inputs">
              <el-input-number
                v-model="filterForm.educationMin"
                :min="0"
                :max="30"
                placeholder="最小"
                controls-position="right"
                size="small"
                style="width: 100%"
              />
              <span class="range-sep">—</span>
              <el-input-number
                v-model="filterForm.educationMax"
                :min="0"
                :max="30"
                placeholder="最大"
                controls-position="right"
                size="small"
                style="width: 100%"
              />
            </div>
          </div>

          <div class="filter-row">
            <span class="filter-sub-label">队列</span>
            <el-select
              v-model="filterForm.cohort"
              placeholder="全部"
              clearable
              filterable
              size="small"
              style="width: 100%"
            >
              <el-option
                v-for="c in cohortOptions"
                :key="c.id"
                :label="c.name"
                :value="c.name"
              />
            </el-select>
          </div>
        </div>

        <!-- Scale scores -->
        <div class="filter-section">
          <div class="section-label">量表评分</div>

          <div class="filter-row">
            <span class="filter-sub-label">量表工具</span>
            <el-select
              v-model="filterForm.instrumentId"
              placeholder="全部"
              clearable
              filterable
              size="small"
              :loading="instrumentsLoading"
              style="width: 100%"
            >
              <el-option
                v-for="inst in instrumentOptions"
                :key="inst.id"
                :label="`${inst.name} (${inst.fullName})`"
                :value="inst.id"
              />
            </el-select>
          </div>

          <div class="filter-row">
            <span class="filter-sub-label">评分范围</span>
            <div class="range-inputs">
              <el-input-number
                v-model="filterForm.scoreMin"
                :min="0"
                placeholder="最低"
                controls-position="right"
                size="small"
                style="width: 100%"
              />
              <span class="range-sep">—</span>
              <el-input-number
                v-model="filterForm.scoreMax"
                :min="0"
                placeholder="最高"
                controls-position="right"
                size="small"
                style="width: 100%"
              />
            </div>
          </div>
        </div>

        <!-- Genetics -->
        <div class="filter-section">
          <div class="section-label">遗传学</div>

          <div class="filter-row">
            <span class="filter-sub-label">基因符号</span>
            <el-input
              v-model="filterForm.geneSymbol"
              placeholder="如 APOE, BDNF..."
              clearable
              size="small"
              @keyup.enter="handleSearch"
            />
          </div>

          <div class="filter-row">
            <span class="filter-sub-label">变异类型</span>
            <el-select
              v-model="filterForm.variantType"
              placeholder="全部"
              clearable
              size="small"
              style="width: 100%"
            >
              <el-option
                v-for="opt in VARIANT_TYPE_OPTIONS"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
          </div>
        </div>

        <!-- Imaging -->
        <div class="filter-section">
          <div class="section-label">影像学</div>

          <div class="filter-row">
            <span class="filter-sub-label">成像模态</span>
            <el-select
              v-model="filterForm.modality"
              placeholder="全部"
              clearable
              size="small"
              style="width: 100%"
            >
              <el-option
                v-for="opt in MODALITY_OPTIONS"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
          </div>
        </div>

        <!-- Lab -->
        <div class="filter-section">
          <div class="section-label">检验数据</div>

          <div class="filter-row">
            <span class="filter-sub-label">检验项目</span>
            <el-select
              v-model="filterForm.labTestId"
              placeholder="全部"
              clearable
              filterable
              size="small"
              :loading="labTestsLoading"
              style="width: 100%"
            >
              <el-option
                v-for="test in labTestOptions"
                :key="test.id"
                :label="`${test.name} [${test.category}]`"
                :value="test.id"
              />
            </el-select>
          </div>

          <div class="filter-row">
            <el-checkbox v-model="filterForm.isAbnormal" label="仅显示异常结果" />
          </div>
        </div>
      </div>

      <!-- Action buttons -->
      <div class="sidebar-actions">
        <el-button type="primary" :icon="Search" :loading="loading" @click="handleSearch" style="width: 100%">
          搜索
        </el-button>
        <div class="action-row">
          <el-button :icon="RefreshRight" @click="handleReset" style="flex: 1">
            重置
          </el-button>
          <el-button :icon="Star" @click="openSaveDialog" style="flex: 1" :disabled="activeFilterCount() === 0">
            保存搜索
          </el-button>
        </div>
      </div>

      <!-- Saved searches -->
      <div v-if="savedSearches.length > 0" class="saved-searches">
        <div class="section-label">已保存的搜索</div>
        <div
          v-for="saved in savedSearches"
          :key="saved.id"
          class="saved-search-item"
          @click="handleLoadSavedSearch(saved)"
        >
          <div class="saved-search-info">
            <el-icon :size="14" style="color: #e6a23c">
              <StarFilled />
            </el-icon>
            <span class="saved-search-name">{{ saved.name }}</span>
          </div>
          <el-button
            link
            type="danger"
            size="small"
            @click.stop="handleDeleteSavedSearch(saved.id)"
          >
            删除
          </el-button>
        </div>
      </div>
    </aside>

    <!-- ================ Right Panel: Search Results ================ -->
    <main class="search-main">
      <!-- Results header -->
      <div v-if="searched" class="results-header">
        <div class="results-count">
          共找到 <strong>{{ pagination.total }}</strong> 条结果
        </div>
        <div class="results-summary" v-if="activeFilterCount() > 0">
          当前筛选：{{ activeFilterCount() }} 项条件
        </div>
      </div>

      <!-- Empty state (before search) -->
      <div v-if="!searched" class="empty-state">
        <div class="empty-icon">
          <el-icon :size="64" color="#c0c4cc">
            <Search />
          </el-icon>
        </div>
        <p class="empty-title">输入搜索条件开始检索</p>
        <p class="empty-desc">
          支持跨模态搜索：关键词、人口学、量表评分、遗传学、影像学、检验数据等维度
        </p>
      </div>

      <!-- Empty state (no results) -->
      <div v-else-if="results.length === 0 && !loading" class="empty-state">
        <el-empty description="未找到匹配的结果，请调整搜索条件后重试">
          <el-button type="primary" @click="handleReset">重置条件</el-button>
        </el-empty>
      </div>

      <!-- Loading -->
      <div v-if="loading" class="loading-wrapper">
        <el-skeleton :rows="5" animated />
      </div>

      <!-- Result cards -->
      <div v-if="!loading && results.length > 0" class="results-grid">
        <div
          v-for="result in results"
          :key="result.subjectId"
          class="result-card"
          @click="handleViewResult(result)"
        >
          <!-- Card header -->
          <div class="card-header">
            <div class="card-subject-id">
              <span class="subject-id-label">受试者</span>
              <span class="subject-id-value">{{ result.subjectCode || result.subjectId }}</span>
            </div>
            <div class="card-score" v-if="result.score !== undefined">
              相关度: {{ (result.score * 100).toFixed(0) }}%
            </div>
          </div>

          <!-- Card body: subject info -->
          <div class="card-body">
            <div class="info-row">
              <span class="info-label">性别</span>
              <span class="info-value">{{ getSexLabel(result.subjectInfo?.sex) }}</span>
            </div>
            <div class="info-row">
              <span class="info-label">出生日期</span>
              <span class="info-value">{{ formatDate(result.subjectInfo?.dateOfBirth) }}</span>
            </div>
            <div class="info-row">
              <span class="info-label">教育程度</span>
              <span class="info-value">{{ getEducationLabel(result.subjectInfo?.educationYears) }}</span>
            </div>
          </div>

          <!-- Card footer: matched criteria -->
          <div v-if="result.matchedOn && result.matchedOn.length > 0" class="card-footer">
            <div class="matched-label">匹配条件:</div>
            <div class="matched-tags">
              <el-tag
                v-for="field in result.matchedOn"
                :key="field"
                :type="getMatchedTagType(field)"
                size="small"
                effect="plain"
              >
                {{ getMatchedLabel(field) }}
              </el-tag>
            </div>
          </div>

          <!-- Highlighted fields -->
          <div
            v-if="result.highlightFields && Object.keys(result.highlightFields).length > 0"
            class="card-highlights"
          >
            <div
              v-for="(val, key) in result.highlightFields"
              :key="key"
              class="highlight-item"
            >
              <span class="highlight-key">{{ getMatchedLabel(key) }}:</span>
              <span class="highlight-val" v-html="val" />
            </div>
          </div>
        </div>
      </div>

      <!-- Pagination -->
      <div v-if="results.length > 0 && pagination.total > pagination.size" class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :page-sizes="[12, 24, 48]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </main>

    <!-- ================ Save Search Dialog ================ -->
    <el-dialog
      v-model="saveDialogVisible"
      title="保存搜索条件"
      width="420px"
      :close-on-click-modal="false"
    >
      <el-form label-position="top">
        <el-form-item label="搜索名称" required>
          <el-input
            v-model="saveName"
            placeholder="如：APOE阳性老年人"
            maxlength="50"
            show-word-limit
            @keyup.enter="handleSaveSearch"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="saveDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" :disabled="!saveName.trim()" @click="handleSaveSearch">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts">
export default {
  name: 'CrossModalSearchView',
}
</script>

<style scoped lang="scss">
.cross-modal-search {
  display: flex;
  height: calc(100vh - 96px);
  gap: 0;
  margin: -20px;
  overflow: hidden;
}

// ===================== Left sidebar =====================

.search-sidebar {
  width: 300px;
  min-width: 300px;
  background: #fff;
  border-right: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  overflow-x: hidden;
  box-shadow: 1px 0 3px rgba(0, 0, 0, 0.04);
  z-index: 1;

  &::-webkit-scrollbar {
    width: 5px;
  }
  &::-webkit-scrollbar-thumb {
    background: #dcdfe6;
    border-radius: 3px;
  }
}

.sidebar-header {
  padding: 16px 16px 8px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #ebeef5;
}

.sidebar-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.active-filter-badge {
  font-size: 12px;
  color: #409eff;
  background: #ecf5ff;
  padding: 2px 8px;
  border-radius: 10px;
  font-weight: 500;
}

.filter-sections {
  flex: 1;
  overflow-y: auto;
  padding: 8px 12px;

  &::-webkit-scrollbar {
    width: 4px;
  }
  &::-webkit-scrollbar-thumb {
    background: #e4e7ed;
    border-radius: 2px;
  }
}

.filter-section {
  padding: 12px 4px;
  border-bottom: 1px solid #f2f3f5;

  &:last-of-type {
    border-bottom: none;
  }
}

.section-label {
  font-size: 13px;
  font-weight: 600;
  color: #606266;
  margin-bottom: 10px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.filter-row {
  margin-bottom: 10px;

  &:last-child {
    margin-bottom: 0;
  }
}

.filter-sub-label {
  display: block;
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}

.range-inputs {
  display: flex;
  align-items: center;
  gap: 6px;
}

.range-sep {
  color: #c0c4cc;
  font-size: 12px;
  flex-shrink: 0;
}

// ===================== Sidebar actions =====================

.sidebar-actions {
  padding: 12px 16px;
  border-top: 1px solid #ebeef5;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.action-row {
  display: flex;
  gap: 8px;
}

// ===================== Saved searches =====================

.saved-searches {
  padding: 8px 16px 16px;
  border-top: 1px solid #ebeef5;
}

.saved-search-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 8px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.2s;
  margin-top: 4px;

  &:hover {
    background: #f5f7fa;
  }
}

.saved-search-info {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.saved-search-name {
  font-size: 13px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

// ===================== Right main area =====================

.search-main {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
  background: #f5f7fa;

  &::-webkit-scrollbar {
    width: 6px;
  }
  &::-webkit-scrollbar-thumb {
    background: #dcdfe6;
    border-radius: 3px;
  }
}

.results-header {
  display: flex;
  align-items: baseline;
  gap: 16px;
  margin-bottom: 20px;
}

.results-count {
  font-size: 15px;
  color: #303133;

  strong {
    font-size: 18px;
    color: #409eff;
    margin: 0 2px;
  }
}

.results-summary {
  font-size: 12px;
  color: #909399;
}

// ===================== Empty state =====================

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 360px;
  text-align: center;
}

.empty-icon {
  margin-bottom: 16px;
}

.empty-title {
  font-size: 16px;
  color: #606266;
  margin: 0 0 8px;
}

.empty-desc {
  font-size: 13px;
  color: #909399;
  margin: 0;
  max-width: 360px;
  line-height: 1.6;
}

// ===================== Loading =====================

.loading-wrapper {
  padding: 20px 0;
}

// ===================== Results grid =====================

.results-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 16px;
}

.result-card {
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e4e7ed;
  cursor: pointer;
  transition: box-shadow 0.2s, border-color 0.2s, transform 0.15s;
  overflow: hidden;

  &:hover {
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
    border-color: #c6d2e6;
    transform: translateY(-1px);
  }

  &:active {
    transform: translateY(0);
  }
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px 10px;
  border-bottom: 1px solid #f0f0f0;
}

.card-subject-id {
  display: flex;
  align-items: center;
  gap: 8px;
}

.subject-id-label {
  font-size: 12px;
  color: #909399;
}

.subject-id-value {
  font-size: 15px;
  font-weight: 700;
  color: #303133;
  font-family: 'Courier New', Courier, monospace;
}

.card-score {
  font-size: 12px;
  color: #67c23a;
  background: #f0f9eb;
  padding: 2px 8px;
  border-radius: 10px;
  font-weight: 500;
}

.card-body {
  padding: 10px 16px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.info-label {
  font-size: 12px;
  color: #909399;
}

.info-value {
  font-size: 13px;
  color: #303133;
  font-weight: 500;
}

.card-footer {
  padding: 8px 16px 10px;
  border-top: 1px solid #f0f0f0;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.matched-label {
  font-size: 11px;
  color: #909399;
  flex-shrink: 0;
}

.matched-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.card-highlights {
  padding: 8px 16px 12px;
  border-top: 1px dashed #ebeef5;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.highlight-item {
  font-size: 12px;
  line-height: 1.6;
}

.highlight-key {
  color: #909399;
  margin-right: 4px;
}

.highlight-val {
  color: #303133;

  :deep(em),
  :deep(mark),
  :deep(.highlight) {
    background: #fff3cd;
    color: #e6a23c;
    font-style: normal;
    font-weight: 600;
    padding: 0 2px;
    border-radius: 2px;
  }
}

// ===================== Pagination =====================

.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 24px;
  padding-bottom: 24px;
}
</style>
