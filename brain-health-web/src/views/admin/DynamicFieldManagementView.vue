<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { adminHttp } from '@/api/client'

interface FieldRow {
  id: number
  fieldCode: string
  label: string
  description?: string
  fieldType: string
  unit?: string
  requiredFlag: boolean
  sortOrder: number
  status: 'DRAFT' | 'PUBLISHED' | 'DISABLED'
}

const rows = ref<FieldRow[]>([])
const selectedModule = ref('GENETICS')
const moduleOptions = [
  { code: 'GENETICS', name: '遗传样本', formCode: 'GENETICS_SAMPLE' },
  { code: 'IMAGING', name: '影像检查', formCode: 'IMAGING_SESSION' },
  { code: 'LAB', name: '实验室访视', formCode: 'LAB_SESSION' },
]
const loading = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number>()
const form = reactive({
  module: 'GENETICS',
  formCode: 'GENETICS_SAMPLE',
  fieldCode: '',
  label: '',
  description: '',
  fieldType: 'TEXT',
  unit: '',
  required: false,
  sortOrder: 0,
  optionsText: '',
})

async function load() {
  loading.value = true
  try {
    const response = await adminHttp.get('/api/v1/admin/dynamic-fields', {
      params: { module: selectedModule.value },
    })
    rows.value = response.data.data || []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  const current = moduleOptions.find(option => option.code === selectedModule.value)!
  editingId.value = undefined
  Object.assign(form, {
    module: current.code, formCode: current.formCode, fieldCode: '', label: '',
    description: '', fieldType: 'TEXT', unit: '', required: false, sortOrder: rows.value.length,
    optionsText: '',
  })
  dialogVisible.value = true
}

function openEdit(row: FieldRow) {
  const current = moduleOptions.find(option => option.code === selectedModule.value)!
  editingId.value = row.id
  Object.assign(form, {
    module: current.code, formCode: current.formCode, fieldCode: row.fieldCode,
    label: row.label, description: row.description || '', fieldType: row.fieldType,
    unit: row.unit || '', required: Boolean(row.requiredFlag), sortOrder: row.sortOrder,
    optionsText: '',
  })
  dialogVisible.value = true
}

async function save() {
  if (!form.fieldCode.trim() || !form.label.trim()) {
    ElMessage.warning('字段代码和中文名称不能为空')
    return
  }
  const payload = {
    ...form,
    options: form.optionsText.split(/[,，\n]/).map(v => v.trim()).filter(Boolean),
  }
  if (editingId.value) {
    await adminHttp.put(`/api/v1/admin/dynamic-fields/${editingId.value}`, payload)
  } else {
    await adminHttp.post('/api/v1/admin/dynamic-fields', payload)
  }
  dialogVisible.value = false
  ElMessage.success('字段已保存')
  await load()
}

async function setStatus(row: FieldRow, status: FieldRow['status']) {
  await adminHttp.put(`/api/v1/admin/dynamic-fields/${row.id}/status`, { status })
  ElMessage.success(status === 'PUBLISHED' ? '字段已发布，医生端立即生效' : '字段状态已更新')
  await load()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="header">
      <div>
        <h2>专业数据字段配置</h2>
        <p>管理员可为遗传、影像和实验室模块增加录入字段；发布后生效，历史值不会被覆盖。</p>
      </div>
      <div>
        <el-select v-model="selectedModule" style="width: 170px; margin-right: 12px" @change="load">
          <el-option v-for="option in moduleOptions" :key="option.code" :label="option.name" :value="option.code" />
        </el-select>
        <el-button type="primary" @click="openCreate">＋ 新增字段</el-button>
      </div>
    </div>
    <el-card>
      <el-table v-loading="loading" :data="rows">
        <el-table-column prop="fieldCode" label="字段代码" width="170" />
        <el-table-column prop="label" label="中文名称" min-width="180" />
        <el-table-column prop="fieldType" label="类型" width="120" />
        <el-table-column prop="unit" label="单位" width="100" />
        <el-table-column label="必填" width="80">
          <template #default="{ row }">{{ row.requiredFlag ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="110" />
        <el-table-column label="操作" width="230">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="row.status !== 'PUBLISHED'" link type="success" @click="setStatus(row, 'PUBLISHED')">发布</el-button>
            <el-button v-else link type="warning" @click="setStatus(row, 'DISABLED')">停用</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !rows.length" description="还没有自定义字段，请点击右上角新增" />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑扩展字段' : '新增扩展字段'" width="620px">
      <el-form label-width="110px">
        <el-form-item label="字段代码" required>
          <el-input v-model="form.fieldCode" :disabled="Boolean(editingId)" placeholder="例如 sequencing_batch" />
        </el-form-item>
        <el-form-item label="中文名称" required><el-input v-model="form.label" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="form.description" type="textarea" /></el-form-item>
        <el-form-item label="字段类型">
          <el-select v-model="form.fieldType">
            <el-option label="单行文本" value="TEXT" />
            <el-option label="长文本" value="TEXTAREA" />
            <el-option label="数字" value="NUMBER" />
            <el-option label="日期" value="DATE" />
            <el-option label="单选" value="SELECT" />
            <el-option label="多选" value="MULTI_SELECT" />
            <el-option label="是/否" value="BOOLEAN" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="['SELECT','MULTI_SELECT'].includes(form.fieldType)" label="选项">
          <el-input v-model="form.optionsText" type="textarea" placeholder="用逗号或换行分隔" />
        </el-form-item>
        <el-form-item label="单位"><el-input v-model="form.unit" /></el-form-item>
        <el-form-item label="必填"><el-switch v-model="form.required" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sortOrder" :min="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page { padding: 24px; }
.header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 18px; }
.header h2 { margin: 0 0 8px; }
.header p { margin: 0; color: #909399; }
</style>
