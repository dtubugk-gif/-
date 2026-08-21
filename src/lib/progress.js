// ניהול התקדמות ב-localStorage: XP, רצף, לבבות, כתרים, טעויות והישגים.

import { UNITS, LESSONS_PER_UNIT, lessonKey } from '../data/course'

const STORAGE_KEY = 'linguago-progress-v1'

export const MAX_HEARTS = 5
export const HEART_REGEN_MINUTES = 30
export const MAX_CROWNS = 3
export const XP_PER_LESSON = 10
export const XP_PERFECT_BONUS = 5
export const DEFAULT_DAILY_GOAL = 30

function todayStr() {
  return new Date().toISOString().slice(0, 10)
}

function defaultState() {
  return {
    xp: 0,
    streak: 0,
    lastActiveDate: null,
    hearts: MAX_HEARTS,
    lastHeartTime: Date.now(),
    dailyGoal: DEFAULT_DAILY_GOAL,
    xpTodayDate: todayStr(),
    xpToday: 0,
    lessons: {}, // key -> { crowns }
    mistakes: [], // [{ en, he, emoji }]
    achievements: [], // achievement ids
    totalLessons: 0,
    perfectLessons: 0,
    practiceSessions: 0,
  }
}

export function loadState() {
  let state
  try {
    state = { ...defaultState(), ...JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}') }
  } catch {
    state = defaultState()
  }
  // חידוש לבבות לפי זמן שעבר
  if (state.hearts < MAX_HEARTS) {
    const regen = Math.floor((Date.now() - state.lastHeartTime) / (HEART_REGEN_MINUTES * 60 * 1000))
    if (regen > 0) {
      state.hearts = Math.min(MAX_HEARTS, state.hearts + regen)
      state.lastHeartTime = state.hearts === MAX_HEARTS ? Date.now() : state.lastHeartTime + regen * HEART_REGEN_MINUTES * 60 * 1000
    }
  }
  // איפוס XP יומי אם התחלף יום
  if (state.xpTodayDate !== todayStr()) {
    state.xpTodayDate = todayStr()
    state.xpToday = 0
  }
  // שבירת רצף אם פספסו יותר מיום
  if (state.lastActiveDate) {
    const diff = daysBetween(state.lastActiveDate, todayStr())
    if (diff > 1) state.streak = 0
  }
  return state
}

export function saveState(state) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(state))
}

function daysBetween(a, b) {
  return Math.round((new Date(b) - new Date(a)) / (24 * 60 * 60 * 1000))
}

export function loseHeart(state) {
  const next = { ...state }
  if (next.hearts === MAX_HEARTS) next.lastHeartTime = Date.now()
  next.hearts = Math.max(0, next.hearts - 1)
  saveState(next)
  return next
}

export function refillHearts(state) {
  const next = { ...state, hearts: MAX_HEARTS, lastHeartTime: Date.now() }
  saveState(next)
  return next
}

export function addMistakes(state, words) {
  const next = { ...state }
  const seen = new Set(next.mistakes.map((m) => m.en))
  for (const w of words) {
    if (!seen.has(w.en)) {
      next.mistakes = [...next.mistakes, { en: w.en, he: w.he, emoji: w.emoji }]
      seen.add(w.en)
    }
  }
  saveState(next)
  return next
}

export function clearMistakes(state, ens) {
  const remove = new Set(ens)
  const next = { ...state, mistakes: state.mistakes.filter((m) => !remove.has(m.en)) }
  saveState(next)
  return next
}

function bumpStreakAndXp(next, xpGained) {
  const today = todayStr()
  if (next.lastActiveDate !== today) {
    const diff = next.lastActiveDate ? daysBetween(next.lastActiveDate, today) : Infinity
    next.streak = diff === 1 ? next.streak + 1 : 1
    next.lastActiveDate = today
  }
  next.xp += xpGained
  if (next.xpTodayDate !== today) {
    next.xpTodayDate = today
    next.xpToday = 0
  }
  next.xpToday += xpGained
}

// סיום שיעור: מחזיר { state, xpGained, newAchievements }
export function completeLesson(state, unitId, lessonIndex, perfect) {
  const next = { ...state, lessons: { ...state.lessons } }
  const key = lessonKey(unitId, lessonIndex)
  const prev = next.lessons[key] || { crowns: 0 }
  next.lessons[key] = { crowns: Math.min(MAX_CROWNS, prev.crowns + 1) }
  next.totalLessons += 1
  if (perfect) next.perfectLessons += 1

  const xpGained = XP_PER_LESSON + (perfect ? XP_PERFECT_BONUS : 0)
  bumpStreakAndXp(next, xpGained)

  const newAchievements = checkAchievements(next)
  saveState(next)
  return { state: next, xpGained, newAchievements }
}

// סיום תרגול טעויות: XP קטן + החזרת לב
export function completePractice(state) {
  const next = { ...state }
  next.practiceSessions += 1
  bumpStreakAndXp(next, 5)
  if (next.hearts < MAX_HEARTS) {
    if (next.hearts === MAX_HEARTS - 1) next.lastHeartTime = Date.now()
    next.hearts += 1
  }
  const newAchievements = checkAchievements(next)
  saveState(next)
  return { state: next, xpGained: 5, newAchievements }
}

export function setDailyGoal(state, goal) {
  const next = { ...state, dailyGoal: goal }
  saveState(next)
  return next
}

export function getCrowns(state, unitId, lessonIndex) {
  return state.lessons[lessonKey(unitId, lessonIndex)]?.crowns || 0
}

export function isLessonCompleted(state, unitId, lessonIndex) {
  return getCrowns(state, unitId, lessonIndex) > 0
}

export function isUnitCompleted(state, unitId) {
  for (let i = 0; i < LESSONS_PER_UNIT; i++) {
    if (!isLessonCompleted(state, unitId, i)) return false
  }
  return true
}

// יחידה פתוחה אם היא הראשונה או שהקודמת הושלמה
export function isUnitUnlocked(state, unitIndex) {
  if (unitIndex === 0) return true
  return isUnitCompleted(state, UNITS[unitIndex - 1].id)
}

// שיעור פתוח אם היחידה פתוחה והשיעור הקודם בה הושלם
export function isLessonUnlocked(state, unitIndex, lessonIndex) {
  if (!isUnitUnlocked(state, unitIndex)) return false
  if (lessonIndex === 0) return true
  return isLessonCompleted(state, UNITS[unitIndex].id, lessonIndex - 1)
}

export const ACHIEVEMENTS = [
  { id: 'first-lesson', icon: '🐣', title: 'צעד ראשון', desc: 'השלמת שיעור ראשון', check: (s) => s.totalLessons >= 1 },
  { id: 'ten-lessons', icon: '📚', title: 'תלמיד מתמיד', desc: 'השלמת 10 שיעורים', check: (s) => s.totalLessons >= 10 },
  { id: 'perfect', icon: '💯', title: 'מושלם!', desc: 'שיעור בלי אף טעות', check: (s) => s.perfectLessons >= 1 },
  { id: 'five-perfect', icon: '🎯', title: 'צלף', desc: '5 שיעורים מושלמים', check: (s) => s.perfectLessons >= 5 },
  { id: 'streak-3', icon: '🔥', title: 'מתחממים', desc: 'רצף של 3 ימים', check: (s) => s.streak >= 3 },
  { id: 'streak-7', icon: '🌋', title: 'שבוע בוער', desc: 'רצף של 7 ימים', check: (s) => s.streak >= 7 },
  { id: 'xp-100', icon: '⭐', title: 'כוכב עולה', desc: 'צברת 100 XP', check: (s) => s.xp >= 100 },
  { id: 'xp-500', icon: '🌟', title: 'סופרסטאר', desc: 'צברת 500 XP', check: (s) => s.xp >= 500 },
  { id: 'unit-done', icon: '👑', title: 'כובש יחידות', desc: 'השלמת יחידה שלמה', check: (s) => UNITS.some((u) => isUnitCompletedRaw(s, u.id)) },
  { id: 'practice-5', icon: '💪', title: 'אלוף התרגול', desc: '5 אימוני חזרה על טעויות', check: (s) => s.practiceSessions >= 5 },
]

function isUnitCompletedRaw(state, unitId) {
  for (let i = 0; i < LESSONS_PER_UNIT; i++) {
    if (!(state.lessons[lessonKey(unitId, i)]?.crowns > 0)) return false
  }
  return true
}

function checkAchievements(state) {
  const fresh = []
  for (const a of ACHIEVEMENTS) {
    if (!state.achievements.includes(a.id) && a.check(state)) {
      state.achievements = [...state.achievements, a.id]
      fresh.push(a)
    }
  }
  return fresh
}
