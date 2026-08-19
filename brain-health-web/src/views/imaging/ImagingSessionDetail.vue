<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft,
  Refresh,
  Download,
  VideoPlay,
  Check,
  Close,
  Document,
} from '@element-plus/icons-vue'
import { imagingApi, type ImagingSession, type ImagingSeries } from '@/api/modules/imaging'
import http from '@/api/client'

const route = useRoute()
const router = useRouter()

// ---- Core state ----
const sessionId = computed(() => Number(route.params.id))
const loading = ref(false)
const session = ref<ImagingSession | null>(null)
const seriesList = ref<ImagingSeries[]>([])
const seriesLoading = ref(false)

// ---- Thumbnail cache ----
const thumbnailMap = reactive<Record<number, string>>({})
const thumbnailLoading = reactive<Record<number, boolean>>({})
const thumbnailUnavailable = reactive<Record<number, boolean>>({})

// ---- Active tab ----
const activeTab = ref('series')
const dynamicFields = ref<any[]>([])
const dynamicValues = reactive<Record<string, any>>({})
const dynamicSaving = ref(false)

function fieldOptions(field: any): string[] {
  if (Array.isArray(field.options)) return field.options
  try { return JSON.parse(field.options || '[]') } catch { return [] }
}

async function fetchDynamicFields() {
  const [fields, values] = await Promise.all([
    http.get('/api/v1/imaging/dynamic-fields'),
    http.get(`/api/v1/imaging/sessions/${sessionId.value}/dynamic-values`),
  ])
  dynamicFields.value = fields.data.data || []
  Object.assign(dynamicValues, values.data.data || {})
}

async function saveDynamicFields() {
  dynamicSaving.value = true
  try {
    await http.put(`/api/v1/imaging/sessions/${sessionId.value}/dynamic-values`, dynamicValues)
    ElMessage.success('影像扩展字段已保存')
  } finally {
    dynamicSaving.value = false
  }
}

// ---- QC state ----
const qcComment = ref('')
const qcSubmitting = ref(false)
const selectedQcStatus = ref<string>('')
const qcHistory = ref<Array<{ timestamp: string; status: string; comment: string; reviewer: string }>>([])
const qcHistoryLoading = ref(false)

// ---- Preprocessing state ----
const preprocessingSubmitting = ref(false)
const selectedPipeline = ref('')
const preprocessingJobId = ref<string | null>(null)
const preprocessingStatus = ref('')
const preprocessingLogs = ref<string[]>([])
const preprocessingPolling = ref<ReturnType<typeof setInterval> | null>(null)

// ---- BIDS state ----
const bidsConverting = ref(false)

// ---- Download state ----
const downloading = reactive<Record<string, boolean>>({})

// ---- Status helpers ----
type TagType = '' | 'success' | 'warning' | 'danger' | 'info'

const qcStatusMap: Record<string, { label: string; type: TagType }> = {
  PASS: { label: '通过', type: 'success' },
  FAIL: { label: '未通过', type: 'danger' },
  PENDING: { label: '待质控', type: 'warning' },
  UNDER_REVIEW: { label: '审核中', type: 'info' },
}

const preprocessingStatusMap: Record<string, { label: string; type: TagType }> = {
  PENDING: { label: '待处理', type: 'info' },
  QUEUED: { label: '排队中', type: 'info' },
  RUNNING: { label: '运行中', type: 'warning' },
  COMPLETED: { label: '已完成', type: 'success' },
  FAILED: { label: '失败', type: 'danger' },
  CANCELLED: { label: '已取消', type: 'info' },
}

function qcStatusLabel(status: string): string {
  return qcStatusMap[status]?.label || status || '-'
}

function qcStatusType(status: string): TagType {
  return qcStatusMap[status]?.type || 'info'
}

function preprocessingStatusLabel(status: string): string {
  return preprocessingStatusMap[status]?.label || status || '-'
}

function preprocessingStatusType(status: string): TagType {
  return preprocessingStatusMap[status]?.type || 'info'
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
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function formatFloat(val?: number, decimals = 2): string {
  if (val === null || val === undefined) return '-'
  return val.toFixed(decimals)
}

// ---- Data fetching ----
async function fetchSession() {
  loading.value = true
  try {
    const res = await imagingApi.getSession(sessionId.value)
    session.value = res.data.data

    // Set preprocessing state from session
    if (session.value) {
      preprocessingStatus.value = session.value.preprocessingStatus || ''
    }
  } catch {
    // handled by HTTP interceptor
  } finally {
    loading.value = false
  }
}

async function fetchSeries() {
  seriesLoading.value = true
  try {
    const res = await imagingApi.getSeries(sessionId.value)
    seriesList.value = res.data.data || []
  } catch {
    seriesList.value = []
  } finally {
    seriesLoading.value = false
  }
}

async function fetchSeriesThumbnail(seriesId: number) {
  if (thumbnailMap[seriesId] || thumbnailLoading[seriesId] || thumbnailUnavailable[seriesId]) return
  thumbnailLoading[seriesId] = true
  try {
    const res = await imagingApi.getPreview(seriesId)
    const blob = res.data instanceof Blob ? res.data : new Blob([res.data])
    const url = URL.createObjectURL(blob)
    thumbnailMap[seriesId] = url
  } catch {
    // thumbnail not available
    thumbnailMap[seriesId] = ''
    thumbnailUnavailable[seriesId] = true
  } finally {
    thumbnailLoading[seriesId] = false
  }
}

async function fetchQCHistory() {
  qcHistoryLoading.value = true
  try {
    // QC history is typically an array embedded in session or a dedicated endpoint.
    // We attempt to read it from the session response; fall back to empty.
    const raw = (session.value as any)?.qcHistory
    qcHistory.value = Array.isArray(raw) ? raw : []
  } catch {
    qcHistory.value = []
  } finally {
    qcHistoryLoading.value = false
  }
}

// ---- Tab change ----
function handleTabChange(tabName: string) {
  activeTab.value = tabName
  if (tabName === 'series' && seriesList.value.length === 0 && !seriesLoading.value) {
    fetchSeries()
  }
  if (tabName === 'qc' && qcHistory.value.length === 0 && !qcHistoryLoading.value) {
    fetchQCHistory()
  }
}

// ---- Lazy-load visible thumbnails ----
function loadVisibleThumbnails() {
  seriesList.value.forEach((s) => {
    if (s.id && !thumbnailMap[s.id] && !thumbnailLoading[s.id] && !thumbnailUnavailable[s.id]) {
      fetchSeriesThumbnail(s.id)
    }
  })
}

watch(seriesList, (list) => {
  if (list.length > 0) {
    loadVisibleThumbnails()
  }
})

// ---- QC actions ----
async function handleQC(status: string) {
  if (!session.value) return
  const actionLabel = status === 'PASS' ? '通过' : '不通过'
  try {
    await ElMessageBox.confirm(
      `确认将该影像会话标记为质控「${actionLabel}」？`,
      '确认操作',
      {
        confirmButtonText: '确认',
        cancelButtonText: '取消',
        type: status === 'PASS' ? 'success' : 'warning',
      },
    )
  } catch {
    return
  }
  qcSubmitting.value = true
  try {
    // Session-level QC — update via the first series or a dedicated session endpoint.
    // If series exist, update the primary series; otherwise call with session-level data.
    if (seriesList.value.length > 0) {
      const primarySeries = seriesList.value[0]
      await imagingApi.updateQC(primarySeries.id!, status, qcComment.value || undefined)
    }
    // Refresh session data
    await fetchSession()
    qcComment.value = ''
    ElMessage.success(`质控状态已更新为「${actionLabel}」`)
    // Refresh QC history
    fetchQCHistory()
  } catch {
    // handled by HTTP interceptor
  } finally {
    qcSubmitting.value = false
  }
}

// ---- Preprocessing actions ----
async function handleSubmitPreprocessing() {
  if (!selectedPipeline.value) {
    ElMessage.warning('请选择预处理流水线')
    return
  }
  preprocessingSubmitting.value = true
  try {
    const res = await imagingApi.submitPreprocessing(sessionId.value, selectedPipeline.value)
    const jobData = res.data.data || res.data
    preprocessingJobId.value = jobData.jobId || jobData.id
    preprocessingStatus.value = 'QUEUED'
    preprocessingLogs.value = ['预处理任务已提交...']
    ElMessage.success('预处理任务已提交')
    startPreprocessingPolling()
  } catch {
    // handled by HTTP interceptor
  } finally {
    preprocessingSubmitting.value = false
  }
}

function startPreprocessingPolling() {
  stopPreprocessingPolling()
  preprocessingPolling.value = setInterval(async () => {
    if (!preprocessingJobId.value) {
      stopPreprocessingPolling()
      return
    }
    try {
      const res = await imagingApi.getPreprocessingStatus(preprocessingJobId.value)
      const data = res.data.data || res.data
      preprocessingStatus.value = data.status || data.preprocessingStatus || ''
      if (data.logs) {
        preprocessingLogs.value = Array.isArray(data.logs) ? data.logs : [data.logs]
      }
      // Stop polling when terminal
      if (['COMPLETED', 'FAILED', 'CANCELLED'].includes(preprocessingStatus.value)) {
        stopPreprocessingPolling()
        if (preprocessingStatus.value === 'COMPLETED') {
          ElMessage.success('预处理完成')
        } else if (preprocessingStatus.value === 'FAILED') {
          ElMessage.error('预处理失败')
        }
        // Refresh session
        fetchSession()
      }
    } catch {
      // keep polling
    }
  }, 5000)
}

function stopPreprocessingPolling() {
  if (preprocessingPolling.value) {
    clearInterval(preprocessingPolling.value)
    preprocessingPolling.value = null
  }
}

// ---- BIDS export ----
async function handleBIDSExport() {
  bidsConverting.value = true
  try {
    await imagingApi.convertToBIDS(sessionId.value)
    ElMessage.success('BIDS 导出任务已启动，完成后可下载')
  } catch {
    // handled by HTTP interceptor
  } finally {
    bidsConverting.value = false
  }
}

// ---- Downloads ----
async function handleDownloadSeries(seriesId: number, format: string) {
  const key = `${seriesId}_${format}`
  downloading[key] = true
  try {
    const res = await imagingApi.downloadSeries(seriesId)
    const blob = res.data instanceof Blob ? res.data : new Blob([res.data])
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    const series = seriesList.value.find((s) => s.id === seriesId)
    const ext = format === 'nifti' ? '.nii.gz' : '.zip'
    a.download = `series_${seriesId}${series?.seriesDescription ? '_' + series.seriesDescription.replace(/\s+/g, '_') : ''}${ext}`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    ElMessage.success('下载开始')
  } catch {
    // handled by HTTP interceptor
  } finally {
    downloading[key] = false
  }
}

// ---- Navigation ----
function goBack() {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push({ name: 'ImagingSessionList' })
  }
}

function goToSubject() {
  if (session.value?.subjectId) {
    router.push({ name: 'SubjectDetail', params: { id: session.value.subjectId } })
  }
}

const modalities: Record<number, string> = {
  1: 'T1w MRI',
  2: 'T2w MRI',
  3: 'FLAIR',
  4: 'fMRI',
  5: 'DTI',
  6: 'ASL',
  7: 'SWI',
  8: 'CT',
  9: 'PET',
}

function modalityName(id: number): string {
  return (session.value as any)?.modalityName || modalities[id] || `模态 #${id}`
}

// ---- DICOM key tags (derived from series data) ----
const dicomTags = computed(() => {
  if (seriesList.value.length === 0) return []
  // Build a tag summary from the first series, enriched across all series
  const first = seriesList.value[0]
  return [
    { tag: '(0008,0060)', name: 'Modality', value: (session.value as any)?.modalityName || modalities[session.value?.modalityId || 0] || '-' },
    { tag: '(0008,103E)', name: 'Series Description', value: first.seriesDescription || '-' },
    { tag: '(0018,0015)', name: 'Body Part Examined', value: (first as any).bodyPartExamined || (session.value as any)?.bodyPart || '-' },
    { tag: '(0018,0050)', name: 'Slice Thickness (mm)', value: formatFloat(first.sliceThickness) },
    { tag: '(0018,0080)', name: 'Repetition Time (ms)', value: formatFloat(first.repetitionTime) },
    { tag: '(0018,0081)', name: 'Echo Time (ms)', value: formatFloat(first.echoTime) },
    { tag: '(0018,1310)', name: 'Acquisition Matrix', value: (first as any).acquisitionMatrix || '-' },
    { tag: '(0028,0030)', name: 'Pixel Spacing', value: (first as any).pixelSpacing || '-' },
    { tag: '(0018,0087)', name: 'Magnetic Field Strength (T)', value: (session.value as any)?.fieldStrength ? formatFloat((session.value as any).fieldStrength, 1) : '-' },
    { tag: '(0008,0070)', name: 'Manufacturer', value: (session.value as any)?.scannerManufacturer || '-' },
    { tag: '(0008,1090)', name: 'Manufacturer Model', value: (session.value as any)?.scannerModel || '-' },
    { tag: '(0020,000D)', name: 'Study Instance UID', value: (session.value as any)?.studyUid || '-' },
    { tag: '(0020,000E)', name: 'Series Instance UID', value: first.seriesUid || '-' },
    { tag: '(0020,0010)', name: 'Study ID', value: (session.value as any)?.studyId || '-' },
    { tag: '(0020,0011)', name: 'Series Number', value: String(first.seriesNumber) },
  ]
})

// ---- Pipeline options ----
const pipelineOptions = [
  { label: 'fMRIPrep (默认)', value: 'fmriprep' },
  { label: 'MRIQC', value: 'mriqc' },
  { label: 'QSIprep (DTI)', value: 'qsiprep' },
  { label: 'PETPrep', value: 'petprep' },
  { label: 'ASLPrep', value: 'aslprep' },
]

// ---- Lifecycle ----
onMounted(async () => {
  await fetchSession()
  if (session.value) {
    // Eagerly load series
    fetchSeries()
    fetchDynamicFields()
  }
})

onUnmounted(() => {
  stopPreprocessingPolling()
  // Revoke thumbnail object URLs
  Object.values(thumbnailMap).forEach((url) => {
    if (url) URL.revokeObjectURL(url)
  })
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
        <h2 class="page-title">影像检查详情</h2>
        <el-tag :type="qcStatusType(session.qcStatus)" size="large" class="status-tag">
          {{ qcStatusLabel(session.qcStatus) }}
        </el-tag>
        <el-tag
          v-if="session.preprocessingStatus"
          :type="preprocessingStatusType(session.preprocessingStatus)"
          size="large"
          class="status-tag"
        >
          预处理: {{ preprocessingStatusLabel(session.preprocessingStatus) }}
        </el-tag>
      </div>
      <div class="header-actions">
        <el-button
          type="primary"
          :icon="Download"
          :loading="bidsConverting"
          @click="handleBIDSExport"
        >
          BIDS 导出
        </el-button>
        <el-button :icon="Refresh" @click="fetchSession">刷新</el-button>
        <el-button
          v-if="session.subjectId"
          text
          type="primary"
          size="small"
          @click="goToSubject"
        >
          查看受试者
        </el-button>
      </div>
    </div>

    <!-- Session info card -->
    <template v-if="session">
      <el-card class="info-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span>检查信息</span>
          </div>
        </template>
        <el-descriptions :column="3" border>
          <el-descriptions-item label="受试者 ID" :span="1">
            {{ (session as any).subjectId || session.subjectId || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="模态" :span="1">
            <el-tag size="small" type="info">{{ modalityName(session.modalityId) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="采集日期" :span="1">
            {{ formatDate((session as any).acquisitionDate) }}
          </el-descriptions-item>
          <el-descriptions-item label="扫描仪" :span="1">
            {{ (session as any).scannerManufacturer && (session as any).scannerModel ? `${(session as any).scannerManufacturer} ${(session as any).scannerModel}` : ((session as any).scannerName || '-') }}
          </el-descriptions-item>
          <el-descriptions-item label="序列数" :span="1">
            {{ session.seriesCount }}
          </el-descriptions-item>
          <el-descriptions-item label="质控状态" :span="1">
            <el-tag :type="qcStatusType(session.qcStatus)" size="small">
              {{ qcStatusLabel(session.qcStatus) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="预处理状态" :span="1">
            <el-tag
              v-if="session.preprocessingStatus"
              :type="preprocessingStatusType(session.preprocessingStatus)"
              size="small"
            >
              {{ preprocessingStatusLabel(session.preprocessingStatus) }}
            </el-tag>
            <span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item label="文件类型" :span="1">
            {{ (session as any).fileType || 'DICOM' }}
          </el-descriptions-item>
          <el-descriptions-item label="创建时间" :span="1">
            {{ formatDateTime((session as any).createdAt) }}
          </el-descriptions-item>
          <el-descriptions-item label="备注" :span="3">
            {{ session.notes || '-' }}
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- Tabs: Series / DICOM Metadata / QC / Preprocessing -->
      <el-card class="tabs-card" shadow="never">
        <el-tabs v-model="activeTab" @tab-change="handleTabChange">
          <!-- Series tab -->
          <el-tab-pane label="序列列表" name="series">
            <div v-loading="seriesLoading">
              <el-empty
                v-if="!seriesLoading && seriesList.length === 0"
                description="暂无序列数据"
              />
              <div v-else class="series-grid">
                <el-card
                  v-for="series in seriesList"
                  :key="series.id"
                  class="series-card"
                  shadow="hover"
                  :body-style="{ padding: '12px' }"
                >
                  <!-- Thumbnail -->
                  <div class="series-thumbnail">
                    <img
                      v-if="thumbnailMap[series.id!]"
                      :src="thumbnailMap[series.id!]"
                      :alt="series.seriesDescription || '系列预览'"
                      class="thumb-img"
                    />
                    <div
                      v-else-if="thumbnailLoading[series.id!]"
                      class="thumb-placeholder"
                    >
                      <el-icon class="is-loading" :size="28"><Refresh /></el-icon>
                    </div>
                    <div v-else class="thumb-placeholder">
                      <el-icon :size="32"><VideoPlay /></el-icon>
                      <span class="thumb-label">无预览</span>
                    </div>
                  </div>

                  <!-- Series info -->
                  <div class="series-info">
                    <div class="series-title" :title="series.seriesDescription">
                      {{ series.seriesDescription || `系列 #${series.seriesNumber}` }}
                    </div>
                    <div class="series-meta">
                      <el-tag size="small" type="info">
                        {{ series.sequenceName || series.fileType || 'DICOM' }}
                      </el-tag>
                      <span class="meta-text">{{ series.numberOfFiles }} 张</span>
                      <el-tag
                        :type="qcStatusType(series.qcStatus)"
                        size="small"
                      >
                        {{ qcStatusLabel(series.qcStatus) }}
                      </el-tag>
                    </div>
                    <div class="series-meta" v-if="series.sliceThickness || series.echoTime || series.repetitionTime">
                      <span v-if="series.sliceThickness" class="meta-text">
                        层厚: {{ series.sliceThickness }} mm
                      </span>
                      <span v-if="series.echoTime" class="meta-text">
                        TE: {{ series.echoTime }} ms
                      </span>
                      <span v-if="series.repetitionTime" class="meta-text">
                        TR: {{ series.repetitionTime }} ms
                      </span>
                    </div>
                  </div>

                  <!-- Actions -->
                  <div class="series-actions">
                    <el-button
                      size="small"
                      text
                      type="primary"
                      :icon="Download"
                      :loading="downloading[`${series.id}_dicom`]"
                      @click="handleDownloadSeries(series.id!, 'dicom')"
                    >
                      DICOM
                    </el-button>
                    <el-button
                      size="small"
                      text
                      type="primary"
                      :icon="Download"
                      :loading="downloading[`${series.id}_nifti`]"
                      @click="handleDownloadSeries(series.id!, 'nifti')"
                    >
                      NIfTI
                    </el-button>
                    <el-button
                      size="small"
                      text
                      type="primary"
                      :icon="Refresh"
                      @click="fetchSeriesThumbnail(series.id!)"
                    >
                      预览
                    </el-button>
                  </div>
                </el-card>
              </div>
            </div>
          </el-tab-pane>

          <!-- DICOM Metadata tab -->
          <el-tab-pane label="DICOM 元数据" name="dicom">
            <el-empty
              v-if="dicomTags.length === 0"
              description="暂无 DICOM 元数据"
            />
            <el-table
              v-else
              :data="dicomTags"
              stripe
              size="small"
              style="width: 100%"
              max-height="520"
            >
              <el-table-column prop="tag" label="Tag" width="160">
                <template #default="{ row }">
                  <code class="dicom-tag-code">{{ row.tag }}</code>
                </template>
              </el-table-column>
              <el-table-column prop="name" label="名称" min-width="200" />
              <el-table-column prop="value" label="值" min-width="200">
                <template #default="{ row }">
                  <span class="dicom-tag-value">{{ row.value }}</span>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="扩展字段" name="dynamic">
            <el-empty v-if="!dynamicFields.length" description="管理员尚未发布影像扩展字段" />
            <el-form v-else label-width="180px" style="max-width: 760px">
              <el-form-item
                v-for="field in dynamicFields"
                :key="field.id"
                :label="field.label"
                :required="Boolean(field.requiredFlag)"
              >
                <el-input-number
                  v-if="field.fieldType === 'NUMBER'"
                  v-model="dynamicValues[field.fieldCode]"
                  style="width: 100%"
                />
                <el-date-picker
                  v-else-if="field.fieldType === 'DATE'"
                  v-model="dynamicValues[field.fieldCode]"
                  value-format="YYYY-MM-DD"
                  style="width: 100%"
                />
                <el-select
                  v-else-if="['SELECT', 'MULTI_SELECT'].includes(field.fieldType)"
                  v-model="dynamicValues[field.fieldCode]"
                  :multiple="field.fieldType === 'MULTI_SELECT'"
                  style="width: 100%"
                >
                  <el-option v-for="option in fieldOptions(field)" :key="option" :label="option" :value="option" />
                </el-select>
                <el-switch
                  v-else-if="field.fieldType === 'BOOLEAN'"
                  v-model="dynamicValues[field.fieldCode]"
                />
                <el-input
                  v-else
                  v-model="dynamicValues[field.fieldCode]"
                  :type="field.fieldType === 'TEXTAREA' ? 'textarea' : 'text'"
                />
              </el-form-item>
              <el-button type="primary" :loading="dynamicSaving" @click="saveDynamicFields">保存扩展字段</el-button>
            </el-form>
          </el-tab-pane>

          <!-- QC tab -->
          <el-tab-pane label="质控" name="qc">
            <div class="qc-section">
              <!-- QC action panel -->
              <el-card shadow="never" class="qc-action-card">
                <template #header>
                  <span class="section-title">质控操作</span>
                </template>
                <div class="qc-action-body">
                  <div class="qc-buttons">
                    <el-button
                      type="success"
                      :icon="Check"
                      :loading="qcSubmitting"
                      :disabled="session.qcStatus === 'PASS'"
                      @click="handleQC('PASS')"
                    >
                      通过
                    </el-button>
                    <el-button
                      type="danger"
                      :icon="Close"
                      :loading="qcSubmitting"
                      :disabled="session.qcStatus === 'FAIL'"
                      @click="handleQC('FAIL')"
                    >
                      不通过
                    </el-button>
                  </div>
                  <div class="qc-comment">
                    <label class="qc-label">质控备注</label>
                    <el-input
                      v-model="qcComment"
                      type="textarea"
                      :rows="3"
                      placeholder="请输入质控意见或备注..."
                      maxlength="500"
                      show-word-limit
                    />
                  </div>
                </div>
              </el-card>

              <!-- QC history -->
              <el-card shadow="never" class="qc-history-card">
                <template #header>
                  <div class="card-header-with-action">
                    <span class="section-title">质控历史</span>
                    <el-button
                      text
                      size="small"
                      :icon="Refresh"
                      :loading="qcHistoryLoading"
                      @click="fetchQCHistory"
                    >
                      刷新
                    </el-button>
                  </div>
                </template>
                <el-empty
                  v-if="!qcHistoryLoading && qcHistory.length === 0"
                  description="暂无质控记录"
                />
                <el-table
                  v-else
                  :data="qcHistory"
                  stripe
                  size="small"
                  style="width: 100%"
                >
                  <el-table-column label="时间" width="180">
                    <template #default="{ row }">
                      {{ formatDateTime(row.timestamp) }}
                    </template>
                  </el-table-column>
                  <el-table-column label="状态" width="100">
                    <template #default="{ row }">
                      <el-tag :type="qcStatusType(row.status)" size="small">
                        {{ qcStatusLabel(row.status) }}
                      </el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column label="审核人" min-width="120">
                    <template #default="{ row }">
                      {{ row.reviewer || '-' }}
                    </template>
                  </el-table-column>
                  <el-table-column label="备注" min-width="200">
                    <template #default="{ row }">
                      {{ row.comment || '-' }}
                    </template>
                  </el-table-column>
                </el-table>
              </el-card>
            </div>
          </el-tab-pane>

          <!-- Preprocessing tab -->
          <el-tab-pane label="预处理" name="preprocessing">
            <div class="preprocessing-section">
              <!-- Status -->
              <el-card shadow="never" class="preprocessing-status-card">
                <template #header>
                  <span class="section-title">预处理状态</span>
                </template>
                <el-descriptions :column="2" border>
                  <el-descriptions-item label="当前状态" :span="1">
                    <el-tag
                      v-if="preprocessingStatus"
                      :type="preprocessingStatusType(preprocessingStatus)"
                      size="small"
                    >
                      {{ preprocessingStatusLabel(preprocessingStatus) }}
                    </el-tag>
                    <span v-else>-</span>
                  </el-descriptions-item>
                  <el-descriptions-item label="任务 ID" :span="1">
                    {{ preprocessingJobId || '-' }}
                  </el-descriptions-item>
                </el-descriptions>
              </el-card>

              <!-- Submit -->
              <el-card shadow="never" class="preprocessing-submit-card">
                <template #header>
                  <span class="section-title">提交预处理</span>
                </template>
                <div class="preprocessing-submit-body">
                  <el-select
                    v-model="selectedPipeline"
                    placeholder="选择预处理流水线"
                    style="width: 300px"
                  >
                    <el-option
                      v-for="opt in pipelineOptions"
                      :key="opt.value"
                      :label="opt.label"
                      :value="opt.value"
                    />
                  </el-select>
                  <el-button
                    type="primary"
                    :loading="preprocessingSubmitting"
                    :disabled="!selectedPipeline"
                    @click="handleSubmitPreprocessing"
                  >
                    提交任务
                  </el-button>
                </div>
              </el-card>

              <!-- Logs -->
              <el-card shadow="never" class="preprocessing-logs-card">
                <template #header>
                  <div class="card-header-with-action">
                    <span class="section-title">预处理日志</span>
                    <el-button
                      v-if="preprocessingPolling"
                      text
                      size="small"
                      type="warning"
                      @click="stopPreprocessingPolling"
                    >
                      停止轮询
                    </el-button>
                  </div>
                </template>
                <el-empty
                  v-if="preprocessingLogs.length === 0"
                  description="暂无日志"
                />
                <div v-else class="logs-container">
                  <div
                    v-for="(log, idx) in preprocessingLogs"
                    :key="idx"
                    class="log-line"
                  >
                    <span class="log-index">{{ idx + 1 }}</span>
                    <span class="log-text">{{ log }}</span>
                  </div>
                </div>
              </el-card>
            </div>
          </el-tab-pane>
        </el-tabs>
      </el-card>

      <!-- Download all options -->
      <el-card shadow="never" class="download-card">
        <template #header>
          <div class="card-header">
            <span>数据下载</span>
          </div>
        </template>
        <div class="download-options">
          <el-button
            type="primary"
            :icon="Download"
            :disabled="seriesList.length === 0"
            @click="seriesList.forEach(s => handleDownloadSeries(s.id!, 'nifti'))"
          >
            全部 NIfTI 下载
          </el-button>
          <el-button
            type="primary"
            :icon="Download"
            :disabled="seriesList.length === 0"
            @click="seriesList.forEach(s => handleDownloadSeries(s.id!, 'dicom'))"
          >
            全部 DICOM 下载
          </el-button>
          <el-button
            type="success"
            :icon="Document"
            :loading="bidsConverting"
            @click="handleBIDSExport"
          >
            BIDS 导出
          </el-button>
        </div>
      </el-card>
    </template>

    <!-- Empty / error state -->
    <el-empty
      v-if="!loading && !session"
      description="影像检查不存在或已被删除"
    >
      <template #extra>
        <el-button type="primary" @click="router.push({ name: 'ImagingSessionList' })">
          返回影像列表
        </el-button>
      </template>
    </el-empty>
  </div>
</template>

<script lang="ts">
export default {
  name: 'ImagingSessionDetail',
}
</script>

<style scoped lang="scss">
.page-container {
  max-width: 1200px;
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
    width: 100px;
    font-weight: 500;
  }
}

.tabs-card {
  margin-bottom: 16px;

  :deep(.el-tabs__header) {
    margin-bottom: 12px;
  }
}

// ---- Series grid ----
.series-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

.series-card {
  display: flex;
  flex-direction: column;

  :deep(.el-card__body) {
    display: flex;
    flex-direction: column;
    gap: 10px;
    flex: 1;
  }
}

.series-thumbnail {
  width: 100%;
  height: 180px;
  background: #f5f7fa;
  border-radius: 6px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;

  .thumb-img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  .thumb-placeholder {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 6px;
    color: #a8abb2;

    .thumb-label {
      font-size: 12px;
    }
  }
}

.series-info {
  display: flex;
  flex-direction: column;
  gap: 6px;

  .series-title {
    font-size: 14px;
    font-weight: 600;
    color: #303133;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .series-meta {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 6px;

    .meta-text {
      font-size: 12px;
      color: #909399;
    }
  }
}

.series-actions {
  display: flex;
  justify-content: flex-start;
  gap: 4px;
  margin-top: auto;
  padding-top: 4px;
  border-top: 1px solid #ebeef5;
}

// ---- DICOM ----
.dicom-tag-code {
  font-family: 'Courier New', Courier, monospace;
  font-size: 12px;
  background: #f5f7fa;
  padding: 2px 6px;
  border-radius: 3px;
  color: #606266;
}

.dicom-tag-value {
  word-break: break-all;
}

// ---- QC ----
.qc-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.qc-action-card,
.qc-history-card {
  .section-title {
    font-size: 15px;
    font-weight: 600;
    color: #303133;
  }
}

.qc-action-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.qc-buttons {
  display: flex;
  gap: 12px;
}

.qc-comment {
  display: flex;
  flex-direction: column;
  gap: 6px;

  .qc-label {
    font-size: 13px;
    color: #606266;
    font-weight: 500;
  }
}

.card-header-with-action {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

// ---- Preprocessing ----
.preprocessing-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.preprocessing-submit-body {
  display: flex;
  align-items: center;
  gap: 12px;
}

.preprocessing-logs-card {
  .section-title {
    font-size: 15px;
    font-weight: 600;
    color: #303133;
  }
}

.logs-container {
  max-height: 400px;
  overflow-y: auto;
  background: #1e1e1e;
  color: #d4d4d4;
  border-radius: 6px;
  padding: 12px;
  font-family: 'Courier New', Courier, monospace;
  font-size: 12px;
  line-height: 1.7;

  .log-line {
    display: flex;
    gap: 10px;

    .log-index {
      color: #858585;
      min-width: 30px;
      text-align: right;
      user-select: none;
    }

    .log-text {
      word-break: break-all;
      white-space: pre-wrap;
    }
  }
}

// ---- Download ----
.download-card {
  margin-bottom: 16px;

  .card-header {
    font-size: 16px;
    font-weight: 600;
    color: #303133;
  }
}

.download-options {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}
</style>
