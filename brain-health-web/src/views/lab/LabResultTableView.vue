<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, RefreshRight, Upload, Download, TrendCharts } from '@element-plus/icons-vue'
import { labApi, type LabResult, type LabTest, type LabReportCandidate, type LabReportPreview } from '@/api/modules/lab'
import http from '@/api/client'

// ======================== Filter form ========================
const filterForm = reactive({
  subjectId: '',
  category: '',
  isAbnormal: undefined as boolean | undefined,
  dateRange: [] as [string, string] | [Date, Date] | [],
})

const CATEGORY_OPTIONS = [
  { label: '全部', value: '' },
  { label: '血常规', value: 'BloodRoutine' },
  { label: '生化', value: 'Biochemistry' },
  { label: '激素', value: 'Hormone' },
  { label: '免疫', value: 'Immunology' },
  { label: '脑脊液标志物', value: 'CSFBiomarker' },
  { label: '其他', value: 'Other' },
] as const

const CATEGORY_TAG_MAP: Record<string, string> = {
  BloodRoutine: 'primary',
  Biochemistry: 'success',
  Hormone: 'warning',
  Immunology: 'danger',
  CSFBiomarker: 'primary',
  Other: 'info',
}

const CATEGORY_LABEL_MAP: Record<string, string> = {
  BloodRoutine: '血常规',
  Biochemistry: '生化',
  Hormone: '激素',
  Immunology: '免疫',
  CSFBiomarker: '脑脊液标志物',
  Other: '其他',
}

const ABNORMAL_OPTIONS = [
  { label: '全部', value: undefined },
  { label: '异常', value: true },
  { label: '正常', value: false },
]

// ======================== Table state ========================
const tableData = ref<LabResult[]>([])
const loading = ref(false)
const pagination = reactive({
  page: 1,
  size: 15,
  total: 0,
})

// ======================== Lab tests lookup ========================
const labTests = ref<LabTest[]>([])
const labTestMap = computed<Map<number, LabTest>>(() => {
  return new Map(labTests.value.map((t) => [t.id, t]))
})

// ======================== Trend chart state ========================
const chartVisible = ref(false)
const chartTestName = ref('')
const chartSubjectId = ref('')
const chartData = ref<LabResult[]>([])
const chartLoading = ref(false)

// ======================== Import dialog ========================
const importDialogVisible = ref(false)
const importFile = ref<File | null>(null)
const importing = ref(false)
const importSessionId = ref<number | null>(null)

// ======================== Original report upload ========================
const reportDialogVisible = ref(false)
const reportFile = ref<File | null>(null)
const reportSubjectId = ref<number | null>(null)
const reportSessionId = ref<number | null>(null)
const reportUploading = ref(false)
const reportProgress = ref(0)
const reportPreview = ref<LabReportPreview | null>(null)
const reportConfirmVisible = ref(false)
const reportConfirming = ref(false)
const dynamicDialogVisible = ref(false)
const dynamicSessionId = ref<number>()
const dynamicFields = ref<any[]>([])
const dynamicValues = reactive<Record<string, any>>({})
const dynamicSaving = ref(false)

function dynamicOptions(field: any): string[] {
  if (Array.isArray(field.options)) return field.options
  try { return JSON.parse(field.options || '[]') } catch { return [] }
}

async function openDynamicFields(row: LabResult) {
  dynamicSessionId.value = row.sessionId
  Object.keys(dynamicValues).forEach(key => delete dynamicValues[key])
  const [fields, values] = await Promise.all([
    http.get('/api/v1/lab/dynamic-fields'),
    http.get(`/api/v1/lab/sessions/${row.sessionId}/dynamic-values`),
  ])
  dynamicFields.value = fields.data.data || []
  Object.assign(dynamicValues, values.data.data || {})
  dynamicDialogVisible.value = true
}

async function saveDynamicFields() {
  if (!dynamicSessionId.value) return
  dynamicSaving.value = true
  try {
    await http.put(`/api/v1/lab/sessions/${dynamicSessionId.value}/dynamic-values`, dynamicValues)
    ElMessage.success('实验室扩展字段已保存')
    dynamicDialogVisible.value = false
  } finally {
    dynamicSaving.value = false
  }
}

// ======================== API calls ========================

async function fetchResults() {
  loading.value = true
  try {
    const params: Record<string, unknown> = {
      page: pagination.page,
      size: pagination.size,
    }
    if (filterForm.subjectId) params.subjectId = Number(filterForm.subjectId)
    if (filterForm.category) params.category = filterForm.category
    if (filterForm.isAbnormal !== undefined) params.isAbnormal = filterForm.isAbnormal

    const [start, end] = (filterForm.dateRange as string[]) || []
    if (start) {
      params.startDate = start
    }
    if (end) {
      params.endDate = end
    }

    const res = await labApi.listResults(params as Parameters<typeof labApi.listResults>[0])
    if (res.data.code === 200) {
      tableData.value = res.data.data.records
      pagination.total = res.data.data.total
    }
  } catch {
    // error handled by interceptor
  } finally {
    loading.value = false
  }
}

async function fetchLabTests() {
  try {
    const res = await labApi.listTests()
    if (res.data.code === 200) {
      labTests.value = res.data.data
    }
  } catch {
    // silent
  }
}

async function fetchTrendData(subjectId: number, labTestId: number, testName: string) {
  chartLoading.value = true
  chartSubjectId.value = String(subjectId)
  chartTestName.value = testName
  try {
    const res = await labApi.listResults({
      subjectId,
      labTestId,
      size: 200,
    })
    if (res.data.code === 200) {
      // Sort by collection date ascending for the chart
      chartData.value = [...res.data.data.records].sort(
        (a, b) => new Date(a.collectionDate).getTime() - new Date(b.collectionDate).getTime()
      )
    }
  } catch {
    chartData.value = []
  } finally {
    chartLoading.value = false
  }
}

// ======================== Handlers ========================

function handleSearch() {
  pagination.page = 1
  fetchResults()
}

function handleReset() {
  filterForm.subjectId = ''
  filterForm.category = ''
  filterForm.isAbnormal = undefined
  filterForm.dateRange = []
  pagination.page = 1
  fetchResults()
}

function handlePageChange(page: number) {
  pagination.page = page
  fetchResults()
}

function handleSizeChange(size: number) {
  pagination.size = size
  pagination.page = 1
  fetchResults()
}

function handleShowTrend(row: LabResult) {
  chartVisible.value = true
  fetchTrendData(row.subjectId, row.labTestId, row.labTestName || getTestName(row.labTestId))
}

function handleCloseChart() {
  chartVisible.value = false
  chartData.value = []
}

async function handleDelete(row: LabResult) {
  try {
    await ElMessageBox.confirm(`确定要删除该检验结果吗？此操作不可恢复。`, '删除确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await labApi.deleteResult(row.id!)
    ElMessage.success('删除成功')
    fetchResults()
  } catch {
    // user cancelled or error
  }
}

// ======================== Import handlers ========================

function handleImportOpen() {
  importFile.value = null
  importSessionId.value = null
  importDialogVisible.value = true
}

function handleReportOpen() {
  reportFile.value = null
  reportSubjectId.value = filterForm.subjectId ? Number(filterForm.subjectId) : null
  reportSessionId.value = null
  reportProgress.value = 0
  reportPreview.value = null
  reportDialogVisible.value = true
}

async function handleReportUpload() {
  if (!reportFile.value || !reportSubjectId.value || !reportSessionId.value) {
    ElMessage.warning('请填写受试者、访视并选择检验报告文件')
    return
  }
  reportUploading.value = true
  try {
    const uploadResponse = await labApi.uploadReport(
      reportFile.value,
      reportSubjectId.value,
      reportSessionId.value,
      (percent) => { reportProgress.value = percent },
    )
    ElMessage.success('报告已上传，已进入待解析队列')
    const previewResponse = await labApi.previewReport(uploadResponse.data.data.id)
    reportPreview.value = previewResponse.data.data
    reportConfirmVisible.value = true
    ElMessage.success(`已解析 ${reportPreview.value.candidates.length} 条候选检验结果，请核对`)
    reportDialogVisible.value = false
  } catch {
    // handled by interceptor
  } finally {
    reportUploading.value = false
  }
}

async function handleReportConfirm() {
  if (!reportPreview.value) return
  const valid: LabReportCandidate[] = reportPreview.value.candidates.filter(candidate => candidate.labTestId && candidate.value)
  if (!valid.length) {
    ElMessage.warning('请至少确认一条已匹配且有结果值的记录')
    return
  }
  reportConfirming.value = true
  try {
    await labApi.confirmReport(reportPreview.value.uploadId, valid)
    ElMessage.success(`已确认导入 ${valid.length} 条检验结果`)
    reportConfirmVisible.value = false
    await fetchResults()
  } finally {
    reportConfirming.value = false
  }
}

function handleFileChange(file: File) {
  importFile.value = file
}

function handleFileRemove() {
  importFile.value = null
}

async function handleImport() {
  if (!importFile.value) {
    ElMessage.warning('请先选择文件')
    return
  }
  importing.value = true
  try {
    const text = await importFile.value.text()
    const lines = text.split(/\r?\n/).filter((line) => line.trim())
    if (lines.length < 2) {
      ElMessage.warning('CSV 文件内容为空或缺少表头')
      return
    }

    // Parse header; first line
    const headers = parseCSVLine(lines[0])
    const results: Partial<LabResult>[] = []

    for (let i = 1; i < lines.length; i++) {
      const values = parseCSVLine(lines[i])
      if (values.length === 0) continue

      const record: Record<string, string> = {}
      headers.forEach((h, idx) => {
        record[h.trim()] = (values[idx] || '').trim()
      })

      const subjectId = Number(record['subjectId'] || record['subject_id'] || record['受试者ID'] || 0)
      const labTestId = Number(record['labTestId'] || record['lab_test_id'] || record['检验项目ID'] || 0)
      const result = record['result'] || record['结果'] || ''
      const unit = record['unit'] || record['单位'] || undefined
      const referenceRange = record['referenceRange'] || record['reference_range'] || record['参考范围'] || undefined
      const isAbnormal = (record['isAbnormal'] || record['is_abnormal'] || record['异常'] || '').toLowerCase() === 'true'
      const collectionDate = record['collectionDate'] || record['collection_date'] || record['采样日期'] || ''
      const sessionId = Number(record['sessionId'] || record['session_id'] || record['访视ID'] || 0)
      const notes = record['notes'] || record['备注'] || undefined

      if (!subjectId || !labTestId || !result || !collectionDate) continue

      results.push({
        subjectId,
        sessionId,
        labTestId,
        result,
        unit,
        referenceRange,
        isAbnormal,
        collectionDate,
        notes,
      })
    }

    if (results.length === 0) {
      ElMessage.warning('未能解析到有效数据，请检查 CSV 格式')
      return
    }

    if (!importSessionId.value) {
      ElMessage.warning('请输入访视ID（sessionId）')
      return
    }

    await labApi.batchCreate(importSessionId.value, results)
    ElMessage.success(`成功导入 ${results.length} 条检验数据`)
    importDialogVisible.value = false
    fetchResults()
  } catch (err: any) {
    ElMessage.error(err?.message || '导入失败')
  } finally {
    importing.value = false
  }
}

function parseCSVLine(line: string): string[] {
  const result: string[] = []
  let current = ''
  let inQuotes = false
  for (let i = 0; i < line.length; i++) {
    const ch = line[i]
    if (inQuotes) {
      if (ch === '"') {
        if (i + 1 < line.length && line[i + 1] === '"') {
          current += '"'
          i++
        } else {
          inQuotes = false
        }
      } else {
        current += ch
      }
    } else {
      if (ch === '"') {
        inQuotes = true
      } else if (ch === ',') {
        result.push(current)
        current = ''
      } else {
        current += ch
      }
    }
  }
  result.push(current)
  return result
}

function handleDownloadTemplate() {
  const templateHeaders = [
    'subjectId',
    'sessionId',
    'labTestId',
    'result',
    'unit',
    'referenceRange',
    'isAbnormal',
    'collectionDate',
    'notes',
  ]
  const templateRow = [
    'SUB001',
    '1',
    '1',
    '4.5',
    'mmol/L',
    '3.9-6.1',
    'false',
    '2025-06-01',
    '空腹血糖',
  ]
  const csvContent = [templateHeaders.join(','), templateRow.join(',')].join('\n')

  const blob = new Blob(['﻿' + csvContent], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = 'lab_result_import_template.csv'
  link.click()
  URL.revokeObjectURL(url)
}

// ======================== Display helpers ========================

function getTestName(labTestId: number): string {
  return labTestMap.value.get(labTestId)?.name || String(labTestId)
}

function getTestCategory(labTestId: number): string {
  return labTestMap.value.get(labTestId)?.category || 'Other'
}

function getCategoryLabel(category: string): string {
  return CATEGORY_LABEL_MAP[category] || category
}

function getCategoryTagType(category: string): string {
  return CATEGORY_TAG_MAP[category] || 'info'
}

function formatDate(dateStr?: string): string {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  if (isNaN(d.getTime())) return dateStr
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

function getAbnormalTagType(isAbnormal: boolean): 'danger' | 'success' {
  return isAbnormal ? 'danger' : 'success'
}

function getAbnormalLabel(isAbnormal: boolean): string {
  return isAbnormal ? '异常' : '正常'
}

function formatResultValue(row: LabResult): string {
  const val = row.result
  if (val == null || String(val).trim() === '' || String(val).toLowerCase() === 'null') return '—'
  if (row.unit) return `${val} ${row.unit}`
  return val
}

// ======================== Trend chart (SVG-based) ========================

interface ChartPoint {
  x: number
  y: number
  label: string
  value: number
}

const chartSvgWidth = 560
const chartSvgHeight = 220
const chartPadding = { top: 20, right: 20, bottom: 40, left: 50 }

const chartPoints = computed<ChartPoint[]>(() => {
  const data = chartData.value
  if (data.length === 0) return []

  const vals = data.map((d) => parseFloat(d.result)).filter((v) => !isNaN(v))
  if (vals.length === 0) return []

  const minVal = Math.min(...vals)
  const maxVal = Math.max(...vals)
  const range = maxVal - minVal || 1

  const plotW = chartSvgWidth - chartPadding.left - chartPadding.right
  const plotH = chartSvgHeight - chartPadding.top - chartPadding.bottom

  return data
    .filter((d) => !isNaN(parseFloat(d.result)))
    .map((d, idx, arr) => {
      const val = parseFloat(d.result)
      // Handle single-point case
      let x: number
      if (arr.length === 1) {
        x = chartPadding.left + plotW / 2
      } else {
        x = chartPadding.left + (idx / (arr.length - 1)) * plotW
      }
      const y = chartPadding.top + plotH - ((val - minVal) / range) * plotH
      return {
        x,
        y,
        label: formatDate(d.collectionDate),
        value: val,
      }
    })
})

const chartPolyline = computed(() => {
  if (chartPoints.value.length === 0) return ''
  return chartPoints.value.map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x} ${p.y}`).join(' ')
})

// ======================== Init ========================

onMounted(() => {
  fetchLabTests()
  fetchResults()
})
</script>

<template>
  <div class="page-container">
    <!-- Page title -->
    <div class="page-title">检验数据管理</div>

    <!-- Filter bar -->
    <div class="filter-bar">
      <el-form :inline="true" :model="filterForm" size="default">
        <el-form-item label="受试者ID">
          <el-input
            v-model="filterForm.subjectId"
            placeholder="请输入受试者ID"
            clearable
            style="width: 160px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>

        <el-form-item label="检验分类">
          <el-select
            v-model="filterForm.category"
            placeholder="全部"
            clearable
            style="width: 140px"
          >
            <el-option
              v-for="opt in CATEGORY_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="异常标记">
          <el-select
            v-model="filterForm.isAbnormal"
            placeholder="全部"
            clearable
            style="width: 120px"
          >
            <el-option
              v-for="opt in ABNORMAL_OPTIONS"
              :key="String(opt.value)"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="采样日期">
          <el-date-picker
            v-model="filterForm.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            style="width: 260px"
            :unlink-panels="true"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">
            搜索
          </el-button>
          <el-button :icon="RefreshRight" @click="handleReset">
            重置
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- Toolbar -->
    <div class="toolbar">
      <div class="toolbar-left">
        <el-button type="success" :icon="Upload" @click="handleReportOpen">
          上传检验报告/压缩包
        </el-button>
        <el-button type="primary" :icon="Upload" @click="handleImportOpen">
          批量导入
        </el-button>
      </div>
      <div class="toolbar-right">
        <span class="total-count">共 {{ pagination.total }} 条记录</span>
      </div>
    </div>

    <!-- Table -->
    <el-table
      v-loading="loading"
      :data="tableData"
      stripe
      border
      style="width: 100%"
      size="default"
    >
      <el-table-column
        label="受试者ID"
        width="140"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          <span class="subject-id-cell">{{ row.subjectId }}</span>
        </template>
      </el-table-column>

      <el-table-column label="检验项目" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          <div class="test-name-cell">
            <span>{{ getTestName(row.labTestId) }}</span>
            <el-tag
              :type="getCategoryTagType(getTestCategory(row.labTestId))"
              size="small"
              class="category-tag"
            >
              {{ getCategoryLabel(getTestCategory(row.labTestId)) }}
            </el-tag>
          </div>
        </template>
      </el-table-column>

      <el-table-column label="检验结果" width="160" align="center">
        <template #default="{ row }">
          <span class="result-value">{{ formatResultValue(row) }}</span>
        </template>
      </el-table-column>

      <el-table-column label="参考范围" width="140" align="center">
        <template #default="{ row }">
          {{ row.referenceRange || '-' }}
        </template>
      </el-table-column>

      <el-table-column label="异常标记" width="100" align="center">
        <template #default="{ row }">
          <el-tag
            :type="getAbnormalTagType(row.isAbnormal)"
            size="small"
            effect="dark"
          >
            {{ getAbnormalLabel(row.isAbnormal) }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column label="采样日期" width="130" align="center">
        <template #default="{ row }">
          {{ formatDate(row.collectionDate) }}
        </template>
      </el-table-column>

      <el-table-column label="实验室" width="100" align="center">
        <template #default="{ row }">
          {{ row.technicianId ? `技师 #${row.technicianId}` : '-' }}
        </template>
      </el-table-column>

      <el-table-column label="备注" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.notes || '-' }}
        </template>
      </el-table-column>

      <el-table-column label="操作" width="250" align="center" fixed="right">
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            size="small"
            :icon="TrendCharts"
            @click="handleShowTrend(row)"
          >
            趋势
          </el-button>
          <el-button link type="primary" size="small" @click="openDynamicFields(row)">
            扩展字段
          </el-button>
          <el-button
            link
            type="danger"
            size="small"
            @click="handleDelete(row)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- Pagination -->
    <div class="pagination-wrapper">
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :page-sizes="[10, 15, 20, 30, 50]"
        :total="pagination.total"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>

    <!-- Trend chart drawer -->
    <el-drawer
      v-model="chartVisible"
      :title="`趋势图 - ${chartTestName}`"
      direction="rtl"
      size="620px"
      @closed="handleCloseChart"
    >
      <div v-if="chartData.length === 0 && !chartLoading" class="chart-empty">
        <el-empty description="该受试者暂无该检验项目的历史数据" />
      </div>

      <div v-else class="chart-container">
        <div class="chart-meta">
          <span class="chart-meta-item">受试者: {{ chartSubjectId }}</span>
          <span class="chart-meta-item">检验项目: {{ chartTestName }}</span>
          <span class="chart-meta-item">共 {{ chartData.length }} 次结果</span>
        </div>

        <!-- SVG sparkline chart -->
        <div class="chart-svg-wrapper">
          <svg
            :viewBox="`0 0 ${chartSvgWidth} ${chartSvgHeight}`"
            :width="chartSvgWidth"
            :height="chartSvgHeight"
            class="trend-svg"
          >
            <!-- Grid lines -->
            <line
              v-for="i in 4"
              :key="'grid-' + i"
              :x1="chartPadding.left"
              :y1="chartPadding.top + ((i - 1) / 3) * (chartSvgHeight - chartPadding.top - chartPadding.bottom)"
              :x2="chartSvgWidth - chartPadding.right"
              :y2="chartPadding.top + ((i - 1) / 3) * (chartSvgHeight - chartPadding.top - chartPadding.bottom)"
              stroke="#ebeef5"
              stroke-width="1"
            />

            <!-- Axis lines -->
            <line
              :x1="chartPadding.left"
              :y1="chartPadding.top"
              :x2="chartPadding.left"
              :y2="chartSvgHeight - chartPadding.bottom"
              stroke="#c0c4cc"
              stroke-width="1"
            />
            <line
              :x1="chartPadding.left"
              :y1="chartSvgHeight - chartPadding.bottom"
              :x2="chartSvgWidth - chartPadding.right"
              :y2="chartSvgHeight - chartPadding.bottom"
              stroke="#c0c4cc"
              stroke-width="1"
            />

            <!-- Polyline -->
            <polyline
              v-if="chartPolyline"
              :points="chartPoints.map((p) => `${p.x},${p.y}`).join(' ')"
              fill="none"
              stroke="#409eff"
              stroke-width="2.5"
              stroke-linejoin="round"
              stroke-linecap="round"
            />

            <!-- Data points -->
            <circle
              v-for="(pt, idx) in chartPoints"
              :key="'pt-' + idx"
              :cx="pt.x"
              :cy="pt.y"
              r="4"
              fill="#409eff"
              stroke="#fff"
              stroke-width="2"
            >
              <title>{{ pt.label }}: {{ pt.value }}</title>
            </circle>

            <!-- X-axis labels -->
            <text
              v-for="(pt, idx) in chartPoints"
              :key="'xlbl-' + idx"
              :x="pt.x"
              :y="chartSvgHeight - chartPadding.bottom + 16"
              text-anchor="middle"
              font-size="10"
              fill="#909399"
            >
              {{ pt.label }}
            </text>
          </svg>
        </div>

        <!-- Data table below chart -->
        <el-table
          :data="chartData"
          size="small"
          border
          class="chart-data-table"
          max-height="240"
        >
          <el-table-column label="日期" width="110">
            <template #default="{ row }">
              {{ formatDate(row.collectionDate) }}
            </template>
          </el-table-column>
          <el-table-column label="结果" width="140">
            <template #default="{ row }">
              {{ formatResultValue(row) }}
            </template>
          </el-table-column>
          <el-table-column label="参考范围" width="130">
            <template #default="{ row }">
              {{ row.referenceRange || '-' }}
            </template>
          </el-table-column>
          <el-table-column label="标记" width="80">
            <template #default="{ row }">
              <el-tag
                :type="getAbnormalTagType(row.isAbnormal)"
                size="small"
                effect="dark"
              >
                {{ getAbnormalLabel(row.isAbnormal) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="备注" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">
              {{ row.notes || '-' }}
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-drawer>

    <el-dialog v-model="dynamicDialogVisible" title="实验室访视扩展字段" width="680px">
      <el-empty v-if="!dynamicFields.length" description="管理员尚未发布实验室扩展字段" />
      <el-form v-else label-width="180px">
        <el-form-item
          v-for="field in dynamicFields"
          :key="field.id"
          :label="field.label"
          :required="Boolean(field.requiredFlag)"
        >
          <el-input-number
            v-if="field.fieldType === 'NUMBER'"
            v-model="dynamicValues[field.fieldCode]"
            style="width: 100%"
          />
          <el-date-picker
            v-else-if="field.fieldType === 'DATE'"
            v-model="dynamicValues[field.fieldCode]"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
          <el-select
            v-else-if="['SELECT','MULTI_SELECT'].includes(field.fieldType)"
            v-model="dynamicValues[field.fieldCode]"
            :multiple="field.fieldType === 'MULTI_SELECT'"
            style="width: 100%"
          >
            <el-option v-for="option in dynamicOptions(field)" :key="option" :label="option" :value="option" />
          </el-select>
          <el-switch
            v-else-if="field.fieldType === 'BOOLEAN'"
            v-model="dynamicValues[field.fieldCode]"
          />
          <el-input
            v-else
            v-model="dynamicValues[field.fieldCode]"
            :type="field.fieldType === 'TEXTAREA' ? 'textarea' : 'text'"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dynamicDialogVisible = false">取消</el-button>
        <el-button
          v-if="dynamicFields.length"
          type="primary"
          :loading="dynamicSaving"
          @click="saveDynamicFields"
        >保存</el-button>
      </template>
    </el-dialog>

    <!-- Batch import dialog -->
    <el-dialog
      v-model="importDialogVisible"
      title="批量导入检验数据"
      width="540px"
      :close-on-click-modal="false"
    >
      <div class="import-dialog-body">
        <p class="import-tip">
          请上传 CSV 格式的检验结果数据文件。文件需包含以下字段：
          subjectId, sessionId, labTestId, result, unit, referenceRange, isAbnormal, collectionDate, notes
        </p>

        <el-form label-position="top" size="default">
          <el-form-item label="访视ID (sessionId)" required>
            <el-input-number
              v-model="importSessionId"
              :min="1"
              placeholder="请输入访视ID"
              style="width: 100%"
            />
          </el-form-item>
        </el-form>

        <el-upload
          ref="uploadRef"
          drag
          :auto-upload="false"
          :limit="1"
          accept=".csv"
          :on-change="(f: any) => handleFileChange(f.raw as File)"
          :on-remove="handleFileRemove"
        >
          <el-icon class="el-icon--upload" :size="40">
            <Upload />
          </el-icon>
          <div class="el-upload__text">
            将文件拖到此处，或 <em>点击上传</em>
          </div>
          <template #tip>
            <div class="el-upload__tip">
              仅支持 .csv 格式，编码 UTF-8
            </div>
          </template>
        </el-upload>

        <div class="import-template">
          <el-button link type="primary" :icon="Download" @click="handleDownloadTemplate">
            下载导入模板
          </el-button>
        </div>
      </div>

      <template #footer>
        <el-button @click="importDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="importing"
          :disabled="!importFile || !importSessionId"
          @click="handleImport"
        >
          导入
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="reportDialogVisible"
      title="上传原始检验报告"
      width="560px"
      :close-on-click-modal="false"
    >
      <el-alert
        title="支持医院直接提供的 PDF、图片、Excel、CSV、ZIP 或 RAR。上传后先进入待解析状态，解析结果须由医生确认后才会写入正式检验结果。"
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 18px"
      />
      <el-form label-position="top">
        <el-form-item label="受试者 ID" required>
          <el-input-number v-model="reportSubjectId" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="访视 ID" required>
          <el-input-number v-model="reportSessionId" :min="1" style="width: 100%" />
        </el-form-item>
      </el-form>
      <el-upload
        drag
        :auto-upload="false"
        :limit="1"
        accept=".pdf,.png,.jpg,.jpeg,.tif,.tiff,.xls,.xlsx,.csv,.zip,.rar"
        :on-change="(f: any) => { reportFile = f.raw as File; reportProgress = 0 }"
        :on-remove="() => { reportFile = null; reportProgress = 0 }"
      >
        <el-icon class="el-icon--upload" :size="40"><Upload /></el-icon>
        <div class="el-upload__text">拖入文件，或<em>点击选择</em></div>
        <template #tip>
          <div class="el-upload__tip">单个文件最大 500MB；原始文件会被完整保留。</div>
        </template>
      </el-upload>
      <el-progress v-if="reportUploading || reportProgress > 0" :percentage="reportProgress" style="margin-top: 14px" />
      <template #footer>
        <el-button :disabled="reportUploading" @click="reportDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="reportUploading"
          :disabled="!reportFile || !reportSubjectId || !reportSessionId"
          @click="handleReportUpload"
        >
          上传并等待解析
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="reportConfirmVisible" title="核对自动解析的检验结果" width="1080px"
      :close-on-click-modal="false">
      <el-alert v-for="warning in reportPreview?.warnings || []" :key="warning" :title="warning"
        type="warning" :closable="false" show-icon style="margin-bottom: 10px" />
      <el-table :data="reportPreview?.candidates || []" border max-height="520">
        <el-table-column prop="sourceName" label="报告项目" min-width="130" />
        <el-table-column label="匹配到系统项目" min-width="190">
          <template #default="{ row }">
            <el-select v-model="row.labTestId" filterable clearable placeholder="请选择检验项目">
              <el-option v-for="test in labTests" :key="test.id" :value="test.id"
                :label="`${test.name}${test.unit ? `（${test.unit}）` : ''}`" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="结果" width="130">
          <template #default="{ row }"><el-input v-model="row.value" /></template>
        </el-table-column>
        <el-table-column label="单位" width="120">
          <template #default="{ row }"><el-input v-model="row.unit" /></template>
        </el-table-column>
        <el-table-column label="参考范围" width="150">
          <template #default="{ row }"><el-input v-model="row.referenceRange" /></template>
        </el-table-column>
        <el-table-column label="异常提示" width="110">
          <template #default="{ row }"><el-input v-model="row.abnormalFlag" /></template>
        </el-table-column>
        <el-table-column label="匹配状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.labTestId ? 'success' : 'warning'">{{ row.labTestId ? '已匹配' : '待选择' }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button :disabled="reportConfirming" @click="reportConfirmVisible = false">稍后处理</el-button>
        <el-button type="primary" :loading="reportConfirming" @click="handleReportConfirm">确认并写入检验结果</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts">
export default {
  name: 'LabResultTableView',
}
</script>

<style scoped lang="scss">
.page-container {
  .page-title {
    font-size: 20px;
    font-weight: 600;
    color: #303133;
    margin-bottom: 20px;
  }
}

// ---- Filter bar ----
.filter-bar {
  background: #fff;
  border-radius: 8px;
  padding: 20px 20px 4px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  margin-bottom: 16px;

  :deep(.el-form-item) {
    margin-bottom: 16px;
  }
}

// ---- Toolbar ----
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;

  .toolbar-left {
    display: flex;
    gap: 10px;
  }

  .toolbar-right {
    .total-count {
      font-size: 13px;
      color: #909399;
    }
  }
}

// ---- Table cell styles ----
.subject-id-cell {
  font-weight: 500;
  font-family: 'Courier New', Courier, monospace;
}

.test-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;

  .category-tag {
    flex-shrink: 0;
  }
}

.result-value {
  font-weight: 600;
  color: #303133;
  font-family: 'Courier New', Courier, monospace;
}

// ---- Pagination ----
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
  padding: 0 4px;
}

// ---- Trend chart ----
.chart-empty {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 300px;
}

.chart-container {
  .chart-meta {
    display: flex;
    gap: 20px;
    margin-bottom: 16px;
    padding: 0 4px;

    .chart-meta-item {
      font-size: 13px;
      color: #606266;

      &:first-child {
        font-weight: 500;
        color: #303133;
      }
    }
  }

  .chart-svg-wrapper {
    overflow-x: auto;
    padding-bottom: 8px;

    .trend-svg {
      display: block;
      min-width: 560px;
      height: auto;
    }
  }

  .chart-data-table {
    margin-top: 20px;
  }
}

// ---- Import dialog ----
.import-dialog-body {
  .import-tip {
    font-size: 13px;
    color: #606266;
    margin: 0 0 16px;
    line-height: 1.7;
    background: #f5f7fa;
    padding: 10px 14px;
    border-radius: 6px;
  }

  .import-template {
    margin-top: 12px;
    text-align: center;
  }
}
</style>
