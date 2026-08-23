// ניהול התקדמות ב-localStorage: XP, רצף, כתרים, טעויות והישגים.

import { LEVELS, LESSONS_PER_LEVEL, lessonKey } from '../data/course'

const STORAGE_KEY = 'linguago-progress-v1'

export const MAX_CROWNS = 3
export const XP_PER_LESSON = 10
export const XP_PERFECT_BONUS = 5
export const DEFAULT_DAILY_GOAL = 30

function todayStr() {
  return new Date().toISOString().slice(0, 10)
}

function defaultState() {
  return {
    profile: { done: false, goal: null, selfLevel: null, placementLevel: 1 },
    xp: 0,
    streak: 0,
    lastActiveDate: null,
    dailyGoal: DEFAULT_DAILY_GOAL,
    xpTodayDate: todayStr(),
    xpToday: 0,
    lessons: {}, // key -> { crowns }
    mistakes: [], // [{ en, he, emoji }]
    achievements: [], // achievement ids
    totalLessons: 0,
    perfectLessons: 0,
  }
}

export function loadState() {
  let state
  try {
    state = { ...defaultState(), ...JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}') }
  } catch {
    state = defaultState()
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

// סיום שיעור: מחזיר { state, xpGained, hintPenalty, newAchievements }.
// כל רמז שנפתח בשיעור מוריד נקודת XP (עד רצפה של 3 — תמיד מרוויחים משהו).
export function completeLesson(state, levelId, lessonIndex, perfect, hintsUsed = 0) {
  const next = { ...state, lessons: { ...state.lessons } }
  const key = lessonKey(levelId, lessonIndex)
  const prev = next.lessons[key] || { crowns: 0 }
  next.lessons[key] = { crowns: Math.min(MAX_CROWNS, prev.crowns + 1) }
  next.totalLessons += 1
  if (perfect) next.perfectLessons += 1

  const baseXp = XP_PER_LESSON + (perfect ? XP_PERFECT_BONUS : 0)
  const xpGained = Math.max(3, baseXp - hintsUsed)
  const hintPenalty = baseXp - xpGained
  bumpStreakAndXp(next, xpGained)

  const newAchievements = checkAchievements(next)
  saveState(next)
  return { state: next, xpGained, hintPenalty, newAchievements }
}

export function setDailyGoal(state, goal) {
  const next = { ...state, dailyGoal: goal }
  saveState(next)
  return next
}

// שמירת פרופיל מהשאלון + מבחן הרמה. ברמה חוזרת — שומרים את הגבוהה מביניהן.
export function saveProfile(state, { goal, selfLevel, placementLevel, dailyGoal }) {
  const next = {
    ...state,
    profile: {
      done: true,
      goal: goal ?? state.profile?.goal ?? null,
      selfLevel: selfLevel ?? state.profile?.selfLevel ?? null,
      placementLevel: Math.max(placementLevel || 1, state.profile?.placementLevel || 1),
    },
  }
  if (dailyGoal) next.dailyGoal = dailyGoal
  saveState(next)
  return next
}

export function levelName(level) {
  const names = ['מתחיל', 'בסיסי', 'טרום-בינוני', 'בינוני', 'בינוני-גבוה', 'מתקדם', 'שולט']
  return names[Math.min(Math.max(level, 1), 7) - 1]
}

export function getCrowns(state, levelId, lessonIndex) {
  return state.lessons[lessonKey(levelId, lessonIndex)]?.crowns || 0
}

export function isLessonCompleted(state, levelId, lessonIndex) {
  return getCrowns(state, levelId, lessonIndex) > 0
}

export function isLevelCompleted(state, levelId) {
  for (let i = 0; i < LESSONS_PER_LEVEL; i++) {
    if (!isLessonCompleted(state, levelId, i)) return false
  }
  return true
}

// רמה פתוחה אם היא הראשונה, אם מבחן הרמה פתח אותה, או שהקודמת הושלמה
export function isLevelUnlocked(state, levelIndex) {
  if (levelIndex === 0) return true
  if (levelIndex < (state.profile?.placementLevel || 1)) return true
  return isLevelCompleted(state, LEVELS[levelIndex - 1].id)
}

// שיעור פתוח אם הרמה פתוחה והשיעור הקודם בה הושלם
export function isLessonUnlocked(state, levelIndex, lessonIndex) {
  if (!isLevelUnlocked(state, levelIndex)) return false
  if (lessonIndex === 0) return true
  return isLessonCompleted(state, LEVELS[levelIndex].id, lessonIndex - 1)
}

// הרמה הנוכחית של המשתמש: הרמה הפתוחה הגבוהה ביותר שעוד לא הושלמה
export function currentLevel(state) {
  for (let i = LEVELS.length - 1; i >= 0; i--) {
    if (isLevelUnlocked(state, i) && !isLevelCompleted(state, LEVELS[i].id)) return i + 1
  }
  return LEVELS.length
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
  { id: 'level-done', icon: '👑', title: 'כובש רמות', desc: 'השלמת רמה שלמה', check: (s) => LEVELS.some((l) => isLevelCompletedRaw(s, l.id)) },
  { id: 'three-levels', icon: '🏔️', title: 'מטפס', desc: 'השלמת 3 רמות', check: (s) => LEVELS.filter((l) => isLevelCompletedRaw(s, l.id)).length >= 3 },
]

function isLevelCompletedRaw(state, levelId) {
  for (let i = 0; i < LESSONS_PER_LEVEL; i++) {
    if (!(state.lessons[lessonKey(levelId, i)]?.crowns > 0)) return false
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
