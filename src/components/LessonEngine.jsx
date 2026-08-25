// מנוע התרגול: מציג תרגילים בזה אחר זה, בודק תשובות,
// מחזיר תרגילים שגויים לסוף התור (כמו דואלינגו) ומדווח על סיום.

import { useState, useEffect, useMemo, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { Capacitor } from '@capacitor/core'
import { App as CapApp } from '@capacitor/app'
import Button from './Button'
import ProgressBar from './ProgressBar'
import TeachCard from './exercises/TeachCard'
import GrammarCard from './exercises/GrammarCard'
import MultipleChoice from './exercises/MultipleChoice'
import Listening from './exercises/Listening'
import TypeTranslation from './exercises/TypeTranslation'
import SentenceBuild from './exercises/SentenceBuild'
import MatchPairs from './exercises/MatchPairs'
import PickImage from './exercises/PickImage'
import FillBlank from './exercises/FillBlank'
import { checkAnswer, isAnswerReady } from './exercises/checkAnswer'
import { exerciseWords } from '../lib/exerciseGen'
import { playCorrect, playWrong, playCombo, playTick, speak } from '../lib/speech'
import { useProgress } from '../hooks/useProgress'
import { isComputer } from '../lib/device'

const EXERCISE_COMPONENTS = {
  teach: TeachCard,
  grammar: GrammarCard,
  multipleChoice: MultipleChoice,
  listening: Listening,
  typeTranslation: TypeTranslation,
  sentenceBuild: SentenceBuild,
  matchPairs: MatchPairs,
  pickImage: PickImage,
  fillBlank: FillBlank,
}

// מחמאות מתחלפות — כיף גם לילדים
const PRAISES = ['כל הכבוד! 🎉', 'תשובה של אלופים! 🌟', 'מדהים! ממשיכים ככה 🚀', 'בול! 🎯', 'וואו, איזה יופי! 👏', 'נכון מאוד! 💪']
// מחמאות מיוחדות לתשובה נכונה ומהירה
const FAST_PRAISES = ['⚡ מהיר כמו ברק!', '🚀 איזו מהירות!', '⏱️ טיל! תשובה בשנייה!']

// אימוג'ים שעפים מעלה אחרי תשובה נכונה
const BURST_EMOJI = ['✨', '⭐', '🎉', '💫', '🌟']
function EmojiBurst({ count = 7 }) {
  const pieces = useMemo(
    () =>
      Array.from({ length: count }, (_, i) => ({
        id: i,
        emoji: BURST_EMOJI[Math.floor(Math.random() * BURST_EMOJI.length)],
        left: 8 + Math.random() * 84,
        delay: Math.random() * 0.25,
        spin: `${Math.round(Math.random() * 60 - 30)}deg`,
      })),
    [count]
  )
  return (
    <div className="pointer-events-none absolute inset-x-0 top-0">
      {pieces.map((p) => (
        <span
          key={p.id}
          className="burst-piece"
          style={{ left: `${p.left}%`, animationDelay: `${p.delay}s`, '--spin': p.spin }}
        >
          {p.emoji}
        </span>
      ))}
    </div>
  )
}

export default function LessonEngine({ exercises: initialExercises, onFinish }) {
  const navigate = useNavigate()
  const { addMistakes } = useProgress()

  const [queue, setQueue] = useState(initialExercises)
  const [current, setCurrent] = useState(0)
  const [answer, setAnswer] = useState(null)
  const [checked, setChecked] = useState(false)
  const [lastResult, setLastResult] = useState(null)
  const [totalDone, setTotalDone] = useState(0)
  const [hadMistake, setHadMistake] = useState(false)
  const [wrongWords, setWrongWords] = useState([])
  // רמזים שנפתחו (מילים ייחודיות) — כל רמז מוריד נקודה מה-XP של השיעור
  const [hintedWords, setHintedWords] = useState(() => new Set())
  // רצף תשובות נכונות ברציפות — קומבו כמו בדואלינגו
  const [combo, setCombo] = useState(0)
  const [maxCombo, setMaxCombo] = useState(0)
  // מתי התחיל התרגיל הנוכחי — לזיהוי תשובות מהירות
  const [exerciseStart, setExerciseStart] = useState(() => Date.now())
  // מדדים למסך הסיום: משך השיעור ואחוז דיוק
  const [lessonStart] = useState(() => Date.now())
  const [checksTotal, setChecksTotal] = useState(0)
  const [checksCorrect, setChecksCorrect] = useState(0)
  // הודעת עידוד באמצע השיעור ("חצי דרך!" / "עוד 2 למושלם")
  const [milestone, setMilestone] = useState(null)
  const [firedMilestones] = useState(() => new Set())
  // הבזק קומבו במרכז המסך ברצפים של 5 ו-10
  const [comboFlash, setComboFlash] = useState(null)
  // אישור יציאה באמצע שיעור — שלא נאבד התקדמות בלחיצה בטעות
  const [exitConfirm, setExitConfirm] = useState(false)
  const totalDoneRef = useRef(0)
  totalDoneRef.current = totalDone

  // כפתור/מחוות "אחורה" של אנדרואיד: פותח את אישור היציאה במקום לצאת מהשיעור
  useEffect(() => {
    if (!Capacitor.isNativePlatform()) return
    const listener = CapApp.addListener('backButton', () => {
      if (totalDoneRef.current === 0) navigate('/')
      else setExitConfirm((open) => !open)
    })
    return () => {
      listener.then((h) => h.remove()).catch(() => {})
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  function registerHint(wordKey) {
    setHintedWords((prev) => {
      if (prev.has(wordKey)) return prev
      const next = new Set(prev)
      next.add(wordKey)
      return next
    })
  }

  const exercise = queue[current]
  const total = queue.length
  const Component = exercise ? EXERCISE_COMPONENTS[exercise.type] : null
  const desktop = isComputer()

  // גרסת מחשב: מקשים 1-4 בוחרים תשובה, Enter בודק/ממשיך
  useEffect(() => {
    if (!desktop || !exercise) return
    function onKey(e) {
      if (exitConfirm) return
      if (e.key === 'Enter') {
        e.preventDefault()
        if (exercise.type === 'teach' || exercise.type === 'grammar') handleTeachContinue()
        else if (checked) handleContinue()
        else if (isAnswerReady(exercise, answer)) handleCheck()
        return
      }
      if (e.target.tagName === 'INPUT') return
      const n = parseInt(e.key, 10)
      if (!checked && n >= 1 && n <= 4 && Array.isArray(exercise.options) && exercise.options[n - 1] !== undefined) {
        setAnswer(exercise.options[n - 1])
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  })

  // איפוס שעון המהירות עם כל תרגיל חדש
  useEffect(() => {
    setExerciseStart(Date.now())
  }, [current])

  // קליק עדין בכל בחירת תשובה (לא בהקלדה ולא בהתאמת זוגות — שם יש צלילים משלהם)
  useEffect(() => {
    if (answer === null || checked) return
    if (!exercise || exercise.type === 'typeTranslation' || exercise.type === 'matchPairs') return
    playTick()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [answer])

  // ניקוי אוטומטי של הודעות עידוד והבזקי קומבו
  useEffect(() => {
    if (!milestone) return
    const t = setTimeout(() => setMilestone(null), 1400)
    return () => clearTimeout(t)
  }, [milestone])
  useEffect(() => {
    if (!comboFlash) return
    const t = setTimeout(() => setComboFlash(null), 1400)
    return () => clearTimeout(t)
  }, [comboFlash])

  // הודעות עידוד: חצי דרך, או "עוד 2 והשיעור מושלם"
  function maybeMilestone(totalDoneNext, queueLen, mistakeSoFar) {
    const half = Math.floor(queueLen / 2)
    if (queueLen >= 8 && totalDoneNext === half && !firedMilestones.has('half')) {
      firedMilestones.add('half')
      setMilestone('חצי דרך! 💪')
      return
    }
    if (queueLen - totalDoneNext === 2 && !mistakeSoFar && !firedMilestones.has('perfect2')) {
      firedMilestones.add('perfect2')
      setMilestone('עוד 2 והשיעור מושלם! 💯')
    }
  }

  function finishPayload() {
    return {
      perfect: !hadMistake,
      wrongWords,
      hintsUsed: hintedWords.size,
      maxCombo,
      durationMs: Date.now() - lessonStart,
      accuracy: checksTotal > 0 ? Math.round((100 * checksCorrect) / checksTotal) : 100,
    }
  }

  // כרטיסיית לימוד: אין בדיקה — פשוט ממשיכים הלאה
  function handleTeachContinue() {
    setTotalDone((d) => d + 1)
    setAnswer(null)
    if (current + 1 >= queue.length) {
      onFinish(finishPayload())
    } else {
      maybeMilestone(totalDone + 1, queue.length, hadMistake)
      setCurrent((c) => c + 1)
    }
  }

  function handleCheck() {
    const result = checkAnswer(exercise, answer)
    // תשובה נכונה תוך פחות מ-4 שניות זוכה למחמאת מהירות (לא בהתאמת זוגות — היא ארוכה מטבעה)
    const fast = Date.now() - exerciseStart < 4000 && exercise.type !== 'matchPairs'
    result.praise = fast && result.correct
      ? FAST_PRAISES[Math.floor(Math.random() * FAST_PRAISES.length)]
      : PRAISES[Math.floor(Math.random() * PRAISES.length)]
    // טעויות בהתאמת זוגות נרשמות אבל לא נחשבות טעות בשיעור
    if (exercise.type === 'matchPairs' && answer?.wrongWords?.length) {
      addMistakes(answer.wrongWords)
      setWrongWords((w) => [...w, ...answer.wrongWords])
    }
    setChecked(true)
    setLastResult(result)
    setChecksTotal((t) => t + 1)
    if (result.correct) {
      setChecksCorrect((c) => c + 1)
      // התאמת זוגות עם טעויות בדרך "עוברת" אבל לא מגדילה את הקומבו
      if (exercise.type === 'matchPairs' && answer?.wrongWords?.length) {
        playCorrect()
      } else {
        const streakNow = combo + 1
        setCombo(streakNow)
        setMaxCombo((m) => Math.max(m, streakNow))
        if (streakNow === 5) setComboFlash('⚡ 5 ברצף! ⚡')
        if (streakNow === 10) setComboFlash('🌟 10 ברצף! אין עליך! 🌟')
        if (streakNow >= 3) {
          result.praise = `🔥 ${streakNow} ברצף! ${result.praise}`
          playCombo(streakNow)
        } else {
          playCorrect()
        }
      }
    } else {
      setCombo(0)
      playWrong()
      setHadMistake(true)
      const words = exerciseWords(exercise)
      addMistakes(words)
      setWrongWords((w) => [...w, ...words])
    }
  }

  function handleContinue() {
    const wasCorrect = lastResult?.correct
    let nextQueue = queue
    if (!wasCorrect) {
      // מחזירים את התרגיל לסוף התור עד שעונים נכון,
      // ולפניו כרטיס לימוד מחדש של המילה — כדי שלא ננסה שוב בלי הקשר
      const word = exerciseWords(exercise)[0]
      const reteach = word && exercise.type !== 'matchPairs' ? [{ type: 'teach', word }] : []
      nextQueue = [...queue, ...reteach, exercise]
      setQueue(nextQueue)
    }
    setTotalDone((d) => d + 1)
    setAnswer(null)
    setChecked(false)
    setLastResult(null)

    if (current + 1 >= nextQueue.length) {
      onFinish(finishPayload())
    } else {
      maybeMilestone(totalDone + 1, nextQueue.length, hadMistake || !wasCorrect)
      setCurrent((c) => c + 1)
    }
  }

  if (!exercise) return null

  return (
    <div className={`mx-auto flex min-h-screen flex-col px-4 ${desktop ? 'max-w-3xl' : 'max-w-2xl'}`}>
      {/* הודעת עידוד / הבזק קומבו — שכבה שלא חוסמת מגע */}
      {milestone && (
        <div className="pointer-events-none fixed inset-0 z-40 flex items-center justify-center" aria-hidden="true">
          <div className="animate-flash rounded-3xl border-4 border-duo-yellow bg-white px-8 py-5 text-2xl font-extrabold text-duo-yellow-text shadow-xl">
            {milestone}
          </div>
        </div>
      )}
      {comboFlash && (
        <div className="pointer-events-none fixed inset-0 z-40 flex items-center justify-center" aria-hidden="true">
          <div className="animate-flash rounded-3xl border-4 border-duo-orange bg-white px-8 py-5 text-2xl font-extrabold text-duo-orange-text shadow-xl">
            {comboFlash}
          </div>
        </div>
      )}

      {/* גיליון אישור יציאה */}
      {exitConfirm && (
        <div className="fixed inset-0 z-50 flex items-end justify-center bg-black/40" onClick={() => setExitConfirm(false)}>
          <div
            className="animate-slide-up safe-bottom-8 w-full max-w-2xl rounded-t-3xl bg-white p-6 text-center"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="mb-2 text-5xl">🥺</div>
            <div className="mb-1 text-xl font-extrabold">לצאת באמצע השיעור?</div>
            <p className="mb-4 font-bold text-duo-muted">ההתקדמות בשיעור הזה תלך לאיבוד</p>
            <div className="flex flex-col gap-2">
              <Button onClick={() => setExitConfirm(false)}>להישאר ולסיים 💪</Button>
              <Button variant="ghost" onClick={() => navigate('/')}>יציאה</Button>
            </div>
          </div>
        </div>
      )}
      {/* פס עליון: יציאה + התקדמות */}
      <div className="flex items-center gap-3 py-4">
        <button
          type="button"
          onClick={() => (totalDone === 0 ? navigate('/') : setExitConfirm(true))}
          className="-m-2 flex h-11 w-11 items-center justify-center rounded-full text-2xl text-duo-muted transition-colors hover:text-duo-text active:bg-gray-100"
          title="יציאה מהשיעור"
          aria-label="יציאה מהשיעור"
        >
          ✕
        </button>
        <ProgressBar value={Math.min(totalDone, total)} max={total} className="flex-1" />
        {combo >= 2 && (
          <div
            key={combo}
            className="combo-pill rounded-full border-2 border-duo-orange bg-orange-50 px-2.5 py-0.5 text-sm font-extrabold text-duo-orange-text"
          >
            <span className="animate-flame">🔥</span>
            <span dir="ltr">×{combo}</span>
          </div>
        )}
      </div>

      <div className={`flex-1 pb-40 pt-4 ${exercise.type === 'teach' || exercise.type === 'grammar' ? 'flex flex-col justify-center' : ''}`}>
        {/* key מאפס state פנימי בין תרגילים וגם מפעיל את אנימציית הכניסה */}
        <div key={current} className="animate-exercise-enter">
          <Component exercise={exercise} answer={answer} setAnswer={setAnswer} checked={checked} onHint={registerHint} />
        </div>
        {hintedWords.size > 0 && (
          <div className="mt-6 text-center text-sm font-bold text-duo-muted">
            🔍 רמזים בשיעור הזה: {hintedWords.size} (כל רמז מוריד נקודה)
          </div>
        )}
      </div>

      {/* פס תחתון: בדיקה / משוב */}
      <div
        className={`safe-bottom-4 fixed bottom-0 left-0 right-0 border-t-2 p-4 ${
          !checked
            ? 'border-duo-gray bg-white'
            : lastResult?.correct
              ? 'animate-slide-up border-green-200 bg-green-100'
              : 'animate-slide-up border-red-200 bg-red-100'
        }`}
      >
        {checked && lastResult?.correct && <EmojiBurst key={current} count={combo >= 3 ? 12 : 7} />}
        <div className={`mx-auto flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between ${desktop ? 'max-w-3xl' : 'max-w-2xl'}`}>
          {exercise.type === 'teach' || exercise.type === 'grammar' ? (
            <>
              <div className="hidden font-bold text-duo-muted sm:block">{desktop ? '💡 Enter להמשך' : ''}</div>
              <Button variant="blue" onClick={handleTeachContinue} className="w-full sm:w-auto">
                הבנתי, המשך
              </Button>
            </>
          ) : checked ? (
            <>
              <div className="flex items-start gap-3">
                <span
                  aria-hidden="true"
                  className={`flex h-12 w-12 shrink-0 items-center justify-center rounded-full text-2xl ${
                    lastResult?.correct ? 'animate-mascot-jump bg-green-200' : 'animate-mascot-sad bg-red-200'
                  }`}
                >
                  {lastResult?.correct ? (lastResult?.almost ? '✏️' : combo >= 5 ? '😎' : '🤩') : '😅'}
                </span>
                <div>
                  <div className={`text-lg font-extrabold ${lastResult?.correct ? 'text-duo-green-darker' : 'text-duo-red-dark'}`}>
                    {lastResult?.correct
                      ? lastResult?.almost ? 'כמעט מושלם! יש טעות קטנה בכתיב' : lastResult?.praise
                      : 'לא נורא, ננסה שוב בהמשך'}
                  </div>
                  {(!lastResult?.correct || lastResult?.almost) && lastResult?.correctText && (
                    <div className={`font-bold ${lastResult?.correct ? 'text-duo-green-darker' : 'text-duo-red-dark'}`}>
                      התשובה הנכונה: <bdi dir="auto">{lastResult.correctText}</bdi>{' '}
                      {!/[֐-׿]/.test(lastResult.correctText) && (
                        <button type="button" onClick={() => speak(lastResult.correctText)} title="השמע">🔊</button>
                      )}
                    </div>
                  )}
                </div>
              </div>
              <Button variant={lastResult?.correct ? 'primary' : 'red'} onClick={handleContinue} className="w-full sm:w-auto">
                המשך
              </Button>
            </>
          ) : (
            <>
              <div className="hidden font-bold text-duo-muted sm:block">{desktop ? '💡 מקשים 1–4 לבחירה · Enter לבדיקה' : ''}</div>
              <Button
                disabled={!isAnswerReady(exercise, answer)}
                onClick={handleCheck}
                className="w-full sm:w-auto"
              >
                בדיקה
              </Button>
            </>
          )}
        </div>
      </div>
    </div>
  )
}
