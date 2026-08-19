<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, RefreshRight, Plus, Download, Check, Close } from '@element-plus/icons-vue'
import { exportHttp as http } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import type { PageResult } from '@/types/api'

// ---- Types ----

interface ExportRequestDTO {
  id?: number
  requestId: string
  requesterId: number
  requesterName: string
  projectId: number
  projectName: string
  exportType: string
  formatType: string
  dataScopeSummary: string
  status: ExportStatus
  fileUrl?: string
  fileSize?: number
  totalRecords?: number
  reason?: string
  reviewComment?: string
  reviewerId?: number
  reviewerName?: string
  reviewedAt?: string
  createdAt: string
  updatedAt?: string
}

type ExportStatus = 'PENDING' | 'APPROVED' | 'PROCESSING' | 'COMPLETED' | 'REJECTED'

interface OptionItem {
  id: number
  name: string
  code?: string
}

interface FilterForm {
  requestId: string
  status: string
  projectId: number | undefined
  exportType: string
  dateRange: string | [string, string] | ''
}

// ---- Auth ----
const authStore = useAuthStore()
const canApprove = computed(() =>
  authStore.hasPermission('export:approve') ||
  authStore.hasAnyRole(['PI', 'ADMIN'])
)

// ---- Filter form ----
const filterForm = reactive<FilterForm>({
  requestId: '',
  status: '',
  projectId: undefined,
  exportType: '',
  dateRange: '',
})

// ---- Dropdown options ----
const projectOptions = ref<OptionItem[]>([])
const projectMap = ref<Map<number, string>>(new Map())

// ---- Format type options ----
const formatTypeOptions: OptionItem[] = [
  { id: 1, name: 'CSV', code: 'CSV' },
  { id: 2, name: 'JSON', code: 'JSON' },
  { id: 3, name: 'Excel', code: 'XLSX' },
  { id: 4, name: 'FHIR', code: 'FHIR' },
  { id: 5, name: 'BIDS', code: 'BIDS' },
]

// ---- Export type options ----
const exportTypeOptions: OptionItem[] = [
  { id: 1, name: '受试者数据', code: 'SUBJECT' },
  { id: 2, name: '量表评估', code: 'SCALE' },
  { id: 3, name: '影像数据', code: 'IMAGING' },
  { id: 4, name: '遗传数据', code: 'GENETICS' },
  { id: 5, name: '检验数据', code: 'LAB' },
  { id: 6, name: '综合数据', code: 'COMPREHENSIVE' },
]

// ---- Modality / field options ----
const fieldOptions: OptionItem[] = [
  { id: 1, name: '人口学信息', code: 'DEMOGRAPHICS' },
  { id: 2, name: '诊断信息', code: 'DIAGNOSIS' },
  { id: 3, name: '量表分数', code: 'SCALE_SCORES' },
  { id: 4, name: '影像元数据', code: 'IMAGING_META' },
  { id: 5, name: '影像文件', code: 'IMAGING_FILES' },
  { id: 6, name: '遗传变异', code: 'GENETIC_VARIANTS' },
  { id: 7, name: '基因型数据', code: 'GENOTYPE_DATA' },
  { id: 8, name: '检验指标', code: 'LAB_RESULTS' },
  { id: 9, name: '随访记录', code: 'FOLLOW_UP' },
]

// ---- Cohort options ----
const cohortOptions: OptionItem[] = [
  { id: 1, name: '抑郁症队列', code: 'MDD_COHORT' },
  { id: 2, name: '焦虑症队列', code: 'ANXIETY_COHORT' },
  { id: 3, name: '双相障碍队列', code: 'BIPOLAR_COHORT' },
  { id: 4, name: '精神分裂症队列', code: 'SCHIZOPHRENIA_COHORT' },
  { id: 5, name: '健康对照', code: 'HEALTHY_CONTROL' },
]

// ---- Status options ----
const statusOptions = [
  { label: '待审批', value: 'PENDING' },
  { label: '已批准', value: 'APPROVED' },
  { label: '处理中', value: 'PROCESSING' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '已驳回', value: 'REJECTED' },
]

// ---- Table ----
const tableData = ref<ExportRequestDTO[]>([])
const loading = ref(false)
const pagination = reactive({
  page: 1,
  size: 15,
  total: 0,
})

// ---- Create export dialog ----
const createDialogVisible = ref(false)
const createForm = reactive({
  subjectSource: 'project' as 'project' | 'cohort' | 'individual',
  projectId: undefined as number | undefined,
  cohortId: undefined as number | undefined,
  subjectIds: '',
  exportType: '' as string,
  selectedFields: [] as string[],
  formatType: '' as string,
  includeImages: false,
  dateFrom: '' as string,
  dateTo: '' as string,
  reason: '',
})
const creating = ref(false)

// ---- Review dialog ----
const reviewDialogVisible = ref(false)
const reviewForm = reactive({
  requestId: 0,
  action: 'APPROVED' as 'APPROVED' | 'REJECTED',
  comment: '',
})
const reviewing = ref(false)
const reviewingRow = ref<ExportRequestDTO | null>(null)

// ---- Detail info panel ----
const currentDetail = ref<ExportRequestDTO | null>(null)

// ======================== API calls ========================

async function fetchRequests() {
  loading.value = true
  try {
    const params: Record<string, unknown> = {
      page: pagination.page,
      size: pagination.size,
    }
    if (filterForm.requestId) params.requestId = filterForm.requestId
    if (filterForm.status) params.status = filterForm.status
    if (filterForm.projectId) params.projectId = filterForm.projectId
    if (filterForm.exportType) params.exportType = filterForm.exportType
    if (filterForm.dateRange && Array.isArray(filterForm.dateRange) && filterForm.dateRange.length === 2) {
      params.dateFrom = filterForm.dateRange[0]
      params.dateTo = filterForm.dateRange[1]
    }

    const res = await http.get<{ code: number; data: PageResult<ExportRequestDTO> }>(
      '/api/v1/export/requests',
      { params }
    )
    if (res.data.code === 200) {
      const pageResult = res.data.data
      tableData.value = pageResult.records
      pagination.total = pageResult.total
    }
  } catch {
    // error handled by interceptor
  } finally {
    loading.value = false
  }
}

async function fetchProjects() {
  try {
    const res = await http.get<{ code: number; data: OptionItem[] }>('/api/v1/admin/projects')
    if (res.data.code === 200 && res.data.data) {
      projectOptions.value = res.data.data
      projectMap.value = new Map(res.data.data.map((item) => [item.id, item.name]))
    }
  } catch {
    // silent
  }
}

async function submitCreateRequest() {
  if (!createForm.exportType) {
    ElMessage.warning('请选择导出数据类型')
    return
  }
  if (createForm.selectedFields.length === 0) {
    ElMessage.warning('请至少选择一个导出字段')
    return
  }
  if (!createForm.formatType) {
    ElMessage.warning('请选择导出格式')
    return
  }

  creating.value = true
  try {
    const payload: Record<string, unknown> = {
      exportType: createForm.exportType,
      fields: createForm.selectedFields,
      formatType: createForm.formatType,
      includeImages: createForm.includeImages,
    }
    if (createForm.subjectSource === 'project') {
      payload.projectId = createForm.projectId
    } else if (createForm.subjectSource === 'cohort') {
      payload.cohortId = createForm.cohortId
    } else {
      payload.subjectIds = createForm.subjectIds
        .split(/[,;\s]+/)
        .map((s) => s.trim())
        .filter(Boolean)
    }
    if (createForm.dateFrom) payload.dateFrom = createForm.dateFrom
    if (createForm.dateTo) payload.dateTo = createForm.dateTo
    if (createForm.reason) payload.reason = createForm.reason

    const res = await http.post('/api/v1/export/requests', payload)
    if (res.data.code === 200 || res.data.code === 201) {
      ElMessage.success('导出申请已提交，请等待审批')
      createDialogVisible.value = false
      fetchRequests()
    }
  } catch {
    // error handled by interceptor
  } finally {
    creating.value = false
  }
}

async function handleApprove(row: ExportRequestDTO) {
  reviewingRow.value = row
  reviewForm.requestId = row.id!
  reviewForm.action = 'APPROVED'
  reviewForm.comment = ''
  reviewDialogVisible.value = true
}

async function handleReject(row: ExportRequestDTO) {
  reviewingRow.value = row
  reviewForm.requestId = row.id!
  reviewForm.action = 'REJECTED'
  reviewForm.comment = ''
  reviewDialogVisible.value = true
}

async function submitReview() {
  if (reviewForm.action === 'REJECTED' && !reviewForm.comment.trim()) {
    ElMessage.warning('驳回时请填写原因')
    return
  }

  reviewing.value = true
  try {
    const res = await http.post(
      `/api/v1/export/requests/${reviewForm.requestId}/review`,
      {
        action: reviewForm.action,
        comment: reviewForm.comment,
      }
    )
    if (res.data.code === 200) {
      ElMessage.success(reviewForm.action === 'APPROVED' ? '已批准该导出申请' : '已驳回该导出申请')
      reviewDialogVisible.value = false
      fetchRequests()
    }
  } catch {
    // error handled by interceptor
  } finally {
    reviewing.value = false
  }
}

async function handleDownload(row: ExportRequestDTO) {
  try {
    const res = await http.get(`/api/v1/export/requests/${row.id}/download`, {
      responseType: 'blob',
    })
    const blob = new Blob([res.data])
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    const disposition = res.headers['content-disposition']
    let filename = `export_${row.requestId}.${getFormatExt(row.formatType)}`
    if (disposition) {
      const match = disposition.match(/filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/)
      if (match && match[1]) {
        filename = match[1].replace(/['"]/g, '')
      }
    }
    link.download = filename
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    ElMessage.success('下载已开始')
  } catch {
    // error handled by interceptor
  }
}

async function handleDelete(row: ExportRequestDTO) {
  try {
    await ElMessageBox.confirm(
      `确定要删除导出申请 "${row.requestId}" 吗？此操作不可恢复。`,
      '删除确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      }
    )
    await http.delete(`/api/v1/export/requests/${row.id}`)
    ElMessage.success('删除成功')
    fetchRequests()
  } catch {
    // user cancelled or error
  }
}

// ======================== Handlers ========================

function handleSearch() {
  pagination.page = 1
  fetchRequests()
}

function handleReset() {
  filterForm.requestId = ''
  filterForm.status = ''
  filterForm.projectId = undefined
  filterForm.exportType = ''
  filterForm.dateRange = ''
  pagination.page = 1
  fetchRequests()
}

function handlePageChange(page: number) {
  pagination.page = page
  fetchRequests()
}

function handleSizeChange(size: number) {
  pagination.size = size
  pagination.page = 1
  fetchRequests()
}

function handleOpenCreate() {
  createForm.subjectSource = 'project'
  createForm.projectId = undefined
  createForm.cohortId = undefined
  createForm.subjectIds = ''
  createForm.exportType = ''
  createForm.selectedFields = []
  createForm.formatType = ''
  createForm.includeImages = false
  createForm.dateFrom = ''
  createForm.dateTo = ''
  createForm.reason = ''
  createDialogVisible.value = true
}

// ======================== Display helpers ========================

function getStatusType(status: ExportStatus): 'warning' | 'primary' | '' | 'success' | 'danger' {
  switch (status) {
    case 'PENDING':
      return 'warning'
    case 'APPROVED':
      return 'primary'
    case 'PROCESSING':
      return ''
    case 'COMPLETED':
      return 'success'
    case 'REJECTED':
      return 'danger'
    default:
      return ''
  }
}

function getStatusLabel(status: ExportStatus): string {
  switch (status) {
    case 'PENDING':
      return '待审批'
    case 'APPROVED':
      return '已批准'
    case 'PROCESSING':
      return '处理中'
    case 'COMPLETED':
      return '已完成'
    case 'REJECTED':
      return '已驳回'
    default:
      return status || '未知'
  }
}

function getExportTypeLabel(type: string): string {
  const found = exportTypeOptions.find((o) => o.code === type)
  return found ? found.name : type || '-'
}

function getFormatLabel(format: string): string {
  const found = formatTypeOptions.find((o) => o.code === format)
  return found ? found.name : format || '-'
}

function getFormatExt(format: string): string {
  switch (format) {
    case 'CSV':
      return 'csv'
    case 'JSON':
      return 'json'
    case 'XLSX':
      return 'xlsx'
    case 'FHIR':
      return 'json'
    case 'BIDS':
      return 'zip'
    default:
      return 'zip'
  }
}

function getProjectName(id: number): string {
  return projectMap.value.get(id) || String(id)
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

function formatDateTime(dateStr?: string): string {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  if (isNaN(d.getTime())) return dateStr
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  return `${y}-${m}-${day} ${hh}:${mm}`
}

function formatFileSize(bytes?: number): string {
  if (bytes === undefined || bytes === null) return '-'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1048576) return `${(bytes / 1024).toFixed(1)} KB`
  if (bytes < 1073741824) return `${(bytes / 1048576).toFixed(1)} MB`
  return `${(bytes / 1073741824).toFixed(2)} GB`
}

// ======================== Init ========================

onMounted(() => {
  fetchProjects()
  fetchRequests()
})
</script>

<template>
  <div class="page-container">
    <!-- Page title -->
    <div class="page-title">数据导出</div>

    <!-- Filter bar -->
    <div class="filter-bar">
      <el-form :inline="true" :model="filterForm" size="default">
        <el-form-item label="申请编号">
          <el-input
            v-model="filterForm.requestId"
            placeholder="请输入申请编号"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>

        <el-form-item label="状态">
          <el-select
            v-model="filterForm.status"
            placeholder="全部"
            clearable
            style="width: 140px"
          >
            <el-option
              v-for="opt in statusOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="项目">
          <el-select
            v-model="filterForm.projectId"
            placeholder="全部"
            clearable
            filterable
            style="width: 200px"
          >
            <el-option
              v-for="proj in projectOptions"
              :key="proj.id"
              :label="proj.name"
              :value="proj.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="数据类型">
          <el-select
            v-model="filterForm.exportType"
            placeholder="全部"
            clearable
            style="width: 150px"
          >
            <el-option
              v-for="opt in exportTypeOptions"
              :key="opt.code"
              :label="opt.name"
              :value="opt.code"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="创建日期">
          <el-date-picker
            v-model="filterForm.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            style="width: 260px"
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
        <el-button type="primary" :icon="Plus" @click="handleOpenCreate">
          新建导出
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
        prop="requestId"
        label="申请编号"
        min-width="160"
        show-overflow-tooltip
      />

      <el-table-column label="申请人" min-width="120" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.requesterName }}
        </template>
      </el-table-column>

      <el-table-column label="项目" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          {{ getProjectName(row.projectId) || row.projectName }}
        </template>
      </el-table-column>

      <el-table-column label="导出类型" width="120" align="center">
        <template #default="{ row }">
          {{ getExportTypeLabel(row.exportType) }}
        </template>
      </el-table-column>

      <el-table-column label="数据范围" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.dataScopeSummary || '-' }}
        </template>
      </el-table-column>

      <el-table-column label="格式" width="80" align="center">
        <template #default="{ row }">
          {{ getFormatLabel(row.formatType) }}
        </template>
      </el-table-column>

      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="getStatusType(row.status)" size="small">
            {{ getStatusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column label="记录数" width="90" align="center">
        <template #default="{ row }">
          {{ row.totalRecords ?? '-' }}
        </template>
      </el-table-column>

      <el-table-column label="文件大小" width="100" align="center">
        <template #default="{ row }">
          {{ formatFileSize(row.fileSize) }}
        </template>
      </el-table-column>

      <el-table-column label="创建时间" width="170" align="center">
        <template #default="{ row }">
          {{ formatDateTime(row.createdAt) }}
        </template>
      </el-table-column>

      <el-table-column label="操作" width="240" align="center" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 'COMPLETED'"
            link
            type="primary"
            size="small"
            :icon="Download"
            @click="handleDownload(row)"
          >
            下载
          </el-button>
          <template v-if="canApprove && row.status === 'PENDING'">
            <el-button
              link
              type="success"
              size="small"
              :icon="Check"
              @click="handleApprove(row)"
            >
              批准
            </el-button>
            <el-button
              link
              type="danger"
              size="small"
              :icon="Close"
              @click="handleReject(row)"
            >
              驳回
            </el-button>
          </template>
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

    <!-- Create export dialog -->
    <el-dialog
      v-model="createDialogVisible"
      title="新建导出申请"
      width="640px"
      :close-on-click-modal="false"
      @closed="createForm.selectedFields = []"
    >
      <div class="create-dialog-body">
        <!-- Subject source selection -->
        <el-form label-width="100px" size="default">
          <el-form-item label="数据来源" required>
            <el-radio-group v-model="createForm.subjectSource">
              <el-radio-button value="project">按项目</el-radio-button>
              <el-radio-button value="cohort">按队列</el-radio-button>
              <el-radio-button value="individual">按个人</el-radio-button>
            </el-radio-group>
          </el-form-item>

          <el-form-item
            v-if="createForm.subjectSource === 'project'"
            label="选择项目"
            required
          >
            <el-select
              v-model="createForm.projectId"
              placeholder="请选择项目"
              filterable
              style="width: 100%"
            >
              <el-option
                v-for="proj in projectOptions"
                :key="proj.id"
                :label="proj.name"
                :value="proj.id"
              />
            </el-select>
          </el-form-item>

          <el-form-item
            v-if="createForm.subjectSource === 'cohort'"
            label="选择队列"
            required
          >
            <el-select
              v-model="createForm.cohortId"
              placeholder="请选择队列"
              filterable
              style="width: 100%"
            >
              <el-option
                v-for="cohort in cohortOptions"
                :key="cohort.id"
                :label="cohort.name"
                :value="cohort.id"
              />
            </el-select>
          </el-form-item>

          <el-form-item
            v-if="createForm.subjectSource === 'individual'"
            label="受试者ID"
            required
          >
            <el-input
              v-model="createForm.subjectIds"
              type="textarea"
              :rows="3"
              placeholder="请输入受试者ID，多个ID用逗号或换行分隔"
            />
          </el-form-item>

          <el-form-item label="数据类型" required>
            <el-select
              v-model="createForm.exportType"
              placeholder="请选择要导出的数据类型"
              style="width: 100%"
            >
              <el-option
                v-for="opt in exportTypeOptions"
                :key="opt.code"
                :label="opt.name"
                :value="opt.code"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="导出字段" required>
            <el-select
              v-model="createForm.selectedFields"
              multiple
              placeholder="请选择要导出的字段/模态"
              style="width: 100%"
            >
              <el-option
                v-for="field in fieldOptions"
                :key="field.code"
                :label="field.name"
                :value="field.code"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="导出格式" required>
            <el-select
              v-model="createForm.formatType"
              placeholder="请选择导出格式"
              style="width: 100%"
            >
              <el-option
                v-for="opt in formatTypeOptions"
                :key="opt.code"
                :label="opt.name"
                :value="opt.code"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="包含影像文件">
            <el-switch v-model="createForm.includeImages" />
            <span class="switch-hint">（仅当选择影像相关字段时有效）</span>
          </el-form-item>

          <el-form-item label="数据时间范围">
            <div class="date-range-row">
              <el-date-picker
                v-model="createForm.dateFrom"
                type="date"
                placeholder="开始日期"
                format="YYYY-MM-DD"
                value-format="YYYY-MM-DD"
                style="width: 190px"
              />
              <span class="date-separator">至</span>
              <el-date-picker
                v-model="createForm.dateTo"
                type="date"
                placeholder="结束日期"
                format="YYYY-MM-DD"
                value-format="YYYY-MM-DD"
                style="width: 190px"
              />
            </div>
          </el-form-item>

          <el-form-item label="申请理由">
            <el-input
              v-model="createForm.reason"
              type="textarea"
              :rows="2"
              placeholder="请简要说明导出数据的目的（选填）"
            />
          </el-form-item>
        </el-form>
      </div>

      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="creating"
          @click="submitCreateRequest"
        >
          提交申请
        </el-button>
      </template>
    </el-dialog>

    <!-- Review dialog -->
    <el-dialog
      v-model="reviewDialogVisible"
      :title="reviewForm.action === 'APPROVED' ? '批准导出申请' : '驳回导出申请'"
      width="480px"
      :close-on-click-modal="false"
    >
      <div class="review-dialog-body">
        <div v-if="reviewingRow" class="review-info">
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="申请编号">
              {{ reviewingRow.requestId }}
            </el-descriptions-item>
            <el-descriptions-item label="申请人">
              {{ reviewingRow.requesterName }}
            </el-descriptions-item>
            <el-descriptions-item label="数据类型">
              {{ getExportTypeLabel(reviewingRow.exportType) }}
            </el-descriptions-item>
            <el-descriptions-item label="数据范围">
              {{ reviewingRow.dataScopeSummary || '-' }}
            </el-descriptions-item>
          </el-descriptions>
        </div>

        <el-form label-width="80px" size="default" class="review-comment-form">
          <el-form-item
            :label="reviewForm.action === 'REJECTED' ? '驳回原因' : '审批意见'"
            :required="reviewForm.action === 'REJECTED'"
          >
            <el-input
              v-model="reviewForm.comment"
              type="textarea"
              :rows="3"
              :placeholder="reviewForm.action === 'REJECTED' ? '请填写驳回原因' : '可选，填写审批意见'"
            />
          </el-form-item>
        </el-form>
      </div>

      <template #footer>
        <el-button @click="reviewDialogVisible = false">取消</el-button>
        <el-button
          :type="reviewForm.action === 'APPROVED' ? 'success' : 'danger'"
          :loading="reviewing"
          @click="submitReview"
        >
          {{ reviewForm.action === 'APPROVED' ? '确认批准' : '确认驳回' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts">
export default {
  name: 'ExportRequestList',
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

// ---- Pagination ----
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
  padding: 0 4px;
}

// ---- Create dialog ----
.create-dialog-body {
  .switch-hint {
    margin-left: 10px;
    font-size: 12px;
    color: #909399;
  }

  .date-range-row {
    display: flex;
    align-items: center;
    gap: 8px;

    .date-separator {
      color: #606266;
      font-size: 14px;
    }
  }
}

// ---- Review dialog ----
.review-dialog-body {
  .review-info {
    margin-bottom: 20px;
  }

  .review-comment-form {
    margin-top: 0;
  }
}
</style>
