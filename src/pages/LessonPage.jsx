// עמוד שיעור: מנוע התרגול + מסך סיום חגיגי.

import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams, Link } from 'react-router-dom'
import { getLesson, LEVELS } from '../data/course'
import { generateExercises } from '../lib/exerciseGen'
import { useProgress } from '../hooks/useProgress'
import { getCrowns, isLevelCompleted } from '../lib/progress'
import LessonEngine from '../components/LessonEngine'
import Confetti from '../components/Confetti'
import Button from '../components/Button'
import { playFanfare, playGift, playTick } from '../lib/speech'

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
    return (
      <FinishScreen
        lesson={lesson}
        finished={finished}
        onContinue={() =>
          navigate('/', { state: { justCompleted: `${lesson.level.id}:${lesson.index}` } })
        }
      />
    )
  }

  return (
    <LessonEngine
      exercises={exercises}
      onFinish={({ perfect, hintsUsed, maxCombo, durationMs, accuracy }) => {
        // state כאן הוא המצב שלפני סיום השיעור — לזיהוי רגעי חגיגה
        const wasLevelDone = isLevelCompleted(state, lesson.level.id)
        const today = new Date().toISOString().slice(0, 10)
        // אם עבר יום מאז הטעינה — ה-XP היומי שבזיכרון שייך לאתמול
        const xpTodayNow = state.xpTodayDate === today ? state.xpToday : 0
        const goalNotHitYet = xpTodayNow < state.dailyGoal
        const firstLessonToday = state.lastActiveDate !== today

        const result = completeLesson(lesson.level.id, lesson.index, perfect, hintsUsed, maxCombo)
        playFanfare()
        setFinished({
          perfect,
          accuracy,
          durationMs,
          xpGained: result.xpGained,
          hintPenalty: result.hintPenalty,
          dailyBonus: result.dailyBonus,
          sticker: result.sticker,
          prevCrowns: result.prevCrowns,
          newCrowns: result.newCrowns,
          newAchievements: result.newAchievements,
          levelJustCompleted: !wasLevelDone && isLevelCompleted(result.state, lesson.level.id),
          dailyGoalHit: goalNotHitYet && result.state.xpToday >= result.state.dailyGoal,
          streakCelebration: firstLessonToday ? result.state.streak : 0,
          activeDates: result.state.activeDates || [],
        })
      }}
    />
  )
}

// מספר שנספר מ-0 עד היעד — כמו מונה ה-XP של דואלינגו
function useCountUp(target, delayMs = 350, durMs = 900) {
  const [value, setValue] = useState(0)
  useEffect(() => {
    let raf
    let start = null
    const t0 = setTimeout(() => {
      const step = (ts) => {
        if (start === null) start = ts
        const p = Math.min(1, (ts - start) / durMs)
        setValue(Math.round(target * p))
        if (p < 1) raf = requestAnimationFrame(step)
      }
      raf = requestAnimationFrame(step)
    }, delayMs)
    return () => {
      clearTimeout(t0)
      cancelAnimationFrame(raf)
    }
  }, [target, delayMs, durMs])
  return value
}

// שורות עידוד של הינשוף — ניסוח ניטרלי שמתאים לכולם
const OWL_LINES = ['איזה כיף ללמוד איתך!', 'המוח שלך גדל עכשיו! 🧠', 'עוד שיעור ואנחנו אלופים!', 'וואו, איזו התקדמות!', 'אין כמוך!']

// מסך רצף בסגנון דואלינגו: להבה ענקית + לוח השבוע — פעם ביום, בשיעור הראשון
const DAY_LETTERS = ['א', 'ב', 'ג', 'ד', 'ה', 'ו', 'ש']
function StreakScreen({ streak, activeDates, onContinue }) {
  const streakCount = useCountUp(streak, 500, 700)
  const week = useMemo(() => {
    const days = []
    for (let i = 6; i >= 0; i--) {
      const d = new Date()
      d.setDate(d.getDate() - i)
      days.push({
        letter: DAY_LETTERS[d.getDay()],
        active: activeDates.includes(d.toISOString().slice(0, 10)),
        isToday: i === 0,
      })
    }
    return days
  }, [activeDates])

  return (
    <div className="celebration-bg flex min-h-screen flex-col items-center justify-center gap-6 p-6 text-center">
      <div className="animate-flame text-9xl">🔥</div>
      <div>
        <div dir="ltr" className="text-6xl font-extrabold text-duo-orange-text">{streakCount}</div>
        <h1 className="mt-1 text-2xl font-extrabold text-duo-orange-text">
          {streak === 1 ? 'יום ראשון לרצף!' : 'ימי רצף!'}
        </h1>
        <p className="mt-2 font-bold text-duo-muted">
          {streak === 1 ? 'התחלה מעולה — נתראה גם מחר? 🦉' : 'כל יום שמתרגלים — הרצף גדל!'}
        </p>
      </div>
      <div className="flex gap-2.5" dir="rtl">
        {week.map((d, i) => (
          <div key={i} className="flex flex-col items-center gap-1.5">
            <span className="text-sm font-bold text-duo-muted">{d.letter}</span>
            <div
              className={`flex h-10 w-10 items-center justify-center rounded-full border-2 text-lg font-extrabold ${
                d.active
                  ? 'border-duo-orange bg-orange-100'
                  : 'border-duo-gray bg-white'
              } ${d.isToday ? 'animate-pop-in' : ''}`}
              style={d.isToday ? { animationDelay: '0.6s' } : undefined}
            >
              {d.active ? '🔥' : ''}
            </div>
          </div>
        ))}
      </div>
      <Button onClick={onContinue} className="mt-2 px-10">המשך</Button>
    </div>
  )
}

function FinishScreen({ lesson, finished, onContinue }) {
  const nextLevel = LEVELS[lesson.levelIndex + 1]
  const xpCount = useCountUp(finished.xpGained)
  const owlLine = useMemo(() => OWL_LINES[Math.floor(Math.random() * OWL_LINES.length)], [])
  // אחרי מסך הנתונים מגיע מסך הרצף (בשיעור הראשון של היום), ואז חוזרים למפה
  const [stage, setStage] = useState('stats')
  const [chestOpen, setChestOpen] = useState(false)

  // צליל פתיחת מתנה — רגע אחרי הפנפרה של סיום השיעור
  useEffect(() => {
    if (!finished.dailyBonus) return
    const t = setTimeout(playGift, 700)
    return () => clearTimeout(t)
  }, [finished.dailyBonus])

  // תקתוק עדין בזמן ספירת ה-XP — כמו מונה פרסים
  useEffect(() => {
    if (xpCount > 0 && xpCount !== finished.xpGained && xpCount % 3 === 0) playTick()
  }, [xpCount, finished.xpGained])

  if (stage === 'streak') {
    return <StreakScreen streak={finished.streakCelebration} activeDates={finished.activeDates} onContinue={onContinue} />
  }
  const handleContinue = finished.streakCelebration > 0 ? () => setStage('streak') : onContinue

  const totalSec = Math.max(1, Math.round((finished.durationMs || 0) / 1000))
  const timeText = `${Math.floor(totalSec / 60)}:${String(totalSec % 60).padStart(2, '0')}`

  return (
    <div className="celebration-bg relative flex min-h-screen flex-col items-center justify-center gap-5 overflow-hidden p-6 text-center">
      <Confetti count={finished.levelJustCompleted ? 70 : 40} />
      <div className="relative">
        <div className="animate-bounce-slow text-8xl">
          <span className="animate-owl">{finished.levelJustCompleted ? '👑' : '🦉'}</span>
        </div>
        {!finished.levelJustCompleted && (
          <div className="animate-pop-in absolute -top-6 right-full mr-1 hidden w-max max-w-44 rounded-2xl border-2 border-duo-gray bg-white px-3 py-1.5 text-sm font-bold text-duo-text shadow sm:block">
            {owlLine}
          </div>
        )}
      </div>
      {!finished.levelJustCompleted && (
        <div className="animate-pop-in -mt-2 text-sm font-bold text-duo-muted sm:hidden">🦉 {owlLine}</div>
      )}
      <h1 className="animate-pop-in text-3xl font-extrabold text-duo-green-darker">
        {finished.levelJustCompleted
          ? `כבשת את רמה ${lesson.levelIndex + 1}!`
          : finished.perfect ? 'שיעור מושלם!' : 'כל הכבוד!'}
      </h1>
      <p className="text-lg font-bold text-duo-muted">
        {finished.levelJustCompleted
          ? `השלמת את כל "${lesson.level.title}"${nextLevel ? ` — רמה ${lesson.levelIndex + 2} "${nextLevel.title}" מחכה לך! ${nextLevel.icon}` : ' — סיימת את הקורס כולו! 🏆'}`
          : `סיימת את ${lesson.isReview ? 'שיעור החזרה' : lesson.title} ברמה ${lesson.levelIndex + 1} — "${lesson.level.title}"`}
      </p>

      {/* טקס כתרים: הכתר החדש נוחת על השורה */}
      {finished.newCrowns > finished.prevCrowns && (
        <div className="flex flex-col items-center gap-1">
          <div className="flex gap-3" dir="ltr">
            {[0, 1, 2].map((i) => (
              <span
                key={i}
                className={`text-3xl ${
                  i === finished.newCrowns - 1
                    ? 'animate-crown-drop'
                    : i < finished.newCrowns ? '' : 'opacity-25 grayscale'
                }`}
              >
                👑
              </span>
            ))}
          </div>
          {finished.newCrowns === 3 && (
            <div className="text-sm font-extrabold text-duo-yellow-text">שיעור מוזהב! ✨</div>
          )}
        </div>
      )}

      {finished.dailyGoalHit && (
        <div className="animate-pop-in rounded-2xl border-2 border-duo-yellow bg-yellow-50 px-5 py-2 font-extrabold text-duo-yellow-text" style={{ animationDelay: '0.3s' }}>
          🎯 הגעת ליעד היומי!
        </div>
      )}

      <div className="flex flex-wrap justify-center gap-4">
        <div className="animate-pop-in rounded-2xl border-2 border-duo-yellow bg-yellow-50 px-6 py-3" style={{ animationDelay: '0.15s' }}>
          <div className="text-sm font-extrabold text-duo-yellow-text">XP שהרווחת</div>
          <div dir="ltr" className="text-2xl font-extrabold text-duo-yellow-text">
            <span className={xpCount === finished.xpGained ? 'animate-xp-tick' : ''}>+{xpCount}</span>
          </div>
        </div>
        <div className="animate-pop-in rounded-2xl border-2 border-duo-green bg-green-50 px-6 py-3" style={{ animationDelay: '0.3s' }}>
          <div className="text-sm font-extrabold text-duo-green-darker">דיוק 🎯</div>
          <div dir="ltr" className="text-2xl font-extrabold text-duo-green-darker">{finished.accuracy ?? 100}%</div>
        </div>
        <div className="animate-pop-in rounded-2xl border-2 border-duo-blue bg-sky-50 px-6 py-3" style={{ animationDelay: '0.45s' }}>
          <div className="text-sm font-extrabold text-duo-blue-dark">זמן ⏱️</div>
          <div dir="ltr" className="text-2xl font-extrabold text-duo-blue-dark">{timeText}</div>
        </div>
        {finished.dailyBonus > 0 && (
          <div className="animate-pop-in rounded-2xl border-2 border-duo-purple bg-purple-50 px-6 py-3" style={{ animationDelay: '0.6s' }}>
            <div className="text-sm font-extrabold text-duo-purple-dark">מתנה יומית!</div>
            <div dir="ltr" className="text-2xl font-extrabold text-duo-purple-dark">
              <span className="animate-gift">🎁</span> +{finished.dailyBonus}
            </div>
          </div>
        )}
        {finished.perfect && (
          <div className="animate-pop-in rounded-2xl border-2 border-duo-green bg-green-50 px-6 py-3" style={{ animationDelay: '0.75s' }}>
            <div className="text-sm font-extrabold text-duo-green-darker">בלי טעויות</div>
            <div className="text-2xl font-extrabold text-duo-green-darker">💯</div>
          </div>
        )}
        {finished.hintPenalty > 0 && (
          <div className="animate-pop-in rounded-2xl border-2 border-duo-blue bg-sky-50 px-6 py-3" style={{ animationDelay: '0.75s' }}>
            <div className="text-sm font-extrabold text-duo-blue-dark">רמזים 🔍</div>
            <div dir="ltr" className="text-2xl font-extrabold text-duo-blue-dark">−{finished.hintPenalty}</div>
          </div>
        )}
      </div>

      {/* תיבת הפתעה: כל שיעור חמישי — מדבקה חדשה לאוסף */}
      {finished.sticker && (
        <button
          type="button"
          onClick={() => {
            if (!chestOpen) {
              setChestOpen(true)
              playGift()
            }
          }}
          className="animate-pop-in cursor-pointer rounded-2xl border-2 border-duo-purple bg-purple-50 px-8 py-4"
          style={{ animationDelay: '0.6s' }}
        >
          {chestOpen ? (
            <div className="animate-pop-in">
              <div className="text-sm font-extrabold text-duo-purple-dark">מדבקה חדשה לאוסף! 🎉</div>
              <div className="my-1 text-5xl">{finished.sticker}</div>
              <div dir="ltr" className="text-sm font-extrabold text-duo-purple-dark">+3 XP</div>
            </div>
          ) : (
            <div>
              <div className="animate-gift text-5xl">🎁</div>
              <div className="mt-1 text-sm font-extrabold text-duo-purple-dark">תיבת הפתעה! לחצו לפתיחה</div>
            </div>
          )}
        </button>
      )}

      {finished.newAchievements?.length > 0 && (
        <div className="animate-pop-in rounded-2xl border-2 border-duo-purple bg-purple-50 px-6 py-4" style={{ animationDelay: '0.8s' }}>
          <div className="mb-1 font-extrabold text-duo-purple-dark">הישג חדש! 🏆</div>
          {finished.newAchievements.map((a) => (
            <div key={a.id} className="font-bold">
              {a.icon} {a.title} — {a.desc}
            </div>
          ))}
        </div>
      )}

      <div className="animate-pop-in mt-2" style={{ animationDelay: '0.9s' }}>
        <Button onClick={handleContinue} className="px-10">המשך</Button>
      </div>
    </div>
  )
}
