<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Top, Bottom, Minus } from '@element-plus/icons-vue'
import http from '@/api/client'

const router = useRouter()

// ---- Types ----
interface SubjectInfo { id: number; subjectId: string; name?: string }
interface ScoreHistoryItem { visitCode: string; date: string; score: number }
interface SubscaleItem { name: string; score: number; maxScore: number }
interface ScaleScoreRecord {
  instrument: string; name: string; totalScore: number; maxScore: number
  interpretation: string; flag: string; cutoffNote: string
  history: ScoreHistoryItem[]; subscales: SubscaleItem[]; interpretationText: string
}

// ---- State ----
const subjects = ref<SubjectInfo[]>([])
const selectedSubject = ref<number | null>(null)
const scores = ref<ScaleScoreRecord[]>([])
const tasks = ref<any[]>([])
const loading = ref(false)
const expandedScale = ref('')

// ---- Color mapping ----
const flagColorMap: Record<string, string> = { normal: '#67c23a', borderline: '#e6a23c', abnormal: '#f56c6c' }
const flagLabelMap: Record<string, string> = { normal: '正常', borderline: '临界', abnormal: '异常' }
const flagTagTypeMap: Record<string, any> = { normal: 'success', borderline: 'warning', abnormal: 'danger' }

// ---- Computed ----
const normalCount = computed(() => scores.value.filter(s => s.flag === 'normal').length)
const borderlineCount = computed(() => scores.value.filter(s => s.flag === 'borderline').length)
const abnormalCount = computed(() => scores.value.filter(s => s.flag === 'abnormal').length)

// ---- Methods ----
async function fetchSubjects() {
  try {
    const res = await http.get('/api/v1/subjects', { params: { page: 1, size: 200 } })
    if (res.data.code === 200) {
      subjects.value = (res.data.data.records || res.data.data || []).map((s: any) => ({
        id: s.id, subjectId: s.subjectId, name: s.name || '',
      }))
    }
  } catch { /* silent */ }
}

async function loadScores() {
  if (!selectedSubject.value) return
  loading.value = true; expandedScale.value = ''
  try {
    const [res, taskResponse] = await Promise.all([
      http.get('/api/v1/scales/scores/' + selectedSubject.value),
      http.get(`/api/v1/scales/subjects/${selectedSubject.value}/tasks`),
    ])
    tasks.value = taskResponse.data.data || []
    if (res.data.code === 200) {
      const raw = res.data.data
      if (Array.isArray(raw)) scores.value = raw.map(normalizeScore)
      else if (raw.scores) scores.value = raw.scores.map(normalizeScore)
      else scores.value = []
      if (scores.value.length === 0) ElMessage.info('该受试者暂无评分记录')
    }
  } catch { scores.value = []; tasks.value = [] } finally { loading.value = false }
}

async function returnTask(task: any) {
  await http.post(`/api/v1/scales/tasks/${task.id}/return`, {
    reason: '请补充或修正量表后重新提交',
  })
  ElMessage.success('已退回患者修改')
  await loadScores()
}

function normalizeScore(raw: any): ScaleScoreRecord {
  return {
    instrument: raw.instrument || raw.code || '', name: raw.name || raw.instrument || raw.code || '',
    totalScore: raw.totalScore ?? raw.total_score ?? raw.score ?? 0,
    maxScore: raw.maxScore ?? raw.max_score ?? 100,
    interpretation: raw.interpretation || '', flag: raw.flag || deriveFlag(raw),
    cutoffNote: raw.cutoffNote || raw.cutoff_note || '',
    history: (raw.history || []).map((h: any) => ({
      visitCode: h.visitCode || h.visit_code || '', date: h.date || h.assessmentDate || h.assessment_date || '',
      score: h.score ?? h.totalScore ?? h.total_score ?? 0,
    })),
    subscales: (raw.subscales || raw.sub_scale_scores || []).map((s: any) => ({
      name: s.name || s.subscale || '', score: s.score ?? 0, maxScore: s.maxScore ?? s.max_score ?? 0,
    })),
    interpretationText: raw.interpretationText || raw.interpretation_text || raw.notes || '',
  }
}

function deriveFlag(raw: any): string {
  if (raw.flag) return raw.flag
  const interp = (raw.interpretation || '').toLowerCase()
  if (interp.includes('正常') || interp.includes('normal')) return 'normal'
  if (interp.includes('轻度') || interp.includes('可疑') || interp.includes('borderline') || interp.includes('mild')) return 'borderline'
  if (interp.includes('中度') || interp.includes('重度') || interp.includes('异常') || interp.includes('abnormal')) return 'abnormal'
  return 'normal'
}

function toggleScale(instrument: string) { expandedScale.value = expandedScale.value === instrument ? '' : instrument }

function trendDirection(history: ScoreHistoryItem[]): 'up' | 'down' | 'flat' {
  if (history.length < 2) return 'flat'
  const last = history[history.length - 1].score; const prev = history[history.length - 2].score
  if (last > prev) return 'up'; if (last < prev) return 'down'; return 'flat'
}

function trendIcon(history: ScoreHistoryItem[]): string {
  const dir = trendDirection(history)
  if (dir === 'up') return 'arrow_upward'; if (dir === 'down') return 'arrow_downward'; return 'remove'
}

function getFlagColor(flag: string): string { return flagColorMap[flag] || '#909399' }
function getFlagLabel(flag: string): string { return flagLabelMap[flag] || flag }
function getFlagTagType(flag: string): any { return flagTagTypeMap[flag] || 'info' }
function subscalePercent(score: number, max: number): number { if (max <= 0) return 0; return Math.round((score / max) * 100) }

function sparklineBars(history: ScoreHistoryItem[], maxScore: number): { height: number; isLast: boolean }[] {
  return history.map((h, i) => ({
    height: maxScore > 0 ? Math.min(100, Math.max(5, (h.score / maxScore) * 100)) : 5,
    isLast: i === history.length - 1,
  }))
}

function trendClass(history: ScoreHistoryItem[]): string {
  const dir = trendDirection(history)
  return dir === 'up' ? 'trend-up' : dir === 'down' ? 'trend-down' : 'trend-flat'
}

onMounted(fetchSubjects)
</script>
<template>
  <div class="score-center">
    <!-- Header -->
    <div class="score-header">
      <h2 class="page-title">评分总览</h2>
      <div class="header-controls">
        <el-select
          v-model="selectedSubject"
          placeholder="请选择受试者"
          filterable
          style="width: 260px"
          @change="loadScores"
        >
          <el-option
            v-for="s in subjects"
            :key="s.id"
            :label="s.name ? s.subjectId + ' (' + s.name + ')' : s.subjectId"
            :value="s.id"
          />
        </el-select>
      </div>
    </div>

    <!-- Empty -->
    <div v-if="!selectedSubject" class="empty-hint">
      <el-empty description="请先选择受试者以查看量表评分" />
    </div>

    <!-- Loading -->
    <div v-else-if="loading" v-loading="loading" style="min-height: 400px" />

    <el-card v-else-if="selectedSubject && tasks.length" class="task-card" shadow="never">
      <template #header><strong>患者量表任务状态</strong></template>
      <el-table :data="tasks" size="small">
        <el-table-column prop="visitCode" label="访视" width="130" />
        <el-table-column prop="sessionDate" label="访视日期" width="140" />
        <el-table-column prop="status" label="状态" width="140" />
        <el-table-column prop="submittedAt" label="提交时间" min-width="180" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button
              v-if="['SUBMITTED', 'SCORED'].includes(row.status)"
              link
              type="warning"
              @click="returnTask(row)"
            >退回修改</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- Summary bar -->
    <div v-else-if="scores.length" class="summary-bar">
      <div class="summary-item normal">
        <span class="summary-dot" style="background: #67c23a" />
        <span class="summary-label">正常</span>
        <span class="summary-count">{{ normalCount }}</span>
      </div>
      <div class="summary-item borderline">
        <span class="summary-dot" style="background: #e6a23c" />
        <span class="summary-label">临界</span>
        <span class="summary-count">{{ borderlineCount }}</span>
      </div>
      <div class="summary-item abnormal">
        <span class="summary-dot" style="background: #f56c6c" />
        <span class="summary-label">异常</span>
        <span class="summary-count">{{ abnormalCount }}</span>
      </div>
      <div class="summary-item total">
        <span class="summary-label">总计</span>
        <span class="summary-count">{{ scores.length }}</span>
      </div>
    </div>

    <!-- Score cards -->
    <el-row v-if="scores.length" :gutter="20">
      <el-col
        v-for="record in scores"
        :key="record.instrument"
        :xs="24"
        :sm="12"
        :md="8"
        :lg="6"
      >
        <div
          class="score-card"
          :class="{ expanded: expandedScale === record.instrument }"
          @click="toggleScale(record.instrument)"
        >
          <!-- Top row: name + flag tag -->
          <div class="card-header">
            <span class="card-name">{{ record.name || record.instrument }}</span>
            <el-tag
              :type="getFlagTagType(record.flag)"
              size="small"
              effect="plain"
            >
              {{ getFlagLabel(record.flag) }}
            </el-tag>
          </div>

          <!-- Score display -->
          <div class="card-score" :style="{ color: getFlagColor(record.flag) }">
            <span class="score-number">{{ record.totalScore }}</span>
            <span class="score-max"> / {{ record.maxScore }}</span>
          </div>

          <!-- Interpretation -->
          <div
            class="card-interpretation"
            :style="{ color: getFlagColor(record.flag) }"
          >
            {{ record.interpretation || '-' }}
          </div>

          <!-- Sparkline bars -->
          <div v-if="record.history.length > 0" class="card-sparkline">
            <div class="sparkline-bars">
              <div
                v-for="(bar, i) in sparklineBars(record.history, record.maxScore)"
                :key="i"
                class="sparkline-bar"
                :class="{ last: bar.isLast }"
                :style="{
                  height: bar.height + '%',
                  backgroundColor: bar.isLast ? getFlagColor(record.flag) : '#c0c4cc',
                }"
                :title="record.history[i].score + '/' + record.maxScore"
              />
            </div>
            <span class="trend-arrow" :class="trendClass(record.history)">
              <el-icon v-if="trendDirection(record.history) === 'up'" :size="14">
                <component is="Top" />
              </el-icon>
              <el-icon v-else-if="trendDirection(record.history) === 'down'" :size="14">
                <component is="Bottom" />
              </el-icon>
              <el-icon v-else :size="14">
                <component is="Minus" />
              </el-icon>
            </span>
          </div>

          <!-- Expand area: history + subscales + interpretation -->
          <div
            v-if="expandedScale === record.instrument"
            class="card-expand"
            @click.stop
          >
            <!-- Score history table -->
            <div v-if="record.history.length > 0" class="expand-section">
              <div class="expand-title">评分历史</div>
              <div class="history-table">
                <div class="history-header">
                  <span>访视</span>
                  <span>得分</span>
                  <span>日期</span>
                </div>
                <div
                  v-for="h in record.history"
                  :key="h.visitCode"
                  class="history-row"
                >
                  <span>{{ h.visitCode }}</span>
                  <span class="history-score">{{ h.score }}/{{ record.maxScore }}</span>
                  <span class="history-date">{{ h.date }}</span>
                </div>
              </div>
            </div>

            <!-- Subscale breakdown -->
            <div v-if="record.subscales.length > 0" class="expand-section">
              <div class="expand-title">子量表分解</div>
              <div
                v-for="sub in record.subscales"
                :key="sub.name"
                class="subscale-row"
              >
                <span class="subscale-name">{{ sub.name }}</span>
                <div class="subscale-bar-wrap">
                  <div
                    class="subscale-bar"
                    :style="{
                      width: subscalePercent(sub.score, sub.maxScore) + '%',
                      backgroundColor: subscalePercent(sub.score, sub.maxScore) >= 70
                        ? '#f56c6c'
                        : subscalePercent(sub.score, sub.maxScore) >= 40
                          ? '#e6a23c'
                          : '#67c23a',
                    }"
                  />
                </div>
                <span class="subscale-value">{{ sub.score }}/{{ sub.maxScore }}</span>
              </div>
            </div>

            <!-- Interpretation text -->
            <div v-if="record.interpretationText" class="expand-section">
              <div class="expand-title">解读说明</div>
              <div class="interpretation-text">{{ record.interpretationText }}</div>
            </div>

            <!-- Cutoff note -->
            <div v-if="record.cutoffNote" class="expand-section">
              <div class="expand-title">截断值参考</div>
              <div class="cutoff-note">{{ record.cutoffNote }}</div>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- No data -->
    <div v-else-if="!loading && selectedSubject" class="empty-hint">
      <el-empty description="该受试者暂无量表评分记录" />
    </div>
  </div>
</template>

<script lang="ts">
export default { name: 'ScoreCenterView' }
</script>

<style scoped lang="scss">
.task-card {
  margin-bottom: 20px;
}

.score-center {
  padding: 0;
}

// ---- Header ----
.score-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;

  .page-title {
    font-size: 20px;
    font-weight: 600;
    color: #303133;
    margin: 0;
  }
}

.header-controls {
  display: flex;
  align-items: center;
  gap: 12px;
}

// ---- Summary bar ----
.summary-bar {
  display: flex;
  gap: 24px;
  background: #fff;
  border-radius: 10px;
  padding: 14px 24px;
  margin-bottom: 20px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.summary-item {
  display: flex;
  align-items: center;
  gap: 6px;

  &.total {
    margin-left: auto;
    padding-left: 24px;
    border-left: 1px solid #ebeef5;
  }
}

.summary-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}

.summary-label {
  font-size: 13px;
  color: #606266;
}

.summary-count {
  font-size: 16px;
  font-weight: 700;
  color: #303133;
}

// ---- Score cards ----
.score-card {
  background: #fff;
  border-radius: 10px;
  padding: 20px 20px 16px;
  margin-bottom: 20px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  cursor: pointer;
  transition: all 0.2s;
  border: 2px solid transparent;

  &:hover {
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
    transform: translateY(-2px);
  }
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.card-name {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-score {
  margin-bottom: 4px;
  line-height: 1.2;

  .score-number {
    font-size: 36px;
    font-weight: 700;
  }

  .score-max {
    font-size: 16px;
    color: #909399;
    font-weight: 400;
  }
}

.card-interpretation {
  font-size: 13px;
  font-weight: 500;
  margin-bottom: 12px;
}

// ---- Sparkline ----
.card-sparkline {
  display: flex;
  align-items: flex-end;
  gap: 6px;
  height: 36px;
  margin-top: 8px;
}

.sparkline-bars {
  display: flex;
  align-items: flex-end;
  gap: 3px;
  flex: 1;
  height: 100%;
}

.sparkline-bar {
  flex: 1;
  min-width: 6px;
  border-radius: 2px 2px 0 0;
  transition: background-color 0.2s;
}

.trend-arrow {
  flex-shrink: 0;

  &.trend-up {
    color: #f56c6c; // worsening scores (e.g., depression going up)
  }
  &.trend-down {
    color: #67c23a; // improving
  }
  &.trend-flat {
    color: #909399;
  }
}

// ---- Expand area ----
.card-expand {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid #ebeef5;
}

.expand-section {
  margin-bottom: 16px;

  &:last-child {
    margin-bottom: 0;
  }
}

.expand-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
  padding-left: 8px;
  border-left: 3px solid #409eff;
}

// History table
.history-table {
  font-size: 13px;
}

.history-header {
  display: flex;
  justify-content: space-between;
  padding: 4px 8px;
  color: #909399;
  font-size: 12px;
  border-bottom: 1px solid #f0f0f0;
  margin-bottom: 4px;

  span {
    flex: 1;

    &:last-child {
      text-align: right;
    }
  }
}

.history-row {
  display: flex;
  justify-content: space-between;
  padding: 5px 8px;
  color: #606266;
  border-radius: 4px;

  &:hover {
    background: #f5f7fa;
  }

  span {
    flex: 1;
  }

  .history-score {
    font-weight: 600;
  }

  .history-date {
    text-align: right;
    color: #909399;
    font-size: 12px;
  }
}

// Subscale
.subscale-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.subscale-name {
  width: 100px;
  font-size: 12px;
  color: #606266;
  flex-shrink: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.subscale-bar-wrap {
  flex: 1;
  height: 8px;
  background: #f5f5f5;
  border-radius: 4px;
  overflow: hidden;
}

.subscale-bar {
  height: 100%;
  border-radius: 4px;
  transition: width 0.4s ease;
}

.subscale-value {
  font-size: 12px;
  color: #303133;
  font-weight: 600;
  width: 60px;
  text-align: right;
  flex-shrink: 0;
}

// Interpretation text
.interpretation-text {
  font-size: 13px;
  color: #606266;
  line-height: 1.7;
  padding: 8px 12px;
  background: #fafafa;
  border-radius: 6px;
}

.cutoff-note {
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
  padding: 8px 12px;
  background: #fafafa;
  border-radius: 6px;
}

// ---- Empty ----
.empty-hint {
  padding: 100px 0;
}
</style>
