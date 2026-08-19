<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, computed, watch, nextTick } from 'vue'
import { ElMessage, ElIcon } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import http from '@/api/client'
import { scaleApi, type VisitAttachment } from '@/api/modules/scale'
import { useAuthStore } from '@/stores/auth'

// ---- Types ----
interface OptionItem { code: string; label: string; score: number }
interface ScaleField { code: string; name: string; type: string; required: boolean; unit: string; options: OptionItem[] }
interface ScaleGroup { code: string; name: string; maxScore: number; cutoff: number | null; itemCount: number; items: ScaleField[]; _loading?: boolean }
interface ItemCard { prefix: string; title: string; items: ScaleField[] }
interface VisitForm { visitCode: string; visitName: string; scales: ScaleGroup[] }
interface SubjectInfo { id: number; subjectId: string; name?: string }
interface ClinicalSection { code: string; name: string; scales: ScaleGroup[] }

// ---- State ----
const formData = ref<VisitForm | null>(null)
const responses = reactive<Record<string, any>>({})
const checkboxGroups = reactive<Record<string, string[]>>({})
const subjects = ref<SubjectInfo[]>([])
const selectedSubject = ref<number | null>(null)
const selectedVisit = ref('V1')
const authStore = useAuthStore()
const isPatient = computed(() => authStore.hasAnyRole(['patient', 'PATIENT']))
const loading = ref(false)
const saving = ref(false)
const submitting = ref(false)
const activeScale = ref('')
interface AttachmentState {
  attachment?: VisitAttachment
  progress: number
  uploading: boolean
  error?: string
  analyzing?: boolean
  recognizedText?: string
  analysisError?: string
}
const attachmentStates = reactive<Record<string, AttachmentState>>({})
const attachmentControllers = new Map<string, AbortController>()

const staffVisitOptions = [
  { code: 'V0', name: '基线访视' }, { code: 'V1', name: '首次评估' },
  { code: 'SF1', name: '第1次随访' }, { code: 'SF2', name: '第2次随访' },
  { code: 'SF3', name: '第3次随访' }, { code: 'SF4', name: '第4次随访' },
  { code: 'SF5', name: '第5次随访' },
]
const patientVisitOptions = ref<Array<{
  code: string
  name: string
  status?: string
  returnReason?: string
  scaleCodes?: string[]
}>>([])
const patientSessionMap = reactive<Record<string, number>>({})
const staffSessionMap = reactive<Record<string, number>>({})
const unifiedFile = ref<File>()
const unifiedUploading = ref(false)
const unifiedProgress = ref(0)
const unifiedBatch = ref<any>()
let unifiedPollTimer: ReturnType<typeof setTimeout> | undefined
const visitOptions = computed(() => isPatient.value ? patientVisitOptions.value : staffVisitOptions)
const currentPatientTask = computed(() =>
  patientVisitOptions.value.find(task => task.code === selectedVisit.value),
)
const patientTaskLocked = computed(() =>
  isPatient.value && ['SUBMITTED', 'SCORED', 'EXPIRED'].includes(currentPatientTask.value?.status || ''),
)

function taskStatusLabel(status?: string): string {
  return ({
    PENDING: '待填写',
    IN_PROGRESS: '填写中',
    SUBMITTED: '已提交',
    SCORED: '已评分',
    RETURNED: '已退回，请修改',
    EXPIRED: '已过期',
  } as Record<string, string>)[status || ''] || status || '待填写'
}

const clinicalSectionDefinitions = [
  { code: 'clinical', name: '临床资料', scales: ['SHFS', 'CLINICAL', 'NIHSS'] },
  { code: 'cognitive', name: '认知评估', scales: ['MMSE', 'BOSTON', 'CDR', 'RAVLT', 'MOCA', 'REY', 'HZSY', 'DSPT', 'VF', 'COGNITIVE'] },
  { code: 'psychiatric', name: '心理与精神评估', scales: ['ADS', 'HAMD', 'GDS', 'HAMA', 'AES', 'NPI', 'PSYCHIATRIC'] },
  { code: 'function', name: '功能与生活质量', scales: ['FUNCTION_QOL'] },
  { code: 'lab', name: '检验与检查', scales: ['LAB_EXAM'] },
  { code: 'attachments', name: '附件资料', scales: ['ATTACHMENTS'] },
]

const clinicalSections = computed<ClinicalSection[]>(() => {
  const scales = formData.value?.scales || []
  const assigned = new Set<string>()
  const sections = clinicalSectionDefinitions.map(section => {
    const members = section.scales
      .map(code => scales.find(scale => scale.code === code))
      .filter((scale): scale is ScaleGroup => Boolean(scale))
    members.forEach(scale => assigned.add(scale.code))
    return { code: section.code, name: section.name, scales: members }
  }).filter(section => section.scales.length > 0)
  const remaining = scales.filter(scale => !assigned.has(scale.code))
  if (remaining.length > 0) sections.push({ code: 'other', name: '其他资料', scales: remaining })
  if (isPatient.value) {
    return sections.filter(section => !['clinical', 'lab', 'attachments'].includes(section.code))
  }
  return sections
})

// ---- Helpers ----
function hasOptions(item: ScaleField): boolean { return item.options && item.options.length > 0 }
function isCheckbox(t: string): boolean { return (t || '').toUpperCase() === 'CHECKBOX' }
function isLongList(item: ScaleField): boolean { return hasOptions(item) && item.options.length >= 6 }
function isDateType(t: string): boolean { const u = (t || '').toUpperCase(); return u === 'DATE' || u === 'DATETIME' }
function isFileType(t: string): boolean { return (t || '').toUpperCase() === 'FILE' }
function isParagraph(t: string): boolean { return (t || '').toUpperCase() === 'PARAGRAPH' }
function isSwitchType(t: string): boolean { return (t || '').toUpperCase() === 'SWITCH' }
function isNumber(t: string): boolean { return (t || '').toUpperCase() === 'NUMBER' }

// ---- 选项标准化：将字符串格式转为结构化对象 ----
function normalizeOptions(raw: any): OptionItem[] {
  if (!raw || !Array.isArray(raw)) return []
  return raw.map((opt: any) => {
    // 已经是结构化对象 {code, label, score}
    if (typeof opt === 'object' && opt !== null && 'code' in opt && 'label' in opt) {
      return { code: String(opt.code), label: String(opt.label), score: Number(opt.score || 0) }
    }
    // 字符串格式 "1=从不饮酒"
    if (typeof opt === 'string') {
      const eqIdx = opt.indexOf('=')
      if (eqIdx > 0) {
        const c = opt.substring(0, eqIdx).trim()
        const l = opt.substring(eqIdx + 1).trim()
        return { code: c, label: l, score: parseFloat(c) || 0 }
      }
      return { code: opt, label: opt, score: 0 }
    }
    return { code: '', label: '', score: 0 }
  })
}

// 移除选项标签中的"数字="格式
function cleanOptionLabel(label: string | undefined): string {
  if (!label) return ''
  return label.replace(/^\d+=\s*/, '').trim()
}

// 标准化单位：处理 "null" 字符串 + 已知单位映射
function formatUnit(item: ScaleField): string {
  const raw = (item.unit || '').trim()
  // 处理 "null" 字符串
  if (!raw || raw === 'null' || raw === 'NULL') {
    // 查找已知映射
    const suffix = itemSuffix(item.code)
    const unitMap: Record<string, string> = {
      'SHFS_SHFS_3': '次',        // 饮酒次数
      'SHFS_SHFS_4': 'ml',        // 平均每次饮酒
      'SHFS_SHFS_6': 'ml',        // 戒酒前饮酒量
      'SHFS_SHFS_12': '支/天',    // 目前吸烟每日数量
      'SHFS_SHFS_13': '岁',       // 戒烟年龄
      'SHFS_SHFS_14': '支/天',    // 既往吸烟量
      'SHFS_SHFS_15': '支/天',    // 戒烟前吸烟量
      'SHFS_SHFS_17': '天/周',    // 二手烟天数
      'SHFS_SHFS_18': '年',       // 二手烟年数
    }
    // 食物频率/量
    if (suffix.startsWith('SHFS_SHFS_')) {
      const n = parseInt(suffix.replace('SHFS_SHFS_', ''), 10)
      if (n >= 24) {
        // 偶数 = 频率, 奇数 = 每次量
        return n % 2 === 0 ? '次/周' : 'g'
      }
    }
    return unitMap[suffix] || ''
  }
  return raw
}

// 题目标签优化：对某些含义不清的题目给出更好的标签
function displayLabel(item: ScaleField): string {
  const base = shortLabel(item)
  const suffix = itemSuffix(item.code)

  // SHFS_3 "具体饮酒次数" — 根据 SHFS_2 的选择动态显示单位
  if (suffix === 'SHFS_SHFS_3') {
    const freqCode = findCodeBySuffix('SHFS_SHFS_2')
    const freqVal = responses[freqCode]
    const unitMap: Record<string, string> = { '1': '次/周', '2': '次/月', '3': '次/年' }
    const unit = freqVal ? unitMap[freqVal] : ''
    return unit ? `具体饮酒次数（${unit}）` : '具体饮酒次数'
  }

  // CDR 词语回忆（复述）— 区分不同次试验
  if (suffix === 'CDRSU_CDR_SU_REM3A') return '第1次即时回忆 — 请勾选正确复述的词语'
  if (suffix === 'CDRSU_CDR_SU_REM3B') return '第2次即时回忆 — 请勾选正确复述的词语'
  if (suffix === 'CDRSU_CDR_SU_REM3C') return '第3次即时回忆 — 请勾选正确复述的词语'
  if (suffix === 'CDRSU_CDR_SU_REM10')  return '延迟回忆 — 请勾选正确复述的词语'

  const labelOverrides: Record<string, string> = {
    'SHFS_SHFS_2': '饮酒频率计算方式',
    'SHFS_SHFS_5': '是否已戒酒',
    'SHFS_SHFS_7': '平均每日摄入酒精量',
  }
  if (labelOverrides[suffix]) return labelOverrides[suffix]
  return base
}

// ---- 题目排序与可见性配置 ----
// 用 code 后缀（去掉访视前缀）作为 key
function itemSuffix(code: string): string {
  // "V0_SHFS_SHFS_1" → "SHFS_SHFS_1"
  return code.replace(/^(SF\d+|V\d+)_/, '')
}

// 获取题目的显示顺序（越小越靠前）
function getItemSortOrder(item: ScaleField): number {
  const suffix = itemSuffix(item.code)
  // 饮酒题组 (100-199)
  if (suffix.startsWith('SHFS_SHFS_')) {
    const qNum = parseInt(suffix.replace('SHFS_SHFS_', ''), 10)
    const drinkingOrder: Record<number, number> = {
      1: 100,   // 饮酒（状态）
      2: 110,   // 饮酒频率
      3: 120,   // 饮酒次数
      4: 130,   // 平均每次饮酒
      5: 140,   // 是否已戒酒
      6: 150,   // 戒酒前饮酒
      7: 160,   // 平均每日摄入酒精量
      8: 170,   // 是否存在一次性暴饮
    }
    if (drinkingOrder[qNum]) return drinkingOrder[qNum]
    // 饮酒类别（多选）— 合并后的 baseCode
    if (suffix === 'SHFS_SHFS_9') return 180
    // 吸烟题组 (200-299)
    const smokingOrder: Record<number, number> = {
      10: 200,  // 吸烟
      11: 210,  // 每日吸烟数量
      12: 220,  // 目前吸烟→吸烟量
      13: 230,  // 既往吸烟→戒烟年龄
      14: 240,  // 既往吸烟→吸烟量
      15: 250,  // 既往吸烟→戒烟前
      16: 260,  // 二手烟
      17: 270,  // 二手烟天数
      18: 280,  // 二手烟发病前10年
    }
    if (smokingOrder[qNum]) return smokingOrder[qNum]
    // 饮食题组 (300-399)
    const dietOrder: Record<number, number> = {
      19: 300,  // 饮食口味倾向
      20: 310,  // 家中常用烹饪方式
      21: 320,  // 有食用腌制的习惯吗
    }
    if (dietOrder[qNum]) return dietOrder[qNum]
    // 蔬菜频率题 (400+)
    if (qNum >= 24) return 400 + qNum
  }
  // CDR 量表排序 (500-599) — 知情者问卷
  if (suffix.startsWith('CDR_CDR_IN_')) {
    const cdrOrder: Record<string, number> = {
      'CDR_CDR_LAN': 501, 'ZQCDR': 505,
      'CDR_CDR_IN_REM1': 510, 'CDR_CDR_IN_REM1A': 511, 'CDR_CDR_IN_REM2': 512, 'CDR_CDR_IN_REM3': 513,
      'CDR_CDR_IN_REM4': 514, 'CDR_CDR_IN_REM5': 515, 'CDR_CDR_IN_REM6': 516, 'CDR_CDR_IN_REM7': 517,
      'CDR_CDR_IN_REM8': 518, 'CDR_CDR_IN_REM9_1': 519, 'CDR_CDR_IN_REM9_2': 520,
      'CDR_CDR_IN_REM10': 521, 'CDR_CDR_IN_REM11': 522, 'CDR_CDR_IN_REM12_N': 523,
      'CDR_CDR_IN_REM12_A': 524, 'CDR_CDR_IN_REM12_G': 525,
      'CDR_CDR_IN_REM13': 526, 'CDR_CDR_IN_REM14': 527, 'CDR_CDR_IN_REM15': 528,
      'CDR_CDR_IN_DRIE1': 530, 'CDR_CDR_IN_DRIE2': 531, 'CDR_CDR_IN_DRIE3': 532, 'CDR_CDR_IN_DRIE4': 533,
      'CDR_CDR_IN_DRIE5': 534, 'CDR_CDR_IN_DRIE6': 535, 'CDR_CDR_IN_DRIE7': 536, 'CDR_CDR_IN_DRIE8': 537,
      'CDR_CDR_IN_SOLV1': 540, 'CDR_CDR_IN_SOLV2': 541, 'CDR_CDR_IN_SOLV3': 542, 'CDR_CDR_IN_SOLV4': 543,
      'CDR_CDR_IN_SOLV4D': 544, 'CDR_CDR_IN_SOLV5': 545, 'CDR_CDR_IN_SOLV6': 546,
      'CDR_CDR_IN_SOC1': 550, 'CDR_CDR_IN_SOC2': 551, 'CDR_CDR_IN_SOC3': 552,
      'CDR_CDR_IN_SOC4_1': 553, 'CDR_CDR_IN_SOC4_2': 554, 'CDR_CDR_IN_SOC4_3': 555,
      'CDR_CDR_IN_SOC5': 556, 'CDR_CDR_IN_SOC6': 557, 'CDR_CDR_IN_SOC7': 558, 'CDR_CDR_IN_SOC8': 559,
      'CDR_CDR_IN_SOC8DES': 560, 'CDR_CDR_IN_SOC9': 561, 'CDR_CDR_IN_SOC10': 562,
      'CDR_CDR_IN_HOS1A': 570, 'CDR_CDR_IN_HOS1B': 571, 'CDR_CDR_IN_HOS2A': 572, 'CDR_CDR_IN_HOS2B': 573,
      'CDR_CDR_IN_HOS3': 574, 'CDR_CDR_IN_HOS4': 575, 'CDR_CDR_IN_HOS4DES': 576,
      'CDR_CDR_IN_HOS5': 577, 'CDR_CDR_IN_HOS6': 578,
      'CDR_CDR_IN_SEL1': 580, 'CDR_CDR_IN_SEL2': 581, 'CDR_CDR_IN_SEL3': 582, 'CDR_CDR_IN_SEL4': 583,
    }
    if (cdrOrder[suffix]) return cdrOrder[suffix]
    return 590
  }
  // CDR 受试者问卷 (600-699)
  if (suffix.startsWith('CDRSU_CDR_')) {
    const cdrSuOrder: Record<string, number> = {
      'CDRSU_CDR_LAN2': 601, 'CDRSU_CDR_JYL': 605, 'CDRSU_CDR_SHSW': 606,
      'CDRSU_CDR_SU_DRIE1': 610, 'CDRSU_CDR_SU_DRIE1A': 611, 'CDRSU_CDR_SU_DRIE2': 612, 'CDRSU_CDR_SU_DRIE2A': 613,
      'CDRSU_CDR_SU_DRIE3': 614, 'CDRSU_CDR_SU_DRIE3A': 615, 'CDRSU_CDR_SU_DRIE4': 616, 'CDRSU_CDR_SU_DRIE4A': 617,
      'CDRSU_CDR_SU_DRIE5': 618, 'CDRSU_CDR_SU_DRIE5A': 619, 'CDRSU_CDR_SU_DRIE6': 620, 'CDRSU_CDR_SU_DRIE6A': 621,
      'CDRSU_CDR_SU_DRIE7': 622, 'CDRSU_CDR_SU_DRIE7A': 623, 'CDRSU_CDR_SU_DRIE8': 624, 'CDRSU_CDR_SU_DRIE8A': 625,
      'CDRSU_CDR_SU_REM1': 630, 'CDRSU_CDR_SU_REM2A': 632, 'CDRSU_CDR_SU_REM2B': 633,
      'CDRSU_CDR_SU_REM3A': 635, 'CDRSU_CDR_SU_REM3B': 636, 'CDRSU_CDR_SU_REM3C': 637,
      'CDRSU_CDR_SU_REM4': 640, 'CDRSU_CDR_SU_REM5': 642, 'CDRSU_CDR_SU_REM6A': 644, 'CDRSU_CDR_SU_REM6B': 645,
      'CDRSU_CDR_SU_REM6C': 646, 'CDRSU_CDR_SU_REM7': 648, 'CDRSU_CDR_SU_REM8': 650, 'CDRSU_CDR_SU_REM9': 652,
      'CDRSU_CDR_SU_REM10': 660,
      'CDRSU_CDR_SU_SOLV1': 670, 'CDRSU_CDR_SU_SOLV2': 672, 'CDRSU_CDR_SU_SOLV3': 674,
      'CDRSU_CDR_SU_SOLV4': 676, 'CDRSU_CDR_SU_SOLV5': 678, 'CDRSU_CDR_SU_SOLV6': 680,
      'CDRSU_CDR_SU_SOLV7': 682, 'CDRSU_CDR_SU_SOLV8': 684, 'CDRSU_CDR_SU_SOLV9': 686,
    }
    if (cdrSuOrder[suffix]) return cdrSuOrder[suffix]
    return 690
  }
  // 其他量表按原始顺序
  return 1000
}

// 条件可见性：根据已填答案判断当前题目是否显示
function isItemVisible(item: ScaleField): boolean {
  const suffix = itemSuffix(item.code)

  // SHFS 量表的条件逻辑
  if (suffix.startsWith('SHFS_SHFS_')) {
    const qNum = parseInt(suffix.replace('SHFS_SHFS_', ''), 10)

    // 获取"饮酒"题的答案 (SHFS_1)
    const drinkStatus = responses[findCodeBySuffix('SHFS_SHFS_1')]
    // SHFS_1 选项: "1"=从不饮酒, "2"=既往饮酒, "3"=目前饮酒, "4"=偶尔饮酒, "98"=不详

    // 饮酒相关题目：如果"从不饮酒"则隐藏后续饮酒题
    const isNeverDrink = drinkStatus === '1'

    if (qNum === 2 || qNum === 3 || qNum === 4) {
      // 饮酒频率、次数、每次量：从不饮酒时隐藏
      if (isNeverDrink) return false
    }
    if (qNum === 5 || qNum === 6) {
      // 已戒酒、戒酒前饮酒：仅既往饮酒或目前饮酒时显示
      if (drinkStatus !== '2' && drinkStatus !== '3') return false
    }
    if (qNum === 7 || qNum === 8) {
      // 目前饮酒相关：仅目前饮酒或偶尔饮酒时显示
      if (drinkStatus !== '3' && drinkStatus !== '4') return false
    }
    if (suffix === 'SHFS_SHFS_9') {
      // 饮酒类别：仅目前饮酒或偶尔饮酒时显示
      if (drinkStatus !== '3' && drinkStatus !== '4') return false
    }

    // 已戒酒 → 显示戒酒前饮酒量 (q5 response = "1" means 是)
    if (qNum === 6) {
      const quitVal = responses[findCodeBySuffix('SHFS_SHFS_5')]
      if (quitVal !== '1') return false
    }

    // 吸烟相关
    const smokeStatus = responses[findCodeBySuffix('SHFS_SHFS_10')]
    if (qNum === 11 || qNum === 12) {
      // 目前吸烟则每日数量：仅"目前吸烟"显示
      if (smokeStatus !== '1') return false
    }
    if (qNum === 13 || qNum === 14 || qNum === 15) {
      // 既往吸烟相关：仅"既往吸烟"显示
      if (smokeStatus !== '2') return false
    }
    if (qNum === 17 || qNum === 18) {
      // 二手烟后续：仅二手烟选"是"时显示
      const secondhand = responses[findCodeBySuffix('SHFS_SHFS_16')]
      if (secondhand !== '1') return false
    }
  }

  return true
}

// 根据后缀查找完整的 item code（因为前缀随访视变化）
function findCodeBySuffix(suffix: string): string {
  if (!formData.value) return suffix
  for (const scale of formData.value.scales) {
    for (const item of scale.items) {
      if (item.code.endsWith(suffix)) return item.code
    }
  }
  // 未找到时尝试当前访视前缀
  return selectedVisit.value + '_' + suffix
}

// Group items by topic into cards, with ordering and visibility
function cardGroups(items: ScaleField[]): ItemCard[] {
  if (!items.length) return []

  // 防御性去重合并
  const mergedItems = mergeAndDeduplicate(items)

  // 按配置排序
  const sorted = [...mergedItems].sort((a, b) => getItemSortOrder(a) - getItemSortOrder(b))

  // Show only visible items, group into cards by topic
  const visible = sorted.filter(item => isItemVisible(item))

  // 按 SHFS 题目分组
  const groups: ItemCard[] = []
  let current: ItemCard | null = null

  for (const item of visible) {
    // Determine group title based on sort order
    const order = getItemSortOrder(item)
    let groupTitle: string
    if (order >= 100 && order < 200) groupTitle = '饮酒情况'
    else if (order >= 200 && order < 300) groupTitle = '吸烟情况'
    else if (order >= 300 && order < 400) groupTitle = '饮食情况'
    else if (order >= 400 && order < 500) groupTitle = '饮食详情'
    else if (order >= 500 && order < 600) groupTitle = '知情者问卷（CDR）'
    else if (order >= 600 && order < 700) groupTitle = '受试者评估（CDR）'
    else {
      // Fallback: group by code prefix
      const code = item.code
      const base = code.split('__')[0]
      const segs = base.split('_')
      let prefix = ''
      if (segs.length >= 5) prefix = segs.slice(0, 4).join('_')
      else if (segs.length >= 4) prefix = segs.slice(0, 3).join('_')
      else prefix = segs.slice(0, 2).join('_')
      if (!current || current.prefix !== prefix) {
        const title = cleanTitle(item.name)
        current = { prefix, title, items: [] }
        groups.push(current)
      }
      current.items.push(item)
      continue
    }

    if (!current || current.title !== groupTitle) {
      current = { prefix: groupTitle, title: groupTitle, items: [] }
      groups.push(current)
    }
    current.items.push(item)
  }
  return groups
}

// 识别多选子项（__N 后缀），如 V0_SHFS_SHFS_9__1, V0_SHFS_SHFS_9__2
function isMultiSelectSubItem(item: ScaleField): boolean {
  if (!isCheckbox(item.type)) return false
  return /__\d+$/.test(item.code)
}

// 从名称中提取子选项标签，如 "饮酒类别（多选）： | 1=高度白酒" → "高度白酒"
function extractSubLabel(name: string): string {
  // 匹配末尾的 | N=xxx 或 | N=xxx, 模式
  const m = name.match(/\|\s*\d+=\s*(.+?)[,;，；]?\s*$/)
  if (m && m[1]) return m[1].trim()
  return name
}

// 合并多选子项为一整个多选题
function mergeMultiSelectGroup(baseCode: string, groupItems: ScaleField[]): ScaleField | null {
  if (groupItems.length === 0) return null

  // 按 __N 后缀排序
  const sorted = [...groupItems].sort((a, b) => {
    const an = parseInt(a.code.match(/__(\d+)$/)?.[1] || '0', 10)
    const bn = parseInt(b.code.match(/__(\d+)$/)?.[1] || '0', 10)
    return an - bn
  })

  // 从第一个子项提取基础名称（去掉 | N=xxx 部分）
  const firstName = sorted[0].name
  let baseName = firstName
    .replace(/\s*\|\s*\d+=\s*\S+[,;，；]?\s*$/, '')  // 移除末尾 | N=xxx
    .replace(/\s*\|\s*\d+=\s*\S+$/, '')               // 移除末尾 | N=xxx (无标点)
    .trim()

  // 用子项生成选项：每个子项是一个可选项
  const mergedOptions: OptionItem[] = sorted.map(item => {
    const subLabel = extractSubLabel(item.name)
    return {
      code: item.code,           // 用完整 code 作为选项值
      label: cleanOptionLabel(subLabel),
      score: 1.0,                // 选中得1分
    }
  })

  const anyRequired = sorted.some(i => i.required)

  return {
    code: baseCode,
    name: baseName,
    type: 'CHECKBOX',
    required: anyRequired,
    unit: '',
    options: mergedOptions,
  }
}

// 类型的优先级（越高越好）：checkbox > select > radio > number > date > text
function typePriority(t: string): number {
  const u = (t || '').toUpperCase()
  if (u === 'CHECKBOX') return 7
  if (u === 'SELECT') return 6
  if (u === 'RADIO') return 5
  if (u === 'NUMBER') return 4
  if (u === 'DATE' || u === 'DATETIME') return 3
  if (u === 'SWITCH') return 2
  return 1  // text, paragraph, file, etc.
}

// 合并相同名称的字段、去重、处理多选子项
function mergeAndDeduplicate(items: ScaleField[]): ScaleField[] {
  // 第零步：按 code 去重 + 按 code+name+type+options 完全匹配去重
  const codeSeen = new Set<string>()
  const hashSeen = new Set<string>()
  const uniqueItems = items.filter(item => {
    if (codeSeen.has(item.code)) return false
    codeSeen.add(item.code)
    // Deep dedup: same name + same type + same options = duplicate (from cross-visit contamination)
    const hash = item.name + '|' + item.type + '|' + JSON.stringify(item.options || [])
    if (hashSeen.has(hash)) return false
    hashSeen.add(hash)
    return true
  })

  // 第一步：分离多选子项和独立项
  const multiSelectGroups = new Map<string, ScaleField[]>()
  const standaloneItems: ScaleField[] = []

  for (const item of uniqueItems) {
    if (isMultiSelectSubItem(item)) {
      const baseCode = item.code.replace(/__\d+$/, '')
      if (!multiSelectGroups.has(baseCode)) {
        multiSelectGroups.set(baseCode, [])
      }
      multiSelectGroups.get(baseCode)!.push(item)
    } else {
      standaloneItems.push(item)
    }
  }

  // 第二步：合并每个多选组
  const mergedMultiSelects: ScaleField[] = []
  for (const [baseCode, group] of multiSelectGroups) {
    const merged = mergeMultiSelectGroup(baseCode, group)
    if (merged) mergedMultiSelects.push(merged)
  }

  // 同名字段可能分别保存“原始回答”和“医生评分”，不能按显示文字合并。
  // 跨访视污染已由后端按访视代码过滤，这里只合并明确的 __N 多选子项。
  return [...mergedMultiSelects, ...standaloneItems]
}

function cleanTitle(name: string): string {
  let t = name || ''
  t = t.replace(/^[\d\.\、\s]+/, '').trim()
  // 移除各种竖线分隔的选项标记
  t = t.replace(/\s*\|\s*\d+=\s*\S+(?:[,;，；]\s*)?/g, '')
  if (t.length > 40) t = t.substring(0, 38) + '…'
  return t || '未命名'
}

// ---- Computed ----
const completedScales = computed(() => formData.value?.scales.filter(s => scaleProgress(s) === 100).length || 0)
const totalScales = computed(() => formData.value?.scales.length || 0)
const progressPct = computed(() => totalScales.value ? Math.round(completedScales.value / totalScales.value * 100) : 0)

function scaleProgress(scale: ScaleGroup): number {
  if (!scale.items.length && scale.itemCount > 0) return 0
  // Only count visible items
  const visible = scale.items.filter(i => isItemVisible(i))
  if (!visible.length) return 0
  const req = visible.filter(i => i.required)
  const total = req.length || visible.length
  const done = (req.length ? req : visible).filter(i => {
    const val = responses[i.code]
    if (isCheckbox(i.type)) return checkboxGroups[i.code]?.length > 0
    return val !== undefined && val !== '' && val !== null
  }).length
  return Math.round(done / total * 100)
}

function scaleScore(scale: ScaleGroup): string {
  const explicitTotal = scale.items.find(item => /_(TOTAL|SCORE)$/i.test(item.code))
  if (explicitTotal) {
    const value = responses[explicitTotal.code]
    if (value !== undefined && value !== null && value !== '') {
      return String(value) + (scale.maxScore ? ' / ' + scale.maxScore : '')
    }
  }
  let total = 0; let answered = 0
  for (const item of scale.items) {
    const t = (item.type || '').toUpperCase(); const val = responses[item.code]
    const informational = /_(LAN|LANGUAGE)$/i.test(item.code) || /语言/.test(item.name)
    if (!informational && (t === 'RADIO' || t === 'SELECT') && val != null && val !== '') {
      answered++; for (const opt of item.options) { if (String(opt.code) === String(val)) { total += opt.score || 0; break } }
    } else if (!informational && t === 'CHECKBOX') {
      const vals = checkboxGroups[item.code] || []
      if (vals.length > 0) { answered++; for (const v of vals) { for (const opt of item.options) { if (String(opt.code) === String(v)) { total += opt.score || 0 } } } }
    }
  }
  if (!answered) return ''
  return String(total) + (scale.maxScore ? ' / ' + scale.maxScore : '')
}

function scaleProgressColor(p: number): string { return p === 100 ? '#67c23a' : p >= 50 ? '#409eff' : '#e6a23c' }

function shortLabel(item: ScaleField): string {
  let n = item.name || ''
  // 移除开头的前缀编码，如 "V1_MOCA_Q01："
  n = n.replace(/^[A-Z0-9_]+[：:]\s*/, '')
  // 移除开头的序号
  n = n.replace(/^[\d\.\、\s]+/, '')
  // 移除末尾的 | N=xxx 或 | N=xxx, 或 | N=xxx； 格式
  n = n.replace(/\s*\|\s*\d+=\s*\S+[,;，；]?\s*$/, '')
  // 移除中间的 | N=xxx 格式
  n = n.replace(/\|\s*\d+=\s*/g, '')
  return n.trim() || item.code
}

// ---- API ----
async function fetchSubjects() {
  if (isPatient.value && authStore.userInfo?.subjectId) {
    selectedSubject.value = authStore.userInfo.subjectId
    subjects.value = [{ id: authStore.userInfo.subjectId, subjectId: '本人' }]
    const taskResponse = await http.get('/api/v1/scales/patient/tasks')
    patientVisitOptions.value = (taskResponse.data?.data || []).map((task: any) => ({
      code: task.formCode,
      name: `${task.visitCode} · ${taskStatusLabel(task.status)}`,
      status: task.status,
      returnReason: task.returnReason,
      scaleCodes: Array.isArray(task.scaleCodes)
        ? task.scaleCodes
        : typeof task.scaleCodes === 'string' ? JSON.parse(task.scaleCodes || '[]') : [],
    }))
    for (const task of taskResponse.data?.data || []) patientSessionMap[task.formCode] = task.sessionId
    if (patientVisitOptions.value.length) selectedVisit.value = patientVisitOptions.value[0].code
    return
  }
  try {
    const res = await http.get('/api/v1/subjects', { params: { page: 1, size: 200 } })
    if (res.data.code === 200) subjects.value = (res.data.data.records || []).map((s: any) => ({ id: s.id, subjectId: s.subjectId, name: s.name || '' }))
  } catch { /* silent */ }
}

async function fetchStaffSessions() {
  if (isPatient.value || !selectedSubject.value) return
  const response = await http.get(`/api/v1/subjects/${selectedSubject.value}/sessions`)
  for (const session of response.data?.data || []) {
    const raw = String(session.visitLabel || session.visitCode || '')
    const normalized = raw === 'V01' ? 'V0' : raw === 'V02' ? 'SF1' : raw === 'V03' ? 'SF2' : raw
    staffSessionMap[normalized] = session.id
  }
  await loadUnifiedBatch()
}

async function loadUnifiedBatch() {
  if (isPatient.value) return
  const sessionId = staffSessionMap[selectedVisit.value]
  if (!sessionId) {
    unifiedBatch.value = undefined
    return
  }
  const response = await http.get(`/api/v1/sessions/${sessionId}/unified-imports`)
  const latest = response.data?.data?.[0]
  if (!latest) {
    unifiedBatch.value = undefined
    return
  }
  const detail = await http.get(`/api/v1/sessions/${sessionId}/unified-imports/${latest.id}`)
  unifiedBatch.value = detail.data.data
}

function onUnifiedFileChange(file: any) {
  unifiedFile.value = file.raw
}

async function analyzeUnifiedPackage() {
  const sessionId = staffSessionMap[selectedVisit.value]
  if (!selectedSubject.value || !sessionId) {
    ElMessage.warning('当前受试者的这个访视尚未创建，请先在受试者详情中创建访视')
    return
  }
  if (!unifiedFile.value) {
    ElMessage.warning('请选择 ZIP 或 RAR 综合压缩包')
    return
  }
  unifiedUploading.value = true
  unifiedProgress.value = 0
  try {
    if (unifiedFile.value.size > 32 * 1024 * 1024) {
      await analyzeUnifiedPackageInChunks(sessionId, unifiedFile.value)
      return
    }
    const body = new FormData()
    body.append('subjectId', String(selectedSubject.value))
    body.append('file', unifiedFile.value)
    const response = await http.post(
      `/api/v1/sessions/${sessionId}/unified-imports/analyze`, body,
      {
        headers: { 'Content-Type': 'multipart/form-data' },
        timeout: 0,
        onUploadProgress: event => {
          if (event.total) unifiedProgress.value = Math.round(event.loaded * 100 / event.total)
        },
      },
    )
    unifiedBatch.value = response.data.data
    ElMessage.success('压缩包已完成一次解压和自动分类，请核对后确认')
  } finally {
    unifiedUploading.value = false
  }
}

async function analyzeUnifiedPackageInChunks(sessionId: number, file: File) {
  const chunkSize = 8 * 1024 * 1024
  const totalChunks = Math.ceil(file.size / chunkSize)
  const resumeKey = `unified-upload:${selectedSubject.value}:${sessionId}:${file.name}:${file.size}:${file.lastModified}`
  let uploadId = localStorage.getItem(resumeKey)
  let uploaded = new Set<number>()
  if (uploadId) {
    try {
      const status = await http.get(
        `/api/v1/sessions/${sessionId}/unified-imports/chunked/${uploadId}`,
        { params: { subjectId: selectedSubject.value } },
      )
      uploaded = new Set(status.data.data.uploadedChunks || [])
    } catch {
      localStorage.removeItem(resumeKey)
      uploadId = null
    }
  }
  if (!uploadId) {
    const initialized = await http.post(
      `/api/v1/sessions/${sessionId}/unified-imports/chunked`,
      {
        subjectId: selectedSubject.value,
        fileName: file.name,
        fileSize: file.size,
        totalChunks,
      },
    )
    uploadId = initialized.data.data.uploadId
    localStorage.setItem(resumeKey, uploadId!)
  }
  unifiedProgress.value = Math.round(uploaded.size * 95 / totalChunks)
  for (let index = 0; index < totalChunks; index += 1) {
    if (uploaded.has(index)) continue
    const body = new FormData()
    body.append('file', file.slice(index * chunkSize, Math.min(file.size, (index + 1) * chunkSize)))
    await http.put(
      `/api/v1/sessions/${sessionId}/unified-imports/chunked/${uploadId}/chunks/${index}`,
      body,
      {
        params: { subjectId: selectedSubject.value },
        headers: { 'Content-Type': 'multipart/form-data' },
        timeout: 0,
      },
    )
    uploaded.add(index)
    unifiedProgress.value = Math.round(uploaded.size * 95 / totalChunks)
  }
  const completed = await http.post(
    `/api/v1/sessions/${sessionId}/unified-imports/chunked/${uploadId}/complete`,
    { subjectId: selectedSubject.value },
    { timeout: 0 },
  )
  unifiedProgress.value = 100
  unifiedBatch.value = completed.data.data
  localStorage.removeItem(resumeKey)
  ElMessage.success('大文件已分片上传并完成自动分类，请核对后确认')
}

async function confirmUnifiedPackage() {
  if (!unifiedBatch.value) return
  const sessionId = staffSessionMap[selectedVisit.value]
  const response = await http.post(
    `/api/v1/sessions/${sessionId}/unified-imports/${unifiedBatch.value.id}/confirm`,
    { items: unifiedBatch.value.items },
  )
  unifiedBatch.value = response.data.data
  ElMessage.success('分类已确认，后台正在写入影像与其他专业模块')
  scheduleUnifiedPoll()
}

async function retryUnifiedPackage() {
  if (!unifiedBatch.value) return
  const sessionId = staffSessionMap[selectedVisit.value]
  unifiedUploading.value = true
  try {
    const response = await http.post(
      `/api/v1/sessions/${sessionId}/unified-imports/${unifiedBatch.value.id}/retry`,
    )
    unifiedBatch.value = response.data.data
    if (unifiedBatch.value.status === 'IMPORTING') {
      ElMessage.info('失败文件正在后台重试')
      scheduleUnifiedPoll()
    } else if (unifiedBatch.value.status === 'COMPLETED') {
      ElMessage.success('失败文件已重新导入，批次现已全部完成')
    } else {
      ElMessage.warning('重试已完成，仍有文件需要处理，请查看失败原因')
    }
  } finally {
    unifiedUploading.value = false
  }
}

function scheduleUnifiedPoll() {
  if (unifiedPollTimer) clearTimeout(unifiedPollTimer)
  if (!unifiedBatch.value || unifiedBatch.value.status !== 'IMPORTING') return
  unifiedPollTimer = setTimeout(async () => {
    const sessionId = staffSessionMap[selectedVisit.value]
    if (!sessionId || !unifiedBatch.value) return
    try {
      const response = await http.get(
        `/api/v1/sessions/${sessionId}/unified-imports/${unifiedBatch.value.id}`,
      )
      unifiedBatch.value = response.data.data
      if (unifiedBatch.value.status === 'IMPORTING') scheduleUnifiedPoll()
      else if (unifiedBatch.value.status === 'COMPLETED') ElMessage.success('综合资料后台导入完成')
      else ElMessage.warning('综合资料部分导入失败，请查看任务结果后重试')
    } catch {
      scheduleUnifiedPoll()
    }
  }, 3000)
}

async function loadForm() {
  if (!selectedSubject.value || !selectedVisit.value) return
  loading.value = true
  Object.keys(responses).forEach(k => delete responses[k])
  Object.keys(checkboxGroups).forEach(k => delete checkboxGroups[k])
  Object.keys(attachmentStates).forEach(k => delete attachmentStates[k])
  try {
    const [res, saved] = await Promise.all([
      http.get('/api/v1/scales/visit-form/' + selectedVisit.value),
      http.get('/api/v1/scales/responses', {
        params: { subjectId: selectedSubject.value, visitCode: selectedVisit.value,
          sessionId: isPatient.value ? patientSessionMap[selectedVisit.value] : undefined },
      }),
    ])
    if (res.data.code === 200) {
      const d = res.data.data
      formData.value = { visitCode: d.visitCode || selectedVisit.value, visitName: d.visitName || '',
        scales: (d.scales || []).map((s: any) => ({
          code: s.code,
          name: s.name || s.code,
          maxScore: s.maxScore || 0,
          cutoff: s.cutoff || null,
          itemCount: s.itemCount || 0,
          items: [],
        })) }
      const assignedScaleCodes = currentPatientTask.value?.scaleCodes || []
      if (isPatient.value && assignedScaleCodes.length) {
        formData.value.scales = formData.value.scales.filter(scale =>
          assignedScaleCodes.includes(scale.code),
        )
      }
      if (formData.value.scales.length) nextTick(() => lazyLoad(formData.value!.scales[0].code))
    }
    if (saved.data.code === 200 && saved.data.data) {
      Object.assign(responses, saved.data.data)
      await Promise.all(Object.entries(saved.data.data).map(async ([fieldCode, value]: [string, any]) => {
        if (!value?.attachmentId) return
        try {
          const metadata = await scaleApi.getVisitAttachment(value.attachmentId)
          attachmentStates[fieldCode] = {
            attachment: metadata.data.data,
            progress: 100,
            uploading: false,
            recognizedText: value.recognizedText || '',
          }
        } catch {
          attachmentStates[fieldCode] = {
            progress: 0,
            uploading: false,
            error: '已保存的附件不存在，请重新上传',
          }
        }
      }))
    }
  } catch { ElMessage.error('加载失败') } finally { loading.value = false }
}

async function lazyLoad(scaleCode: string) {
  const scale = formData.value?.scales.find(s => s.code === scaleCode)
  if (!scale || scale.items.length > 0) return
  scale._loading = true
  try {
    const res = await http.get('/api/v1/scales/visit-form/' + selectedVisit.value + '/scale/' + scaleCode)
    if (res.data.code === 200 && res.data.data?.items) {
      // 标准化选项（兼容字符串格式 "1=xxx" 和结构化格式 {code,label,score}）
      const normalized = (res.data.data.items || []).map((item: any) => ({
        ...item,
        options: normalizeOptions(item.options),
      }))
      // 合并多选子项、去重、排序
      const merged = mergeAndDeduplicate(normalized)
      const patientItems = isPatient.value
        ? merged.filter((item: ScaleField) => {
            if (/_(TOTAL|SCORE)$/i.test(item.code)) return false
            const scoringOptions = item.options?.length > 0
              && item.options.every(option => /分$/.test(option.label))
            if (!scoringOptions) return true
            return !merged.some((candidate: ScaleField) =>
              candidate.code !== item.code
              && candidate.name === item.name
              && (!candidate.options?.length
                || !candidate.options.every(option => /分$/.test(option.label))))
          })
        : merged
      scale.items = patientItems
      // 初始化 checkboxGroups（使用合并后的 code）
      for (const item of patientItems) {
        if (isCheckbox(item.type)) {
          checkboxGroups[item.code] = Array.isArray(responses[item.code]) ? [...responses[item.code]] : []
        }
      }
    }
  } catch { /* silent */ } finally { scale._loading = false }
}

async function saveDraft() {
  if (patientTaskLocked.value || !selectedSubject.value || Object.keys(responses).length === 0) return
  const sessionId = isPatient.value
    ? patientSessionMap[selectedVisit.value]
    : staffSessionMap[selectedVisit.value]
  if (!sessionId) return
  saving.value = true
  try { await http.post('/api/v1/scales/save-draft', { subjectId: selectedSubject.value, visitCode: selectedVisit.value, sessionId, responses: { ...responses } }); ElMessage.success('已保存') }
  catch { /* ignore */ } finally { saving.value = false }
}

async function uploadAttachment(item: ScaleField, options: any) {
  if (!selectedSubject.value) {
    options.onError?.(new Error('请先选择受试者'))
    return
  }
  const controller = new AbortController()
  attachmentControllers.get(item.code)?.abort()
  attachmentControllers.set(item.code, controller)
  const state = attachmentStates[item.code] || { progress: 0, uploading: false }
  state.progress = 0
  state.uploading = true
  state.error = undefined
  attachmentStates[item.code] = state

  const formData = new FormData()
  formData.append('file', options.file)
  formData.append('subjectId', String(selectedSubject.value))
  formData.append('visitCode', selectedVisit.value)
  formData.append('fieldCode', item.code)

  try {
    const response = await scaleApi.uploadVisitAttachment(formData, {
      signal: controller.signal,
      onProgress: (percent) => {
        state.progress = percent
        options.onProgress?.({ percent })
      },
    })
    state.attachment = response.data.data
    state.progress = 100
    responses[item.code] = { attachmentId: response.data.data.id }
    options.onSuccess?.(response.data.data)
    ElMessage.success('附件上传成功')
  } catch (error: any) {
    if (error?.name !== 'CanceledError' && error?.code !== 'ERR_CANCELED') {
      state.error = error?.response?.data?.message || error?.message || '上传失败，请重试'
      options.onError?.(error)
    }
  } finally {
    state.uploading = false
    attachmentControllers.delete(item.code)
  }
}

async function analyzeAttachment(fieldCode: string) {
  const state = attachmentStates[fieldCode]
  if (!state?.attachment || state.analyzing) return
  state.analyzing = true
  state.analysisError = undefined
  try {
    const response = await scaleApi.analyzeVisitAttachment(state.attachment.id)
    state.recognizedText = response.data.data.text
    responses[fieldCode] = {
      attachmentId: state.attachment.id,
      recognizedText: response.data.data.text,
      extractedFields: response.data.data.fields,
      analysisMethod: response.data.data.method,
      doctorConfirmed: false,
    }
    ElMessage.success('文字分析完成，请核对识别草稿')
  } catch (error: any) {
    state.analysisError = error?.response?.data?.message || error?.message || '文字分析失败'
  } finally {
    state.analyzing = false
  }
}

function cancelAttachmentUpload(fieldCode: string) {
  attachmentControllers.get(fieldCode)?.abort()
  attachmentControllers.delete(fieldCode)
  const state = attachmentStates[fieldCode]
  if (state) {
    state.uploading = false
    state.error = '已取消上传'
  }
}

async function removeAttachment(fieldCode: string) {
  const state = attachmentStates[fieldCode]
  if (!state?.attachment) return
  try {
    await scaleApi.deleteVisitAttachment(state.attachment.id)
    delete responses[fieldCode]
    delete attachmentStates[fieldCode]
    ElMessage.success('附件已删除')
  } catch {
    // Global HTTP interceptor provides the server error message.
  }
}

function attachmentDownloadUrl(fieldCode: string): string {
  const id = attachmentStates[fieldCode]?.attachment?.id
  return id ? scaleApi.visitAttachmentDownloadUrl(id) : '#'
}

async function submitForm() {
  if (patientTaskLocked.value) {
    ElMessage.warning('该任务已经提交，需由医生退回后才能修改')
    return
  }
  const unconfirmed = Object.values(responses).some(
    value => value && typeof value === 'object' && value.recognizedText && !value.doctorConfirmed,
  )
  if (unconfirmed) {
    ElMessage.warning('存在尚未由医生核对的图片/PDF识别文字，请确认后再提交')
    return
  }
  const sessionId = isPatient.value
    ? patientSessionMap[selectedVisit.value]
    : staffSessionMap[selectedVisit.value]
  if (!selectedSubject.value || !sessionId) {
    ElMessage.warning('当前访视尚未创建，无法提交')
    return
  }
  submitting.value = true
  try { await http.post('/api/v1/scales/submit', { subjectId: selectedSubject.value, visitCode: selectedVisit.value, sessionId, responses: { ...responses } }); ElMessage.success('提交成功'); Object.keys(responses).forEach(k => delete responses[k]); if (isPatient.value) await fetchSubjects() }
  catch { ElMessage.error('提交失败') } finally { submitting.value = false }
}

function scrollTo(scaleCode: string) { activeScale.value = scaleCode; lazyLoad(scaleCode); nextTick(() => document.getElementById('scale-' + scaleCode)?.scrollIntoView({ behavior: 'smooth', block: 'start' })) }

// ---- Lifecycle ----
let autoSaveTimer: any
onMounted(async () => {
  if (!authStore.userInfo) await authStore.fetchUserInfo()
  await fetchSubjects()
  autoSaveTimer = setInterval(saveDraft, 60000)
})
onUnmounted(() => {
  clearInterval(autoSaveTimer)
  if (unifiedPollTimer) clearTimeout(unifiedPollTimer)
  attachmentControllers.forEach(controller => controller.abort())
  attachmentControllers.clear()
})
watch(selectedSubject, async () => {
  Object.keys(staffSessionMap).forEach(key => delete staffSessionMap[key])
  unifiedBatch.value = undefined
  await fetchStaffSessions()
})
watch([selectedSubject, selectedVisit], () => { if (selectedSubject.value) loadForm() })
watch(selectedVisit, loadUnifiedBatch)
</script>

<template>
  <div class="visit-entry">
    <div class="entry-heading">
      <div>
        <h1>{{ isPatient ? '我的量表任务' : '临床评估与数据录入' }}</h1>
        <p>{{ isPatient ? '请完成本次访视分配给你的量表，系统会自动保存填写进度。' : '按临床资料、认知评估、心理与精神评估、功能与生活质量、检验检查和附件资料完成本次访视。' }}</p>
      </div>
      <el-tag type="info" effect="plain">完整字段字典：10,290 项</el-tag>
    </div>
    <!-- Top Bar -->
    <div class="top-bar">
      <div class="top-left">
        <el-select v-if="!isPatient" v-model="selectedSubject" placeholder="选择受试者" filterable style="width:200px">
          <el-option v-for="s in subjects" :key="s.id" :label="s.subjectId" :value="s.id" />
        </el-select>
        <el-select v-model="selectedVisit" style="width:160px;margin-left:12px">
          <el-option v-for="v in visitOptions" :key="v.code" :label="v.name" :value="v.code" />
        </el-select>
      </div>
      <div class="top-center">
        <el-progress :percentage="progressPct" :color="progressPct===100?'#67c23a':'#409eff'" :stroke-width="14">
          <span style="font-size:12px">{{ completedScales }}/{{ totalScales }} 个表单</span>
        </el-progress>
      </div>
      <div class="top-right">
        <el-button @click="saveDraft" :loading="saving" :disabled="patientTaskLocked">保存草稿</el-button>
        <el-button type="primary" @click="submitForm" :loading="submitting" :disabled="patientTaskLocked">
          {{ patientTaskLocked ? taskStatusLabel(currentPatientTask?.status) : '核对并提交' }}
        </el-button>
      </div>
    </div>

    <el-alert
      v-if="isPatient && currentPatientTask?.status === 'RETURNED'"
      :title="`医生已退回本任务：${currentPatientTask.returnReason || '请补充或修正后重新提交'}`"
      type="warning"
      :closable="false"
      show-icon
      style="margin-bottom: 16px"
    />
    <el-alert
      v-else-if="patientTaskLocked"
      title="该任务已提交并锁定。如需修改，请联系医生退回。"
      type="success"
      :closable="false"
      show-icon
      style="margin-bottom: 16px"
    />

    <div v-if="!selectedSubject" class="empty-hint"><el-empty description="请先选择受试者和访视" /></div>
    <div v-else-if="loading" v-loading="loading" style="min-height:400px" />

    <el-card v-else-if="!isPatient" class="unified-import-card" shadow="never">
      <template #header>
        <div class="card-header">
          <div>
            <strong>本次访视综合资料</strong>
            <div class="hint">只上传一次患者 ZIP/RAR，系统自动识别影像、遗传、实验室和其他附件。</div>
          </div>
        </div>
      </template>
      <el-upload :auto-upload="false" :limit="1" accept=".zip,.rar" :on-change="onUnifiedFileChange">
        <el-button>选择综合压缩包</el-button>
      </el-upload>
      <el-button
        type="primary"
        :loading="unifiedUploading"
        :disabled="!unifiedFile"
        style="margin-top: 12px"
        @click="analyzeUnifiedPackage"
      >上传并自动识别</el-button>
      <el-progress v-if="unifiedUploading" :percentage="unifiedProgress" style="margin-top: 12px" />
      <div v-if="unifiedBatch" class="unified-result">
        <el-alert
          :title="`已识别 ${unifiedBatch.totalFiles} 个文件，状态：${unifiedBatch.status}`"
          :type="['FAILED', 'PARTIAL_FAILED'].includes(unifiedBatch.status) ? 'warning' : 'success'"
          :closable="false"
        />
        <el-alert
          v-if="unifiedBatch.itemsTruncated"
          :title="`文件较多，页面仅展示前 ${unifiedBatch.previewItemCount} 条；后台已完整保存 ${unifiedBatch.totalFiles} 条。`"
          type="info"
          :closable="false"
          style="margin-top: 8px"
        />
        <el-table :data="unifiedBatch.items" max-height="360" style="margin-top: 12px">
          <el-table-column prop="relativePath" label="文件" min-width="360" show-overflow-tooltip />
          <el-table-column label="分类" width="170">
            <template #default="{ row }">
              <el-select v-model="row.confirmedModule" :disabled="unifiedBatch.status !== 'AWAITING_CONFIRMATION'">
                <el-option label="影像" value="IMAGING" />
                <el-option label="遗传" value="GENETICS" />
                <el-option label="实验室" value="LAB" />
                <el-option label="访视附件" value="ATTACHMENT" />
                <el-option label="其他" value="OTHER" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="置信度" width="100">
            <template #default="{ row }">{{ Math.round(Number(row.confidence) * 100) }}%</template>
          </el-table-column>
          <el-table-column label="纳入" width="80">
            <template #default="{ row }">
              <el-switch v-model="row.included" :disabled="unifiedBatch.status !== 'AWAITING_CONFIRMATION'" />
            </template>
          </el-table-column>
          <el-table-column prop="status" label="导入状态" width="130" />
          <el-table-column prop="errorMessage" label="失败原因" min-width="220" show-overflow-tooltip />
        </el-table>
        <el-table
          v-if="unifiedBatch.jobs?.length"
          :data="unifiedBatch.jobs"
          size="small"
          style="margin-top: 12px"
        >
          <el-table-column prop="module" label="模块" width="130" />
          <el-table-column prop="itemCount" label="文件数" width="90" />
          <el-table-column prop="status" label="任务状态" width="140" />
          <el-table-column prop="errorMessage" label="任务说明" min-width="260" show-overflow-tooltip />
        </el-table>
        <el-button
          v-if="unifiedBatch.status === 'AWAITING_CONFIRMATION'"
          type="success"
          style="margin-top: 12px"
          @click="confirmUnifiedPackage"
        >确认分类并建立模块任务</el-button>
        <el-button
          v-if="['FAILED', 'PARTIAL_FAILED'].includes(unifiedBatch.status)"
          type="warning"
          :loading="unifiedUploading"
          style="margin-top: 12px"
          @click="retryUnifiedPackage"
        >仅重试失败文件</el-button>
      </div>
    </el-card>

    <!-- Two-column layout -->
    <div v-if="selectedSubject && !loading" class="entry-body">
      <!-- Left Nav -->
      <div class="scale-nav">
        <template v-for="section in clinicalSections" :key="section.code">
          <div class="nav-section-title">{{ section.name }}</div>
          <div v-for="scale in section.scales" :key="scale.code" class="nav-item"
               :class="{ active: activeScale === scale.code }" @click="scrollTo(scale.code)">
            <span class="nav-dot" :style="{background:scaleProgressColor(scaleProgress(scale))}" />
            <span class="nav-name">{{ scale.name || scale.code }}</span>
            <span v-if="scale._loading" style="color:#409eff;font-size:11px">…</span>
            <span v-else class="nav-pct">{{ scaleProgress(scale) }}%</span>
          </div>
        </template>
      </div>

      <!-- Right Form -->
      <div class="form-area">
        <template v-for="section in clinicalSections" :key="section.code">
        <div class="clinical-section-heading">
          <span>{{ section.name }}</span>
          <small>{{ section.scales.length }} 个表单</small>
        </div>
        <div v-for="scale in section.scales" :key="scale.code" :id="'scale-'+scale.code" class="scale-card">
          <div class="scale-card-header">
            <span class="scale-title">{{ scale.name || scale.code }}</span>
            <span v-if="scaleScore(scale)" class="scale-score-text" :style="{color:scaleProgress(scale)===100?'#67c23a':'#909399'}">
              得分：{{ scaleScore(scale) }}
            </span>
          </div>

          <!-- Cards grouped by topic within this scale -->
          <div v-if="scale.items.length > 0" class="card-grid">
            <div v-for="card in cardGroups(scale.items)" :key="card.prefix" class="topic-card">
              <div class="topic-card-title">{{ card.title }}</div>
              <el-form label-position="top" size="default" class="question-form" :disabled="patientTaskLocked">
                <div v-for="item in card.items" :key="item.code" class="question-item">
                  <el-form-item :label="displayLabel(item)" :required="item.required" class="question-label">
                    <!-- CHECKBOX -->
                    <el-checkbox-group v-if="isCheckbox(item.type)" v-model="checkboxGroups[item.code]" class="checkbox-group">
                      <el-checkbox v-for="opt in item.options" :key="opt.code" :label="opt.code">
                        {{ cleanOptionLabel(opt.label) }}
                      </el-checkbox>
                    </el-checkbox-group>

                    <!-- LONG → SELECT -->
                    <el-select v-else-if="isLongList(item)" v-model="responses[item.code]" placeholder="请选择" class="form-select">
                      <el-option v-for="opt in item.options" :key="opt.code" :label="cleanOptionLabel(opt.label)" :value="opt.code" />
                    </el-select>

                    <!-- HAS OPTIONS → RADIO -->
                    <el-radio-group v-else-if="hasOptions(item)" v-model="responses[item.code]" class="radio-group">
                      <el-radio v-for="opt in item.options" :key="opt.code" :value="opt.code">
                        {{ cleanOptionLabel(opt.label) }}
                      </el-radio>
                    </el-radio-group>

                    <!-- DATE -->
                    <el-date-picker v-else-if="isDateType(item.type)" v-model="responses[item.code]" type="date" placeholder="选择日期" class="form-datepicker" value-format="YYYY-MM-DD" />

                    <!-- SWITCH -->
                    <el-switch v-else-if="isSwitchType(item.type)" v-model="responses[item.code]" active-value="1" inactive-value="0" />

                    <!-- FILE -->
                    <div v-else-if="isFileType(item.type)" class="attachment-field">
                      <div v-if="attachmentStates[item.code]?.attachment" class="attachment-result">
                        <a :href="attachmentDownloadUrl(item.code)" target="_blank" rel="noopener">
                          {{ attachmentStates[item.code].attachment!.originalName }}
                        </a>
                        <span>{{ Math.ceil(attachmentStates[item.code].attachment!.size / 1024) }} KB</span>
                        <el-button text type="primary" :loading="attachmentStates[item.code]?.analyzing"
                          @click="analyzeAttachment(item.code)">分析图片/PDF文字</el-button>
                        <el-button text type="danger" @click="removeAttachment(item.code)">删除</el-button>
                      </div>
                      <div v-if="attachmentStates[item.code]?.recognizedText" class="attachment-analysis">
                        <div class="analysis-title">识别草稿（请对照原文件核对）</div>
                        <el-input v-model="attachmentStates[item.code].recognizedText" type="textarea" :rows="6"
                          @input="responses[item.code].recognizedText = attachmentStates[item.code].recognizedText" />
                        <el-checkbox v-model="responses[item.code].doctorConfirmed">我已核对识别文字</el-checkbox>
                      </div>
                      <div v-if="attachmentStates[item.code]?.analysisError" class="attachment-error">
                        {{ attachmentStates[item.code].analysisError }}
                      </div>
                      <el-upload
                        v-else
                        :auto-upload="true"
                        :show-file-list="false"
                        :http-request="(options:any) => uploadAttachment(item, options)"
                        :disabled="attachmentStates[item.code]?.uploading"
                        drag
                        class="form-upload"
                      >
                        <el-icon><Plus /></el-icon>
                        <span class="upload-label">
                          {{ attachmentStates[item.code]?.uploading ? '上传中…' : '选择或拖入文件' }}
                        </span>
                      </el-upload>
                      <el-progress
                        v-if="attachmentStates[item.code]?.uploading"
                        :percentage="attachmentStates[item.code].progress"
                        :stroke-width="8"
                      />
                      <div v-if="attachmentStates[item.code]?.uploading" class="attachment-actions">
                        <el-button text type="danger" @click="cancelAttachmentUpload(item.code)">取消</el-button>
                      </div>
                      <div v-if="attachmentStates[item.code]?.error" class="attachment-error">
                        {{ attachmentStates[item.code].error }}
                      </div>
                    </div>

                    <!-- PARAGRAPH -->
                    <el-input v-else-if="isParagraph(item.type)" v-model="responses[item.code]" type="textarea" :rows="3" placeholder="请输入" class="form-textarea" />

                    <!-- NUMBER (true numeric) -->
                    <div v-else-if="isNumber(item.type)" class="number-with-unit">
                      <el-input-number v-model="responses[item.code]" :controls="true" :min="0" class="form-number" placeholder="请输入数字" />
                      <span v-if="formatUnit(item)" class="unit-suffix">{{ formatUnit(item) }}</span>
                    </div>

                    <!-- DEFAULT: TEXT -->
                    <el-input v-else v-model="responses[item.code]" placeholder="请输入" class="form-input" />
                  </el-form-item>
                </div>
              </el-form>
            </div>
          </div>

          <div v-else class="scale-empty">
            <span v-if="scale._loading">加载中...</span>
            <span v-else style="color:#909399;cursor:pointer" @click="scrollTo(scale.code)">点击加载</span>
          </div>
        </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script lang="ts">export default { name: 'VisitEntryView' }</script>

<style scoped lang="scss">
.entry-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 18px;

  h1 {
    margin: 0 0 6px;
    color: #1f2937;
    font-size: 22px;
    line-height: 1.35;
  }

  p {
    margin: 0;
    color: #4b5563;
    font-size: 14px;
    line-height: 1.6;
  }
}

.visit-entry {
  padding: 0; 
  min-height: 100vh;
  background-color: #f5f7fa;
}

/* Top Bar */
.top-bar { 
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff; 
  padding: 12px 24px; 
  border-radius: 12px; 
  margin: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

.top-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.top-center { 
  flex: 1; 
  max-width: 300px;
  margin: 0 32px;
}

.top-right {
  display: flex;
  gap: 12px;
}

.empty-hint { 
  padding: 100px 0; 
  text-align: center;
}

/* Entry Body Layout */
.entry-body { 
  display: flex; 
  gap: 16px;
  padding: 0 16px 16px;
}

/* Left Navigation */
.scale-nav { 
  width: 180px; 
  flex-shrink: 0; 
  position: sticky; 
  top: 16px; 
  align-self: flex-start; 
  background: #fff; 
  border-radius: 12px; 
  padding: 12px; 
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  max-height: calc(100vh - 120px); 
  overflow-y: auto;
}

.nav-section-title {
  padding: 16px 12px 6px;
  color: #30445a;
  font-size: 13px;
  font-weight: 700;
}

.nav-item { 
  display: flex; 
  align-items: center; 
  gap: 8px; 
  padding: 10px 12px; 
  cursor: pointer; 
  border-radius: 8px; 
  font-size: 13px; 
  color: #606266; 
  transition: all 0.2s;
  margin-bottom: 4px;
  &:hover { 
    background: #f5f7fa; 
  } 
  &.active { 
    background: #ecf5ff; 
    color: #409eff; 
    font-weight: 600; 
  } 
}

.nav-dot { 
  width: 8px; 
  height: 8px; 
  border-radius: 50%; 
  flex-shrink: 0; 
}

.nav-pct { 
  margin-left: auto; 
  font-size: 12px; 
  color: #c0c4cc; 
}

/* Form Area */
.form-area { 
  flex: 1; 
  min-width: 0; 
}

.clinical-section-heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin: 24px 0 12px;
  padding: 0 2px 9px;
  border-bottom: 2px solid #d8e2ec;
  color: #24364b;
  font-size: 18px;
  font-weight: 700;
}

.clinical-section-heading:first-child { margin-top: 0; }
.clinical-section-heading small { color: #65758a; font-size: 12px; font-weight: 500; }

/* Scale Card */
.scale-card { 
  background: #fff; 
  border-radius: 12px; 
  padding: 24px; 
  margin-bottom: 16px; 
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

.scale-card-header { 
  display: flex; 
  justify-content: space-between; 
  align-items: center; 
  margin-bottom: 20px; 
  padding-bottom: 16px; 
  border-bottom: 2px solid #409eff;
}

.scale-title { 
  font-size: 18px; 
  font-weight: 600; 
  color: #303133; 
}

.scale-score-text { 
  font-size: 14px; 
  font-weight: 600; 
}

.scale-empty { 
  text-align: center; 
  padding: 40px 0; 
  color: #c0c4cc; 
}

/* Card Grid */
.card-grid { 
  display: flex; 
  flex-direction: column; 
  gap: 16px; 
}

/* Topic Card */
.topic-card { 
  background: #fafbfc; 
  border: 1px solid #ebeef5; 
  border-radius: 10px; 
  padding: 20px;
}

.topic-card-title { 
  font-size: 15px; 
  font-weight: 600; 
  color: #303133; 
  margin-bottom: 16px; 
  padding-bottom: 12px; 
  border-bottom: 1px dashed #e4e7ed;
}

/* Question Form */
.question-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.question-item {
  width: 100%;
}

.question-label {
  margin-bottom: 0;
}

/* Form Controls */
:deep(.el-form-item) {
  margin-bottom: 0;
}

:deep(.el-form-item__label) {
  font-size: 14px;
  font-weight: 500;
  padding-bottom: 8px;
  color: #303133;
  line-height: 1.5;
}

:deep(.el-form-item__label.is-required)::before {
  content: '*';
  color: #f56c6c;
  margin-right: 4px;
}

/* Radio Group */
.radio-group {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}

:deep(.radio-group .el-radio) {
  margin-right: 0;
  margin-bottom: 8px;
  padding: 8px 16px;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #dcdfe6;
  transition: all 0.2s;
  
  &:hover {
    border-color: #409eff;
    background: #ecf5ff;
  }
  
  &.is-checked {
    background: #ecf5ff;
    border-color: #409eff;
  }
}

:deep(.radio-group .el-radio__label) {
  font-size: 14px;
  color: #606266;
}

/* Checkbox Group */
.checkbox-group {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 12px;
}

:deep(.checkbox-group .el-checkbox) {
  padding: 8px 12px;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #dcdfe6;
  transition: all 0.2s;
  
  &:hover {
    border-color: #409eff;
    background: #ecf5ff;
  }
  
  &.is-checked {
    background: #ecf5ff;
    border-color: #409eff;
  }
}

:deep(.checkbox-group .el-checkbox__label) {
  font-size: 14px;
  color: #606266;
}

/* Form Select */
.form-select {
  width: 100%;
}

/* Form Datepicker */
.form-datepicker {
  width: 100%;
}

/* Form Upload */
.form-upload {
  width: 100%;
}

.attachment-field {
  width: 100%;
}

.upload-label {
  margin-left: 8px;
  color: #303133;
  font-size: 13px;
}

.attachment-result {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 10px 12px;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  color: #303133;

  a {
    flex: 1;
    overflow: hidden;
    color: #1f5f99;
    font-weight: 600;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  span {
    color: #4b5563;
    font-size: 12px;
  }
}

.attachment-actions {
  display: flex;
  justify-content: flex-end;
}

.attachment-error {
  margin-top: 6px;
  color: #b42318;
  font-size: 12px;
  font-weight: 600;
}

.attachment-analysis {
  margin-top: 10px;
  padding: 12px;
  border: 1px solid #c7d7e5;
  border-radius: 8px;
  background: #f7fafc;
}

.analysis-title {
  margin-bottom: 8px;
  color: #34495e;
  font-weight: 600;
}

/* Form Textarea */
.form-textarea {
  width: 100%;
}

/* Form Number + Unit */
.number-with-unit {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}

.form-number {
  flex: 1;
  min-width: 120px;
}

.unit-suffix {
  font-size: 14px;
  color: #606266;
  font-weight: 500;
  white-space: nowrap;
  background: #f5f7fa;
  padding: 4px 12px;
  border-radius: 6px;
  border: 1px solid #e4e7ed;
}

/* Form Input */
.form-input {
  width: 100%;
}

/* Option Hint */
.opt-hint { 
  color: #909399; 
  font-size: 12px; 
  margin-left: 4px; 
}

/* Responsive */
@media (max-width: 768px) {
  .entry-body {
    flex-direction: column;
  }
  
  .scale-nav {
    width: 100%;
    position: relative;
    max-height: 200px;
  }
  
  .top-bar {
    flex-direction: column;
    gap: 12px;
    padding: 16px;
  }
  
  .top-center {
    margin: 0;
    width: 100%;
    max-width: none;
  }
  
  .checkbox-group {
    grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  }
}
</style>
