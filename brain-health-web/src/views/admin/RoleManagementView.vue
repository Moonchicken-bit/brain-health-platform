<script setup lang="ts">
import { ref, reactive, onMounted, computed, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, Search } from '@element-plus/icons-vue'
import { adminApi, type RoleDTO, type PermissionDTO } from '@/api/modules/admin'

// ---- Resource labels ----
const RESOURCE_LABELS: Record<string, string> = {
  subject: '受试者管理',
  session: '访视管理',
  scale: '量表管理',
  imaging: '影像管理',
  genetics: '遗传数据',
  lab: '检验数据',
  export: '数据导出',
  admin: '系统管理',
}

// ---- Table ----
interface RoleRow {
  id: number
  name: string
  code: string
  description: string
  userCount: number
  permissions: number[]
}

const tableData = ref<RoleRow[]>([])
const loading = ref(false)

// ---- Permission tree ----
interface TreeNode {
  id: string
  label: string
  children?: TreeNode[]
}

const allPermissions = ref<PermissionDTO[]>([])
const permissionTreeData = ref<TreeNode[]>([])

// ---- Dialogs ----
const dialogVisible = ref(false)
const dialogTitle = ref('新增角色')
const isEditMode = ref(false)
const editingRoleId = ref<number | null>(null)
const submitting = ref(false)

const formData = reactive({
  name: '',
  code: '',
  description: '',
  permissions: [] as number[],
})

// ---- Permission tree refs ----
const permissionTreeRef = ref<any>(null)
const checkedPermissionIds = ref<number[]>([])

// ---- Validation ----
const formRef = ref<any>(null)
const formRules = {
  name: [
    { required: true, message: '请输入角色名称', trigger: 'blur' },
    { max: 50, message: '角色名称不超过50个字符', trigger: 'blur' },
  ],
  code: [
    { required: true, message: '请输入角色编码', trigger: 'blur' },
    {
      pattern: /^ROLE_[A-Z_]+$/,
      message: '角色编码格式: ROLE_XXX (大写字母和下划线)',
      trigger: 'blur',
    },
  ],
  description: [
    { max: 200, message: '描述不超过200个字符', trigger: 'blur' },
  ],
}

// ======================== API calls ========================

async function fetchRoles() {
  loading.value = true
  try {
    const res = await adminApi.listRoles()
    if (res.data.code === 200 || !res.data.code) {
      const list = res.data.data || res.data || []
      tableData.value = (Array.isArray(list) ? list : list.records || []).map(
        (item: any) => ({
          id: item.id,
          name: item.name,
          code: item.code,
          description: item.description || '',
          userCount: item.userCount ?? item.user_count ?? 0,
          permissions: item.permissions || [],
        })
      )
    }
  } catch {
    // error handled by interceptor
  } finally {
    loading.value = false
  }
}

async function fetchPermissions() {
  try {
    const res = await adminApi.listPermissions()
    if (res.data.code === 200 || !res.data.code) {
      allPermissions.value = res.data.data || res.data || []
      buildPermissionTree()
    }
  } catch {
    // silent
  }
}

function buildPermissionTree() {
  // Group permissions by resource
  const grouped: Record<string, PermissionDTO[]> = {}
  for (const perm of allPermissions.value) {
    const resource = perm.resource || 'other'
    if (!grouped[resource]) grouped[resource] = []
    grouped[resource].push(perm)
  }

  // Build tree nodes with a stable order based on RESOURCE_LABELS keys
  const orderedResources = Object.keys(RESOURCE_LABELS)
  const tree: TreeNode[] = []

  for (const resource of orderedResources) {
    const perms = grouped[resource]
    if (!perms || perms.length === 0) continue
    tree.push({
      id: `resource:${resource}`,
      label: RESOURCE_LABELS[resource] || resource,
      children: perms.map((p) => ({
        id: `perm:${p.id}`,
        label: p.name || p.code,
      })),
    })
  }

  // Append any resources not in our ordered list
  for (const resource of Object.keys(grouped)) {
    if (orderedResources.includes(resource)) continue
    const perms = grouped[resource]
    tree.push({
      id: `resource:${resource}`,
      label: resource,
      children: perms.map((p) => ({
        id: `perm:${p.id}`,
        label: p.name || p.code,
      })),
    })
  }

  permissionTreeData.value = tree
}

// ======================== Handlers ========================

function handleOpenCreate() {
  isEditMode.value = false
  editingRoleId.value = null
  dialogTitle.value = '新增角色'
  formData.name = ''
  formData.code = ''
  formData.description = ''
  formData.permissions = []
  checkedPermissionIds.value = []
  dialogVisible.value = true
  // Reset tree check state
  nextTick(() => {
    permissionTreeRef.value?.setCheckedKeys([])
  })
}

function handleOpenEdit(row: RoleRow) {
  isEditMode.value = true
  editingRoleId.value = row.id
  dialogTitle.value = '编辑角色'
  formData.name = row.name
  formData.code = row.code
  formData.description = row.description
  formData.permissions = [...(row.permissions || [])]
  checkedPermissionIds.value = [...(row.permissions || [])]
  dialogVisible.value = true
  // Set checked keys after dialog renders
  nextTick(() => {
    const keys = (row.permissions || []).map((id) => `perm:${id}`)
    permissionTreeRef.value?.setCheckedKeys(keys)
  })
}

function handleTreeCheck(
  _node: any,
  treeState: { checkedKeys: string[]; halfCheckedKeys: string[] }
) {
  const permIds: number[] = []
  for (const key of treeState.checkedKeys) {
    if (key.startsWith('perm:')) {
      permIds.push(Number(key.replace('perm:', '')))
    }
  }
  for (const key of treeState.halfCheckedKeys) {
    if (key.startsWith('perm:')) {
      permIds.push(Number(key.replace('perm:', '')))
    }
  }
  checkedPermissionIds.value = [...new Set(permIds)]
  formData.permissions = checkedPermissionIds.value
}

async function handleSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  if (checkedPermissionIds.value.length === 0) {
    ElMessage.warning('请至少选择一个权限')
    return
  }

  submitting.value = true
  try {
    const payload: RoleDTO = {
      name: formData.name,
      code: formData.code,
      description: formData.description,
      permissions: checkedPermissionIds.value,
    }

    if (isEditMode.value && editingRoleId.value) {
      await adminApi.updateRole(editingRoleId.value, payload)
      ElMessage.success('角色更新成功')
    } else {
      await adminApi.createRole(payload)
      ElMessage.success('角色创建成功')
    }

    dialogVisible.value = false
    fetchRoles()
  } catch {
    // error handled by interceptor
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row: RoleRow) {
  try {
    await ElMessageBox.confirm(
      `确定要删除角色 "${row.name}" 吗？如果该角色已分配给用户，删除后相关用户将失去对应权限。`,
      '删除确认',
      {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'warning',
      }
    )
    await adminApi.deleteRole(row.id)
    ElMessage.success('删除成功')
    fetchRoles()
  } catch {
    // user cancelled or error
  }
}

function handleDialogClose() {
  formRef.value?.resetFields()
}

// ======================== Helpers ----

function getUserCountType(count: number): 'success' | 'info' | 'warning' | 'danger' {
  if (count === 0) return 'info'
  if (count < 10) return 'success'
  if (count < 50) return 'warning'
  return 'danger'
}

// ======================== Init ========================

onMounted(() => {
  fetchRoles()
  fetchPermissions()
})
</script>

<template>
  <div class="page-container">
    <!-- Page title -->
    <div class="page-title">角色管理</div>

    <!-- Toolbar -->
    <div class="toolbar">
      <div class="toolbar-left">
        <el-button type="primary" :icon="Plus" @click="handleOpenCreate">
          新增角色
        </el-button>
      </div>
      <div class="toolbar-right">
        <span class="total-count">共 {{ tableData.length }} 个角色</span>
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
      empty-text="暂无角色数据"
    >
      <el-table-column
        prop="name"
        label="角色名称"
        min-width="160"
        show-overflow-tooltip
      />
      <el-table-column
        prop="code"
        label="角色编码"
        width="180"
        show-overflow-tooltip
      />
      <el-table-column
        prop="description"
        label="描述"
        min-width="200"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          {{ row.description || '-' }}
        </template>
      </el-table-column>
      <el-table-column
        label="用户数"
        width="100"
        align="center"
      >
        <template #default="{ row }">
          <el-tag :type="getUserCountType(row.userCount)" size="small" round>
            {{ row.userCount }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        label="操作"
        width="180"
        align="center"
        fixed="right"
      >
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            size="small"
            :icon="Edit"
            @click="handleOpenEdit(row)"
          >
            编辑
          </el-button>
          <el-button
            link
            type="danger"
            size="small"
            :icon="Delete"
            @click="handleDelete(row)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- Create / Edit dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="640px"
      :close-on-click-modal="false"
      @close="handleDialogClose"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="100px"
      >
        <el-form-item label="角色名称" prop="name">
          <el-input
            v-model="formData.name"
            placeholder="请输入角色名称"
            maxlength="50"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="角色编码" prop="code">
          <el-input
            v-model="formData.code"
            placeholder="例如 ROLE_DATA_MANAGER"
            :disabled="isEditMode"
            maxlength="50"
          />
          <div class="form-item-tip">
            编码格式: ROLE_ 开头，大写字母与下划线，创建后不可修改
          </div>
        </el-form-item>

        <el-form-item label="描述" prop="description">
          <el-input
            v-model="formData.description"
            type="textarea"
            :rows="3"
            placeholder="请输入角色描述（可选）"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="权限分配" required>
          <div class="permission-tree-wrapper">
            <el-tree
              ref="permissionTreeRef"
              :data="permissionTreeData"
              show-checkbox
              node-key="id"
              default-expand-all
              check-strictly
              @check="handleTreeCheck"
            >
              <template #default="{ data }">
                <span class="tree-node-label">
                  {{ data.label }}
                </span>
              </template>
            </el-tree>
          </div>
          <div class="form-item-tip">
            勾选父节点会同时选中其下所有权限；取消父节点会取消其下所有权限
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="submitting"
          @click="handleSubmit"
        >
          {{ isEditMode ? '保存修改' : '创建角色' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts">
export default {
  name: 'RoleManagementView',
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

// ---- Form ----
.form-item-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
  line-height: 1.5;
}

// ---- Permission tree ----
.permission-tree-wrapper {
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  padding: 12px 16px;
  max-height: 360px;
  overflow-y: auto;
  background: #fafafa;

  .tree-node-label {
    font-size: 14px;
    color: #303133;
  }
}
</style>
