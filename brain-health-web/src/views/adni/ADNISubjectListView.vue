<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, RefreshRight, Download, Link } from '@element-plus/icons-vue'
import { adniApi, type ADNISubjectDTO, type ADNISubjectSearchParams, type ADNIStatistics } from '@/api/modules/adni'
import { subjectApi, type SubjectDTO } from '@/api/modules/subject'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()

// ---- Statistics ----
const stats = ref<ADNIStatistics>({
  totalSubjects: 0,
  cnCount: 0,
  mciCount: 0,
  adCount: 0,
  otherCount: 0,
})

// ---- Filter form ----
const filterForm = reactive<ADNISubjectSearchParams>({
  diagnosis: '',
  ageMin: undefined,
  ageMax: undefined,
  sex: '',
  apoeStatus: '',
})

// ---- Table ----
const tableData = ref<ADNISubjectDTO[]>([])
const loading = ref(false)
const pagination = reactive({
  page: 1,
  size: 15,
  total: 0,
})

// ---- Link-to-local dialog ----
const linkDialogVisible = ref(false)
const linkTarget = ref<ADNISubjectDTO | null>(null)
const localSubjectSearchKeyword = ref('')
const localSubjectOptions = ref<SubjectDTO[]>([])
const selectedLocalSubjectId = ref<number | null>(null)
const linking = ref(false)
const localSubjectLoading = ref(false)

// ---- Import trigger state ----
const importing = ref(false)
const importInput = ref<HTMLInputElement | null>(null)

// ======================== API calls ========================

async function fetchStatistics() {
  try {
    const res = await adniApi.statistics()
    if (res.data.code === 200) {
      stats.value = res.data.data
    }
  } catch {
    // silent
  }
}

async function fetchSubjects() {
  loading.value = true
  try {
    const params: ADNISubjectSearchParams = {
      page: pagination.page,
      size: pagination.size,
    }
    if (filterForm.diagnosis) params.diagnosis = filterForm.diagnosis
    if (filterForm.ageMin !== undefined) params.ageMin = filterForm.ageMin
    if (filterForm.ageMax !== undefined) params.ageMax = filterForm.ageMax
    if (filterForm.sex) params.sex = filterForm.sex
    if (filterForm.apoeStatus) params.apoeStatus = filterForm.apoeStatus

    const res = await adniApi.list(params)
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

async function searchLocalSubjects(query: string) {
  if (!query || query.length < 1) {
    localSubjectOptions.value = []
    return
  }
  localSubjectLoading.value = true
  try {
    const res = await subjectApi.list({ keyword: query, size: 20 })
    if (res.data.code === 200) {
      localSubjectOptions.value = res.data.data.records
    }
  } catch {
    // silent
  } finally {
    localSubjectLoading.value = false
  }
}

// ======================== Handlers ========================

function handleSearch() {
  pagination.page = 1
  fetchSubjects()
}

function handleReset() {
  filterForm.diagnosis = ''
  filterForm.ageMin = undefined
  filterForm.ageMax = undefined
  filterForm.sex = ''
  filterForm.apoeStatus = ''
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

function handleOpenLinkDialog(row: ADNISubjectDTO) {
  linkTarget.value = row
  selectedLocalSubjectId.value = null
  localSubjectSearchKeyword.value = ''
  localSubjectOptions.value = []
  linkDialogVisible.value = true
}

async function handleLinkToLocal() {
  if (!selectedLocalSubjectId.value) {
    ElMessage.warning('请先选择本地受试者')
    return
  }
  if (!linkTarget.value?.id) return

  linking.value = true
  try {
    const res = await adniApi.linkToLocalSubject(linkTarget.value.id, selectedLocalSubjectId.value)
    if (res.data.code === 200) {
      ElMessage.success('关联成功')
      linkDialogVisible.value = false
      fetchSubjects()
    }
  } catch {
    // error handled by interceptor
  } finally {
    linking.value = false
  }
}

function handleTriggerImport() {
  importInput.value?.click()
}

async function handleImportFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  importing.value = true
  try {
    const res = await adniApi.triggerImport(file)
    if (res.data.code === 200) {
      const result = res.data.data as { importedCount?: number; updatedCount?: number }
      ElMessage.success(`导入完成：新增 ${result.importedCount ?? 0} 条，更新 ${result.updatedCount ?? 0} 条`)
      fetchStatistics()
      fetchSubjects()
    }
  } catch {
    // error handled by interceptor
  } finally {
    importing.value = false
    input.value = ''
  }
}

function handleLocalSubjectSelect(id: number) {
  selectedLocalSubjectId.value = id
}

function handleLocalSubjectSearch(query: string) {
  localSubjectSearchKeyword.value = query
  searchLocalSubjects(query)
}

// ======================== Display helpers ========================

function getDiagnosisTagType(diagnosis: string): 'success' | 'warning' | 'danger' | 'info' {
  switch (diagnosis) {
    case 'CN': return 'success'
    case 'MCI': return 'warning'
    case 'AD': return 'danger'
    default: return 'info'
  }
}

function getDiagnosisLabel(diagnosis: string): string {
  switch (diagnosis) {
    case 'CN': return '认知正常'
    case 'MCI': return '轻度认知障碍'
    case 'AD': return '阿尔茨海默病'
    default: return diagnosis || '-'
  }
}

function getSexLabel(sex?: string): string {
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

function isAdmin(): boolean {
  return authStore.hasAnyRole(['admin', 'superadmin'])
}

// ======================== Init ========================

onMounted(() => {
  fetchStatistics()
  fetchSubjects()
})
</script>

<template>
  <div class="page-container">
    <!-- Page title -->
    <div class="page-title">ADNI 公开数据集</div>

    <!-- Statistics cards -->
    <div class="stats-row">
      <div class="stat-card stat-total">
        <div class="stat-value">{{ stats.totalSubjects }}</div>
        <div class="stat-label">受试者总数</div>
      </div>
      <div class="stat-card stat-cn">
        <div class="stat-value">{{ stats.cnCount }}</div>
        <div class="stat-label">认知正常 (CN)</div>
      </div>
      <div class="stat-card stat-mci">
        <div class="stat-value">{{ stats.mciCount }}</div>
        <div class="stat-label">轻度认知障碍 (MCI)</div>
      </div>
      <div class="stat-card stat-ad">
        <div class="stat-value">{{ stats.adCount }}</div>
        <div class="stat-label">阿尔茨海默病 (AD)</div>
      </div>
    </div>

    <!-- Filter bar -->
    <div class="filter-bar">
      <el-form :inline="true" :model="filterForm" size="default">
        <el-form-item label="诊断">
          <el-select
            v-model="filterForm.diagnosis"
            placeholder="全部"
            clearable
            style="width: 160px"
          >
            <el-option label="认知正常" value="CN" />
            <el-option label="轻度认知障碍" value="MCI" />
            <el-option label="阿尔茨海默病" value="AD" />
          </el-select>
        </el-form-item>

        <el-form-item label="年龄范围">
          <el-input-number
            v-model="filterForm.ageMin"
            :min="0"
            :max="120"
            placeholder="最小"
            controls-position="right"
            style="width: 110px"
          />
          <span style="margin: 0 8px; color: #909399">—</span>
          <el-input-number
            v-model="filterForm.ageMax"
            :min="0"
            :max="120"
            placeholder="最大"
            controls-position="right"
            style="width: 110px"
          />
        </el-form-item>

        <el-form-item label="性别">
          <el-select
            v-model="filterForm.sex"
            placeholder="全部"
            clearable
            style="width: 100px"
          >
            <el-option label="男" value="M" />
            <el-option label="女" value="F" />
          </el-select>
        </el-form-item>

        <el-form-item label="ApoE">
          <el-select
            v-model="filterForm.apoeStatus"
            placeholder="全部"
            clearable
            style="width: 130px"
          >
            <el-option label="ε2/ε2" value="E2E2" />
            <el-option label="ε2/ε3" value="E2E3" />
            <el-option label="ε2/ε4" value="E2E4" />
            <el-option label="ε3/ε3" value="E3E3" />
            <el-option label="ε3/ε4" value="E3E4" />
            <el-option label="ε4/ε4" value="E4E4" />
          </el-select>
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
          v-if="isAdmin()"
          type="warning"
          :icon="Download"
          :loading="importing"
          @click="handleTriggerImport"
        >
          导入ADNI数据
        </el-button>
        <input
          ref="importInput"
          type="file"
          accept=".csv,.xls,.xlsx"
          hidden
          @change="handleImportFile"
        />
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
        prop="adniSubjectId"
        label="ADNI 受试者ID"
        min-width="180"
        show-overflow-tooltip
      />

      <el-table-column label="诊断" width="150" align="center">
        <template #default="{ row }">
          <el-tag :type="getDiagnosisTagType(row.diagnosis)" size="small">
            {{ getDiagnosisLabel(row.diagnosis) }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column label="年龄" width="80" align="center">
        <template #default="{ row }">
          {{ row.age ?? '-' }}
        </template>
      </el-table-column>

      <el-table-column label="性别" width="70" align="center">
        <template #default="{ row }">
          {{ getSexLabel(row.sex) }}
        </template>
      </el-table-column>

      <el-table-column label="教育程度" width="120" align="center">
        <template #default="{ row }">
          {{ getEducationLabel(row.educationYears) }}
        </template>
      </el-table-column>

      <el-table-column label="ApoE" width="100" align="center">
        <template #default="{ row }">
          {{ row.apoeGenotype || '-' }}
        </template>
      </el-table-column>

      <el-table-column label="影像数据" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.hasImaging ? 'success' : 'info'" size="small">
            {{ row.hasImaging ? '有' : '无' }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column label="遗传数据" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.hasGenetics ? 'success' : 'info'" size="small">
            {{ row.hasGenetics ? '有' : '无' }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column label="导入时间" width="170" align="center">
        <template #default="{ row }">
          {{ formatDate(row.importedAt) }}
        </template>
      </el-table-column>

      <el-table-column label="操作" width="140" align="center" fixed="right">
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            size="small"
            :icon="Link"
            @click="handleOpenLinkDialog(row)"
          >
            导入到本地
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

    <!-- Link to local subject dialog -->
    <el-dialog
      v-model="linkDialogVisible"
      title="关联到本地受试者"
      width="520px"
      :close-on-click-modal="false"
    >
      <div v-if="linkTarget" class="link-dialog-body">
        <el-descriptions :column="1" border size="small" style="margin-bottom: 20px">
          <el-descriptions-item label="ADNI ID">
            {{ linkTarget.adniSubjectId }}
          </el-descriptions-item>
          <el-descriptions-item label="诊断">
            <el-tag :type="getDiagnosisTagType(linkTarget.diagnosis)" size="small">
              {{ getDiagnosisLabel(linkTarget.diagnosis) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="性别">
            {{ getSexLabel(linkTarget.sex) }}
          </el-descriptions-item>
          <el-descriptions-item label="ApoE">
            {{ linkTarget.apoeGenotype || '-' }}
          </el-descriptions-item>
        </el-descriptions>

        <p class="link-dialog-tip">
          请搜索并选择要关联的本地受试者：
        </p>

        <el-select
          v-model="selectedLocalSubjectId"
          placeholder="请搜索本地受试者ID或关键词"
          filterable
          remote
          reserve-keyword
          :remote-method="handleLocalSubjectSearch"
          :loading="localSubjectLoading"
          style="width: 100%"
          clearable
        >
          <el-option
            v-for="item in localSubjectOptions"
            :key="item.id"
            :label="`${item.subjectId} (${getSexLabel(item.sex)}, ${getEducationLabel(item.educationYears)})`"
            :value="item.id!"
          />
        </el-select>

        <div v-if="linkTarget.localSubjectId" class="already-linked">
          <el-alert
            type="info"
            :closable="false"
            show-icon
          >
            <template #title>
              该 ADNI 受试者当前已关联到本地受试者 (ID: {{ linkTarget.localSubjectId }})，
              重新选择将覆盖现有关联。
            </template>
          </el-alert>
        </div>
      </div>

      <template #footer>
        <el-button @click="linkDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="linking"
          :disabled="!selectedLocalSubjectId"
          @click="handleLinkToLocal"
        >
          确认关联
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts">
export default {
  name: 'ADNISubjectListView',
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

// ---- Statistics cards ----
.stats-row {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
}

.stat-card {
  flex: 1;
  min-width: 0;
  background: #fff;
  border-radius: 8px;
  padding: 20px 24px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  border-left: 4px solid transparent;

  .stat-value {
    font-size: 28px;
    font-weight: 700;
    line-height: 1.2;
    margin-bottom: 4px;
  }

  .stat-label {
    font-size: 13px;
    color: #909399;
  }

  &.stat-total {
    border-left-color: #409eff;
    .stat-value { color: #409eff; }
  }

  &.stat-cn {
    border-left-color: #67c23a;
    .stat-value { color: #67c23a; }
  }

  &.stat-mci {
    border-left-color: #e6a23c;
    .stat-value { color: #e6a23c; }
  }

  &.stat-ad {
    border-left-color: #f56c6c;
    .stat-value { color: #f56c6c; }
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

// ---- Link dialog ----
.link-dialog-body {
  .link-dialog-tip {
    font-size: 14px;
    color: #606266;
    margin: 0 0 12px;
    line-height: 1.6;
  }

  .already-linked {
    margin-top: 12px;
  }
}
</style>
