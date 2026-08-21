// מפת הלמידה: שביל יחידות ושיעורים בסגנון דואלינגו.

import { Navigate } from 'react-router-dom'
import { UNITS, LESSONS_PER_UNIT } from '../data/course'
import { useProgress } from '../hooks/useProgress'
import { getCrowns, isLessonUnlocked, isUnitUnlocked } from '../lib/progress'
import LessonNode from '../components/LessonNode'
import ProgressBar from '../components/ProgressBar'

// הזחות "שביל מתפתל"
const OFFSETS = [0, -55, 0, 55]

export default function HomePage() {
  const { state } = useProgress()

  // כניסה ראשונה — בניית מסלול מותאם אישית
  if (!state.profile?.done) {
    return <Navigate to="/onboarding" replace />
  }

  return (
    <div>
      {/* יעד יומי */}
      <div className="mb-6 rounded-2xl border-2 border-duo-gray p-4">
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

      {UNITS.map((unit, unitIndex) => {
        const unlocked = isUnitUnlocked(state, unitIndex)
        return (
          <section key={unit.id} className="mb-10">
            <div
              className="mb-6 rounded-2xl p-4 text-white shadow-md"
              style={{ backgroundColor: unlocked ? unit.color : '#afafaf' }}
            >
              <div className="flex items-center justify-between">
                <div>
                  <h2 className="text-xl font-extrabold">
                    {unit.icon} יחידה {unitIndex + 1}: {unit.title}
                  </h2>
                  <p className="font-bold opacity-90">{unit.subtitle}</p>
                </div>
                {!unlocked && <span className="text-3xl">🔒</span>}
              </div>
            </div>

            <div className="flex flex-col items-center gap-6">
              {Array.from({ length: LESSONS_PER_UNIT }, (_, li) => (
                <LessonNode
                  key={li}
                  unit={unit}
                  lessonIndex={li}
                  unlocked={isLessonUnlocked(state, unitIndex, li)}
                  crowns={getCrowns(state, unit.id, li)}
                  title={li === LESSONS_PER_UNIT - 1 ? 'חזרה' : `שיעור ${li + 1}`}
                  offset={OFFSETS[li % OFFSETS.length]}
                />
              ))}
            </div>
          </section>
        )
      })}

      <div className="pb-4 text-center text-sm font-bold text-duo-muted">
        עוד יחידות בדרך... המשיכו ללמוד! 🦉
      </div>
    </div>
  )
}
