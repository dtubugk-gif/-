// עמוד פרופיל: סטטיסטיקות, יעד יומי והישגים.

import { useProgress } from '../hooks/useProgress'
import { ACHIEVEMENTS } from '../lib/progress'
import { UNITS, LESSONS_PER_UNIT } from '../data/course'
import { isLessonCompleted } from '../lib/progress'

const GOALS = [10, 30, 50, 100]

export default function ProfilePage() {
  const { state, setDailyGoal } = useProgress()

  const lessonsDone = UNITS.reduce((sum, u) => {
    let c = 0
    for (let i = 0; i < LESSONS_PER_UNIT; i++) if (isLessonCompleted(state, u.id, i)) c++
    return sum + c
  }, 0)
  const totalLessonCount = UNITS.length * LESSONS_PER_UNIT

  const stats = [
    { icon: '🔥', label: 'רצף ימים', value: state.streak, color: 'text-duo-orange' },
    { icon: '⚡', label: 'סה"כ XP', value: state.xp, color: 'text-duo-yellow-dark' },
    { icon: '📗', label: 'שיעורים הושלמו', value: `${lessonsDone}/${totalLessonCount}`, color: 'text-duo-green-darker' },
    { icon: '💯', label: 'שיעורים מושלמים', value: state.perfectLessons, color: 'text-duo-blue-dark' },
  ]

  return (
    <div>
      <div className="mb-6 flex items-center gap-4">
        <div className="flex h-20 w-20 items-center justify-center rounded-full bg-duo-green text-5xl">🦉</div>
        <div>
          <h1 className="text-2xl font-extrabold">הפרופיל שלי</h1>
          <p className="font-bold text-duo-muted">לומד/ת אנגלית 🇬🇧</p>
        </div>
      </div>

      <div className="mb-8 grid grid-cols-2 gap-3">
        {stats.map((s) => (
          <div key={s.label} className="rounded-2xl border-2 border-duo-gray p-4">
            <div className="text-2xl">{s.icon}</div>
            <div className={`text-2xl font-extrabold ${s.color}`}>{s.value}</div>
            <div className="text-sm font-bold text-duo-muted">{s.label}</div>
          </div>
        ))}
      </div>

      <h2 className="mb-3 text-xl font-extrabold">היעד היומי שלי 🎯</h2>
      <div className="mb-8 flex gap-2">
        {GOALS.map((g) => (
          <button
            key={g}
            type="button"
            onClick={() => setDailyGoal(g)}
            className={`btn-3d flex-1 rounded-2xl border-2 py-3 font-extrabold ${
              state.dailyGoal === g
                ? 'border-duo-green-darker bg-duo-green text-white'
                : 'border-duo-gray bg-white text-duo-text hover:bg-gray-50'
            }`}
          >
            {g} XP
          </button>
        ))}
      </div>

      <h2 className="mb-3 text-xl font-extrabold">הישגים 🏆</h2>
      <div className="grid grid-cols-2 gap-3 pb-6">
        {ACHIEVEMENTS.map((a) => {
          const earned = state.achievements.includes(a.id)
          return (
            <div
              key={a.id}
              className={`rounded-2xl border-2 p-4 ${
                earned ? 'border-duo-yellow bg-yellow-50' : 'border-duo-gray opacity-55 grayscale'
              }`}
            >
              <div className="text-3xl">{a.icon}</div>
              <div className="font-extrabold">{a.title}</div>
              <div className="text-sm font-bold text-duo-muted">{a.desc}</div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
