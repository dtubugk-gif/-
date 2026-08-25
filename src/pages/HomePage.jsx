// מפת הלמידה: הרמות בסדר קושי עולה, עם סימון הרמה הנוכחית וגלילה אליה.

import { useEffect, useMemo, useRef, useState } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { LEVELS, LESSONS_PER_LEVEL } from '../data/course'
import { useProgress } from '../hooks/useProgress'
import { getCrowns, isLessonUnlocked, isLevelUnlocked, isLevelCompleted, isLessonCompleted, currentLevel, levelName } from '../lib/progress'
import LessonNode from '../components/LessonNode'
import ProgressBar from '../components/ProgressBar'

// הזחות "שביל מתפתל"
const OFFSETS = [0, -55, 0, 55]

// ברכת הינשוף לפי שעת היום
function owlGreeting() {
  const h = new Date().getHours()
  if (h < 5) return 'לילה טוב'
  if (h < 12) return 'בוקר טוב'
  if (h < 18) return 'צהריים טובים'
  return 'ערב טוב'
}
const OWL_NUDGES = ['מוכנים לתרגל קצת אנגלית?', 'שיעור קטן ביום — וזה מצטבר!', 'בואו נלמד משהו חדש!', 'האנגלית מחכה לנו! 💪']

export default function HomePage() {
  const { state } = useProgress()
  const currentRef = useRef(null)
  const nudge = useMemo(() => OWL_NUDGES[Math.floor(Math.random() * OWL_NUDGES.length)], [])
  // חזרה משיעור שהושלם הרגע: טקס קטן על השביל — כתר נוחת ומנעול נפתח.
  // נלכד פעם אחת ב-state כדי שרענון הדף לא ינגן את הטקס שוב.
  const location = useLocation()
  const [justCompleted] = useState(() => location.state?.justCompleted || null)
  useEffect(() => {
    if (location.state?.justCompleted) window.history.replaceState({}, '')
  }, [location.state])

  // השיעור שנפתח הרגע: היורש הישיר של השיעור שהושלם (שיעור הבא באותה רמה,
  // או השיעור הראשון ברמה הבאה אם הושלם שיעור החזרה)
  const justUnlockedKey = useMemo(() => {
    if (!justCompleted) return null
    const [doneLevelId, doneIndexStr] = justCompleted.split(':')
    const doneIndex = Number(doneIndexStr)
    const doneLevelIdx = LEVELS.findIndex((l) => l.id === doneLevelId)
    if (doneLevelIdx === -1) return null
    if (doneIndex < LESSONS_PER_LEVEL - 1) return `${doneLevelId}:${doneIndex + 1}`
    const next = LEVELS[doneLevelIdx + 1]
    return next ? `${next.id}:0` : null
  }, [justCompleted])

  const current = state.profile?.done ? currentLevel(state) : 1

  // השיעור הבא: השיעור הפתוח הראשון שעוד לא הושלם החל מהרמה הנוכחית
  // (לא מרמה 1 — מי שעבר מבחן רמה מתחיל גבוה יותר), עליו קופצת בועת "התחל"
  const nextLesson = useMemo(() => {
    for (let li = current - 1; li < LEVELS.length; li++) {
      for (let i = 0; i < LESSONS_PER_LEVEL; i++) {
        if (isLessonUnlocked(state, li, i) && !isLessonCompleted(state, LEVELS[li].id, i)) {
          return { levelId: LEVELS[li].id, index: i }
        }
      }
    }
    return null
  }, [state, current])

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
      {/* ברכת הינשוף + הרמה שלי + יעד יומי */}
      <div className="mb-6 rounded-2xl border-2 border-duo-gray p-4 card-soft">
        <div className="mb-3 flex items-center gap-2 border-b-2 border-duo-gray/60 pb-3">
          <span className="animate-owl text-3xl">🦉</span>
          <span className="font-bold text-duo-text">
            {owlGreeting()}! {nudge}
          </span>
        </div>
        <div className="mb-3 flex items-center justify-between">
          <span className="font-extrabold">הרמה שלי 🏅</span>
          <span className="rounded-full bg-duo-blue px-3 py-0.5 text-sm font-extrabold text-white">
            רמה {current}/{LEVELS.length} · {levelName(current)}
          </span>
        </div>
        <div className="mb-2 flex items-center justify-between font-extrabold">
          <span>היעד היומי שלך 🎯</span>
          <span className="text-duo-yellow-text">
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
        let lessonsDone = 0
        for (let i = 0; i < LESSONS_PER_LEVEL; i++) if (isLessonCompleted(state, level.id, i)) lessonsDone++
        return (
          <section key={level.id} ref={isCurrent ? currentRef : null} className="mb-10 scroll-mt-20">
            <div
              className={`level-header mb-6 rounded-2xl p-4 text-white ${isCurrent ? 'ring-4 ring-duo-yellow' : ''}`}
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
                  {unlocked && !completed && lessonsDone > 0 && !isCurrent && (
                    <span className="rounded-full bg-white/25 px-2.5 py-1 text-sm font-extrabold">
                      {lessonsDone}/{LESSONS_PER_LEVEL}
                    </span>
                  )}
                  {isCurrent && !completed && (
                    <span className="rounded-full bg-white px-3 py-1 text-sm font-extrabold" style={{ color: level.color }}>
                      אתה כאן 📍
                    </span>
                  )}
                </div>
              </div>
            </div>

            <div className="flex flex-col items-center gap-1.5">
              {Array.from({ length: LESSONS_PER_LEVEL }, (_, li) => (
                <div
                  key={li}
                  className={`flex flex-col items-center gap-1.5 ${
                    li === 0 && nextLesson?.levelId === level.id && nextLesson?.index === 0 ? 'mt-8' : ''
                  }`}
                >
                  {li > 0 && (
                    <div
                      className="path-connector"
                      style={{ transform: `translateX(${(OFFSETS[(li - 1) % OFFSETS.length] + OFFSETS[li % OFFSETS.length]) / 2}px)` }}
                    />
                  )}
                  <LessonNode
                    level={level}
                    lessonIndex={li}
                    unlocked={isLessonUnlocked(state, levelIndex, li)}
                    crowns={getCrowns(state, level.id, li)}
                    title={li === LESSONS_PER_LEVEL - 1 ? 'חזרה' : `שיעור ${li + 1}`}
                    offset={OFFSETS[li % OFFSETS.length]}
                    isNext={nextLesson?.levelId === level.id && nextLesson?.index === li}
                    justEarned={justCompleted === `${level.id}:${li}`}
                    justUnlocked={justUnlockedKey === `${level.id}:${li}` && getCrowns(state, level.id, li) === 0 && isLessonUnlocked(state, levelIndex, li)}
                  />
                </div>
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
