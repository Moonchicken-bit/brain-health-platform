<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http, { adminHttp } from '@/api/client'

const visits = ['V0', 'V1', 'SF1', 'SF2', 'SF3', 'SF4', 'SF5']
const selectedVisit = ref('V0')
const rows = ref<any[]>([])
const dialogVisible = ref(false)
const baseScales = ref<any[]>([])
const selectedScale = ref('')
const baseItems = ref<any[]>([])
const itemKeyword = ref('')
const editingId = ref<number>()
const form = reactive({
  visitCode: 'V0',
  fieldCode: '',
  label: '',
  fieldType: 'TEXT',
  unit: '',
  optionsText: '',
  required: false,
  sortOrder: 0,
})

const filteredBaseItems = computed(() => {
  const keyword = itemKeyword.value.trim().toLowerCase()
  return baseItems.value
    .filter(item => !keyword || `${item.code} ${item.name}`.toLowerCase().includes(keyword))
    .slice(0, 300)
})

async function load() {
  const response = await adminHttp.get('/api/v1/admin/visit-fields', {
    params: { visitCode: selectedVisit.value },
  })
  rows.value = response.data.data || []
  await loadBaseScales()
}

async function loadBaseScales() {
  const response = await http.get(`/api/v1/scales/visit-form/${selectedVisit.value}`)
  baseScales.value = (response.data.data?.scales || []).filter((scale: any) => scale.code !== 'CUSTOM')
  if (!baseScales.value.some(scale => scale.code === selectedScale.value)) {
    selectedScale.value = baseScales.value[0]?.code || ''
  }
  await loadBaseItems()
}

async function loadBaseItems() {
  if (!selectedScale.value) {
    baseItems.value = []
    return
  }
  const [itemsResponse, overrideResponse] = await Promise.all([
    http.get(`/api/v1/scales/visit-form/${selectedVisit.value}/scale/${selectedScale.value}`),
    http.get('/api/v1/scales/admin/item-overrides', {
      params: { visitCode: selectedVisit.value },
    }),
  ])
  const overrides = new Map<string, any>(
    (overrideResponse.data.data || [])
      .filter((item: any) => item.scaleCode === selectedScale.value)
      .map((item: any) => [item.itemCode, item]),
  )
  const current = (itemsResponse.data.data?.items || []).map((item: any) => ({
    ...item,
    status: overrides.get(item.code)?.status || 'PUBLISHED',
  }))
  for (const override of overrides.values() as any) {
    if (!current.some((item: any) => item.code === override.itemCode)) {
      current.push({
        code: override.itemCode,
        name: override.labelOverride || override.itemCode,
        required: Boolean(override.requiredOverride),
        status: override.status,
      })
    }
  }
  baseItems.value = current
}

async function setBaseItemStatus(item: any, statusValue: string) {
  await http.put('/api/v1/scales/admin/item-overrides', {
    visitCode: selectedVisit.value,
    scaleCode: selectedScale.value,
    itemCode: item.code,
    label: item.name,
    required: item.required,
    status: statusValue,
  })
  item.status = statusValue
  ElMessage.success(statusValue === 'DISABLED' ? '题目已停用，历史数据仍保留' : '题目已恢复')
}

function create() {
  editingId.value = undefined
  Object.assign(form, {
    visitCode: selectedVisit.value,
    fieldCode: '',
    label: '',
    fieldType: 'TEXT',
    unit: '',
    optionsText: '',
    required: false,
    sortOrder: rows.value.length,
  })
  dialogVisible.value = true
}

function edit(row: any) {
  editingId.value = row.id
  Object.assign(form, {
    ...row,
    required: Boolean(row.requiredFlag),
    optionsText: Array.isArray(row.options) ? row.options.join('\n') : '',
  })
  dialogVisible.value = true
}

async function save() {
  if (!form.fieldCode || !form.label) {
    ElMessage.warning('请填写字段代码和显示名称')
    return
  }
  const payload = {
    ...form,
    options: form.optionsText.split(/[,，\n]/).map(value => value.trim()).filter(Boolean),
  }
  if (editingId.value) {
    await adminHttp.put(`/api/v1/admin/visit-fields/${editingId.value}`, payload)
  } else {
    await adminHttp.post('/api/v1/admin/visit-fields', payload)
  }
  dialogVisible.value = false
  ElMessage.success('访视项目已保存')
  await load()
}

async function setCustomFieldStatus(row: any, value: string) {
  await adminHttp.put(`/api/v1/admin/visit-fields/${row.id}/status`, { status: value })
  ElMessage.success(value === 'PUBLISHED' ? '已发布，医生和患者表单立即生效' : '已停用')
  await load()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="header">
      <div>
        <h2>访视表单配置</h2>
        <p>管理员可新增字段，也可停用或恢复现有题目；已产生的历史数据不会删除。</p>
      </div>
      <div>
        <el-select v-model="selectedVisit" style="width: 130px; margin-right: 12px" @change="load">
          <el-option v-for="visit in visits" :key="visit" :label="visit" :value="visit" />
        </el-select>
        <el-button type="primary" @click="create">＋ 新增项目</el-button>
      </div>
    </div>

    <el-card>
      <template #header><strong>管理员新增项目</strong></template>
      <el-table :data="rows">
        <el-table-column prop="fieldCode" label="字段代码" />
        <el-table-column prop="label" label="显示名称" />
        <el-table-column prop="fieldType" label="类型" width="120" />
        <el-table-column prop="requiredFlag" label="必填" width="80">
          <template #default="{ row }">{{ row.requiredFlag ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="110" />
        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <el-button link type="primary" @click="edit(row)">编辑</el-button>
            <el-button
              v-if="row.status !== 'PUBLISHED'"
              link
              type="success"
              @click="setCustomFieldStatus(row, 'PUBLISHED')"
            >发布</el-button>
            <el-button
              v-else
              link
              type="warning"
              @click="setCustomFieldStatus(row, 'DISABLED')"
            >停用</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!rows.length" description="当前访视还没有管理员新增项目" />
    </el-card>

    <el-card style="margin-top: 18px">
      <template #header>
        <div>
          <strong>现有题目启用/停用</strong>
          <span class="hint">停用后医生和患者不再看到该题，历史答案仍保留</span>
        </div>
      </template>
      <div class="toolbar">
        <el-select v-model="selectedScale" style="width: 240px" @change="loadBaseItems">
          <el-option
            v-for="scale in baseScales"
            :key="scale.code"
            :label="scale.name || scale.code"
            :value="scale.code"
          />
        </el-select>
        <el-input v-model="itemKeyword" placeholder="搜索题目代码或名称" clearable style="width: 320px" />
      </div>
      <el-table :data="filteredBaseItems" max-height="520">
        <el-table-column prop="code" label="题目代码" width="260" show-overflow-tooltip />
        <el-table-column prop="name" label="题目名称" min-width="360" show-overflow-tooltip />
        <el-table-column label="必填" width="80">
          <template #default="{ row }">{{ row.required ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="110" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button
              v-if="row.status !== 'DISABLED'"
              link
              type="warning"
              @click="setBaseItemStatus(row, 'DISABLED')"
            >停用</el-button>
            <el-button
              v-else
              link
              type="success"
              @click="setBaseItemStatus(row, 'PUBLISHED')"
            >恢复</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑项目' : '新增访视项目'" width="600px">
      <el-form label-width="100px">
        <el-form-item label="访视"><el-input v-model="form.visitCode" disabled /></el-form-item>
        <el-form-item label="字段代码" required>
          <el-input v-model="form.fieldCode" :disabled="Boolean(editingId)" />
        </el-form-item>
        <el-form-item label="显示名称" required><el-input v-model="form.label" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.fieldType">
            <el-option label="文本" value="TEXT" />
            <el-option label="长文本" value="TEXTAREA" />
            <el-option label="数字" value="NUMBER" />
            <el-option label="日期" value="DATE" />
            <el-option label="单选" value="SELECT" />
            <el-option label="多选" value="CHECKBOX" />
            <el-option label="是/否" value="SWITCH" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="['SELECT', 'CHECKBOX'].includes(form.fieldType)" label="选项">
          <el-input v-model="form.optionsText" type="textarea" placeholder="用逗号或换行分隔" />
        </el-form-item>
        <el-form-item label="单位"><el-input v-model="form.unit" /></el-form-item>
        <el-form-item label="必填"><el-switch v-model="form.required" /></el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" />
        </el-form-item>
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
.hint { color: #909399; margin-left: 12px; font-weight: normal; }
.toolbar { display: flex; gap: 12px; margin-bottom: 14px; }
</style>
