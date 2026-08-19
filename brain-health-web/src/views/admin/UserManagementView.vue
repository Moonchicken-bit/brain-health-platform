<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Search, RefreshRight, Plus, Key } from '@element-plus/icons-vue'
import { adminApi, type UserDTO } from '@/api/modules/admin'
import http from '@/api/client'

// ======================== Types ========================

interface InstitutionOption {
  id: number
  name: string
}

interface RoleOption {
  id: number
  name: string
  code: string
}

interface ProjectOption {
  id: number
  name: string
}

interface UserFormData {
  username: string
  password: string
  realName: string
  email: string
  phone: string
  institutionId: number | undefined
  roles: string[]
  projectIds: number[]
}

interface ResetPwdForm {
  newPassword: string
  confirmPassword: string
}

// ======================== Search ========================

const keyword = ref('')

// ======================== Table ========================

const tableData = ref<UserDTO[]>([])
const loading = ref(false)
const pagination = reactive({
  page: 1,
  size: 15,
  total: 0,
})

// ======================== Dropdown options ========================

const institutionOptions = ref<InstitutionOption[]>([])
const institutionMap = ref<Map<number, string>>(new Map())
const roleOptions = ref<RoleOption[]>([])
const projectOptions = ref<ProjectOption[]>([])

// ======================== Create / Edit dialog ========================

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const dialogLoading = ref(false)
const dialogTitle = ref('新增用户')
const editingUserId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const defaultFormData: UserFormData = {
  username: '',
  password: '',
  realName: '',
  email: '',
  phone: '',
  institutionId: undefined,
  roles: [],
  projectIds: [],
}

const formData = reactive<UserFormData>({ ...defaultFormData })

const formRules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 50, message: '用户名长度为 3-50 个字符', trigger: 'blur' },
  ],
  realName: [
    { required: true, message: '请输入真实姓名', trigger: 'blur' },
  ],
  email: [
    { type: 'email', message: '请输入有效的邮箱地址', trigger: 'blur' },
  ],
  institutionId: [
    { required: true, message: '请选择所属机构', trigger: 'change' },
  ],
  roles: [
    { required: true, message: '请选择至少一个角色', trigger: 'change' },
  ],
}

const createFormRules: FormRules = {
  ...formRules,
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度为 6-32 个字符', trigger: 'blur' },
  ],
}

// ======================== Reset password dialog ========================

const resetPwdVisible = ref(false)
const resetPwdLoading = ref(false)
const resetPwdUserId = ref<number | null>(null)
const resetPwdUsername = ref('')
const resetPwdFormRef = ref<FormInstance>()
const resetPwdForm = reactive<ResetPwdForm>({
  newPassword: '',
  confirmPassword: '',
})

const resetPwdRules: FormRules = {
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度为 6-32 个字符', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== resetPwdForm.newPassword) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}

// ======================== API calls ========================

async function fetchUsers() {
  loading.value = true
  try {
    const params: { page: number; size: number; keyword?: string } = {
      page: pagination.page,
      size: pagination.size,
    }
    if (keyword.value) params.keyword = keyword.value

    const res = await adminApi.listUsers(params)
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

async function fetchInstitutions() {
  try {
    const res = await http.get<{ code: number; data: InstitutionOption[] }>(
      '/api/v1/admin/institutions'
    )
    if (res.data.code === 200 && res.data.data) {
      institutionOptions.value = res.data.data
      institutionMap.value = new Map(res.data.data.map((item) => [item.id, item.name]))
    }
  } catch {
    // silent
  }
}

async function fetchRoles() {
  try {
    const res = await adminApi.listRoles()
    if (res.data.code === 200 && res.data.data) {
      roleOptions.value = res.data.data
    }
  } catch {
    // silent
  }
}

async function fetchProjects() {
  try {
    const res = await adminApi.listProjects({ page: 1, size: 500 })
    projectOptions.value = (res.data.data?.records || [])
      .filter(project => project.id != null)
      .map(project => ({ id: project.id as number, name: project.name }))
  } catch {
    projectOptions.value = []
  }
}

// ======================== Search handlers ========================

function handleSearch() {
  pagination.page = 1
  fetchUsers()
}

function handleReset() {
  keyword.value = ''
  pagination.page = 1
  fetchUsers()
}

function handlePageChange(page: number) {
  pagination.page = page
  fetchUsers()
}

function handleSizeChange(size: number) {
  pagination.size = size
  pagination.page = 1
  fetchUsers()
}

// ======================== Create / Edit dialog handlers ========================

function openCreateDialog() {
  dialogMode.value = 'create'
  dialogTitle.value = '新增用户'
  editingUserId.value = null
  Object.assign(formData, { ...defaultFormData })
  formRef.value?.resetFields()
  dialogVisible.value = true
}

function openEditDialog(row: UserDTO) {
  dialogMode.value = 'edit'
  dialogTitle.value = '编辑用户'
  editingUserId.value = row.id ?? null
  formData.username = row.username
  formData.password = ''
  formData.realName = row.realName
  formData.email = row.email
  formData.phone = row.phone || ''
  formData.institutionId = row.institutionId
  formData.roles = [...(row.roles || [])]
  formData.projectIds = [...(row.projectIds || [])]
  formRef.value?.clearValidate()
  dialogVisible.value = true
}

async function handleDialogSubmit() {
  const rules = dialogMode.value === 'create' ? createFormRules : formRules
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  dialogLoading.value = true
  try {
    const payload: any = {
      username: formData.username,
      realName: formData.realName,
      email: formData.email,
      phone: formData.phone,
      institutionId: formData.institutionId,
      roles: formData.roles,
      projectIds: formData.projectIds,
    }

    if (dialogMode.value === 'create') {
      payload.password = formData.password
      await adminApi.createUser(payload)
      ElMessage.success('用户创建成功')
    } else {
      await adminApi.updateUser(editingUserId.value!, payload)
      ElMessage.success('用户信息更新成功')
    }

    dialogVisible.value = false
    fetchUsers()
  } catch {
    // error handled by interceptor
  } finally {
    dialogLoading.value = false
  }
}

// ======================== Toggle status ========================

async function handleToggleStatus(row: UserDTO) {
  const newStatus = !row.isActive
  const actionLabel = newStatus ? '启用' : '停用'

  try {
    await ElMessageBox.confirm(
      `确定要${actionLabel}用户 "${row.username}" 吗？`,
      `${actionLabel}确认`,
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      }
    )
    await adminApi.toggleUserStatus(row.id!, newStatus)
    ElMessage.success(`${actionLabel}成功`)
    row.isActive = newStatus
  } catch {
    // user cancelled or error
  }
}

// ======================== Reset password ========================

function openResetPwdDialog(row: UserDTO) {
  resetPwdUserId.value = row.id ?? null
  resetPwdUsername.value = row.username
  resetPwdForm.newPassword = ''
  resetPwdForm.confirmPassword = ''
  resetPwdFormRef.value?.resetFields()
  resetPwdVisible.value = true
}

async function handleResetPwd() {
  const valid = await resetPwdFormRef.value?.validate().catch(() => false)
  if (!valid) return

  resetPwdLoading.value = true
  try {
    await adminApi.resetPassword(resetPwdUserId.value!, resetPwdForm.newPassword)
    ElMessage.success('密码重置成功')
    resetPwdVisible.value = false
  } catch {
    // error handled by interceptor
  } finally {
    resetPwdLoading.value = false
  }
}

// ======================== Display helpers ========================

function getInstitutionName(id: number): string {
  return institutionMap.value.get(id) || String(id)
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

function getRoleLabel(code: string): string {
  const role = roleOptions.value.find((r) => r.code === code)
  return role ? role.name : code
}

function getStatusType(isActive: boolean): 'success' | 'danger' {
  return isActive ? 'success' : 'danger'
}

// ======================== Init ========================

onMounted(() => {
  fetchInstitutions()
  fetchRoles()
  fetchProjects()
  fetchUsers()
})
</script>

<template>
  <div class="page-container">
    <!-- Page title -->
    <div class="page-title">用户管理</div>

    <!-- Filter bar -->
    <div class="filter-bar">
      <el-form :inline="true" size="default" @submit.prevent="handleSearch">
        <el-form-item label="关键词">
          <el-input
            v-model="keyword"
            placeholder="用户名 / 真实姓名 / 邮箱"
            clearable
            style="width: 260px"
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
        <el-button type="primary" :icon="Plus" @click="openCreateDialog">
          新增用户
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
        prop="username"
        label="用户名"
        min-width="140"
        show-overflow-tooltip
      />
      <el-table-column
        prop="realName"
        label="真实姓名"
        min-width="120"
        show-overflow-tooltip
      />
      <el-table-column
        prop="email"
        label="邮箱"
        min-width="200"
        show-overflow-tooltip
      />
      <el-table-column label="机构" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">
          {{ getInstitutionName(row.institutionId) }}
        </template>
      </el-table-column>
      <el-table-column label="角色" min-width="160">
        <template #default="{ row }">
          <template v-if="row.roles && row.roles.length">
            <el-tag
              v-for="role in row.roles"
              :key="role"
              size="small"
              type=""
              style="margin-right: 4px; margin-bottom: 2px"
            >
              {{ getRoleLabel(role) }}
            </el-tag>
          </template>
          <span v-else class="text-muted">-</span>
        </template>
      </el-table-column>
      <el-table-column label="项目范围" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.projectIds?.length
            ? projectOptions.filter(project => row.projectIds.includes(project.id)).map(project => project.name).join('、')
            : '全部/按机构' }}
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-switch
            :model-value="row.isActive"
            :active-text="row.isActive ? '启用' : '停用'"
            :active-value="true"
            :inactive-value="false"
            inline-prompt
            size="small"
            @change="handleToggleStatus(row)"
          />
        </template>
      </el-table-column>
      <el-table-column label="最后登录" width="170" align="center">
        <template #default="{ row }">
          {{ formatDateTime((row as any).lastLoginAt) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openEditDialog(row)">
            编辑
          </el-button>
          <el-button link type="warning" size="small" @click="openResetPwdDialog(row)">
            重置密码
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
      width="580px"
      :close-on-click-modal="false"
      @closed="formRef?.resetFields()"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="dialogMode === 'create' ? createFormRules : formRules"
        label-width="100px"
        size="default"
      >
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="用户名" prop="username">
              <el-input
                v-model="formData.username"
                placeholder="请输入用户名"
                :disabled="dialogMode === 'edit'"
              />
            </el-form-item>
          </el-col>
          <el-col v-if="dialogMode === 'create'" :span="12">
            <el-form-item label="密码" prop="password">
              <el-input
                v-model="formData.password"
                type="password"
                placeholder="请输入密码"
                show-password
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="真实姓名" prop="realName">
              <el-input
                v-model="formData.realName"
                placeholder="请输入真实姓名"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="邮箱" prop="email">
              <el-input
                v-model="formData.email"
                placeholder="请输入邮箱"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="手机号" prop="phone">
          <el-input
            v-model="formData.phone"
            placeholder="请输入手机号（选填）"
          />
        </el-form-item>

        <el-form-item label="所属机构" prop="institutionId">
          <el-select
            v-model="formData.institutionId"
            placeholder="请选择机构"
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="inst in institutionOptions"
              :key="inst.id"
              :label="inst.name"
              :value="inst.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="角色" prop="roles">
          <el-select
            v-model="formData.roles"
            placeholder="请选择角色"
            multiple
            style="width: 100%"
          >
            <el-option
              v-for="role in roleOptions"
              :key="role.code"
              :label="role.name"
              :value="role.code"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="项目数据范围">
          <el-select
            v-model="formData.projectIds"
            placeholder="可多选；留空时按所属机构控制"
            multiple
            filterable
            clearable
            style="width: 100%"
          >
            <el-option
              v-for="project in projectOptions"
              :key="project.id"
              :label="project.name"
              :value="project.id"
            />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="dialogLoading"
          @click="handleDialogSubmit"
        >
          {{ dialogMode === 'create' ? '创建' : '保存' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- Reset password dialog -->
    <el-dialog
      v-model="resetPwdVisible"
      title="重置密码"
      width="480px"
      :close-on-click-modal="false"
    >
      <div class="reset-pwd-hint">
        正在为用户 <strong>{{ resetPwdUsername }}</strong> 重置密码
      </div>

      <el-form
        ref="resetPwdFormRef"
        :model="resetPwdForm"
        :rules="resetPwdRules"
        label-width="100px"
        size="default"
      >
        <el-form-item label="新密码" prop="newPassword">
          <el-input
            v-model="resetPwdForm.newPassword"
            type="password"
            placeholder="请输入新密码"
            show-password
          />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="resetPwdForm.confirmPassword"
            type="password"
            placeholder="请再次输入新密码"
            show-password
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="resetPwdVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="resetPwdLoading"
          @click="handleResetPwd"
        >
          确认重置
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts">
export default {
  name: 'UserManagementView',
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

// ---- Reset password ----
.reset-pwd-hint {
  font-size: 14px;
  color: #606266;
  margin-bottom: 20px;
  line-height: 1.6;
}

.text-muted {
  color: #c0c4cc;
}
</style>
