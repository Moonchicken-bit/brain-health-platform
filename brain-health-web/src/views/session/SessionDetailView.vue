<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft,
  DocumentCopy,
  CircleCheck,
  CircleClose,
  Refresh,
} from '@element-plus/icons-vue'
import { sessionApi, type SessionDTO, type SessionAssessmentSummary, type SessionImagingSummary, type SessionLabSummary } from '@/api/modules/session'

const route = useRoute()
const router = useRouter()

// ---- Core state ----
const sessionId = computed(() => Number(route.params.id))
const loading = ref(false)
const session = ref<SessionDTO | null>(null)
const statusChanging = ref(false)
const copying = ref(false)

// ---- Tab state ----
const activeTab = ref('assessments')
const assessments = ref<SessionAssessmentSummary[]>([])
const imaging = ref<SessionImagingSummary[]>([])
const labTests = ref<SessionLabSummary[]>([])
const tabLoading: Record<string, boolean> = reactive({
  assessments: false,
  imaging: false,
  labTests: false,
})

// ---- Status helpers ----
type StatusType = '' | 'success' | 'warning' | 'danger' | 'info'

const statusMap: Record<string, { label: string; type: StatusType }> = {
  PLANNED: { label: '已计划', type: 'info' },
  IN_PROGRESS: { label: '进行中', type: 'warning' },
  COMPLETED: { label: '已完成', type: 'success' },
  WITHDRAWN: { label: '已撤回', type: 'danger' },
}

function statusLabel(status: string): string {
  return statusMap[status]?.label || status
}

function statusType(status: string): StatusType {
  return statusMap[status]?.type || 'info'
}

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

// ---- Data fetching ----
async function fetchSession() {
  loading.value = true
  try {
    const res = await sessionApi.getById(sessionId.value)
    session.value = res.data.data
  } catch {
    // handled by HTTP interceptor
  } finally {
    loading.value = false
  }
}

async function loadTabData(tab: string) {
  if (tabLoading[tab]) return
  tabLoading[tab] = true
  try {
    switch (tab) {
      case 'assessments': {
        const res = await sessionApi.getAssessments(sessionId.value)
        assessments.value = res.data.data || []
        break
      }
      case 'imaging': {
        const res = await sessionApi.getImaging(sessionId.value)
        imaging.value = res.data.data || []
        break
      }
      case 'labTests': {
        const res = await sessionApi.getLabTests(sessionId.value)
        labTests.value = res.data.data || []
        break
      }
    }
  } catch {
    // handled by HTTP interceptor
  } finally {
    tabLoading[tab] = false
  }
}

function handleTabChange(tabName: string) {
  activeTab.value = tabName
  const empty =
    (tabName === 'assessments' && assessments.value.length === 0) ||
    (tabName === 'imaging' && imaging.value.length === 0) ||
    (tabName === 'labTests' && labTests.value.length === 0)
  if (empty) {
    loadTabData(tabName)
  }
}

// ---- Actions ----
function goBack() {
  // Navigate back to the subject if we came from subject detail, otherwise fallback
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push({ name: 'SubjectList' })
  }
}

function goToSubject() {
  if (session.value?.subjectId) {
    router.push({ name: 'SubjectDetail', params: { id: session.value.subjectId } })
  }
}

async function handleCopyFromLast() {
  if (!session.value || session.value.status === 'COMPLETED' || session.value.status === 'WITHDRAWN') {
    ElMessage.warning('当前访视状态不允许复制')
    return
  }
  try {
    await ElMessageBox.confirm(
      '将复制上一次访视的量表评估和检验项目配置到本次访视，是否继续？',
      '确认操作',
      { confirmButtonText: '确认复制', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  copying.value = true
  try {
    await sessionApi.copyFromLast(sessionId.value)
    ElMessage.success('已从上一次访视复制配置')
    // Refresh session data and tab data
    await fetchSession()
    if (activeTab.value === 'assessments') await loadTabData('assessments')
    if (activeTab.value === 'labTests') await loadTabData('labTests')
  } catch {
    // handled by HTTP interceptor
  } finally {
    copying.value = false
  }
}

async function handleUpdateStatus(newStatus: string) {
  const actionLabel = newStatus === 'COMPLETED' ? '完成' : '撤回'
  try {
    await ElMessageBox.confirm(
      `确认将访视状态更新为「${actionLabel}」？`,
      '确认操作',
      { confirmButtonText: '确认', cancelButtonText: '取消', type: newStatus === 'WITHDRAWN' ? 'error' : 'success' }
    )
  } catch {
    return
  }
  statusChanging.value = true
  try {
    const res = await sessionApi.updateStatus(sessionId.value, newStatus)
    session.value = res.data.data
    ElMessage.success(`访视状态已更新为「${actionLabel}」`)
  } catch {
    // handled by HTTP interceptor
  } finally {
    statusChanging.value = false
  }
}

// ---- Assessment status helpers ----
function assessmentStatusLabel(status: string): string {
  const map: Record<string, string> = {
    DRAFT: '草稿',
    IN_PROGRESS: '评估中',
    COMPLETED: '已完成',
    SCORED: '已评分',
    VERIFIED: '已审核',
  }
  return map[status] || status
}

function assessmentStatusType(status: string): StatusType {
  const map: Record<string, StatusType> = {
    DRAFT: 'info',
    IN_PROGRESS: 'warning',
    COMPLETED: 'success',
    SCORED: 'success',
    VERIFIED: 'success',
  }
  return map[status] || 'info'
}

function qcStatusLabel(status: string): string {
  const map: Record<string, string> = {
    PASS: '通过',
    FAIL: '未通过',
    PENDING: '待质控',
    UNDER_REVIEW: '审核中',
  }
  return map[status] || status
}

function qcStatusType(status: string): StatusType {
  const map: Record<string, StatusType> = {
    PASS: 'success',
    FAIL: 'danger',
    PENDING: 'warning',
    UNDER_REVIEW: 'info',
  }
  return map[status] || 'info'
}

function canComplete(): boolean {
  return session.value?.status === 'IN_PROGRESS' || session.value?.status === 'PLANNED'
}

function canWithdraw(): boolean {
  return session.value?.status === 'IN_PROGRESS' || session.value?.status === 'PLANNED'
}

function canCopy(): boolean {
  return session.value?.status === 'PLANNED' || session.value?.status === 'IN_PROGRESS'
}

// ---- Lifecycle ----
onMounted(async () => {
  await fetchSession()
  // Load first tab data eagerly
  if (session.value) {
    await loadTabData('assessments')
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
    <div class="page-header" v-if="session">
      <div class="header-left">
        <h2 class="page-title">{{ session.visitLabel }}</h2>
        <el-tag :type="statusType(session.status)" size="large" class="status-tag">
          {{ statusLabel(session.status) }}
        </el-tag>
      </div>
      <div class="header-actions">
        <el-button
          v-if="canCopy()"
          :icon="DocumentCopy"
          :loading="copying"
          @click="handleCopyFromLast"
        >
          从上次访视复制
        </el-button>
        <el-button
          v-if="canComplete()"
          type="success"
          :icon="CircleCheck"
          :loading="statusChanging"
          @click="handleUpdateStatus('COMPLETED')"
        >
          标记完成
        </el-button>
        <el-button
          v-if="canWithdraw()"
          type="danger"
          :icon="CircleClose"
          :loading="statusChanging"
          @click="handleUpdateStatus('WITHDRAWN')"
        >
          撤回访视
        </el-button>
        <el-button :icon="Refresh" @click="fetchSession">刷新</el-button>
      </div>
    </div>

    <template v-if="session">
      <!-- Session info card -->
      <el-card class="info-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span>访视信息</span>
          </div>
        </template>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="访视标签" :span="1">
            {{ session.visitLabel }}
          </el-descriptions-item>
          <el-descriptions-item label="访视日期" :span="1">
            {{ formatDate(session.visitDate) }}
          </el-descriptions-item>
          <el-descriptions-item label="状态" :span="1">
            <el-tag :type="statusType(session.status)" size="small">
              {{ statusLabel(session.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="创建时间" :span="1">
            {{ formatDateTime(session.createdAt) }}
          </el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">
            {{ session.notes || '-' }}
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- Linked subject card -->
      <el-card class="info-card" shadow="never" v-if="session.subject">
        <template #header>
          <div class="card-header">
            <span>关联受试者</span>
            <el-button text type="primary" size="small" @click="goToSubject">
              查看详情
            </el-button>
          </div>
        </template>
        <el-descriptions :column="3" border>
          <el-descriptions-item label="受试者编号" :span="1">
            {{ session.subject.subjectId }}
          </el-descriptions-item>
          <el-descriptions-item label="性别" :span="1">
            {{ session.subject.sex === 'MALE' ? '男' : session.subject.sex === 'FEMALE' ? '女' : session.subject.sex || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="出生日期" :span="1">
            {{ formatDate(session.subject.dateOfBirth) }}
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- Tabs: Assessments / Imaging / Lab tests -->
      <el-card class="tabs-card" shadow="never">
        <el-tabs v-model="activeTab" @tab-change="handleTabChange">
          <!-- Assessments tab -->
          <el-tab-pane label="量表评估" name="assessments">
            <div v-loading="tabLoading.assessments">
              <el-empty v-if="!tabLoading.assessments && assessments.length === 0" description="暂无评估记录" />
              <el-table
                v-else
                :data="assessments"
                stripe
                style="width: 100%"
                size="small"
              >
                <el-table-column prop="instrumentName" label="量表名称" min-width="160" />
                <el-table-column prop="totalScore" label="总分" width="100" align="center">
                  <template #default="{ row }">
                    <span :style="{ fontWeight: 600, color: '#303133' }">
                      {{ row.totalScore !== null && row.totalScore !== undefined ? row.totalScore : '-' }}
                    </span>
                  </template>
                </el-table-column>
                <el-table-column prop="dataEntryStatus" label="状态" width="100" align="center">
                  <template #default="{ row }">
                    <el-tag :type="assessmentStatusType(row.dataEntryStatus)" size="small">
                      {{ assessmentStatusLabel(row.dataEntryStatus) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="assessmentDate" label="评估日期" width="130" align="center">
                  <template #default="{ row }">
                    {{ formatDate(row.assessmentDate) }}
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="100" align="center" fixed="right">
                  <template #default="{ row }">
                    <el-button
                      text
                      type="primary"
                      size="small"
                      @click="router.push({ name: 'ScaleAssessmentDetail', params: { id: row.id } })"
                    >
                      查看
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </el-tab-pane>

          <!-- Imaging tab -->
          <el-tab-pane label="影像数据" name="imaging">
            <div v-loading="tabLoading.imaging">
              <el-empty v-if="!tabLoading.imaging && imaging.length === 0" description="暂无影像数据" />
              <el-table
                v-else
                :data="imaging"
                stripe
                style="width: 100%"
                size="small"
              >
                <el-table-column prop="modalityName" label="模态" width="120" />
                <el-table-column prop="seriesCount" label="序列数" width="100" align="center" />
                <el-table-column prop="qcStatus" label="质控状态" width="110" align="center">
                  <template #default="{ row }">
                    <el-tag :type="qcStatusType(row.qcStatus)" size="small">
                      {{ qcStatusLabel(row.qcStatus) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="100" align="center" fixed="right">
                  <template #default="{ row }">
                    <el-button
                      text
                      type="primary"
                      size="small"
                      @click="router.push({ name: 'ImagingSessionDetail', params: { id: row.id } })"
                    >
                      查看
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </el-tab-pane>

          <!-- Lab tests tab -->
          <el-tab-pane label="检验数据" name="labTests">
            <div v-loading="tabLoading.labTests">
              <el-empty v-if="!tabLoading.labTests && labTests.length === 0" description="暂无检验数据" />
              <el-table
                v-else
                :data="labTests"
                stripe
                style="width: 100%"
                size="small"
              >
                <el-table-column prop="labTestName" label="检验项目" min-width="140" />
                <el-table-column prop="result" label="结果" width="120" align="center">
                  <template #default="{ row }">
                    <span :class="{ 'abnormal-value': row.isAbnormal }">
                      {{ row.result || '-' }}
                    </span>
                  </template>
                </el-table-column>
                <el-table-column prop="referenceRange" label="参考范围" width="140" align="center">
                  <template #default="{ row }">
                    {{ row.referenceRange || '-' }}
                  </template>
                </el-table-column>
                <el-table-column label="异常" width="80" align="center">
                  <template #default="{ row }">
                    <el-tag v-if="row.isAbnormal" type="danger" size="small">异常</el-tag>
                    <el-tag v-else type="success" size="small">正常</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="collectionDate" label="采样日期" width="130" align="center">
                  <template #default="{ row }">
                    {{ formatDate(row.collectionDate) }}
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </el-tab-pane>
        </el-tabs>
      </el-card>
    </template>

    <!-- Error / empty state when session not found -->
    <el-empty v-if="!loading && !session" description="访视不存在或已被删除">
      <el-button type="primary" @click="router.push({ name: 'SubjectList' })">返回列表</el-button>
    </el-empty>
  </div>
</template>

<script lang="ts">
export default {
  name: 'SessionDetailView',
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

.tabs-card {
  :deep(.el-tabs__header) {
    margin-bottom: 12px;
  }

  .abnormal-value {
    font-weight: 700;
    color: #f56c6c;
  }
}
</style>
