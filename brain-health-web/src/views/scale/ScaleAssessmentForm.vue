<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  scaleApi,
  type ScaleAssessment,
  type ScaleInstrument,
  type ScaleInstrumentItem,
  type ScaleItemResponse,
} from '@/api/modules/scale'
import { subjectApi } from '@/api/modules/subject'

interface DynamicItem {
  index: number
  category: string
  questionText: string
  inputType: 'radio' | 'number' | 'select' | 'text'
  options: Array<{ value: string; label: string; score: number }>
  maxScore: number
  response: string | number | null
  score: number
}

const route = useRoute()
const router = useRouter()
const instrumentId = Number(route.params.id)
const instrument = ref<ScaleInstrument | null>(null)
const items = ref<DynamicItem[]>([])
const subjects = ref<Array<{ value: number; label: string }>>([])
const sessions = ref<Array<{ value: number; label: string }>>([])
const loading = ref(true)
const saving = ref(false)
const assessmentId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const formData = reactive({
  subjectId: null as number | null,
  sessionId: null as number | null,
  assessmentDate: new Date().toISOString().slice(0, 10),
  administrationMode: 'InPerson',
  notes: '',
})
const rules: FormRules = {
  subjectId: [{ required: true, message: '请选择受试者', trigger: 'change' }],
  sessionId: [{ required: true, message: '请选择访视', trigger: 'change' }],
  assessmentDate: [{ required: true, message: '请选择评估日期', trigger: 'change' }],
}

const groupedItems = computed(() => {
  const groups = new Map<string, DynamicItem[]>()
  for (const item of items.value) {
    const group = groups.get(item.category) || []
    group.push(item)
    groups.set(item.category, group)
  }
  return [...groups.entries()]
})
const totalScore = computed(() => items.value.reduce((sum, item) => sum + item.score, 0))
const maxTotalScore = computed(() => items.value.reduce((sum, item) => sum + item.maxScore, 0))

function parseOptions(raw?: string): DynamicItem['options'] {
  if (!raw?.trim()) return []
  try {
    const parsed = JSON.parse(raw)
    if (Array.isArray(parsed)) {
      return parsed.map((option, index) => {
        if (typeof option === 'object') {
          const value = String(option.value ?? option.code ?? option.score ?? index)
          return { value, label: String(option.label ?? option.text ?? value), score: Number(option.score ?? value) || 0 }
        }
        const value = String(option)
        return { value, label: value, score: Number(value.split(/\s*[-:：]\s*/)[0]) || index }
      })
    }
  } catch { /* 兼容历史分隔符格式 */ }
  return raw.split(/[|,，;]/).map((text, index) => {
    const label = text.trim()
    return { value: label, label, score: Number(label.split(/\s*[-:：]\s*/)[0]) || index }
  }).filter(option => option.label)
}

function toDynamicItem(item: ScaleInstrumentItem): DynamicItem {
  const input = (item.inputType || '').toLowerCase()
  const options = parseOptions(item.options)
  return {
    index: item.itemIndex,
    category: item.domainName || '量表项目',
    questionText: item.questionText,
    inputType: options.length && (input.includes('select') || input.includes('choice'))
      ? 'select' : options.length ? 'radio'
      : input.includes('number') || input.includes('score') ? 'number' : 'text',
    options,
    maxScore: item.maxScore ?? 0,
    response: null,
    score: 0,
  }
}

async function loadInstrument() {
  loading.value = true
  try {
    const response = await scaleApi.getInstrument(instrumentId)
    const detail = response.data.data
    instrument.value = detail.instrument
    items.value = (detail.items || []).map(toDynamicItem)
    if (!items.value.length) ElMessage.warning('该量表尚未配置题目，请联系管理员补充')
  } catch {
    ElMessage.error('加载量表定义失败')
  } finally {
    loading.value = false
  }
}

async function searchSubjects(keyword: string) {
  if (!keyword.trim()) return
  const response = await subjectApi.list({ keyword, size: 20 })
  subjects.value = (response.data?.data?.records || []).map((subject: any) => ({
    value: subject.id,
    label: `${subject.subjectId}${subject.lastName || subject.firstName ? `（${subject.lastName || ''}${subject.firstName || ''}）` : ''}`,
  }))
}

async function loadSessions(subjectId: number) {
  formData.sessionId = null
  const response = await subjectApi.getSessions(subjectId)
  sessions.value = (response.data?.data || []).map((session: any) => ({
    value: session.id,
    label: `${session.visitLabel || session.visitCode || '访视'} · ${session.sessionDate || '日期待定'}`,
  }))
}

function updateResponse(item: DynamicItem, value: string | number | null) {
  item.response = value
  const option = item.options.find(entry => entry.value === String(value))
  const numeric = option ? option.score : Number(value)
  item.score = Math.max(0, Math.min(Number.isFinite(numeric) ? numeric : 0, item.maxScore))
}

async function ensureAssessment() {
  if (assessmentId.value) return assessmentId.value
  const payload: ScaleAssessment = {
    sessionId: formData.sessionId!,
    subjectId: formData.subjectId!,
    instrumentId,
    assessmentDate: formData.assessmentDate,
    administrationMode: formData.administrationMode,
    dataEntryStatus: 'Incomplete',
    notes: formData.notes,
  }
  const response = await scaleApi.createAssessment(payload)
  assessmentId.value = response.data.data.id
  return assessmentId.value!
}

async function saveAssessment(complete: boolean) {
  if (!formRef.value || !(await formRef.value.validate().catch(() => false))) return
  saving.value = true
  try {
    const id = await ensureAssessment()
    const responses: ScaleItemResponse[] = items.value.map(item => ({
      itemIndex: item.index,
      questionText: item.questionText,
      response: item.response ?? '',
      score: item.score,
      maxScore: item.maxScore,
      category: item.category,
    }))
    await scaleApi.saveItemResponses(id, responses)
    if (complete) {
      await scaleApi.submitAssessment(id, items.value.map(item => ({ itemIndex: item.index, score: item.score })))
      ElMessage.success('评估已提交')
      router.push({ name: 'ScaleAssessmentDetail', params: { id } })
    } else {
      ElMessage.success('草稿已保存')
    }
  } catch {
    ElMessage.error('保存失败，请检查网络或必填内容')
  } finally {
    saving.value = false
  }
}

onMounted(loadInstrument)
</script>

<template>
  <div class="assessment-page" v-loading="loading">
    <header class="assessment-header">
      <div>
        <el-button link @click="router.back()">返回</el-button>
        <h1>{{ instrument?.fullName || instrument?.name || '量表评估' }}</h1>
        <p>题目来自管理员维护的正式量表定义，不使用临时模拟内容。</p>
      </div>
      <div class="header-actions">
        <el-button :loading="saving" @click="saveAssessment(false)">保存草稿</el-button>
        <el-button type="primary" :loading="saving" :disabled="!items.length" @click="saveAssessment(true)">核对并提交</el-button>
      </div>
    </header>

    <el-card shadow="never" class="metadata-card">
      <el-form ref="formRef" :model="formData" :rules="rules" label-position="top">
        <div class="metadata-grid">
          <el-form-item label="受试者" prop="subjectId">
            <el-select v-model="formData.subjectId" filterable remote :remote-method="searchSubjects"
              placeholder="输入受试者编号或姓名" @change="loadSessions">
              <el-option v-for="subject in subjects" :key="subject.value" :label="subject.label" :value="subject.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="访视" prop="sessionId">
            <el-select v-model="formData.sessionId" placeholder="选择访视">
              <el-option v-for="session in sessions" :key="session.value" :label="session.label" :value="session.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="评估日期" prop="assessmentDate">
            <el-date-picker v-model="formData.assessmentDate" value-format="YYYY-MM-DD" type="date" />
          </el-form-item>
          <el-form-item label="评估方式">
            <el-select v-model="formData.administrationMode">
              <el-option label="面对面" value="InPerson" />
              <el-option label="电话" value="Telephone" />
              <el-option label="自评" value="SelfReport" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="备注"><el-input v-model="formData.notes" type="textarea" :rows="2" /></el-form-item>
      </el-form>
    </el-card>

    <el-empty v-if="!loading && !items.length" description="暂无正式题目，不能提交空白评估" />
    <el-card v-for="[domain, domainItems] in groupedItems" :key="domain" shadow="never" class="domain-card">
      <template #header><strong>{{ domain }}</strong></template>
      <div class="question-grid">
        <div v-for="item in domainItems" :key="item.index" class="question">
          <label><span>{{ item.index }}</span>{{ item.questionText }}</label>
          <el-radio-group v-if="item.inputType === 'radio'" :model-value="item.response"
            @change="(value: any) => updateResponse(item, value as string)">
            <el-radio v-for="option in item.options" :key="option.value" :value="option.value">{{ option.label }}</el-radio>
          </el-radio-group>
          <el-select v-else-if="item.inputType === 'select'" :model-value="item.response"
            @change="(value: any) => updateResponse(item, value)">
            <el-option v-for="option in item.options" :key="option.value" :value="option.value" :label="option.label" />
          </el-select>
          <el-input-number v-else-if="item.inputType === 'number'" :model-value="item.response as number"
            :min="0" :max="item.maxScore" @change="(value: any) => updateResponse(item, value)" />
          <el-input v-else :model-value="item.response as string" placeholder="请输入"
            @input="(value: any) => updateResponse(item, value)" />
        </div>
      </div>
    </el-card>

    <div class="score-bar">
      <span>当前总分</span><strong>{{ totalScore }}</strong><span>/ {{ maxTotalScore }}</span>
    </div>
  </div>
</template>

<style scoped>
.assessment-page { max-width: 1240px; margin: 0 auto; padding: 24px; color: #243447; }
.assessment-header { display: flex; justify-content: space-between; gap: 24px; margin-bottom: 20px; }
.assessment-header h1 { display: inline; margin-left: 12px; font-size: 24px; }
.assessment-header p { margin: 8px 0 0 64px; color: #526476; }
.header-actions { display: flex; align-items: flex-start; gap: 8px; }
.metadata-card, .domain-card { margin-bottom: 16px; }
.metadata-grid { display: grid; grid-template-columns: repeat(4, minmax(180px, 1fr)); gap: 16px; }
.metadata-grid :deep(.el-select), .metadata-grid :deep(.el-date-editor) { width: 100%; }
.question-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 18px 28px; }
.question { padding: 14px; border: 1px solid #d9e1e8; border-radius: 8px; background: #fbfcfd; }
.question label { display: flex; gap: 8px; min-height: 42px; margin-bottom: 12px; line-height: 1.55; font-weight: 600; }
.question label span { color: #2563a8; }
.score-bar { position: sticky; bottom: 16px; width: fit-content; margin: 24px auto 0; padding: 12px 24px;
  border-radius: 28px; color: white; background: #183a5a; box-shadow: 0 8px 24px #183a5a33; }
.score-bar strong { margin: 0 8px 0 14px; font-size: 28px; }
@media (max-width: 900px) {
  .metadata-grid, .question-grid { grid-template-columns: 1fr; }
  .assessment-header { flex-direction: column; }
}
</style>
