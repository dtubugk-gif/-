import { createContext, useContext, useState, useRef, useCallback } from 'react'
import * as P from '../lib/progress'

const ProgressContext = createContext(null)

// כל המוטציות רצות מחוץ ל-setState (עם ref למצב העדכני) כדי שלא יורצו
// פעמיים ב-StrictMode — יש להן תופעות לוואי (שמירה ל-localStorage).
export function useProgressProvider() {
  const [state, setState] = useState(() => {
    const s = P.loadState()
    P.saveState(s)
    return s
  })
  const stateRef = useRef(state)

  const apply = useCallback((next) => {
    stateRef.current = next
    setState(next)
    return next
  }, [])

  const loseHeart = useCallback(() => apply(P.loseHeart(stateRef.current)), [apply])
  const refillHearts = useCallback(() => apply(P.refillHearts(stateRef.current)), [apply])
  const addMistakes = useCallback((words) => apply(P.addMistakes(stateRef.current, words)), [apply])
  const clearMistakes = useCallback((ens) => apply(P.clearMistakes(stateRef.current, ens)), [apply])
  const setDailyGoal = useCallback((goal) => apply(P.setDailyGoal(stateRef.current, goal)), [apply])
  const saveProfile = useCallback((profile) => apply(P.saveProfile(stateRef.current, profile)), [apply])

  const completeLesson = useCallback(
    (unitId, lessonIndex, perfect) => {
      const result = P.completeLesson(stateRef.current, unitId, lessonIndex, perfect)
      apply(result.state)
      return result
    },
    [apply]
  )

  return {
    state,
    loseHeart,
    refillHearts,
    addMistakes,
    clearMistakes,
    setDailyGoal,
    saveProfile,
    completeLesson,
  }
}

export function useProgress() {
  const ctx = useContext(ProgressContext)
  if (!ctx) throw new Error('useProgress must be used inside ProgressProvider')
  return ctx
}

export { ProgressContext }
