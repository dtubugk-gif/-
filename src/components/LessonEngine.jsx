// מנוע התרגול: מציג תרגילים בזה אחר זה, בודק תשובות,
// מחזיר תרגילים שגויים לסוף התור (כמו דואלינגו) ומדווח על סיום.

import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
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
import { playCorrect, playWrong, playCombo, speak } from '../lib/speech'
import { useProgress } from '../hooks/useProgress'

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

  // כרטיסיית לימוד: אין בדיקה — פשוט ממשיכים הלאה
  function handleTeachContinue() {
    setTotalDone((d) => d + 1)
    setAnswer(null)
    if (current + 1 >= queue.length) {
      onFinish({ perfect: !hadMistake, wrongWords, hintsUsed: hintedWords.size, maxCombo })
    } else {
      setCurrent((c) => c + 1)
    }
  }

  function handleCheck() {
    const result = checkAnswer(exercise, answer)
    result.praise = PRAISES[Math.floor(Math.random() * PRAISES.length)]
    // טעויות בהתאמת זוגות נרשמות אבל לא נחשבות טעות בשיעור
    if (exercise.type === 'matchPairs' && answer?.wrongWords?.length) {
      addMistakes(answer.wrongWords)
      setWrongWords((w) => [...w, ...answer.wrongWords])
    }
    setChecked(true)
    setLastResult(result)
    if (result.correct) {
      // התאמת זוגות עם טעויות בדרך "עוברת" אבל לא מגדילה את הקומבו
      if (exercise.type === 'matchPairs' && answer?.wrongWords?.length) {
        playCorrect()
      } else {
        const streakNow = combo + 1
        setCombo(streakNow)
        setMaxCombo((m) => Math.max(m, streakNow))
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
      onFinish({ perfect: !hadMistake, wrongWords, hintsUsed: hintedWords.size, maxCombo })
    } else {
      setCurrent((c) => c + 1)
    }
  }

  if (!exercise) return null

  return (
    <div className="mx-auto flex min-h-screen max-w-2xl flex-col px-4">
      {/* פס עליון: יציאה + התקדמות */}
      <div className="flex items-center gap-3 py-4">
        <button
          type="button"
          onClick={() => navigate('/')}
          className="text-2xl text-duo-muted transition-colors hover:text-duo-text"
          title="יציאה מהשיעור"
        >
          ✕
        </button>
        <ProgressBar value={Math.min(totalDone, total)} max={total} className="flex-1" />
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
        className={`fixed bottom-0 left-0 right-0 border-t-2 p-4 ${
          !checked
            ? 'border-duo-gray bg-white'
            : lastResult?.correct
              ? 'animate-slide-up border-green-200 bg-green-100'
              : 'animate-slide-up border-red-200 bg-red-100'
        }`}
      >
        <div className="mx-auto flex max-w-2xl flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          {exercise.type === 'teach' || exercise.type === 'grammar' ? (
            <>
              <div className="hidden text-duo-muted sm:block" />
              <Button variant="blue" onClick={handleTeachContinue} className="w-full sm:w-auto">
                הבנתי, המשך
              </Button>
            </>
          ) : checked ? (
            <>
              <div className="flex items-start gap-3">
                <span className="text-3xl">{lastResult?.correct ? (lastResult?.almost ? '✏️' : '✅') : '❌'}</span>
                <div>
                  <div className={`text-lg font-extrabold ${lastResult?.correct ? 'text-duo-green-darker' : 'text-duo-red-dark'}`}>
                    {lastResult?.correct
                      ? lastResult?.almost ? 'כמעט מושלם! יש טעות קטנה בכתיב' : lastResult?.praise
                      : 'לא נורא, ננסה שוב בהמשך'}
                  </div>
                  {(!lastResult?.correct || lastResult?.almost) && lastResult?.correctText && (
                    <div className={`font-bold ${lastResult?.correct ? 'text-duo-green-darker' : 'text-duo-red-dark'}`}>
                      התשובה הנכונה: <bdi dir="auto">{lastResult.correctText}</bdi>{' '}
                      {!/[\u0590-\u05FF]/.test(lastResult.correctText) && (
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
              <div className="hidden text-duo-muted sm:block" />
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
