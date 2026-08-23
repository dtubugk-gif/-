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

  const addMistakes = useCallback((words) => apply(P.addMistakes(stateRef.current, words)), [apply])
  const clearMistakes = useCallback((ens) => apply(P.clearMistakes(stateRef.current, ens)), [apply])
  const setDailyGoal = useCallback((goal) => apply(P.setDailyGoal(stateRef.current, goal)), [apply])
  const saveProfile = useCallback((profile) => apply(P.saveProfile(stateRef.current, profile)), [apply])

  const completeLesson = useCallback(
    (levelId, lessonIndex, perfect, hintsUsed) => {
      const result = P.completeLesson(stateRef.current, levelId, lessonIndex, perfect, hintsUsed)
      apply(result.state)
      return result
    },
    [apply]
  )

  return {
    state,
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
