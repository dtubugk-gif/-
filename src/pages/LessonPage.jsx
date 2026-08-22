// עמוד שיעור: מנוע התרגול + מסך סיום חגיגי.

import { useMemo, useState } from 'react'
import { useNavigate, useParams, Link } from 'react-router-dom'
import { getLesson } from '../data/course'
import { generateExercises } from '../lib/exerciseGen'
import { useProgress } from '../hooks/useProgress'
import { getCrowns } from '../lib/progress'
import LessonEngine from '../components/LessonEngine'
import Confetti from '../components/Confetti'
import Button from '../components/Button'
import { playFanfare } from '../lib/speech'

export default function LessonPage() {
  const { levelId, lessonId } = useParams()
  const navigate = useNavigate()
  const { state, completeLesson } = useProgress()

  const lesson = useMemo(() => getLesson(levelId, lessonId), [levelId, lessonId])
  // בפעם הראשונה מלמדים כל מילה לפני שבוחנים עליה; בשיעור חוזר או בשיעור
  // "חזרה" (המילים כבר נלמדו בשיעורים הקודמים) ניגשים ישר לתרגול
  const exercises = useMemo(
    () =>
      lesson
        ? generateExercises({
            ...lesson,
            teach: !lesson.isReview && getCrowns(state, lesson.level.id, lesson.index) === 0,
          })
        : [],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [lesson]
  )

  const [finished, setFinished] = useState(null) // { perfect, xpGained, newAchievements }

  if (!lesson) {
    return (
      <div className="flex min-h-screen flex-col items-center justify-center gap-4 p-6 text-center">
        <div className="text-6xl">🤔</div>
        <h1 className="text-2xl font-extrabold">השיעור לא נמצא</h1>
        <Link to="/"><Button>חזרה למפה</Button></Link>
      </div>
    )
  }

  if (finished) {
    return <FinishScreen lesson={lesson} finished={finished} onContinue={() => navigate('/')} />
  }

  return (
    <LessonEngine
      exercises={exercises}
      onFinish={({ perfect }) => {
        const result = completeLesson(lesson.level.id, lesson.index, perfect)
        playFanfare()
        setFinished({ perfect, xpGained: result.xpGained, newAchievements: result.newAchievements })
      }}
    />
  )
}

function FinishScreen({ lesson, finished, onContinue }) {
  return (
    <div className="relative flex min-h-screen flex-col items-center justify-center gap-5 overflow-hidden p-6 text-center">
      <Confetti />
      <div className="animate-bounce-slow text-8xl">🦉</div>
      <h1 className="animate-pop-in text-3xl font-extrabold text-duo-green">
        {finished.perfect ? 'שיעור מושלם!' : 'כל הכבוד!'}
      </h1>
      <p className="text-lg font-bold text-duo-muted">
        סיימת את {lesson.title} ברמה {lesson.levelIndex + 1} — "{lesson.level.title}"
      </p>

      <div className="flex gap-4">
        <div className="animate-pop-in rounded-2xl border-2 border-duo-yellow bg-yellow-50 px-6 py-3">
          <div className="text-sm font-extrabold text-duo-yellow-dark">XP שהרווחת</div>
          <div dir="ltr" className="text-2xl font-extrabold text-duo-yellow-dark">+{finished.xpGained}</div>
        </div>
        {finished.perfect && (
          <div className="animate-pop-in rounded-2xl border-2 border-duo-green bg-green-50 px-6 py-3">
            <div className="text-sm font-extrabold text-duo-green-darker">בונוס דיוק</div>
            <div className="text-2xl font-extrabold text-duo-green-darker">💯</div>
          </div>
        )}
      </div>

      {finished.newAchievements?.length > 0 && (
        <div className="animate-pop-in rounded-2xl border-2 border-duo-purple bg-purple-50 px-6 py-4">
          <div className="mb-1 font-extrabold text-duo-purple">הישג חדש! 🏆</div>
          {finished.newAchievements.map((a) => (
            <div key={a.id} className="font-bold">
              {a.icon} {a.title} — {a.desc}
            </div>
          ))}
        </div>
      )}

      <Button onClick={onContinue} className="mt-2 px-10">המשך</Button>
    </div>
  )
}
