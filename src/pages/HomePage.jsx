// מפת הלמידה: 7 רמות בסדר קושי עולה, עם סימון הרמה הנוכחית וגלילה אליה.

import { useEffect, useRef } from 'react'
import { Navigate } from 'react-router-dom'
import { LEVELS, LESSONS_PER_LEVEL } from '../data/course'
import { useProgress } from '../hooks/useProgress'
import { getCrowns, isLessonUnlocked, isLevelUnlocked, isLevelCompleted, currentLevel, levelName } from '../lib/progress'
import LessonNode from '../components/LessonNode'
import ProgressBar from '../components/ProgressBar'

// הזחות "שביל מתפתל"
const OFFSETS = [0, -55, 0, 55]

export default function HomePage() {
  const { state } = useProgress()
  const currentRef = useRef(null)

  const current = state.profile?.done ? currentLevel(state) : 1

  // גלילה אוטומטית לרמה הנוכחית
  useEffect(() => {
    if (currentRef.current && current > 1) {
      currentRef.current.scrollIntoView({ behavior: 'smooth', block: 'start' })
    }
  }, [current])

  // כניסה ראשונה — בניית מסלול מותאם אישית
  if (!state.profile?.done) {
    return <Navigate to="/onboarding" replace />
  }

  return (
    <div>
      {/* הרמה שלי + יעד יומי */}
      <div className="mb-6 rounded-2xl border-2 border-duo-gray p-4">
        <div className="mb-3 flex items-center justify-between">
          <span className="font-extrabold">הרמה שלי 🏅</span>
          <span className="rounded-full bg-duo-blue px-3 py-0.5 text-sm font-extrabold text-white">
            רמה {current}/{LEVELS.length} · {levelName(current)}
          </span>
        </div>
        <div className="mb-2 flex items-center justify-between font-extrabold">
          <span>היעד היומי שלך 🎯</span>
          <span className="text-duo-yellow-dark">
            {Math.min(state.xpToday, state.dailyGoal)}/{state.dailyGoal} XP
          </span>
        </div>
        <ProgressBar value={state.xpToday} max={state.dailyGoal} color="#ffc800" />
        {state.xpToday >= state.dailyGoal && (
          <div className="mt-2 text-sm font-bold text-duo-green-darker">כל הכבוד! השלמת את היעד היומי 🎉</div>
        )}
      </div>

      {LEVELS.map((level, levelIndex) => {
        const unlocked = isLevelUnlocked(state, levelIndex)
        const completed = isLevelCompleted(state, level.id)
        const isCurrent = levelIndex + 1 === current
        return (
          <section key={level.id} ref={isCurrent ? currentRef : null} className="mb-10 scroll-mt-20">
            <div
              className={`mb-6 rounded-2xl p-4 text-white shadow-md ${isCurrent ? 'ring-4 ring-duo-yellow' : ''}`}
              style={{ backgroundColor: unlocked ? level.color : '#afafaf' }}
            >
              <div className="flex items-center justify-between">
                <div>
                  <h2 className="text-xl font-extrabold">
                    {level.icon} רמה {levelIndex + 1}: {level.title}
                  </h2>
                  <p className="font-bold opacity-90">{level.subtitle}</p>
                </div>
                <div className="text-end">
                  {!unlocked && <span className="text-3xl">🔒</span>}
                  {completed && <span className="text-3xl">✅</span>}
                  {isCurrent && !completed && (
                    <span className="rounded-full bg-white px-3 py-1 text-sm font-extrabold" style={{ color: level.color }}>
                      אתה כאן 📍
                    </span>
                  )}
                </div>
              </div>
            </div>

            <div className="flex flex-col items-center gap-6">
              {Array.from({ length: LESSONS_PER_LEVEL }, (_, li) => (
                <LessonNode
                  key={li}
                  level={level}
                  lessonIndex={li}
                  unlocked={isLessonUnlocked(state, levelIndex, li)}
                  crowns={getCrowns(state, level.id, li)}
                  title={li === LESSONS_PER_LEVEL - 1 ? 'חזרה' : `שיעור ${li + 1}`}
                  offset={OFFSETS[li % OFFSETS.length]}
                />
              ))}
            </div>
          </section>
        )
      })}

      <div className="pb-4 text-center text-sm font-bold text-duo-muted">
        סיימת את כל {LEVELS.length} הרמות? אנגלית מושלמת! 🦉🏆
      </div>
    </div>
  )
}
