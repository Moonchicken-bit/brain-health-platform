<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowLeft,
  Download,
  Document,
  Printer,
  Clock,
} from '@element-plus/icons-vue'
import { scaleApi, type ScaleAssessment, type ScaleItemResponse } from '@/api/modules/scale'

// ---- Router ----
const route = useRoute()
const router = useRouter()

const assessmentId = computed(() => Number(route.params.id))

// ---- Core state ----
const loading = ref(false)
const itemsLoading = ref(false)
const historyLoading = ref(false)
const exporting = ref(false)

const assessment = ref<(ScaleAssessment & Record<string, any>) | null>(null)
const instrument = ref<Record<string, any> | null>(null)
const subject = ref<Record<string, any> | null>(null)
const session = ref<Record<string, any> | null>(null)
const examiner = ref<Record<string, any> | null>(null)

const items = ref<ScaleItemResponse[]>([])
const history = ref<any[]>([])

// ---- Tab state ----
const activeTab = ref('responses')

// ---- Administration mode labels ----
const adminModeLabels: Record<string, string> = {
  SELF_REPORT: '自评',
  CLINICIAN_ADMINISTERED: '他评',
  INTERVIEW: '访谈',
  COMPUTER_ADAPTIVE: '计算机自适应',
  PAPER_PENCIL: '纸笔',
}

function adminModeLabel(mode: string): string {
  return adminModeLabels[mode] || mode || '-'
}

// ---- Status helpers ----
type TagType = '' | 'success' | 'warning' | 'danger' | 'info'

function statusLabel(status: string): string {
  const map: Record<string, string> = {
    DRAFT: '草稿',
    IN_PROGRESS: '评估中',
    SUBMITTED: '已提交',
    COMPLETED: '已完成',
    SCORED: '已评分',
    VERIFIED: '已审核',
  }
  return map[status] || status || '-'
}

function statusType(status: string): TagType {
  const map: Record<string, TagType> = {
    DRAFT: 'info',
    IN_PROGRESS: 'warning',
    SUBMITTED: 'warning',
    COMPLETED: 'success',
    SCORED: 'success',
    VERIFIED: '',
  }
  return map[status] || 'info'
}

// ---- Interpretation ----
interface Interpretation {
  level: string
  label: string
  color: string
  tagType: TagType
}

function interpretation(totalScore: number | null | undefined, cutoff: number | null | undefined, rangeMin: number, rangeMax: number): Interpretation {
  if (totalScore == null || cutoff == null) {
    return { level: 'unknown', label: '暂无解释', color: '#909399', tagType: 'info' }
  }

  // Calculate thresholds relative to the scoring range
  // borderline: within 80%-110% of cutoff
  const borderlineLower = cutoff * 0.8
  const borderlineUpper = cutoff * 1.1

  if (totalScore >= borderlineUpper) {
    return { level: 'impaired', label: '异常', color: '#f56c6c', tagType: 'danger' }
  }
  if (totalScore >= borderlineLower) {
    return { level: 'borderline', label: '边缘', color: '#e6a23c', tagType: 'warning' }
  }
  return { level: 'normal', label: '正常', color: '#67c23a', tagType: 'success' }
}

const resultInterpretation = computed<Interpretation>(() => {
  const score = assessment.value?.totalScore
  const cutoff = instrument.value?.cutoffScore ?? null
  const rangeMin = instrument.value?.scoringRangeMin ?? 0
  const rangeMax = instrument.value?.scoringRangeMax ?? 100
  return interpretation(score, cutoff, rangeMin, rangeMax)
})

// ---- Domain scores computed from items ----
interface DomainScore {
  category: string
  score: number
  maxScore: number
  percentage: number
  itemCount: number
}

const domainScores = computed<DomainScore[]>(() => {
  if (!items.value.length) return []

  const domainMap = new Map<string, { score: number; maxScore: number; count: number }>()

  for (const item of items.value) {
    const cat = item.category || '未分类'
    const existing = domainMap.get(cat)
    if (existing) {
      existing.score += item.score
      existing.maxScore += item.maxScore
      existing.count += 1
    } else {
      domainMap.set(cat, { score: item.score, maxScore: item.maxScore, count: 1 })
    }
  }

  const result: DomainScore[] = []
  for (const [category, data] of domainMap) {
    result.push({
      category,
      score: data.score,
      maxScore: data.maxScore,
      percentage: data.maxScore > 0 ? Math.round((data.score / data.maxScore) * 100) : 0,
      itemCount: data.count,
    })
  }

  return result
})

// ---- Score progress percentage (for total score bar) ----
const scorePercentage = computed(() => {
  const score = assessment.value?.totalScore
  const min = instrument.value?.scoringRangeMin ?? 0
  const max = instrument.value?.scoringRangeMax ?? 100
  if (score == null || max <= min) return 0
  return Math.min(100, Math.max(0, Math.round(((score - min) / (max - min)) * 100)))
})

// ---- Formatting helpers ----
function formatDate(dateStr?: string): string {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

function formatDateTime(dateStr?: string): string {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function formatScore(val: number | null | undefined): string {
  if (val == null) return '-'
  return Number.isInteger(val) ? String(val) : val.toFixed(1)
}

function domainColor(percentage: number): string {
  if (percentage >= 80) return '#f56c6c'
  if (percentage >= 60) return '#e6a23c'
  return '#67c23a'
}

// ---- Data fetching ----
async function fetchAssessment() {
  loading.value = true
  try {
    const res = await scaleApi.getAssessment(assessmentId.value)
    const data = res.data.data

    assessment.value = data
    instrument.value = data.instrument ?? null
    subject.value = data.subject ?? null
    session.value = data.session ?? null
    examiner.value = data.examiner ?? null
  } catch {
    // handled by HTTP interceptor
  } finally {
    loading.value = false
  }
}

async function fetchItems() {
  itemsLoading.value = true
  try {
    const res = await scaleApi.getItemResponses(assessmentId.value)
    items.value = res.data.data ?? []
  } catch {
    items.value = []
  } finally {
    itemsLoading.value = false
  }
}

async function fetchHistory() {
  historyLoading.value = true
  try {
    const res = await scaleApi.getHistory(assessmentId.value)
    history.value = res.data.data ?? []
  } catch {
    history.value = []
  } finally {
    historyLoading.value = false
  }
}

// ---- Tab change ----
const loadedTabs = reactive<Record<string, boolean>>({
  responses: false,
  history: false,
})

function handleTabChange(tabName: string) {
  activeTab.value = tabName
  if (tabName === 'responses' && !loadedTabs.responses) {
    fetchItems().then(() => { loadedTabs.responses = true })
  }
  if (tabName === 'history' && !loadedTabs.history) {
    fetchHistory().then(() => { loadedTabs.history = true })
  }
}

// ---- Actions ----
function goBack() {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push({ name: 'ScaleInstrumentList' })
  }
}

function goToSubject() {
  if (subject.value?.id) {
    router.push({ name: 'SubjectDetail', params: { id: subject.value.id } })
  }
}

function goToSession() {
  if (session.value?.id) {
    router.push({ name: 'SessionDetail', params: { id: session.value.id } })
  }
}

function goToAssessment(otherId: number) {
  router.push({ name: 'ScaleAssessmentDetail', params: { id: otherId } })
}

async function handleExportPDF() {
  exporting.value = true
  try {
    const res = await scaleApi.exportAssessment(assessmentId.value, 'pdf')
    // Create a blob URL and trigger download
    const blob = res.data instanceof Blob ? res.data : new Blob([res.data], { type: 'application/pdf' })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url

    // Derive filename from assessment info
    const subjectId = subject.value?.subjectId || subject.value?.id || 'unknown'
    const instrumentName = instrument.value?.name || instrument.value?.fullName || 'assessment'
    const dateStr = assessment.value?.assessmentDate
      ? new Date(assessment.value.assessmentDate).toISOString().slice(0, 10)
      : 'unknown'
    link.download = `${subjectId}_${instrumentName}_${dateStr}.pdf`

    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)

    ElMessage.success('PDF 导出成功')
  } catch (err: any) {
    // Handle error gracefully — the interceptor may have already shown a message
    if (err?.message && err.message !== 'Request failed with status code 500') {
      ElMessage.error('导出失败: ' + err.message)
    }
  } finally {
    exporting.value = false
  }
}

function handlePrint() {
  window.print()
}

// ---- Lifecycle ----
onMounted(async () => {
  await fetchAssessment()
  // Load items tab eagerly if assessment exists
  if (assessment.value) {
    await fetchItems()
    loadedTabs.responses = true
  }
})
</script>

<template>
  <div class="page-container" v-loading="loading">
    <!-- Back navigation -->
    <div class="page-nav">
      <el-button text @click="goBack">
        <el-icon><ArrowLeft /></el-icon>
        返回
      </el-button>
    </div>

    <!-- Header row: title + actions -->
    <div class="page-header" v-if="assessment">
      <div class="header-left">
        <h2 class="page-title">
          {{ instrument?.fullName || instrument?.name || '量表评估' }}
        </h2>
        <el-tag :type="statusType(assessment.dataEntryStatus)" size="large" class="status-tag">
          {{ statusLabel(assessment.dataEntryStatus) }}
        </el-tag>
      </div>
      <div class="header-actions">
        <el-button
          type="primary"
          :icon="Download"
          :loading="exporting"
          @click="handleExportPDF"
        >
          导出 PDF
        </el-button>
        <el-button :icon="Printer" @click="handlePrint">
          打印
        </el-button>
      </div>
    </div>

    <template v-if="assessment">
      <!-- Section 1: Assessment Metadata -->
      <el-card class="info-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span>评估信息</span>
          </div>
        </template>
        <el-descriptions :column="2" border>
          <!-- Subject -->
          <el-descriptions-item label="受试者" :span="1">
            <template v-if="subject">
              <el-link type="primary" @click="goToSubject">
                {{ subject.subjectId || subject.id || '-' }}
                <template v-if="subject.name || subject.realName">
                  （{{ subject.name || subject.realName }}）
                </template>
              </el-link>
            </template>
            <span v-else>-</span>
          </el-descriptions-item>

          <!-- Session -->
          <el-descriptions-item label="所属访视" :span="1">
            <template v-if="session">
              <el-link type="primary" @click="goToSession">
                {{ session.visitLabel || `访视 #${session.id}` }}
              </el-link>
            </template>
            <template v-else-if="assessment.sessionId">
              访视 #{{ assessment.sessionId }}
            </template>
            <span v-else>-</span>
          </el-descriptions-item>

          <!-- Instrument -->
          <el-descriptions-item label="量表工具" :span="1">
            {{ instrument?.fullName || instrument?.name || assessment.instrumentId }}
            <template v-if="instrument?.version">
              <el-tag size="small" class="ml-2">{{ instrument.version }}</el-tag>
            </template>
          </el-descriptions-item>

          <!-- Category -->
          <el-descriptions-item label="量表分类" :span="1">
            {{ instrument?.category || '-' }}
          </el-descriptions-item>

          <!-- Assessment date -->
          <el-descriptions-item label="评估日期" :span="1">
            <el-icon class="inline-icon"><Clock /></el-icon>
            {{ formatDate(assessment.assessmentDate) }}
          </el-descriptions-item>

          <!-- Examiner -->
          <el-descriptions-item label="评估者" :span="1">
            {{ examiner?.realName || examiner?.username || assessment.examinerId || '-' }}
          </el-descriptions-item>

          <!-- Administration mode -->
          <el-descriptions-item label="评估方式" :span="1">
            {{ adminModeLabel((assessment as any).administrationMode) }}
          </el-descriptions-item>

          <!-- Status -->
          <el-descriptions-item label="录入状态" :span="1">
            <el-tag :type="statusType(assessment.dataEntryStatus)" size="small">
              {{ statusLabel(assessment.dataEntryStatus) }}
            </el-tag>
          </el-descriptions-item>

          <!-- Scoring range -->
          <el-descriptions-item label="评分范围" :span="1">
            {{ instrument?.scoringRangeMin ?? 0 }} – {{ instrument?.scoringRangeMax ?? 100 }}
          </el-descriptions-item>

          <!-- Cutoff -->
          <el-descriptions-item label="临界分值" :span="1">
            {{ instrument?.cutoffScore != null ? instrument.cutoffScore : '未设定' }}
          </el-descriptions-item>

          <!-- Notes -->
          <el-descriptions-item label="备注" :span="2">
            {{ assessment.notes || '-' }}
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- Section 2: Total Score & Interpretation -->
      <el-card class="info-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span>评估结果</span>
          </div>
        </template>

        <div class="result-summary">
          <!-- Total score display -->
          <div class="total-score-section">
            <div class="total-score-value">
              {{ formatScore(assessment.totalScore) }}
            </div>
            <div class="total-score-label">总分</div>
          </div>

          <!-- Score bar -->
          <div class="score-bar-wrapper">
            <div class="score-bar-range">
              <span>{{ instrument?.scoringRangeMin ?? 0 }}</span>
              <span>{{ instrument?.scoringRangeMax ?? 100 }}</span>
            </div>
            <div class="score-bar">
              <div
                class="score-bar-fill"
                :style="{
                  width: scorePercentage + '%',
                  backgroundColor: resultInterpretation.color,
                }"
              />
              <div
                v-if="instrument?.cutoffScore != null"
                class="score-bar-cutoff"
                :style="{
                  left: Math.min(100, Math.max(0,
                    ((instrument.cutoffScore - (instrument.scoringRangeMin ?? 0))
                    / ((instrument.scoringRangeMax ?? 100) - (instrument.scoringRangeMin ?? 0))) * 100
                  )) + '%',
                }"
                :title="'临界值: ' + instrument.cutoffScore"
              />
            </div>
            <div class="score-bar-cutoff-label" v-if="instrument?.cutoffScore != null">
              临界值: {{ instrument.cutoffScore }}
            </div>
          </div>

          <!-- Interpretation badge -->
          <div class="interpretation-section">
            <span class="interpretation-label">判定：</span>
            <el-tag
              :type="resultInterpretation.tagType"
              size="large"
              effect="dark"
              class="interpretation-tag"
            >
              {{ resultInterpretation.label }}
            </el-tag>
            <span class="interpretation-detail" v-if="instrument?.cutoffScore != null && assessment.totalScore != null">
              （总分 {{ assessment.totalScore }}
              <template v-if="assessment.totalScore >= instrument.cutoffScore"> &ge; </template>
              <template v-else> &lt; </template>
              临界值 {{ instrument.cutoffScore }}）
            </span>
          </div>
        </div>
      </el-card>

      <!-- Section 3: Domain Scores -->
      <el-card class="info-card" shadow="never" v-if="domainScores.length">
        <template #header>
          <div class="card-header">
            <span>维度得分</span>
          </div>
        </template>
        <div class="domain-scores-grid">
          <div
            v-for="domain in domainScores"
            :key="domain.category"
            class="domain-card"
          >
            <div class="domain-header">
              <span class="domain-name">{{ domain.category }}</span>
              <span class="domain-items">{{ domain.itemCount }} 题</span>
            </div>
            <div class="domain-score-row">
              <span class="domain-score">
                {{ domain.score }} / {{ domain.maxScore }}
              </span>
              <span
                class="domain-pct"
                :style="{ color: domainColor(domain.percentage) }"
              >
                {{ domain.percentage }}%
              </span>
            </div>
            <el-progress
              :percentage="domain.percentage"
              :color="domainColor(domain.percentage)"
              :stroke-width="8"
              :show-text="false"
            />
          </div>
        </div>
      </el-card>

      <!-- Section 4: Tabs (Item Responses / Change History) -->
      <el-card class="tabs-card" shadow="never">
        <el-tabs v-model="activeTab" @tab-change="handleTabChange">
          <!-- Tab: Item-by-item responses -->
          <el-tab-pane label="作答明细" name="responses">
            <div v-loading="itemsLoading">
              <el-empty
                v-if="!itemsLoading && !items.length"
                description="暂无作答记录"
              />
              <el-table
                v-else
                :data="items"
                stripe
                size="small"
                style="width: 100%"
                max-height="520"
              >
                <el-table-column
                  type="index"
                  label="#"
                  width="55"
                  align="center"
                />
                <el-table-column
                  prop="questionText"
                  label="题目"
                  min-width="280"
                  show-overflow-tooltip
                />
                <el-table-column
                  label="作答"
                  width="160"
                  align="center"
                >
                  <template #default="{ row }">
                    <span v-if="typeof row.response === 'boolean'">
                      {{ row.response ? '是' : '否' }}
                    </span>
                    <span v-else>
                      {{ row.response }}
                    </span>
                  </template>
                </el-table-column>
                <el-table-column
                  prop="score"
                  label="得分"
                  width="90"
                  align="center"
                >
                  <template #default="{ row }">
                    <span class="item-score">
                      {{ row.score }}
                    </span>
                  </template>
                </el-table-column>
                <el-table-column
                  label="满分"
                  width="80"
                  align="center"
                >
                  <template #default="{ row }">
                    <span class="item-max">{{ row.maxScore }}</span>
                  </template>
                </el-table-column>
                <el-table-column
                  prop="category"
                  label="维度"
                  width="140"
                  align="center"
                >
                  <template #default="{ row }">
                    <el-tag size="small" type="info" v-if="row.category">
                      {{ row.category }}
                    </el-tag>
                    <span v-else>-</span>
                  </template>
                </el-table-column>
                <!-- Score ratio mini bar -->
                <el-table-column
                  label="得分率"
                  width="110"
                  align="center"
                >
                  <template #default="{ row }">
                    <el-progress
                      :percentage="row.maxScore > 0 ? Math.round((row.score / row.maxScore) * 100) : 0"
                      :stroke-width="6"
                      :show-text="true"
                      :color="row.maxScore > 0 && row.score / row.maxScore >= 0.8 ? '#f56c6c' : row.maxScore > 0 && row.score / row.maxScore >= 0.6 ? '#e6a23c' : '#67c23a'"
                    />
                  </template>
                </el-table-column>
              </el-table>

              <!-- Item summary -->
              <div class="items-summary" v-if="items.length">
                共 {{ items.length }} 题，总分 {{ domainScores.reduce((s, d) => s + d.score, 0) }}
                / {{ domainScores.reduce((s, d) => s + d.maxScore, 0) }}
              </div>
            </div>
          </el-tab-pane>

          <!-- Tab: Change History -->
          <el-tab-pane label="历史记录" name="history">
            <div v-loading="historyLoading">
              <el-empty
                v-if="!historyLoading && !history.length"
                description="暂无历史评估记录"
              />
              <el-timeline v-else>
                <el-timeline-item
                  v-for="(item, index) in history"
                  :key="item.id ?? index"
                  :timestamp="formatDateTime(item.assessmentDate)"
                  placement="top"
                  :color="item.id === assessmentId ? '#409eff' : '#e4e7ed'"
                >
                  <el-card
                    shadow="hover"
                    class="history-card"
                    :class="{ 'current-card': item.id === assessmentId }"
                  >
                    <div class="history-row">
                      <div class="history-info">
                        <span class="history-instrument">
                          {{ item.instrumentName || item.instrument?.name || instrument?.name || '-' }}
                        </span>
                        <el-tag
                          :type="statusType(item.dataEntryStatus)"
                          size="small"
                          class="history-status"
                        >
                          {{ statusLabel(item.dataEntryStatus) }}
                        </el-tag>
                      </div>
                      <div class="history-score-section">
                        <span class="history-score-label">总分：</span>
                        <span class="history-score-value">
                          {{ formatScore(item.totalScore) }}
                        </span>
                      </div>
                      <div class="history-actions">
                        <el-button
                          v-if="item.id !== assessmentId"
                          text
                          type="primary"
                          size="small"
                          @click="goToAssessment(item.id)"
                        >
                          查看
                        </el-button>
                        <el-tag
                          v-else
                          type="primary"
                          size="small"
                          effect="plain"
                        >
                          当前
                        </el-tag>
                      </div>
                    </div>

                    <!-- Examiner & mode -->
                    <div class="history-meta" v-if="item.examiner || item.administrationMode">
                      <span v-if="item.examiner">
                        评估者：{{ item.examiner.realName || item.examiner.username || item.examinerId || '-' }}
                      </span>
                      <span v-if="item.administrationMode" class="ml-3">
                        方式：{{ adminModeLabel(item.administrationMode) }}
                      </span>
                    </div>
                  </el-card>
                </el-timeline-item>
              </el-timeline>
            </div>
          </el-tab-pane>
        </el-tabs>
      </el-card>
    </template>

    <!-- Error / empty state when assessment not found -->
    <el-empty v-if="!loading && !assessment" description="评估记录不存在或已被删除">
      <el-button type="primary" @click="router.push({ name: 'ScaleInstrumentList' })">
        返回量表列表
      </el-button>
    </el-empty>
  </div>
</template>

<script lang="ts">
export default {
  name: 'ScaleAssessmentDetail',
}
</script>

<style scoped lang="scss">
.page-container {
  max-width: 1100px;
}

.page-nav {
  margin-bottom: 8px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  flex-wrap: wrap;
  gap: 12px;

  .header-left {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .header-actions {
    display: flex;
    align-items: center;
    gap: 8px;
  }
}

.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}

.status-tag {
  font-size: 14px;
}

.info-card {
  margin-bottom: 16px;

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: 16px;
    font-weight: 600;
    color: #303133;
  }

  :deep(.el-descriptions__label) {
    width: 110px;
    font-weight: 500;
  }
}

.ml-2 {
  margin-left: 8px;
}

.ml-3 {
  margin-left: 12px;
}

.inline-icon {
  margin-right: 4px;
  vertical-align: -2px;
}

// ---- Result summary ----
.result-summary {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
  padding: 10px 0;
}

.total-score-section {
  text-align: center;
}

.total-score-value {
  font-size: 48px;
  font-weight: 700;
  color: #303133;
  line-height: 1.2;
}

.total-score-label {
  font-size: 14px;
  color: #909399;
  margin-top: 4px;
}

// Score bar
.score-bar-wrapper {
  width: 100%;
  max-width: 600px;
}

.score-bar-range {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}

.score-bar {
  position: relative;
  height: 18px;
  background: #f0f2f5;
  border-radius: 9px;
  overflow: visible;
}

.score-bar-fill {
  height: 100%;
  border-radius: 9px;
  transition: width 0.4s ease;
  min-width: 4px;
}

.score-bar-cutoff {
  position: absolute;
  top: -3px;
  bottom: -3px;
  width: 3px;
  background: #303133;
  border-radius: 2px;
  transform: translateX(-50%);
}

.score-bar-cutoff-label {
  text-align: right;
  font-size: 12px;
  color: #606266;
  margin-top: 2px;
}

// Interpretation
.interpretation-section {
  display: flex;
  align-items: center;
  gap: 8px;
}

.interpretation-label {
  font-size: 15px;
  font-weight: 500;
  color: #303133;
}

.interpretation-tag {
  font-size: 16px !important;
  padding: 6px 16px;
}

.interpretation-detail {
  font-size: 13px;
  color: #909399;
}

// ---- Domain scores ----
.domain-scores-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

.domain-card {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 16px;
  background: #fafafa;

  .domain-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 10px;
  }

  .domain-name {
    font-size: 14px;
    font-weight: 600;
    color: #303133;
  }

  .domain-items {
    font-size: 12px;
    color: #909399;
  }

  .domain-score-row {
    display: flex;
    justify-content: space-between;
    align-items: baseline;
    margin-bottom: 10px;
  }

  .domain-score {
    font-size: 20px;
    font-weight: 700;
    color: #303133;
  }

  .domain-pct {
    font-size: 16px;
    font-weight: 600;
  }
}

// ---- Items table ----
.item-score {
  font-weight: 600;
  color: #303133;
}

.item-max {
  color: #909399;
}

.items-summary {
  margin-top: 12px;
  text-align: right;
  font-size: 13px;
  color: #606266;
  font-weight: 500;
}

// ---- History timeline ----
.history-card {
  .history-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    flex-wrap: wrap;
    gap: 8px;
  }

  .history-info {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .history-instrument {
    font-weight: 600;
    color: #303133;
  }

  .history-status {
    flex-shrink: 0;
  }

  .history-score-section {
    display: flex;
    align-items: baseline;
  }

  .history-score-label {
    font-size: 13px;
    color: #909399;
  }

  .history-score-value {
    font-size: 20px;
    font-weight: 700;
    color: #303133;
  }

  .history-actions {
    display: flex;
    align-items: center;
    gap: 4px;
  }

  .history-meta {
    margin-top: 8px;
    font-size: 12px;
    color: #909399;
  }
}

.current-card {
  border-color: #409eff;
  background: #ecf5ff;
}

// Print styles
@media print {
  .page-nav,
  .page-header .header-actions,
  .tabs-card :deep(.el-tabs__header),
  .history-actions {
    display: none !important;
  }

  .page-container {
    max-width: 100%;
  }

  .info-card {
    break-inside: avoid;
  }

  .domain-scores-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
