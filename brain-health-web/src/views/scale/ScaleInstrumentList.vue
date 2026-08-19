<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Search, RefreshRight, Clock, TrendCharts, Collection } from '@element-plus/icons-vue'
import { scaleApi, type ScaleInstrument } from '@/api/modules/scale'

const router = useRouter()

// ---- Category options ----
const CATEGORY_OPTIONS = [
  { label: '全部', value: '' },
  { label: '认知功能', value: 'Cognitive' },
  { label: '情绪评估', value: 'Mood' },
  { label: '精神症状', value: 'Psychiatric' },
  { label: '行为评估', value: 'Behavioral' },
  { label: '功能评估', value: 'Functional' },
  { label: '生活质量', value: 'QualityOfLife' },
  { label: '其他', value: 'Other' },
] as const

const CATEGORY_TAG_MAP: Record<string, string> = {
  Cognitive: 'primary',
  Mood: 'warning',
  Psychiatric: 'danger',
  Behavioral: 'info',
  Functional: 'success',
  QualityOfLife: '',
  Other: 'info',
}

const CATEGORY_LABEL_MAP: Record<string, string> = {
  Cognitive: '认知功能',
  Mood: '情绪评估',
  Psychiatric: '精神症状',
  Behavioral: '行为评估',
  Functional: '功能评估',
  QualityOfLife: '生活质量',
  Other: '其他',
}

// ---- Filter state ----
const keyword = ref('')
const selectedCategory = ref('')

// ---- Instruments list ----
const instruments = ref<ScaleInstrument[]>([])
const loading = ref(false)

// ---- Computed ----
const filteredInstruments = computed(() => {
  let list = instruments.value
  if (selectedCategory.value) {
    list = list.filter((item) => item.category === selectedCategory.value)
  }
  if (keyword.value.trim()) {
    const kw = keyword.value.trim().toLowerCase()
    list = list.filter(
      (item) =>
        item.name.toLowerCase().includes(kw) ||
        item.fullName.toLowerCase().includes(kw) ||
        (item.description && item.description.toLowerCase().includes(kw))
    )
  }
  return list
})

// ======================== API calls ========================

async function fetchInstruments() {
  loading.value = true
  try {
    const params: { category?: string; keyword?: string } = {}
    if (selectedCategory.value) params.category = selectedCategory.value
    if (keyword.value.trim()) params.keyword = keyword.value.trim()

    const res = await scaleApi.listInstruments(params)
    if (res.data.code === 200) {
      instruments.value = res.data.data
    }
  } catch {
    // error handled by interceptor
  } finally {
    loading.value = false
  }
}

// ======================== Handlers ========================

function handleSearch() {
  fetchInstruments()
}

function handleReset() {
  keyword.value = ''
  selectedCategory.value = ''
  fetchInstruments()
}

function handleCategoryChange() {
  fetchInstruments()
}

function handleStartAssessment(instrument: ScaleInstrument) {
  router.push(`/scales/${instrument.id}/assess`)
}

// ======================== Display helpers ========================

function getCategoryLabel(category: string): string {
  return CATEGORY_LABEL_MAP[category] || category
}

function getCategoryTagType(category: string): string {
  return CATEGORY_TAG_MAP[category] || 'info'
}

function formatScoringRange(min: number, max: number): string {
  return `${min} - ${max}`
}

function formatDuration(minutes: number): string {
  if (minutes < 60) {
    return `${minutes} 分钟`
  }
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  return m > 0 ? `${h} 小时 ${m} 分钟` : `${h} 小时`
}

function formatCutoff(score?: number): string {
  if (score === undefined || score === null) return '无'
  return String(score)
}

// ======================== Init ========================

onMounted(() => {
  fetchInstruments()
})
</script>

<template>
  <div class="page-container">
    <!-- Page title -->
    <div class="page-title">量表管理</div>

    <!-- Filter bar -->
    <div class="filter-bar">
      <el-form :inline="true" size="default">
        <el-form-item label="分类">
          <el-select
            v-model="selectedCategory"
            placeholder="全部类别"
            clearable
            style="width: 180px"
            @change="handleCategoryChange"
          >
            <el-option
              v-for="opt in CATEGORY_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="搜索">
          <el-input
            v-model="keyword"
            placeholder="量表名称/全称"
            clearable
            style="width: 260px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">
            搜索
          </el-button>
          <el-button :icon="RefreshRight" @click="handleReset">
            重置
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- Toolbar -->
    <div class="toolbar">
      <div class="toolbar-left">
        <span class="total-count">
          共 <strong>{{ filteredInstruments.length }}</strong> 个量表
        </span>
      </div>
    </div>

    <!-- Card grid -->
    <div v-loading="loading" class="card-grid">
      <el-empty
        v-if="!loading && filteredInstruments.length === 0"
        description="暂无匹配的量表数据"
      />

      <el-card
        v-for="instrument in filteredInstruments"
        :key="instrument.id"
        class="instrument-card"
        shadow="hover"
        @click="handleStartAssessment(instrument)"
      >
        <div class="card-header">
          <div class="card-name">
            <span class="instrument-abbr">{{ instrument.name }}</span>
            <el-tag
              :type="getCategoryTagType(instrument.category)"
              size="small"
              class="category-tag"
            >
              {{ getCategoryLabel(instrument.category) }}
            </el-tag>
          </div>
          <div class="card-fullname">{{ instrument.fullName }}</div>
          <div v-if="instrument.version" class="card-version">
            版本: {{ instrument.version }}
          </div>
        </div>

        <el-divider class="card-divider" />

        <div v-if="instrument.description" class="card-description">
          {{ instrument.description }}
        </div>

        <div class="card-meta">
          <div class="meta-item">
            <el-icon :size="16" color="#909399">
              <TrendCharts />
            </el-icon>
            <span class="meta-label">评分范围</span>
            <span class="meta-value">
              {{ formatScoringRange(instrument.scoringRangeMin, instrument.scoringRangeMax) }}
            </span>
          </div>

          <div class="meta-item">
            <el-icon :size="16" color="#909399">
              <Collection />
            </el-icon>
            <span class="meta-label">截断值</span>
            <span class="meta-value cutoff">
              {{ formatCutoff(instrument.cutoffScore) }}
            </span>
          </div>

          <div class="meta-item">
            <el-icon :size="16" color="#909399">
              <Clock />
            </el-icon>
            <span class="meta-label">预计耗时</span>
            <span class="meta-value">
              {{ formatDuration(instrument.estimatedDurationMin) }}
            </span>
          </div>
        </div>

        <div class="card-action">
          <el-button type="primary" plain size="small">
            开始评估
          </el-button>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script lang="ts">
export default {
  name: 'ScaleInstrumentList',
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

// ---- Filter bar ----
.filter-bar {
  background: #fff;
  border-radius: 8px;
  padding: 20px 20px 4px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  margin-bottom: 16px;

  :deep(.el-form-item) {
    margin-bottom: 16px;
  }
}

// ---- Toolbar ----
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;

  .toolbar-left {
    .total-count {
      font-size: 13px;
      color: #909399;

      strong {
        color: #303133;
      }
    }
  }
}

// ---- Card grid ----
.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 20px;
  min-height: 200px;
}

.instrument-card {
  border-radius: 10px;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;

  &:hover {
    transform: translateY(-3px);
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
  }

  :deep(.el-card__body) {
    padding: 20px;
    display: flex;
    flex-direction: column;
    height: 100%;
  }

  .card-header {
    .card-name {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 6px;

      .instrument-abbr {
        font-size: 18px;
        font-weight: 700;
        color: #303133;
      }

      .category-tag {
        flex-shrink: 0;
      }
    }

    .card-fullname {
      font-size: 13px;
      color: #606266;
      line-height: 1.4;
      margin-bottom: 4px;
    }

    .card-version {
      font-size: 12px;
      color: #c0c4cc;
    }
  }

  .card-divider {
    margin: 12px 0;
  }

  .card-description {
    font-size: 13px;
    color: #909399;
    line-height: 1.6;
    margin-bottom: 14px;
    flex: 1;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }

  .card-meta {
    display: flex;
    flex-direction: column;
    gap: 8px;
    margin-bottom: 16px;

    .meta-item {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 13px;

      .meta-label {
        color: #909399;
        min-width: 60px;
      }

      .meta-value {
        color: #303133;
        font-weight: 500;

        &.cutoff {
          color: #e6a23c;
        }
      }
    }
  }

  .card-action {
    text-align: center;
  }
}

// ---- Empty state ----
:deep(.el-empty) {
  grid-column: 1 / -1;
  padding: 60px 0;
}
</style>
