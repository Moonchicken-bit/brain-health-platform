<script setup lang="ts">
import { ref, onMounted, markRaw } from 'vue'
import { useRouter } from 'vue-router'
import { User, Plus, Edit, PictureFilled, WarningFilled, Clock } from '@element-plus/icons-vue'
import { subjectHttp } from '@/api/client'
import http from '@/api/client'

interface StatCard {
  label: string; value: number; icon: typeof User; iconBg: string
}
interface BreakdownItem {
  label: string; count: number; color: string
}
interface PendingAssessment {
  subjectId: number; subjectLabel: string; visitCode: string; visitName: string
  instrumentName: string; dueDate: string
}
interface ScoreAlert {
  subjectId: number; subjectLabel: string; instrumentName: string
  currentScore: number; previousScore: number; trend: 'up' | 'down'
  interpretation: string; visitCode: string
}

const router = useRouter()

const statCards = ref<StatCard[]>([
  { label: '受试者总数', value: 0, icon: markRaw(User), iconBg: '#409eff' },
  { label: '本月新增', value: 0, icon: markRaw(Plus), iconBg: '#67c23a' },
  { label: '待完成评估', value: 0, icon: markRaw(Edit), iconBg: '#e6a23c' },
  { label: '待质控影像', value: 0, icon: markRaw(PictureFilled), iconBg: '#8b5cf6' },
])

const recentSubjects = ref<any[]>([])
const genderBreakdown = ref<BreakdownItem[]>([])
const projectBreakdown = ref<BreakdownItem[]>([])
const assessmentBreakdown = ref<BreakdownItem[]>([])

// New: pending assessments and alerts
const pendingAssessments = ref<PendingAssessment[]>([])
const scoreAlerts = ref<ScoreAlert[]>([])

const colors = ['#409eff', '#67c23a', '#e6a23c', '#8b5cf6', '#909399', '#f56c6c']

onMounted(async () => {
  try {
    const res = await subjectHttp.get('/api/v1/dashboard/stats')
    const d = res.data.data
    statCards.value[0].value = d.totalSubjects || 0
    statCards.value[1].value = d.newThisMonth || 0
    statCards.value[2].value = d.pendingAssessments || 0
    statCards.value[3].value = d.pendingImagingQC || 0

    genderBreakdown.value = (d.genderBreakdown || []).map((item: any, i: number) => ({
      label: item.label === 'Male' ? '男' : item.label === 'Female' ? '女' : item.label,
      count: item.count, color: colors[i % colors.length]
    }))

    projectBreakdown.value = (d.projectBreakdown || []).map((item: any, i: number) => ({
      label: item.projectName, count: item.count, color: colors[i % colors.length]
    }))

    assessmentBreakdown.value = (d.assessmentBreakdown || []).map((item: any, i: number) => ({
      label: item.status === 'Complete' ? '已完成' : item.status === 'Incomplete' ? '进行中' : item.status,
      count: item.count, color: colors[i % colors.length]
    }))

    recentSubjects.value = (d.recentSubjects || []).map((s: any) => ({
      id: s.subjectId,
      gender: s.sex === 'Male' ? '男' : s.sex === 'Female' ? '女' : s.sex,
      age: s.dateOfBirth ? Math.floor((Date.now() - new Date(s.dateOfBirth).getTime()) / 31557600000) : '-',
      registeredAt: s.registeredAt?.substring(0, 10) || '',
      project: s.projectName || ''
    }))

    // New fields from dashboard stats
    pendingAssessments.value = (d.pendingAssessmentsList || []).map((a: any) => ({
      subjectId: a.subjectId || a.subject_id,
      subjectLabel: a.subjectLabel || a.subject_label || a.subjectName || '',
      visitCode: a.visitCode || a.visit_code || '',
      visitName: a.visitName || a.visit_name || '',
      instrumentName: a.instrumentName || a.instrument_name || '',
      dueDate: a.dueDate?.substring(0, 10) || a.due_date?.substring(0, 10) || '',
    }))

    scoreAlerts.value = (d.scoreAlerts || []).map((a: any) => ({
      subjectId: a.subjectId || a.subject_id,
      subjectLabel: a.subjectLabel || a.subject_label || a.subjectName || '',
      instrumentName: a.instrumentName || a.instrument_name || '',
      currentScore: a.currentScore ?? a.current_score ?? 0,
      previousScore: a.previousScore ?? a.previous_score ?? 0,
      trend: a.trend || (a.currentScore > a.previousScore ? 'up' : 'down'),
      interpretation: a.interpretation || '',
      visitCode: a.visitCode || a.visit_code || '',
    }))
  } catch (e) {
    console.error('Dashboard load failed', e)
    // Fallback: set empty arrays
    pendingAssessments.value = []
    scoreAlerts.value = []
  }
})

function formatNumber(n: number): string {
  if (n >= 10000) return (n / 10000).toFixed(1) + 'w'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'k'
  return String(n)
}

function goToVisitEntry(subjectId: number, visitCode: string) {
  router.push({
    path: '/visit-entry',
    query: { subjectId: String(subjectId), visitCode },
  })
}

function navigateToSubjects() {
  router.push('/subjects')
}

function navigateToScoreCenter() {
  router.push('/score-center')
}
</script>

<template>
  <div class="page-container">
    <!-- Page title -->
    <div class="page-title">工作台</div>

    <!-- ====== NEW: Pending tasks & alerts section ====== -->
    <el-row :gutter="20" class="attention-row">
      <!-- Today's pending assessments -->
      <el-col :span="13">
        <div class="attention-card">
          <div class="attention-header">
            <div class="attention-title">
              <el-icon :size="18" color="#e6a23c"><Clock /></el-icon>
              <span>今日待填量表</span>
            </div>
            <el-badge :value="pendingAssessments.length" :hidden="pendingAssessments.length === 0" type="warning" />
          </div>

          <div v-if="pendingAssessments.length === 0" class="attention-empty">
            <span class="empty-icon">&#10003;</span>
            <span>今日无量表需要填写</span>
          </div>
          <div v-else class="pending-list">
            <div
              v-for="item in pendingAssessments.slice(0, 5)"
              :key="item.subjectId + '-' + item.visitCode + '-' + item.instrumentName"
              class="pending-item"
            >
              <div class="pending-info">
                <span class="pending-subject">{{ item.subjectLabel }}</span>
                <span class="pending-visit">{{ item.visitName }}</span>
                <span class="pending-instrument">{{ item.instrumentName }}</span>
              </div>
              <el-button
                type="primary"
                size="small"
                text
                @click="goToVisitEntry(item.subjectId, item.visitCode)"
              >
                开始填写
              </el-button>
            </div>
            <div v-if="pendingAssessments.length > 5" class="more-hint">
              还有 {{ pendingAssessments.length - 5 }} 项...
            </div>
          </div>
        </div>
      </el-col>

      <!-- Abnormal score alerts -->
      <el-col :span="11">
        <div class="attention-card alert-card">
          <div class="attention-header">
            <div class="attention-title">
              <el-icon :size="18" color="#f56c6c"><WarningFilled /></el-icon>
              <span>异常分数告警</span>
            </div>
            <el-badge :value="scoreAlerts.length" :hidden="scoreAlerts.length === 0" type="danger" />
          </div>

          <div v-if="scoreAlerts.length === 0" class="attention-empty">
            <span class="empty-icon">&#10003;</span>
            <span>所有受试者评分正常</span>
          </div>
          <div v-else class="alert-list">
            <div
              v-for="item in scoreAlerts.slice(0, 5)"
              :key="item.subjectId + '-' + item.instrumentName"
              class="alert-item"
            >
              <div class="alert-info">
                <div class="alert-subject-row">
                  <span class="alert-subject">{{ item.subjectLabel }}</span>
                  <span class="alert-instrument">{{ item.instrumentName }}</span>
                </div>
                <div class="alert-trend">
                  <span class="alert-score">
                    {{ item.previousScore }} &#8594; <strong :style="{ color: item.trend === 'up' ? '#f56c6c' : '#67c23a' }">{{ item.currentScore }}</strong>
                  </span>
                  <el-tag
                    :type="item.trend === 'up' ? 'danger' : 'success'"
                    size="small"
                    effect="plain"
                  >
                    {{ item.trend === 'up' ? '恶化' : '改善' }}
                  </el-tag>
                </div>
              </div>
              <el-button
                type="danger"
                size="small"
                text
                @click="goToVisitEntry(item.subjectId, item.visitCode)"
              >
                查看详情
              </el-button>
            </div>
            <div v-if="scoreAlerts.length > 5" class="more-hint">
              还有 {{ scoreAlerts.length - 5 }} 项...
            </div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- Stat cards row -->
    <el-row :gutter="20" class="stat-row">
      <el-col
        v-for="card in statCards"
        :key="card.label"
        :span="6"
      >
        <div class="stat-card">
          <div class="stat-icon" :style="{ backgroundColor: card.iconBg }">
            <el-icon :size="24">
              <component :is="card.icon" />
            </el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ formatNumber(card.value) }}</div>
            <div class="stat-label">{{ card.label }}</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- Two-column layout -->
    <el-row :gutter="20" class="content-row">
      <!-- Left: Recent subjects -->
      <el-col :span="14">
        <div class="section-card">
          <div class="section-header">
            <span class="section-title">最近登记的受试者</span>
            <el-button text type="primary" size="small" @click="navigateToSubjects">查看全部</el-button>
          </div>
          <el-table
            :data="recentSubjects"
            stripe
            style="width: 100%"
            size="small"
          >
            <el-table-column prop="id" label="编号" min-width="160" />
            <el-table-column prop="name" label="姓名" width="80" />
            <el-table-column prop="gender" label="性别" width="60" />
            <el-table-column prop="age" label="年龄" width="60" />
            <el-table-column prop="project" label="项目" min-width="140" />
            <el-table-column prop="registeredAt" label="登记时间" min-width="150" />
          </el-table>
        </div>
      </el-col>

      <!-- Right: Data overview -->
      <el-col :span="10">
        <div class="section-card">
          <div class="section-header">
            <span class="section-title">数据概览</span>
          </div>

          <!-- Gender breakdown -->
          <div class="overview-group">
            <div class="overview-group-title">性别分布</div>
            <div class="breakdown-bar">
              <div
                v-for="item in genderBreakdown"
                :key="item.label"
                class="breakdown-segment"
                :style="{
                  flex: item.count,
                  backgroundColor: item.color,
                }"
              />
            </div>
            <div class="breakdown-legend">
              <span
                v-for="item in genderBreakdown"
                :key="item.label"
                class="legend-item"
              >
                <span class="legend-dot" :style="{ backgroundColor: item.color }" />
                {{ item.label }} ({{ item.count }})
              </span>
            </div>
          </div>

          <!-- Project breakdown -->
          <div class="overview-group">
            <div class="overview-group-title">项目分布</div>
            <div class="breakdown-bar">
              <div
                v-for="item in projectBreakdown"
                :key="item.label"
                class="breakdown-segment"
                :style="{
                  flex: item.count,
                  backgroundColor: item.color,
                }"
              />
            </div>
            <div class="breakdown-legend">
              <span
                v-for="item in projectBreakdown"
                :key="item.label"
                class="legend-item"
              >
                <span class="legend-dot" :style="{ backgroundColor: item.color }" />
                {{ item.label }} ({{ item.count }})
              </span>
            </div>
          </div>

          <!-- Assessment status -->
          <div class="overview-group">
            <div class="overview-group-title">评估状态</div>
            <div class="assessment-stats">
              <div
                v-for="item in assessmentBreakdown"
                :key="item.label"
                class="assessment-item"
              >
                <span class="assessment-count" :style="{ color: item.color }">
                  {{ item.count }}
                </span>
                <span class="assessment-label">{{ item.label }}</span>
              </div>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script lang="ts">
export default {
  name: 'DashboardView',
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

// ---- Attention cards ----
.attention-row {
  margin-bottom: 20px;
}

.attention-card {
  background: #fff;
  border-radius: 10px;
  padding: 16px 20px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  height: 100%;
  min-height: 140px;

  &.alert-card {
    // subtle red tint on left
  }
}

.attention-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
  padding-bottom: 10px;
  border-bottom: 1px solid #f0f0f0;
}

.attention-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.attention-empty {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 20px 0;
  color: #909399;
  font-size: 14px;

  .empty-icon {
    font-size: 18px;
    color: #67c23a;
  }
}

// Pending list
.pending-list, .alert-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.pending-item, .alert-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 10px;
  border-radius: 6px;
  transition: background 0.15s;

  &:hover {
    background: #f5f7fa;
  }
}

.pending-info {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.pending-subject {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  white-space: nowrap;
}

.pending-visit {
  font-size: 12px;
  color: #409eff;
  background: #ecf5ff;
  padding: 1px 8px;
  border-radius: 3px;
}

.pending-instrument {
  font-size: 12px;
  color: #909399;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.alert-info {
  min-width: 0;
}

.alert-subject-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 2px;
}

.alert-subject {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}

.alert-instrument {
  font-size: 11px;
  color: #909399;
  background: #f0f0f0;
  padding: 1px 6px;
  border-radius: 3px;
}

.alert-trend {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}

.alert-score {
  color: #606266;

  strong {
    font-size: 14px;
  }
}

.more-hint {
  font-size: 12px;
  color: #c0c4cc;
  text-align: center;
  padding: 4px 0;
}

// ---- Stat cards ----
.stat-row {
  margin-bottom: 20px;
}

.stat-card {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  transition: box-shadow 0.3s;

  &:hover {
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  }

  .stat-icon {
    width: 52px;
    height: 52px;
    border-radius: 10px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    flex-shrink: 0;
  }

  .stat-info {
    flex: 1;
    min-width: 0;

    .stat-value {
      font-size: 26px;
      font-weight: 700;
      color: #303133;
      line-height: 1.2;
    }

    .stat-label {
      font-size: 13px;
      color: #909399;
      margin-top: 4px;
    }
  }
}

// ---- Content section ----
.content-row {
  align-items: stretch;
}

.section-card {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  height: 100%;

  .section-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;

    .section-title {
      font-size: 16px;
      font-weight: 600;
      color: #303133;
      position: relative;
      padding-left: 12px;

      &::before {
        content: '';
        position: absolute;
        left: 0;
        top: 50%;
        transform: translateY(-50%);
        width: 3px;
        height: 16px;
        background: #409eff;
        border-radius: 2px;
      }
    }
  }
}

// ---- Breakdown bars ----
.overview-group {
  margin-bottom: 18px;

  &:last-child {
    margin-bottom: 0;
  }

  .overview-group-title {
    font-size: 14px;
    color: #606266;
    margin-bottom: 8px;
    font-weight: 500;
  }
}

.breakdown-bar {
  display: flex;
  height: 8px;
  border-radius: 4px;
  overflow: hidden;
  margin-bottom: 8px;

  .breakdown-segment {
    min-width: 2px;
  }
}

.breakdown-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;

  .legend-item {
    font-size: 12px;
    color: #909399;
    display: flex;
    align-items: center;
    gap: 4px;

    .legend-dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      display: inline-block;
    }
  }
}

// ---- Assessment stats ----
.assessment-stats {
  display: flex;
  gap: 24px;

  .assessment-item {
    display: flex;
    flex-direction: column;
    align-items: center;

    .assessment-count {
      font-size: 24px;
      font-weight: 700;
      line-height: 1.3;
    }

    .assessment-label {
      font-size: 12px;
      color: #909399;
      margin-top: 2px;
    }
  }
}
</style>
