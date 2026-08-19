<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, RefreshRight, Download } from '@element-plus/icons-vue'
import { adminApi, type AuditLogEntry } from '@/api/modules/admin'
import http from '@/api/client'
import type { PageResult } from '@/types/api'

// ---- Types ----

interface UserOption {
  id: number
  username: string
  realName: string
}

interface FilterForm {
  userId: number | undefined
  action: string
  resourceType: string
  status: string
  dateRange: [string, string] | null
}

// ---- Helper: compute default date range (last 7 days) ----
function getDefaultDateRange(): [string, string] {
  const today = new Date()
  const endStr = formatISODate(today)
  const start = new Date(today.getTime() - 7 * 24 * 60 * 60 * 1000)
  const startStr = formatISODate(start)
  return [startStr, endStr]
}

function formatISODate(d: Date): string {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

// ---- Filter form ----
const filterForm = reactive<FilterForm>({
  userId: undefined,
  action: '',
  resourceType: '',
  status: '',
  dateRange: getDefaultDateRange(),
})

// ---- Action type options ----
const actionOptions = [
  { label: '登录', value: 'LOGIN' },
  { label: '登出', value: 'LOGOUT' },
  { label: '创建', value: 'CREATE' },
  { label: '更新', value: 'UPDATE' },
  { label: '删除', value: 'DELETE' },
  { label: '查看', value: 'VIEW' },
  { label: '导出', value: 'EXPORT' },
  { label: '批准', value: 'APPROVE' },
  { label: '驳回', value: 'REJECT' },
  { label: '上传', value: 'UPLOAD' },
  { label: '下载', value: 'DOWNLOAD' },
  { label: '导入', value: 'IMPORT' },
  { label: '重置密码', value: 'RESET_PASSWORD' },
  { label: '分配角色', value: 'ASSIGN_ROLE' },
]

// ---- Resource type options ----
const resourceTypeOptions = [
  { label: '用户', value: 'USER' },
  { label: '角色', value: 'ROLE' },
  { label: '权限', value: 'PERMISSION' },
  { label: '受试者', value: 'SUBJECT' },
  { label: '量表评估', value: 'SCALE_ASSESSMENT' },
  { label: '影像检查', value: 'IMAGING_SESSION' },
  { label: '遗传样本', value: 'GENETIC_SAMPLE' },
  { label: '检验结果', value: 'LAB_RESULT' },
  { label: '导出申请', value: 'EXPORT_REQUEST' },
  { label: '就诊记录', value: 'SESSION' },
  { label: '机构', value: 'INSTITUTION' },
  { label: '项目', value: 'PROJECT' },
]

// ---- Status options ----
const statusOptions = [
  { label: '成功', value: 'SUCCESS' },
  { label: '失败', value: 'FAILURE' },
  { label: '警告', value: 'WARNING' },
  { label: '拒绝', value: 'DENIED' },
]

// ---- User dropdown ----
const userOptions = ref<UserOption[]>([])
const userLoading = ref(false)

async function fetchUsers() {
  userLoading.value = true
  try {
    const res = await http.get<{ code: number; data: PageResult<UserOption> }>(
      '/api/v1/admin/users',
      { params: { page: 1, size: 200 } }
    )
    if (res.data.code === 200 && res.data.data) {
      userOptions.value = res.data.data.records
    }
  } catch {
    // silent — dropdown will be empty
  } finally {
    userLoading.value = false
  }
}

// ---- Table ----
const tableData = ref<AuditLogEntry[]>([])
const loading = ref(false)
const pagination = reactive({
  page: 1,
  size: 15,
  total: 0,
})

// ---- Export ----
const exporting = ref(false)

// ======================== API calls ========================

async function fetchAuditLogs() {
  loading.value = true
  try {
    const params: Record<string, unknown> = {
      page: pagination.page,
      size: pagination.size,
    }
    if (filterForm.userId) params.userId = filterForm.userId
    if (filterForm.action) params.action = filterForm.action
    if (filterForm.resourceType) params.resourceType = filterForm.resourceType
    if (filterForm.status) params.status = filterForm.status
    if (filterForm.dateRange && filterForm.dateRange.length === 2) {
      params.dateFrom = filterForm.dateRange[0]
      params.dateTo = filterForm.dateRange[1]
    }

    const res = await adminApi.listAuditLogs(params as Parameters<typeof adminApi.listAuditLogs>[0])
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

async function handleExport() {
  exporting.value = true
  try {
    const params: Record<string, unknown> = {}
    if (filterForm.userId) params.userId = filterForm.userId
    if (filterForm.action) params.action = filterForm.action
    if (filterForm.resourceType) params.resourceType = filterForm.resourceType
    if (filterForm.status) params.status = filterForm.status
    if (filterForm.dateRange && filterForm.dateRange.length === 2) {
      params.dateFrom = filterForm.dateRange[0]
      params.dateTo = filterForm.dateRange[1]
    }

    const res = await http.get('/api/v1/admin/audit-logs/export', {
      params,
      responseType: 'blob',
    })

    const blob = new Blob([res.data])
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    const disposition = res.headers['content-disposition']
    let filename = `audit_logs_${formatISODate(new Date())}.csv`
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
    ElMessage.success('导出已开始')
  } catch {
    // error handled by interceptor
  } finally {
    exporting.value = false
  }
}

// ======================== Handlers ========================

function handleSearch() {
  pagination.page = 1
  fetchAuditLogs()
}

function handleReset() {
  filterForm.userId = undefined
  filterForm.action = ''
  filterForm.resourceType = ''
  filterForm.status = ''
  filterForm.dateRange = getDefaultDateRange()
  pagination.page = 1
  fetchAuditLogs()
}

function handlePageChange(page: number) {
  pagination.page = page
  fetchAuditLogs()
}

function handleSizeChange(size: number) {
  pagination.size = size
  pagination.page = 1
  fetchAuditLogs()
}

function handleDateRangeClear() {
  filterForm.dateRange = null
}

// ======================== Display helpers ========================

function getActionLabel(action: string): string {
  const found = actionOptions.find((o) => o.value === action)
  return found ? found.label : action || '-'
}

function getResourceTypeLabel(type: string): string {
  const found = resourceTypeOptions.find((o) => o.value === type)
  return found ? found.label : type || '-'
}

function getStatusType(status: string): 'success' | 'danger' | 'warning' | 'info' {
  switch (status) {
    case 'SUCCESS':
      return 'success'
    case 'FAILURE':
      return 'danger'
    case 'WARNING':
      return 'warning'
    case 'DENIED':
      return 'danger'
    default:
      return 'info'
  }
}

function getStatusLabel(status: string): string {
  const found = statusOptions.find((o) => o.value === status)
  return found ? found.label : status || '未知'
}

function getActionTagType(action: string): '' | 'success' | 'warning' | 'danger' | 'info' {
  switch (action) {
    case 'CREATE':
    case 'UPLOAD':
    case 'IMPORT':
      return 'success'
    case 'UPDATE':
    case 'ASSIGN_ROLE':
    case 'RESET_PASSWORD':
      return 'warning'
    case 'DELETE':
    case 'REJECT':
      return 'danger'
    case 'EXPORT':
    case 'DOWNLOAD':
      return ''
    case 'APPROVE':
      return 'success'
    case 'LOGIN':
    case 'LOGOUT':
      return 'info'
    case 'VIEW':
      return ''
    default:
      return 'info'
  }
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
  const ss = String(d.getSeconds()).padStart(2, '0')
  return `${y}-${m}-${day} ${hh}:${mm}:${ss}`
}

function getUserDisplayName(userId: number | undefined): string {
  if (!userId) return '-'
  const found = userOptions.value.find((u) => u.id === userId)
  return found ? `${found.realName || found.username} (${found.username})` : String(userId)
}

// ======================== Init ========================

onMounted(() => {
  fetchUsers()
  fetchAuditLogs()
})
</script>

<template>
  <div class="page-container">
    <!-- Page title -->
    <div class="page-title">审计日志</div>

    <!-- Filter bar -->
    <div class="filter-bar">
      <el-form :inline="true" :model="filterForm" size="default">
        <el-form-item label="用户">
          <el-select
            v-model="filterForm.userId"
            placeholder="全部用户"
            clearable
            filterable
            :loading="userLoading"
            style="width: 200px"
          >
            <el-option
              v-for="user in userOptions"
              :key="user.id"
              :label="`${user.realName || user.username} (${user.username})`"
              :value="user.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="操作类型">
          <el-select
            v-model="filterForm.action"
            placeholder="全部"
            clearable
            style="width: 140px"
          >
            <el-option
              v-for="opt in actionOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="资源类型">
          <el-select
            v-model="filterForm.resourceType"
            placeholder="全部"
            clearable
            style="width: 150px"
          >
            <el-option
              v-for="opt in resourceTypeOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="状态">
          <el-select
            v-model="filterForm.status"
            placeholder="全部"
            clearable
            style="width: 120px"
          >
            <el-option
              v-for="opt in statusOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="时间范围">
          <el-date-picker
            v-model="filterForm.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            :clearable="true"
            @clear="handleDateRangeClear"
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
        <el-button
          type="primary"
          :icon="Download"
          :loading="exporting"
          @click="handleExport"
        >
          导出审计日志
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
      <!-- Expandable detail row -->
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="expand-detail">
            <div class="detail-section">
              <h4 class="detail-heading">操作详情</h4>
              <pre class="detail-content">{{ row.detail || '无详细信息' }}</pre>
            </div>
          </div>
        </template>
      </el-table-column>

      <el-table-column
        label="时间"
        width="180"
        align="center"
        sortable
        prop="createdAt"
      >
        <template #default="{ row }">
          {{ formatDateTime(row.createdAt) }}
        </template>
      </el-table-column>

      <el-table-column label="用户" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.username || '-' }}
        </template>
      </el-table-column>

      <el-table-column label="操作" width="120" align="center">
        <template #default="{ row }">
          <el-tag :type="getActionTagType(row.action)" size="small" effect="plain">
            {{ getActionLabel(row.action) }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column label="资源" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          <span>{{ getResourceTypeLabel(row.resourceType) }}</span>
          <template v-if="row.resourceId != null">
            <span class="resource-separator">/</span>
            <span class="resource-id">#{{ row.resourceId }}</span>
          </template>
        </template>
      </el-table-column>

      <el-table-column label="IP 地址" width="150" align="center">
        <template #default="{ row }">
          <code class="ip-address">{{ row.ipAddress || '-' }}</code>
        </template>
      </el-table-column>

      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="getStatusType(row.status)" size="small">
            {{ getStatusLabel(row.status) }}
          </el-tag>
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
  </div>
</template>

<script lang="ts">
export default {
  name: 'AuditLogView',
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

// ---- Expand detail ----
.expand-detail {
  padding: 12px 24px 16px;
  background: #fafafa;
  border-radius: 4px;

  .detail-section {
    .detail-heading {
      margin: 0 0 8px;
      font-size: 13px;
      font-weight: 600;
      color: #606266;
    }

    .detail-content {
      margin: 0;
      padding: 12px 16px;
      background: #fff;
      border: 1px solid #e4e7ed;
      border-radius: 4px;
      font-size: 13px;
      line-height: 1.7;
      color: #303133;
      white-space: pre-wrap;
      word-break: break-all;
      max-height: 300px;
      overflow-y: auto;
    }
  }
}

// ---- Resource display ----
.resource-separator {
  color: #c0c4cc;
  margin: 0 2px;
}

.resource-id {
  color: #909399;
  font-size: 12px;
}

// ---- IP address ----
.ip-address {
  padding: 1px 6px;
  background: #f4f4f5;
  border-radius: 3px;
  font-size: 12px;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  color: #606266;
}
</style>
