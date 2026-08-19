<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowLeft,
  ZoomIn,
  ZoomOut,
  RefreshLeft,
  RefreshRight,
  DArrowLeft,
  DArrowRight,
  InfoFilled,
  Cpu,
} from '@element-plus/icons-vue'
import { imagingApi, type ImagingSession, type ImagingSeries } from '@/api/modules/imaging'

const route = useRoute()
const router = useRouter()

// ---- Core state ----
const sessionId = computed(() => Number(route.params.id))
const session = ref<ImagingSession | null>(null)
const seriesList = ref<ImagingSeries[]>([])
const loading = ref(false)

// ---- Viewer state ----
const currentSeriesId = ref<number | null>(null)
const selectedSeries = computed(() =>
  seriesList.value.find((s) => s.id === currentSeriesId.value) ?? null
)

const previewUrl = ref('')
const previewLoading = ref(false)
const previewError = ref('')

// Display settings
const zoomLevel = ref(100)
const windowCenter = ref(40)
const windowWidth = ref(80)
const currentSlice = ref(0)
const totalSlices = ref(0)
const isFlippedH = ref(false)
const isFlippedV = ref(false)

// ---- UI state ----
const metadataPanelVisible = ref(true)
const activeViewMode = ref<'axial' | 'coronal' | 'sagittal'>('axial')

// ---- Loading data ----
async function fetchSession() {
  loading.value = true
  try {
    const res = await imagingApi.getSession(sessionId.value)
    session.value = res.data.data
  } catch {
    // handled by HTTP interceptor
  } finally {
    loading.value = false
  }
}

async function fetchSeries() {
  try {
    const res = await imagingApi.getSeries(sessionId.value)
    seriesList.value = res.data.data ?? []
    if (seriesList.value.length > 0 && currentSeriesId.value === null) {
      currentSeriesId.value = seriesList.value[0].id!
      updateSeriesInfo(seriesList.value[0])
    }
  } catch {
    seriesList.value = []
  }
}

async function updateSeriesInfo(series: ImagingSeries) {
  totalSlices.value = series.numberOfFiles || 1
  currentSlice.value = Math.floor(totalSlices.value / 2)
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = ''
  previewError.value = ''
  previewLoading.value = true
  try {
    const response = await imagingApi.getPreview(series.id!)
    previewUrl.value = URL.createObjectURL(response.data)
  } catch {
    previewError.value = '该序列尚未生成可查看的预览图，请先完成影像导入或预处理。'
  } finally {
    previewLoading.value = false
  }
}

// ---- Series selection ----
async function selectSeries(seriesId: number) {
  currentSeriesId.value = seriesId
  const s = seriesList.value.find((s) => s.id === seriesId)
  if (s) await updateSeriesInfo(s)
}

// ---- Toolbar actions (simulated) ----
function zoomIn() {
  zoomLevel.value = Math.min(500, zoomLevel.value + 10)
}

function zoomOut() {
  zoomLevel.value = Math.max(10, zoomLevel.value - 10)
}

function resetZoom() {
  zoomLevel.value = 100
}

function prevSlice() {
  if (currentSlice.value > 0) currentSlice.value--
}

function nextSlice() {
  if (currentSlice.value < totalSlices.value - 1) currentSlice.value++
}

function goToFirstSlice() {
  currentSlice.value = 0
}

function goToLastSlice() {
  currentSlice.value = totalSlices.value - 1
}

function toggleFlipH() {
  isFlippedH.value = !isFlippedH.value
}

function toggleFlipV() {
  isFlippedV.value = !isFlippedV.value
}

function setViewMode(mode: 'axial' | 'coronal' | 'sagittal') {
  activeViewMode.value = mode
}

// ---- Window/Level presets ----
const windowLevelPresets = [
  { label: '脑窗 (Brain)', center: 40, width: 80 },
  { label: '骨窗 (Bone)', center: 300, width: 1500 },
  { label: '肺窗 (Lung)', center: -500, width: 1500 },
  { label: '软组织 (Soft)', center: 50, width: 350 },
  { label: '默认', center: 128, width: 256 },
]

function applyPreset(preset: { label: string; center: number; width: number }) {
  windowCenter.value = preset.center
  windowWidth.value = preset.width
  ElMessage.success(`已应用窗宽窗位: ${preset.label}`)
}

// ---- Navigation ----
function goBack() {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push({ name: 'ImagingSessionList' })
  }
}

function goToSessionDetail() {
  router.push({ name: 'ImagingSessionDetail', params: { id: sessionId.value } })
}

// ---- Thumbnail for series selector ----
function seriesThumbnailLabel(series: ImagingSeries): string {
  return series.seriesDescription || series.sequenceName || `序列 ${series.seriesNumber}`
}

// ---- Init ----
onMounted(async () => {
  await fetchSession()
  await fetchSeries()
})

onBeforeUnmount(() => {
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
})
</script>

<template>
  <div class="imaging-viewer" v-loading="loading">
    <!-- Top bar -->
    <div class="viewer-topbar">
      <div class="topbar-left">
        <el-button text :icon="ArrowLeft" @click="goBack">返回</el-button>
        <el-divider direction="vertical" />
        <span class="topbar-title" v-if="session">
          影像查看器 - {{ session.modalityId ? '模态 ' + session.modalityId : '' }}
        </span>
      </div>
      <div class="topbar-right">
        <el-button
          size="small"
          :icon="InfoFilled"
          @click="metadataPanelVisible = !metadataPanelVisible"
        >
          {{ metadataPanelVisible ? '隐藏元数据' : '显示元数据' }}
        </el-button>
        <el-button size="small" @click="goToSessionDetail">返回详情</el-button>
      </div>
    </div>

    <template v-if="session">
      <!-- Notice banner -->
      <el-alert
        title="完整功能查看器即将上线"
        type="info"
        :closable="false"
        show-icon
        class="viewer-notice"
      >
        <template #default>
          <span>
            当前为功能预览版本。未来将集成 Papaya / OHIF Viewer 实现完整的 DICOM / NIfTI 图像查看功能，包括窗宽窗位调节、多平面重建 (MPR)、三维渲染、测量标注工具等。
          </span>
        </template>
      </el-alert>

      <!-- Main viewer area -->
      <div class="viewer-main">
        <!-- Left: Series list sidebar -->
        <div class="series-sidebar">
          <div class="sidebar-header">
            <span class="sidebar-title">序列列表</span>
            <el-tag size="small" type="info">{{ seriesList.length }} 个序列</el-tag>
          </div>
          <el-empty
            v-if="seriesList.length === 0"
            description="暂无序列"
            :image-size="60"
          />
          <div class="series-list" v-else>
            <div
              v-for="series in seriesList"
              :key="series.id"
              class="series-item"
              :class="{ active: currentSeriesId === series.id }"
              @click="selectSeries(series.id!)"
            >
              <div class="series-thumb">
                <el-icon :size="28"><Cpu /></el-icon>
              </div>
              <div class="series-info">
                <div class="series-name">{{ seriesThumbnailLabel(series) }}</div>
                <div class="series-meta">
                  <span>{{ series.numberOfFiles }} 帧</span>
                  <span v-if="series.sliceThickness">{{ series.sliceThickness }}mm</span>
                  <span>{{ series.fileType?.toUpperCase() }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Center: Viewer canvas + toolbar -->
        <div class="viewer-center">
          <!-- Toolbar -->
          <div class="viewer-toolbar">
            <!-- Window/Level presets -->
            <div class="toolbar-group">
              <span class="toolbar-label">窗宽窗位</span>
              <el-select
                :model-value="`${windowCenter}/${windowWidth}`"
                placeholder="选择预设"
                size="small"
                style="width: 180px"
                @change="(val: string) => {
                  const preset = windowLevelPresets.find(p => `${p.center}/${p.width}` === val)
                  if (preset) applyPreset(preset)
                }"
              >
                <el-option
                  v-for="preset in windowLevelPresets"
                  :key="preset.label"
                  :label="`${preset.label} (C:${preset.center} W:${preset.width})`"
                  :value="`${preset.center}/${preset.width}`"
                />
              </el-select>
            </div>

            <el-divider direction="vertical" />

            <!-- Zoom -->
            <div class="toolbar-group">
              <span class="toolbar-label">缩放</span>
              <el-button-group size="small">
                <el-button :icon="ZoomOut" @click="zoomOut" />
                <el-button disabled style="min-width: 56px">{{ zoomLevel }}%</el-button>
                <el-button :icon="ZoomIn" @click="zoomIn" />
              </el-button-group>
              <el-button size="small" @click="resetZoom">重置</el-button>
            </div>

            <el-divider direction="vertical" />

            <!-- Pan / flip -->
            <div class="toolbar-group">
              <span class="toolbar-label">方向</span>
              <el-button-group size="small">
                <el-button
                  :icon="RefreshRight"
                  :type="isFlippedH ? 'primary' : 'default'"
                  @click="toggleFlipH"
                  title="水平翻转"
                />
                <el-button
                  :icon="RefreshLeft"
                  :type="isFlippedV ? 'primary' : 'default'"
                  @click="toggleFlipV"
                  title="垂直翻转"
                />
              </el-button-group>
            </div>

            <el-divider direction="vertical" />

            <!-- View mode -->
            <div class="toolbar-group">
              <span class="toolbar-label">视图</span>
              <el-radio-group
                v-model="activeViewMode"
                size="small"
                @change="(val: string) => setViewMode(val as 'axial' | 'coronal' | 'sagittal')"
              >
                <el-radio-button value="axial">横断</el-radio-button>
                <el-radio-button value="coronal">冠状</el-radio-button>
                <el-radio-button value="sagittal">矢状</el-radio-button>
              </el-radio-group>
            </div>

            <el-divider direction="vertical" />

            <!-- Slice navigation -->
            <div class="toolbar-group">
              <span class="toolbar-label">切片</span>
              <el-button-group size="small">
                <el-button :icon="DArrowLeft" @click="goToFirstSlice" title="第一层" />
                <el-button :icon="ArrowLeft" @click="prevSlice" title="上一层" />
                <el-button disabled style="min-width: 70px">
                  {{ currentSlice + 1 }} / {{ totalSlices }}
                </el-button>
                <el-button :icon="ArrowLeft" style="transform: rotate(180deg)" @click="nextSlice" title="下一层" />
                <el-button :icon="DArrowRight" @click="goToLastSlice" title="最后一层" />
              </el-button-group>
              <el-slider
                v-model="currentSlice"
                :min="0"
                :max="totalSlices - 1"
                :step="1"
                :show-tooltip="false"
                style="width: 120px; margin-left: 8px"
                :disabled="totalSlices <= 1"
              />
            </div>
          </div>

          <!-- Canvas area -->
          <div class="canvas-wrapper">
            <div
              class="canvas-area"
              :style="{
                transform: `scale(${zoomLevel / 100})`,
                transformOrigin: 'center center',
              }"
            >
              <div class="canvas-placeholder" v-loading="previewLoading">
                <img
                  v-if="previewUrl"
                  :src="previewUrl"
                  :alt="selectedSeries ? seriesThumbnailLabel(selectedSeries) : '影像预览'"
                  class="medical-preview"
                  :style="{
                    transform: `scaleX(${isFlippedH ? -1 : 1}) scaleY(${isFlippedV ? -1 : 1})`,
                    filter: `contrast(${Math.max(0.2, 80 / Math.max(1, windowWidth))}) brightness(${Math.max(0.2, windowCenter / 40)})`,
                  }"
                />
                <div v-else class="placeholder-text">
                  <p class="placeholder-title">
                    {{ previewError || (selectedSeries ? seriesThumbnailLabel(selectedSeries) : '请选择序列') }}
                  </p>
                  <p class="placeholder-sub" v-if="selectedSeries">
                    切片 {{ currentSlice + 1 }} / {{ totalSlices }}
                    &nbsp;|&nbsp;
                    视图: {{ activeViewMode === 'axial' ? '横断面' : activeViewMode === 'coronal' ? '冠状面' : '矢状面' }}
                    &nbsp;|&nbsp;
                    窗宽窗位: C{{ windowCenter }} / W{{ windowWidth }}
                  </p>
                </div>

                <div class="orientation-marker marker-top">A</div>
                <div class="orientation-marker marker-bottom">P</div>
                <div class="orientation-marker marker-left">R</div>
                <div class="orientation-marker marker-right">L</div>

              </div>
            </div>
          </div>
        </div>

        <!-- Right: Metadata panel (collapsible) -->
        <transition name="slide-panel">
          <div class="metadata-panel" v-show="metadataPanelVisible">
            <div class="panel-header">
              <span class="panel-title">元数据</span>
            </div>
            <div class="panel-body">
              <template v-if="selectedSeries">
                <el-descriptions :column="1" size="small" border>
                  <el-descriptions-item label="序列描述">
                    {{ selectedSeries.seriesDescription || '-' }}
                  </el-descriptions-item>
                  <el-descriptions-item label="序列号">
                    {{ selectedSeries.seriesNumber }}
                  </el-descriptions-item>
                  <el-descriptions-item label="序列 UID" :span="1">
                    <span class="uid-text">{{ selectedSeries.seriesUid || '-' }}</span>
                  </el-descriptions-item>
                  <el-descriptions-item label="序列名称">
                    {{ selectedSeries.sequenceName || '-' }}
                  </el-descriptions-item>
                  <el-descriptions-item label="文件数量">
                    {{ selectedSeries.numberOfFiles }}
                  </el-descriptions-item>
                  <el-descriptions-item label="文件类型">
                    {{ selectedSeries.fileType?.toUpperCase() || '-' }}
                  </el-descriptions-item>
                  <el-descriptions-item label="层厚 (mm)">
                    {{ selectedSeries.sliceThickness ?? '-' }}
                  </el-descriptions-item>
                  <el-descriptions-item label="回波时间 TE (ms)">
                    {{ selectedSeries.echoTime ?? '-' }}
                  </el-descriptions-item>
                  <el-descriptions-item label="重复时间 TR (ms)">
                    {{ selectedSeries.repetitionTime ?? '-' }}
                  </el-descriptions-item>
                  <el-descriptions-item label="质控状态">
                    <el-tag
                      :type="selectedSeries.qcStatus === 'PASS' ? 'success' : selectedSeries.qcStatus === 'FAIL' ? 'danger' : selectedSeries.qcStatus === 'PENDING' ? 'warning' : 'info'"
                      size="small"
                    >
                      {{ selectedSeries.qcStatus }}
                    </el-tag>
                  </el-descriptions-item>
                </el-descriptions>

                <!-- Session-level metadata -->
                <el-divider content-position="left" style="margin: 16px 0 10px">
                  <span style="font-size: 13px; color: #909399">检查信息</span>
                </el-divider>
                <el-descriptions :column="1" size="small" border>
                  <el-descriptions-item label="采集日期">
                    {{ session.acquisitionDate || '-' }}
                  </el-descriptions-item>
                  <el-descriptions-item label="扫描仪 ID">
                    {{ session.scannerId || '-' }}
                  </el-descriptions-item>
                  <el-descriptions-item label="预处理状态">
                    <el-tag
                      :type="session.preprocessingStatus === 'COMPLETED' ? 'success' : session.preprocessingStatus === 'RUNNING' ? 'warning' : 'info'"
                      size="small"
                    >
                      {{ session.preprocessingStatus || '-' }}
                    </el-tag>
                  </el-descriptions-item>
                  <el-descriptions-item label="质控状态">
                    <el-tag
                      :type="session.qcStatus === 'PASS' ? 'success' : session.qcStatus === 'FAIL' ? 'danger' : session.qcStatus === 'PENDING' ? 'warning' : 'info'"
                      size="small"
                    >
                      {{ session.qcStatus }}
                    </el-tag>
                  </el-descriptions-item>
                  <el-descriptions-item label="备注">
                    {{ session.notes || '-' }}
                  </el-descriptions-item>
                </el-descriptions>
              </template>
              <el-empty v-else description="请选择一个序列" :image-size="60" />
            </div>

            <!-- Current view info -->
            <div class="panel-footer" v-if="selectedSeries">
              <div class="view-info-item">
                <span class="view-info-label">窗宽窗位</span>
                <span class="view-info-value">C:{{ windowCenter }} / W:{{ windowWidth }}</span>
              </div>
              <div class="view-info-item">
                <span class="view-info-label">缩放</span>
                <span class="view-info-value">{{ zoomLevel }}%</span>
              </div>
              <div class="view-info-item">
                <span class="view-info-label">当前切片</span>
                <span class="view-info-value">{{ currentSlice + 1 }} / {{ totalSlices }}</span>
              </div>
              <div class="view-info-item">
                <span class="view-info-label">视图平面</span>
                <span class="view-info-value">{{ activeViewMode === 'axial' ? '横断面' : activeViewMode === 'coronal' ? '冠状面' : '矢状面' }}</span>
              </div>
            </div>
          </div>
        </transition>
      </div>
    </template>

    <!-- Empty / error state -->
    <el-empty
      v-if="!loading && !session"
      description="影像检查不存在或已被删除"
    >
      <el-button type="primary" @click="router.push({ name: 'ImagingSessionList' })">
        返回列表
      </el-button>
    </el-empty>
  </div>
</template>

<script lang="ts">
export default {
  name: 'ImagingViewer',
}
</script>

<style scoped lang="scss">
.imaging-viewer {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 60px - 40px - 40px); // header - footer - layout padding
  min-height: 600px;
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}

// ---- Top bar ----
.viewer-topbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 16px;
  border-bottom: 1px solid #ebeef5;
  background: #fafafa;
  flex-shrink: 0;

  .topbar-left {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .topbar-title {
    font-size: 15px;
    font-weight: 600;
    color: #303133;
  }

  .topbar-right {
    display: flex;
    gap: 8px;
  }
}

// ---- Notice banner ----
.viewer-notice {
  margin: 0;
  border-radius: 0;
  flex-shrink: 0;

  :deep(.el-alert__content) {
    font-size: 13px;
  }
}

// ---- Main area (series sidebar + canvas + metadata) ----
.viewer-main {
  display: flex;
  flex: 1;
  overflow: hidden;
  min-height: 0;
}

// ---- Series sidebar ----
.series-sidebar {
  width: 200px;
  flex-shrink: 0;
  border-right: 1px solid #ebeef5;
  display: flex;
  flex-direction: column;
  background: #fafafa;

  .sidebar-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 12px;
    border-bottom: 1px solid #ebeef5;
  }

  .sidebar-title {
    font-size: 14px;
    font-weight: 600;
    color: #303133;
  }

  .series-list {
    flex: 1;
    overflow-y: auto;
    padding: 4px;
  }

  .series-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 10px;
    border-radius: 6px;
    cursor: pointer;
    transition: background 0.15s;

    &:hover {
      background: #ecf5ff;
    }

    &.active {
      background: #d9ecff;
      border: 1px solid #a0cfff;
    }
  }

  .series-thumb {
    width: 44px;
    height: 44px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: #e8e8e8;
    border-radius: 4px;
    color: #909399;
    flex-shrink: 0;
  }

  .series-info {
    min-width: 0;
    flex: 1;
  }

  .series-name {
    font-size: 13px;
    font-weight: 500;
    color: #303133;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .series-meta {
    display: flex;
    gap: 6px;
    font-size: 11px;
    color: #909399;
    margin-top: 3px;
  }
}

// ---- Viewer center ----
.viewer-center {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
}

// ---- Toolbar ----
.viewer-toolbar {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 12px;
  border-bottom: 1px solid #ebeef5;
  background: #fafafa;
  flex-wrap: wrap;
  flex-shrink: 0;

  .toolbar-group {
    display: flex;
    align-items: center;
    gap: 4px;
  }

  .toolbar-label {
    font-size: 12px;
    color: #909399;
    margin-right: 2px;
    white-space: nowrap;
  }

  :deep(.el-divider--vertical) {
    height: 20px;
    margin: 0 8px;
  }
}

// ---- Canvas area ----
.canvas-wrapper {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #000;
  overflow: hidden;
  position: relative;
}

.canvas-area {
  transition: transform 0.15s ease;
  position: relative;
}

.canvas-placeholder {
  width: 512px;
  height: 512px;
  background: #1a1a2e;
  border: 2px solid #2a2a4a;
  border-radius: 4px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
}

.medical-preview {
  display: block;
  max-width: 92%;
  max-height: 82vh;
  object-fit: contain;
  transition: transform 0.15s ease, filter 0.15s ease;
}

.placeholder-icon {
  color: #3a3a5a;
  margin-bottom: 16px;
}

.placeholder-text {
  text-align: center;
  padding: 0 20px;

  .placeholder-title {
    font-size: 16px;
    font-weight: 600;
    color: #c0c0d0;
    margin: 0 0 8px;
  }

  .placeholder-sub {
    font-size: 13px;
    color: #8888a0;
    margin: 0 0 8px;
  }

  .placeholder-hint {
    font-size: 12px;
    color: #606080;
    margin: 0;
    font-style: italic;
  }
}

// Orientation markers
.orientation-marker {
  position: absolute;
  color: #ffd700;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 0.05em;

  &.marker-top {
    top: 10px;
    left: 50%;
    transform: translateX(-50%);
  }

  &.marker-bottom {
    bottom: 10px;
    left: 50%;
    transform: translateX(-50%);
  }

  &.marker-left {
    left: 10px;
    top: 50%;
    transform: translateY(-50%);
  }

  &.marker-right {
    right: 10px;
    top: 50%;
    transform: translateY(-50%);
  }
}

// Simulated grid overlay
.simulated-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.03) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.03) 1px, transparent 1px);
  background-size: 32px 32px;
  pointer-events: none;
}

// ---- Metadata panel ----
.metadata-panel {
  width: 280px;
  flex-shrink: 0;
  border-left: 1px solid #ebeef5;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #fafafa;

  .panel-header {
    padding: 12px 16px;
    border-bottom: 1px solid #ebeef5;
    background: #f5f7fa;
  }

  .panel-title {
    font-size: 14px;
    font-weight: 600;
    color: #303133;
  }

  .panel-body {
    flex: 1;
    overflow-y: auto;
    padding: 12px;

    :deep(.el-descriptions__label) {
      width: 90px;
    }

    .uid-text {
      font-family: 'Courier New', monospace;
      font-size: 11px;
      word-break: break-all;
      color: #606266;
    }
  }

  .panel-footer {
    padding: 10px 16px;
    border-top: 1px solid #ebeef5;
    background: #f5f7fa;
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  .view-info-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .view-info-label {
    font-size: 12px;
    color: #909399;
  }

  .view-info-value {
    font-size: 12px;
    font-weight: 500;
    color: #303133;
    font-family: 'Courier New', monospace;
  }
}

// Panel slide transition
.slide-panel-enter-active,
.slide-panel-leave-active {
  transition: width 0.25s ease, opacity 0.2s ease;
}

.slide-panel-enter-from,
.slide-panel-leave-to {
  width: 0;
  opacity: 0;
}
</style>
