<template>
  <div class="checkin-calendar">
    <!-- Header: streak stats -->
    <div class="streak-row" v-if="streak">
      <div class="streak-item">
        <span class="streak-val">{{ streak.currentStreak }}</span>
        <span class="streak-label">连续打卡</span>
      </div>
      <div class="streak-divider"></div>
      <div class="streak-item">
        <span class="streak-val">{{ streak.longestStreak }}</span>
        <span class="streak-label">最长连续</span>
      </div>
      <div class="streak-divider"></div>
      <div class="streak-item">
        <span class="streak-val">{{ streak.totalDays }}</span>
        <span class="streak-label">累计打卡</span>
      </div>
    </div>

    <!-- Month navigation -->
    <div class="cal-header">
      <button class="nav-arrow" @click="prevMonth">‹</button>
      <span class="cal-title">{{ displayYear }} 年 {{ displayMonth }} 月</span>
      <button class="nav-arrow" @click="nextMonth" :disabled="isCurrentMonth">›</button>
    </div>

    <!-- Weekday labels -->
    <div class="weekday-row">
      <span v-for="d in weekdays" :key="d" class="weekday-label">{{ d }}</span>
    </div>

    <!-- Calendar grid -->
    <div class="cal-grid">
      <span
        v-for="i in leadingBlanks"
        :key="'blank-' + i"
        class="cal-cell blank"
      ></span>
      <button
        v-for="day in daysInMonth"
        :key="day"
        class="cal-cell day-cell"
        :class="{
          checked: checkedSet.has(day),
          today: isToday(day),
          future: isFuture(day)
        }"
        :disabled="isFuture(day)"
        @click="handleDayClick(day)"
        :title="checkedSet.has(day) ? '已打卡' : '点击打卡'"
      >
        <span class="day-num">{{ day }}</span>
        <span v-if="checkedSet.has(day)" class="check-mark">✓</span>
      </button>
    </div>

    <!-- Checkin today button -->
    <div class="checkin-action" v-if="isCurrentMonth">
      <button
        class="btn btn-gold"
        :disabled="streak?.checkedToday || loadingCheckin"
        @click="doCheckin"
      >
        <span v-if="streak?.checkedToday">今日已打卡 ✓</span>
        <span v-else-if="loadingCheckin">打卡中…</span>
        <span v-else>今日打卡</span>
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { apiCheckin, apiGetCalendar, apiGetStreak } from '@/api/checkin'
import { useUserStore } from '@/stores/user'
import { useToast } from '@/composables/useToast'

const props = defineProps({
  novelId: { type: [Number, String], default: null }
})

const userStore = useUserStore()
const { show } = useToast()

const today = new Date()
const displayYear = ref(today.getFullYear())
const displayMonth = ref(today.getMonth() + 1)

const streak = ref(null)
const calendarData = ref(null)
const loadingCheckin = ref(false)

const weekdays = ['日', '一', '二', '三', '四', '五', '六']

const isCurrentMonth = computed(() => {
  return displayYear.value === today.getFullYear() && displayMonth.value === (today.getMonth() + 1)
})

const daysInMonth = computed(() => {
  return new Date(displayYear.value, displayMonth.value, 0).getDate()
})

const leadingBlanks = computed(() => {
  return new Date(displayYear.value, displayMonth.value - 1, 1).getDay()
})

const checkedSet = computed(() => {
  return new Set(calendarData.value?.checkedDays ?? [])
})

function isToday(day) {
  return isCurrentMonth.value && day === today.getDate()
}

function isFuture(day) {
  if (!isCurrentMonth.value) return false
  return day > today.getDate()
}

async function load() {
  if (!userStore.isLoggedIn) return
  try {
    const [cal, str] = await Promise.all([
      apiGetCalendar(displayYear.value, displayMonth.value),
      apiGetStreak()
    ])
    calendarData.value = cal
    streak.value = str
  } catch (e) {
    show(e.message)
  }
}

async function doCheckin() {
  if (!userStore.isLoggedIn) { show('请先登录'); return }
  if (loadingCheckin.value) return
  loadingCheckin.value = true
  try {
    await apiCheckin({ novelId: props.novelId ? Number(props.novelId) : null })
    show('打卡成功！继续保持 🎉')
    await load()
  } catch (e) {
    show(e.message)
  } finally {
    loadingCheckin.value = false
  }
}

function handleDayClick(day) {
  if (isToday(day) && !checkedSet.value.has(day)) {
    doCheckin()
  }
}

function prevMonth() {
  if (displayMonth.value === 1) {
    displayMonth.value = 12
    displayYear.value--
  } else {
    displayMonth.value--
  }
}

function nextMonth() {
  if (isCurrentMonth.value) return
  if (displayMonth.value === 12) {
    displayMonth.value = 1
    displayYear.value++
  } else {
    displayMonth.value++
  }
}

watch([displayYear, displayMonth], load)
onMounted(load)
</script>

<style scoped>
.checkin-calendar {
  background: var(--paper-0);
  border: 1px solid var(--paper-3);
  border-radius: var(--radius-lg);
  padding: var(--space-6);
}

/* Streak row */
.streak-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-6);
  margin-bottom: var(--space-6);
  padding-bottom: var(--space-5);
  border-bottom: 1px solid var(--paper-2);
}

.streak-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.streak-val {
  font-family: var(--font-serif);
  font-size: 1.75rem;
  font-weight: 700;
  color: var(--gold-0);
  line-height: 1;
}

.streak-label {
  font-size: 0.75rem;
  color: var(--ink-4);
}

.streak-divider {
  width: 1px;
  height: 32px;
  background: var(--paper-3);
}

/* Month header */
.cal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-4);
}

.cal-title {
  font-family: var(--font-serif);
  font-size: 1.0625rem;
  color: var(--ink-1);
}

.nav-arrow {
  width: 32px;
  height: 32px;
  border: 1px solid var(--paper-3);
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--ink-3);
  font-size: 1.25rem;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--transition-fast);
  line-height: 1;
}

.nav-arrow:hover:not(:disabled) {
  background: var(--paper-2);
  border-color: var(--ink-4);
}

.nav-arrow:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

/* Weekday row */
.weekday-row {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  margin-bottom: var(--space-2);
}

.weekday-label {
  text-align: center;
  font-size: 0.75rem;
  color: var(--ink-4);
  padding: var(--space-2) 0;
}

/* Grid */
.cal-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 4px;
}

.cal-cell {
  aspect-ratio: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-sm);
  position: relative;
}

.blank { background: transparent; }

.day-cell {
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 0.875rem;
  color: var(--ink-2);
  transition: background var(--transition-fast), color var(--transition-fast);
  gap: 1px;
}

.day-cell:hover:not(:disabled) {
  background: var(--paper-2);
}

.day-cell.today {
  background: var(--paper-2);
  color: var(--gold-0);
  font-weight: 600;
}

.day-cell.checked {
  background: linear-gradient(135deg, var(--gold-0), var(--gold-1));
  color: var(--paper-0) !important;
  box-shadow: 0 2px 6px rgba(200, 153, 58, 0.35);
}

.day-cell.future {
  opacity: 0.3;
  cursor: not-allowed;
}

.day-num {
  font-size: 0.875rem;
  line-height: 1;
}

.check-mark {
  font-size: 0.6rem;
  opacity: 0.9;
  line-height: 1;
}

/* Checkin button */
.checkin-action {
  margin-top: var(--space-5);
  display: flex;
  justify-content: center;
}
</style>
