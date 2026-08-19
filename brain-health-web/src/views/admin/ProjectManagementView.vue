<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Search, RefreshRight, Plus, Edit, Delete } from '@element-plus/icons-vue'
import { adminApi, type ProjectDTO } from '@/api/modules/admin'

// ---- Filter form ----
const filterForm = reactive({
  keyword: '',
  status: '' as string,
})

// ---- Table ----
const tableData = ref<ProjectDTO[]>([])
const loading = ref(false)
const pagination = reactive({
  page: 1,
  size: 15,
  total: 0,
})

// ---- Dialog state ----
const dialogVisible = ref(false)
const dialogTitle = ref('新增项目')
const isEditing = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const submitting = ref(false)

const formData = reactive<ProjectDTO>({
  name: '',
  alias: '',
  pi: '',
  piContact: '',
  institutionId: 0,
  startDate: '',
  endDate: '',
  recruitmentTarget: 0,
  status: 'PLANNING',
  description: '',
})

// ---- Validation rules ----
const formRules: FormRules = {
  name: [
    { required: true, message: '请输入项目名称', trigger: 'blur' },
    { max: 100, message: '项目名称不能超过100个字符', trigger: 'blur' },
  ],
  alias: [
    { required: true, message: '请输入项目别名/缩写', trigger: 'blur' },
    { max: 50, message: '别名不能超过50个字符', trigger: 'blur' },
  ],
  pi: [
    { required: true, message: '请输入负责人姓名', trigger: 'blur' },
    { max: 50, message: '负责人姓名不能超过50个字符', trigger: 'blur' },
  ],
  startDate: [{ required: true, message: '请选择开始日期', trigger: 'change' }],
  endDate: [{ required: true, message: '请选择结束日期', trigger: 'change' }],
  recruitmentTarget: [
    { required: true, message: '请输入招募目标人数', trigger: 'blur' },
    { type: 'number', min: 1, message: '招募目标人数至少为1', trigger: 'blur' },
  ],
}

// ======================== API calls ========================

async function fetchProjects() {
  loading.value = true
  try {
    const params: { page: number; size: number; keyword?: string; status?: string } = {
      page: pagination.page,
      size: pagination.size,
    }
    if (filterForm.keyword) params.keyword = filterForm.keyword
    if (filterForm.status) params.status = filterForm.status

    const res = await adminApi.listProjects(params)
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

// ======================== Handlers ========================

function handleSearch() {
  pagination.page = 1
  fetchProjects()
}

function handleReset() {
  filterForm.keyword = ''
  filterForm.status = ''
  pagination.page = 1
  fetchProjects()
}

function handlePageChange(page: number) {
  pagination.page = page
  fetchProjects()
}

function handleSizeChange(size: number) {
  pagination.size = size
  pagination.page = 1
  fetchProjects()
}

// ---- CRUD operations ----

function handleCreate() {
  isEditing.value = false
  editingId.value = null
  dialogTitle.value = '新增项目'
  resetForm()
  dialogVisible.value = true
}

function handleEdit(row: ProjectDTO) {
  isEditing.value = true
  editingId.value = row.id!
  dialogTitle.value = '编辑项目'
  formData.name = row.name
  formData.alias = row.alias
  formData.pi = row.pi
  formData.piContact = row.piContact || ''
  formData.institutionId = row.institutionId ?? 0
  formData.startDate = row.startDate || ''
  formData.endDate = row.endDate || ''
  formData.recruitmentTarget = row.recruitmentTarget ?? 0
  formData.status = row.status
  formData.description = row.description || ''
  dialogVisible.value = true
}

async function handleDelete(row: ProjectDTO) {
  try {
    await ElMessageBox.confirm(
      `确定要删除项目 "${row.name}" 吗？删除后项目下的所有关联数据将不可恢复。`,
      '删除确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      }
    )
    await adminApi.deleteProject(row.id!)
    ElMessage.success('删除成功')
    fetchProjects()
  } catch {
    // user cancelled or error (interceptor shows message)
  }
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (isEditing.value && editingId.value) {
      await adminApi.updateProject(editingId.value, { ...formData })
      ElMessage.success('更新成功')
    } else {
      await adminApi.createProject({ ...formData })
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    fetchProjects()
  } catch {
    // error handled by interceptor
  } finally {
    submitting.value = false
  }
}

function handleDialogClosed() {
  formRef.value?.resetFields()
}

function resetForm() {
  formData.name = ''
  formData.alias = ''
  formData.pi = ''
  formData.piContact = ''
  formData.institutionId = 0
  formData.startDate = ''
  formData.endDate = ''
  formData.recruitmentTarget = 0
  formData.status = 'PLANNING'
  formData.description = ''
}

// ======================== Display helpers ========================

const statusMap: Record<string, { label: string; type: 'info' | 'success' | 'warning' | 'danger' }> = {
  PLANNING: { label: '规划中', type: 'info' },
  ACTIVE: { label: '进行中', type: 'success' },
  COMPLETED: { label: '已完成', type: 'warning' },
  SUSPENDED: { label: '已暂停', type: 'danger' },
}

function getStatusLabel(status: string): string {
  return statusMap[status]?.label || status || '-'
}

function getStatusType(status: string): 'info' | 'success' | 'warning' | 'danger' {
  return statusMap[status]?.type || 'info'
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

// ======================== Init ========================

onMounted(() => {
  fetchProjects()
})
</script>

<template>
  <div class="page-container">
    <!-- Page title -->
    <div class="page-title">项目管理</div>

    <!-- Filter bar -->
    <div class="filter-bar">
      <el-form :inline="true" :model="filterForm" size="default">
        <el-form-item label="关键词">
          <el-input
            v-model="filterForm.keyword"
            placeholder="项目名称/别名/负责人"
            clearable
            style="width: 260px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>

        <el-form-item label="状态">
          <el-select
            v-model="filterForm.status"
            placeholder="全部"
            clearable
            style="width: 160px"
          >
            <el-option label="规划中" value="PLANNING" />
            <el-option label="进行中" value="ACTIVE" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="已暂停" value="SUSPENDED" />
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
        <el-button type="primary" :icon="Plus" @click="handleCreate">
          新增项目
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
        prop="name"
        label="项目名称"
        min-width="180"
        show-overflow-tooltip
      />
      <el-table-column
        prop="alias"
        label="别名"
        min-width="140"
        show-overflow-tooltip
      />
      <el-table-column
        prop="pi"
        label="负责人"
        width="120"
        show-overflow-tooltip
      />
      <el-table-column label="开始日期" width="120" align="center">
        <template #default="{ row }">
          {{ formatDate(row.startDate) }}
        </template>
      </el-table-column>
      <el-table-column label="结束日期" width="120" align="center">
        <template #default="{ row }">
          {{ formatDate(row.endDate) }}
        </template>
      </el-table-column>
      <el-table-column label="招募目标" width="110" align="center">
        <template #default="{ row }">
          {{ row.recruitmentTarget ?? '-' }} 人
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="getStatusType(row.status)" size="small">
            {{ getStatusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="170" align="center">
        <template #default="{ row }">
          {{ formatDateTime(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" :icon="Edit" @click="handleEdit(row)">
            编辑
          </el-button>
          <el-button link type="danger" size="small" :icon="Delete" @click="handleDelete(row)">
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

    <!-- Create / Edit dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="600px"
      :close-on-click-modal="false"
      @closed="handleDialogClosed"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="110px"
        size="default"
      >
        <el-form-item label="项目名称" prop="name">
          <el-input
            v-model="formData.name"
            placeholder="请输入项目全称"
            maxlength="100"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="项目别名" prop="alias">
          <el-input
            v-model="formData.alias"
            placeholder="请输入项目别名/缩写"
            maxlength="50"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="负责人(PI)" prop="pi">
          <el-input
            v-model="formData.pi"
            placeholder="请输入项目负责人姓名"
            maxlength="50"
          />
        </el-form-item>

        <el-form-item label="负责人联系方式">
          <el-input
            v-model="formData.piContact"
            placeholder="邮箱或电话"
            maxlength="100"
          />
        </el-form-item>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="开始日期" prop="startDate">
              <el-date-picker
                v-model="formData.startDate"
                type="date"
                placeholder="选择开始日期"
                value-format="YYYY-MM-DD"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结束日期" prop="endDate">
              <el-date-picker
                v-model="formData.endDate"
                type="date"
                placeholder="选择结束日期"
                value-format="YYYY-MM-DD"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="招募目标" prop="recruitmentTarget">
              <el-input-number
                v-model="formData.recruitmentTarget"
                :min="1"
                :max="999999"
                :step="10"
                placeholder="目标受试者人数"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="项目状态">
              <el-select
                v-model="formData.status"
                placeholder="请选择状态"
                style="width: 100%"
              >
                <el-option label="规划中" value="PLANNING" />
                <el-option label="进行中" value="ACTIVE" />
                <el-option label="已完成" value="COMPLETED" />
                <el-option label="已暂停" value="SUSPENDED" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="项目描述">
          <el-input
            v-model="formData.description"
            type="textarea"
            placeholder="请输入项目描述（可选）"
            :rows="3"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="submitting"
          @click="handleSubmit"
        >
          {{ isEditing ? '保存修改' : '创建项目' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts">
export default {
  name: 'ProjectManagementView',
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
</style>
