<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http, { adminHttp } from '@/api/client'

const rows = ref<any[]>([])
const projects = ref<any[]>([])
const scaleOptions = ref<any[]>([])
const dialogVisible = ref(false)
const editingId = ref<number>()
const form = reactive({
  code: '',
  name: '',
  description: '',
  projectId: undefined as number | undefined,
  visitCode: 'V0',
  visitName: '基线访视',
  allowUnifiedUpload: true,
  requiredModules: ['IMAGING', 'GENETICS', 'LAB'] as string[],
  patientDeadlineDays: 14 as number | undefined,
  scaleCodes: [] as string[],
})

async function load() {
  const [templates, projectResponse] = await Promise.all([
    adminHttp.get('/api/v1/admin/visit-templates'),
    adminHttp.get('/api/v1/admin/projects', { params: { page: 1, size: 500 } }),
  ])
  rows.value = templates.data.data || []
  projects.value = projectResponse.data.data?.records || []
}

async function loadScales() {
  const response = await http.get(`/api/v1/scales/visit-form/${form.visitCode}`)
  scaleOptions.value = response.data.data?.scales || []
}

function create() {
  editingId.value = undefined
  Object.assign(form, {
    code: '',
    name: '',
    description: '',
    projectId: undefined,
    visitCode: 'V0',
    visitName: '基线访视',
    allowUnifiedUpload: true,
    requiredModules: ['IMAGING', 'GENETICS', 'LAB'],
    patientDeadlineDays: 14,
    scaleCodes: [],
  })
  dialogVisible.value = true
  loadScales()
}

function edit(row: any) {
  editingId.value = row.id
  Object.assign(form, {
    code: row.code,
    name: row.name,
    description: row.description || '',
    projectId: row.projectId,
    visitCode: row.visitCode || 'V0',
    visitName: row.visitName || row.visitCode || '',
    allowUnifiedUpload: Boolean(row.allowUnifiedUpload),
    requiredModules: row.requiredModules || [],
    patientDeadlineDays: row.patientDeadlineDays,
    scaleCodes: row.scaleCodes || [],
  })
  dialogVisible.value = true
  loadScales()
}

async function save() {
  if (!form.code || !form.name || !form.visitCode) {
    ElMessage.warning('请填写模板代码、名称和访视代码')
    return
  }
  if (editingId.value) {
    await adminHttp.put(`/api/v1/admin/visit-templates/${editingId.value}`, form)
    ElMessage.success('已创建新的草稿版本，发布后用于新访视')
  } else {
    await adminHttp.post('/api/v1/admin/visit-templates', form)
    ElMessage.success('访视模板已创建')
  }
  dialogVisible.value = false
  await load()
}

async function publish(row: any) {
  await adminHttp.put(`/api/v1/admin/visit-templates/${row.id}/publish`, {
    versionId: row.versionId,
  })
  ElMessage.success('模板版本已发布；已有访视仍使用原快照')
  await load()
}

async function toggle(row: any) {
  await adminHttp.put(`/api/v1/admin/visit-templates/${row.id}/status`, {
    isActive: row.status !== 'ACTIVE',
  })
  await load()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="header">
      <div>
        <h2>访视模板管理</h2>
        <p>配置新建访视需要的患者量表、综合资料类别和截止时间；每次修改生成新版本。</p>
      </div>
      <el-button type="primary" @click="create">＋ 新建模板</el-button>
    </div>

    <el-card>
      <el-table :data="rows">
        <el-table-column prop="code" label="模板代码" width="150" />
        <el-table-column prop="name" label="模板名称" min-width="180" />
        <el-table-column prop="visitCode" label="访视" width="100" />
        <el-table-column prop="versionNo" label="版本" width="80" />
        <el-table-column label="适用项目" min-width="180">
          <template #default="{ row }">
            {{ projects.find(project => project.id === row.projectId)?.name || '全平台' }}
          </template>
        </el-table-column>
        <el-table-column label="患者量表" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">{{ row.scaleCodes?.join('、') || '使用访视默认表单' }}</template>
        </el-table-column>
        <el-table-column prop="versionStatus" label="版本状态" width="120" />
        <el-table-column prop="status" label="模板状态" width="110" />
        <el-table-column label="操作" width="230">
          <template #default="{ row }">
            <el-button link type="primary" @click="edit(row)">新建版本</el-button>
            <el-button
              v-if="row.versionStatus !== 'PUBLISHED'"
              link
              type="success"
              @click="publish(row)"
            >发布</el-button>
            <el-button link type="warning" @click="toggle(row)">
              {{ row.status === 'ACTIVE' ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!rows.length" description="暂无访视模板" />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? '新建模板版本' : '新建访视模板'" width="720px">
      <el-form label-width="120px">
        <el-form-item label="模板代码" required>
          <el-input v-model="form.code" :disabled="Boolean(editingId)" placeholder="例如 V0_STANDARD" />
        </el-form-item>
        <el-form-item label="模板名称" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="适用项目">
          <el-select v-model="form.projectId" clearable filterable placeholder="留空表示全平台" style="width: 100%">
            <el-option v-for="project in projects" :key="project.id" :label="project.name" :value="project.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="访视代码" required>
          <el-select v-model="form.visitCode" style="width: 100%" @change="loadScales">
            <el-option v-for="code in ['V0','V1','SF1','SF2','SF3','SF4','SF5']" :key="code" :label="code" :value="code" />
          </el-select>
        </el-form-item>
        <el-form-item label="访视名称"><el-input v-model="form.visitName" /></el-form-item>
        <el-form-item label="患者量表">
          <el-select v-model="form.scaleCodes" multiple filterable clearable style="width: 100%" placeholder="留空使用默认表单">
            <el-option v-for="scale in scaleOptions" :key="scale.code" :label="scale.name || scale.code" :value="scale.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="必需资料类别">
          <el-checkbox-group v-model="form.requiredModules">
            <el-checkbox label="IMAGING">影像</el-checkbox>
            <el-checkbox label="GENETICS">遗传</el-checkbox>
            <el-checkbox label="LAB">实验室</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="允许综合上传"><el-switch v-model="form.allowUnifiedUpload" /></el-form-item>
        <el-form-item label="患者截止天数">
          <el-input-number v-model="form.patientDeadlineDays" :min="1" :max="365" />
        </el-form-item>
        <el-form-item label="说明"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存草稿</el-button>
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
