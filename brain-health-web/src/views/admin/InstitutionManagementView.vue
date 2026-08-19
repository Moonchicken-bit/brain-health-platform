<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Search, RefreshRight, Plus, Edit, Delete, CircleCheck, CircleClose } from '@element-plus/icons-vue'
import { adminApi, type InstitutionDTO } from '@/api/modules/admin'

// ---- Filter form ----
const filterForm = reactive({
  keyword: '',
  city: '',
})

// ---- Table ----
const tableData = ref<InstitutionDTO[]>([])
const loading = ref(false)
const pagination = reactive({
  page: 1,
  size: 15,
  total: 0,
})

// ---- Dialog ----
const dialogVisible = ref(false)
const dialogTitle = ref('新增机构')
const dialogLoading = ref(false)
const isEdit = ref(false)
const editingId = ref<number | undefined>(undefined)
const formRef = ref<FormInstance>()

const formData = reactive<InstitutionDTO>({
  name: '',
  alias: '',
  city: '',
  contact: '',
  contactPhone: '',
  address: '',
  isActive: true,
})

const formRules: FormRules = {
  name: [
    { required: true, message: '请输入机构名称', trigger: 'blur' },
    { min: 2, max: 80, message: '机构名称长度在 2 到 80 个字符之间', trigger: 'blur' },
  ],
  city: [
    { required: true, message: '请输入所在城市', trigger: 'blur' },
  ],
  contact: [
    { required: true, message: '请输入联系人', trigger: 'blur' },
  ],
  contactPhone: [
    { pattern: /^1[3-9]\d{9}$/, message: '请输入有效的手机号码', trigger: 'blur' },
  ],
}

// ======================== API calls ========================

async function fetchList() {
  loading.value = true
  try {
    const params: Record<string, unknown> = {
      page: pagination.page,
      size: pagination.size,
    }
    if (filterForm.keyword.trim()) params.keyword = filterForm.keyword.trim()
    if (filterForm.city) params.city = filterForm.city

    const res = await adminApi.listInstitutions(params)
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
  fetchList()
}

function handleReset() {
  filterForm.keyword = ''
  filterForm.city = ''
  pagination.page = 1
  fetchList()
}

function handlePageChange(page: number) {
  pagination.page = page
  fetchList()
}

function handleSizeChange(size: number) {
  pagination.size = size
  pagination.page = 1
  fetchList()
}

function handleCreate() {
  isEdit.value = false
  editingId.value = undefined
  dialogTitle.value = '新增机构'
  resetForm()
  dialogVisible.value = true
}

function handleEdit(row: InstitutionDTO) {
  isEdit.value = true
  editingId.value = row.id
  dialogTitle.value = '编辑机构'
  formData.name = row.name
  formData.alias = row.alias || ''
  formData.city = row.city
  formData.contact = row.contact
  formData.contactPhone = row.contactPhone || ''
  formData.address = row.address || ''
  formData.isActive = row.isActive
  dialogVisible.value = true
}

async function handleDelete(row: InstitutionDTO) {
  try {
    await ElMessageBox.confirm(
      `确定要删除机构 "${row.name}" 吗？删除后相关联的数据将无法访问。`,
      '删除确认',
      {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'warning',
      }
    )
    await adminApi.deleteInstitution(row.id!)
    ElMessage.success('删除成功')
    fetchList()
  } catch {
    // user cancelled or error
  }
}

async function handleToggleStatus(row: InstitutionDTO) {
  const newStatus = !row.isActive
  const actionLabel = newStatus ? '启用' : '停用'
  try {
    await ElMessageBox.confirm(
      `确定要${actionLabel}机构 "${row.name}" 吗？`,
      `${actionLabel}确认`,
      {
        confirmButtonText: `确定${actionLabel}`,
        cancelButtonText: '取消',
        type: 'warning',
      }
    )
    await adminApi.toggleInstitutionStatus(row.id!, newStatus)
    ElMessage.success(`${actionLabel}成功`)
    fetchList()
  } catch {
    // user cancelled or error
  }
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  dialogLoading.value = true
  try {
    const payload: InstitutionDTO = {
      name: formData.name.trim(),
      alias: formData.alias?.trim() || undefined,
      city: formData.city.trim(),
      contact: formData.contact.trim(),
      contactPhone: formData.contactPhone?.trim() || undefined,
      address: formData.address?.trim() || undefined,
      isActive: formData.isActive,
    }

    if (isEdit.value && editingId.value) {
      await adminApi.updateInstitution(editingId.value, payload)
      ElMessage.success('更新成功')
    } else {
      await adminApi.createInstitution(payload)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    fetchList()
  } catch {
    // error handled by interceptor
  } finally {
    dialogLoading.value = false
  }
}

function handleDialogClose() {
  resetForm()
}

function resetForm() {
  formData.name = ''
  formData.alias = ''
  formData.city = ''
  formData.contact = ''
  formData.contactPhone = ''
  formData.address = ''
  formData.isActive = true
  formRef.value?.resetFields()
}

// ======================== Display helpers ========================

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
  fetchList()
})
</script>

<template>
  <div class="page-container">
    <!-- Page title -->
    <div class="page-title">机构管理</div>

    <!-- Filter bar -->
    <div class="filter-bar">
      <el-form :inline="true" :model="filterForm" size="default">
        <el-form-item label="关键词">
          <el-input
            v-model="filterForm.keyword"
            placeholder="机构名称/别名/联系人"
            clearable
            style="width: 240px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>

        <el-form-item label="城市">
          <el-input
            v-model="filterForm.city"
            placeholder="请输入城市"
            clearable
            style="width: 160px"
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
        <el-button type="primary" :icon="Plus" @click="handleCreate">
          新增机构
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
        label="机构名称"
        min-width="200"
        show-overflow-tooltip
      />
      <el-table-column
        prop="alias"
        label="别名/简称"
        min-width="140"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          {{ row.alias || '-' }}
        </template>
      </el-table-column>
      <el-table-column
        prop="city"
        label="所在城市"
        width="120"
        show-overflow-tooltip
      />
      <el-table-column
        prop="contact"
        label="联系人"
        width="110"
        show-overflow-tooltip
      />
      <el-table-column
        label="联系电话"
        width="140"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          {{ row.contactPhone || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.isActive ? 'success' : 'info'" size="small">
            {{ row.isActive ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="170" align="center">
        <template #default="{ row }">
          {{ formatDateTime(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="240" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" :icon="Edit" @click="handleEdit(row)">
            编辑
          </el-button>
          <el-button
            link
            :type="row.isActive ? 'warning' : 'success'"
            size="small"
            :icon="row.isActive ? CircleClose : CircleCheck"
            @click="handleToggleStatus(row)"
          >
            {{ row.isActive ? '停用' : '启用' }}
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
      width="560px"
      :close-on-click-modal="false"
      @close="handleDialogClose"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="90px"
        size="default"
      >
        <el-form-item label="机构名称" prop="name">
          <el-input
            v-model="formData.name"
            placeholder="请输入机构全称"
            maxlength="80"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="别名/简称" prop="alias">
          <el-input
            v-model="formData.alias"
            placeholder="机构的常用别名或简称"
            maxlength="40"
          />
        </el-form-item>

        <el-form-item label="所在城市" prop="city">
          <el-input
            v-model="formData.city"
            placeholder="例如：合肥市"
            maxlength="40"
          />
        </el-form-item>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="联系人" prop="contact">
              <el-input
                v-model="formData.contact"
                placeholder="联系人姓名"
                maxlength="20"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话" prop="contactPhone">
              <el-input
                v-model="formData.contactPhone"
                placeholder="手机号码"
                maxlength="11"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="地址" prop="address">
          <el-input
            v-model="formData.address"
            type="textarea"
            :rows="2"
            placeholder="机构详细地址（选填）"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="状态" prop="isActive">
          <el-switch
            v-model="formData.isActive"
            active-text="启用"
            inactive-text="停用"
          />
          <span class="status-hint">
            {{ formData.isActive ? '启用后可在业务中使用' : '停用后将不可用于新业务' }}
          </span>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="dialogLoading" @click="handleSubmit">
          {{ isEdit ? '保存' : '创建' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts">
export default {
  name: 'InstitutionManagementView',
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

// ---- Dialog ----
.status-hint {
  margin-left: 10px;
  font-size: 12px;
  color: #909399;
}
</style>
