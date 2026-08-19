<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, RefreshRight, Upload, Download, Star, StarFilled } from '@element-plus/icons-vue'
import { subjectApi, type SubjectDTO, type SubjectSearchParams } from '@/api/modules/subject'
import http from '@/api/client'

const router = useRouter()

// ---- Filter form ----
const filterForm = reactive<SubjectSearchParams>({
  subjectId: '',
  sex: '',
  institutionId: undefined,
  projectId: undefined,
  keyword: '',
})

// ---- Dropdown options ----
interface OptionItem {
  id: number
  name: string
}

const institutionOptions = ref<OptionItem[]>([])
const projectOptions = ref<OptionItem[]>([])

// ---- Table ----
const tableData = ref<SubjectDTO[]>([])
const loading = ref(false)
const pagination = reactive({
  page: 1,
  size: 15,
  total: 0,
})
const favoriteSubjectIds = ref(new Set<number>())

// ---- Lookup maps for ID-to-name display ----
const institutionMap = ref<Map<number, string>>(new Map())
const projectMap = ref<Map<number, string>>(new Map())

// ---- Import dialog ----
const importDialogVisible = ref(false)
const importFile = ref<File | null>(null)
const importing = ref(false)

// ======================== API calls ========================

async function fetchSubjects() {
  loading.value = true
  try {
    const params: SubjectSearchParams = {
      page: pagination.page,
      size: pagination.size,
    }
    if (filterForm.subjectId) params.subjectId = filterForm.subjectId
    if (filterForm.sex) params.sex = filterForm.sex
    if (filterForm.institutionId) params.institutionId = filterForm.institutionId
    if (filterForm.projectId) params.projectId = filterForm.projectId
    if (filterForm.keyword) params.keyword = filterForm.keyword

    const res = await subjectApi.list(params)
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

async function fetchFavorites() {
  try {
    const res = await subjectApi.getFavorites()
    favoriteSubjectIds.value = new Set(res.data.data || [])
  } catch {
    favoriteSubjectIds.value = new Set()
  }
}

async function toggleFavorite(row: SubjectDTO) {
  if (!row.id) return
  const favorite = !favoriteSubjectIds.value.has(row.id)
  await subjectApi.setFavorite(row.id, favorite)
  const next = new Set(favoriteSubjectIds.value)
  favorite ? next.add(row.id) : next.delete(row.id)
  favoriteSubjectIds.value = next
  ElMessage.success(favorite ? '已加入个人星标' : '已取消个人星标')
}

async function fetchInstitutions() {
  try {
    const res = await http.get<{ code: number; data: OptionItem[] }>('/api/v1/admin/institutions')
    if (res.data.code === 200 && res.data.data) {
      institutionOptions.value = res.data.data
      institutionMap.value = new Map(res.data.data.map((item) => [item.id, item.name]))
    }
  } catch {
    // silent — dropdown will be empty
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

// ======================== Handlers ========================

function handleSearch() {
  pagination.page = 1
  fetchSubjects()
}

function handleReset() {
  filterForm.subjectId = ''
  filterForm.sex = ''
  filterForm.institutionId = undefined
  filterForm.projectId = undefined
  filterForm.keyword = ''
  pagination.page = 1
  fetchSubjects()
}

function handlePageChange(page: number) {
  pagination.page = page
  fetchSubjects()
}

function handleSizeChange(size: number) {
  pagination.size = size
  pagination.page = 1
  fetchSubjects()
}

function handleView(row: SubjectDTO) {
  router.push(`/subjects/${row.id}`)
}

function handleEdit(row: SubjectDTO) {
  router.push({ path: `/subjects/${row.id}`, query: { edit: '1' } })
}

async function handleDelete(row: SubjectDTO) {
  try {
    await ElMessageBox.confirm(
      `确定要删除受试者 "${row.subjectId}" 吗？此操作不可恢复。`,
      '删除确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      }
    )
    await subjectApi.delete(row.id!)
    ElMessage.success('删除成功')
    fetchSubjects()
  } catch {
    // user cancelled or error (interceptor shows message)
  }
}

function handleGoRegister() {
  router.push('/subjects/register')
}

function handleImportOpen() {
  importFile.value = null
  importDialogVisible.value = true
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
    const formData = new FormData()
    formData.append('file', importFile.value)
    await subjectApi.batchImport(formData)
    ElMessage.success('导入成功')
    importDialogVisible.value = false
    fetchSubjects()
  } catch {
    // error handled by interceptor
  } finally {
    importing.value = false
  }
}

function handleDownloadTemplate() {
  // Trigger download of the CSV/Excel template file
  const link = document.createElement('a')
  link.href = '/templates/subject_import_template.csv'
  link.download = 'subject_import_template.csv'
  link.click()
}

// ======================== Display helpers ========================

function getSexLabel(sex: string): string {
  if (sex === 'M' || sex === '男') return '男'
  if (sex === 'F' || sex === '女') return '女'
  return sex || '-'
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

function getEducationLabel(years?: number): string {
  if (years === undefined || years === null) return '-'
  if (years <= 6) return '小学'
  if (years <= 9) return '初中'
  if (years <= 12) return '高中/中专'
  if (years <= 16) return '本科/大专'
  return '研究生及以上'
}

function getInstitutionName(id: number): string {
  return institutionMap.value.get(id) || String(id)
}

function getProjectName(id: number): string {
  return projectMap.value.get(id) || String(id)
}

function getStatusType(isActive?: boolean): 'success' | 'info' {
  return isActive ? 'success' : 'info'
}

function getStatusLabel(isActive?: boolean): string {
  return isActive ? '活跃' : '停用'
}

// ======================== Init ========================

onMounted(() => {
  fetchInstitutions()
  fetchProjects()
  fetchSubjects()
  fetchFavorites()
})
</script>

<template>
  <div class="page-container">
    <!-- Page title -->
    <div class="page-title">受试者管理</div>

    <!-- Filter bar -->
    <div class="filter-bar">
      <el-form :inline="true" :model="filterForm" size="default">
        <el-form-item label="受试者ID">
          <el-input
            v-model="filterForm.subjectId"
            placeholder="请输入受试者ID"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>

        <el-form-item label="性别">
          <el-select
            v-model="filterForm.sex"
            placeholder="全部"
            clearable
            style="width: 120px"
          >
            <el-option label="男" value="M" />
            <el-option label="女" value="F" />
          </el-select>
        </el-form-item>

        <el-form-item label="机构">
          <el-select
            v-model="filterForm.institutionId"
            placeholder="全部"
            clearable
            filterable
            style="width: 200px"
          >
            <el-option
              v-for="inst in institutionOptions"
              :key="inst.id"
              :label="inst.name"
              :value="inst.id"
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

        <el-form-item label="关键词">
          <el-input
            v-model="filterForm.keyword"
            placeholder="姓名/备注等"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
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
        <el-button type="primary" @click="handleGoRegister">
          登记受试者
        </el-button>
        <el-button :icon="Upload" @click="handleImportOpen">
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
      <el-table-column label="星标" width="64" align="center" fixed="left">
        <template #default="{ row }">
          <el-button
            link
            :type="favoriteSubjectIds.has(row.id) ? 'warning' : 'info'"
            :icon="favoriteSubjectIds.has(row.id) ? StarFilled : Star"
            :aria-label="favoriteSubjectIds.has(row.id) ? '取消星标' : '加入星标'"
            @click.stop="toggleFavorite(row)"
          />
        </template>
      </el-table-column>
      <el-table-column
        prop="subjectId"
        label="受试者ID"
        min-width="160"
        show-overflow-tooltip
      />
      <el-table-column label="性别" width="70" align="center">
        <template #default="{ row }">
          {{ getSexLabel(row.sex) }}
        </template>
      </el-table-column>
      <el-table-column label="出生日期" width="120" align="center">
        <template #default="{ row }">
          {{ formatDate(row.dateOfBirth) }}
        </template>
      </el-table-column>
      <el-table-column label="教育程度" width="120" align="center">
        <template #default="{ row }">
          {{ getEducationLabel(row.educationYears) }}
        </template>
      </el-table-column>
      <el-table-column label="机构" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          {{ getInstitutionName(row.institutionId) }}
        </template>
      </el-table-column>
      <el-table-column label="项目" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          {{ getProjectName(row.projectId) }}
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="getStatusType(row.isActive)" size="small">
            {{ getStatusLabel(row.isActive) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="登记时间" width="170" align="center">
        <template #default="{ row }">
          {{ formatDateTime(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="handleView(row)">
            查看
          </el-button>
          <el-button link type="primary" size="small" @click="handleEdit(row)">
            编辑
          </el-button>
          <el-button link type="danger" size="small" @click="handleDelete(row)">
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

    <!-- Batch import dialog -->
    <el-dialog
      v-model="importDialogVisible"
      title="批量导入受试者"
      width="520px"
      :close-on-click-modal="false"
    >
      <div class="import-dialog-body">
        <p class="import-tip">
          请上传 CSV 或 Excel (.xlsx) 格式的受试者数据文件。
          文件大小不超过 10MB。
        </p>

        <el-upload
          ref="uploadRef"
          drag
          :auto-upload="false"
          :limit="1"
          accept=".csv,.xlsx,.xls"
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
              支持 .csv / .xlsx 格式
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
          :disabled="!importFile"
          @click="handleImport"
        >
          导入
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts">
export default {
  name: 'SubjectListView',
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

// ---- Import dialog ----
.import-dialog-body {
  .import-tip {
    font-size: 14px;
    color: #606266;
    margin: 0 0 16px;
    line-height: 1.6;
  }

  .import-template {
    margin-top: 12px;
    text-align: center;
  }
}
</style>
