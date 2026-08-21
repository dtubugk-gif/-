// מנוע התרגול: מציג תרגילים בזה אחר זה, בודק תשובות, מנהל לבבות,
// מחזיר תרגילים שגויים לסוף התור (כמו דואלינגו) ומדווח על סיום.

import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import Button from './Button'
import ProgressBar from './ProgressBar'
import MultipleChoice from './exercises/MultipleChoice'
import Listening from './exercises/Listening'
import TypeTranslation from './exercises/TypeTranslation'
import SentenceBuild from './exercises/SentenceBuild'
import MatchPairs from './exercises/MatchPairs'
import { checkAnswer, isAnswerReady } from './exercises/checkAnswer'
import { exerciseWords } from '../lib/exerciseGen'
import { playCorrect, playWrong, speak } from '../lib/speech'
import { useProgress } from '../hooks/useProgress'

const EXERCISE_COMPONENTS = {
  multipleChoice: MultipleChoice,
  listening: Listening,
  typeTranslation: TypeTranslation,
  sentenceBuild: SentenceBuild,
  matchPairs: MatchPairs,
}

export default function LessonEngine({ exercises: initialExercises, useHearts = true, onFinish, onOutOfHearts }) {
  const navigate = useNavigate()
  const { state, loseHeart, addMistakes } = useProgress()

  const [queue, setQueue] = useState(initialExercises)
  const [current, setCurrent] = useState(0)
  const [answer, setAnswer] = useState(null)
  const [checked, setChecked] = useState(false)
  const [lastResult, setLastResult] = useState(null)
  const [totalDone, setTotalDone] = useState(0)
  const [hadMistake, setHadMistake] = useState(false)
  const [wrongWords, setWrongWords] = useState([])

  const exercise = queue[current]
  const total = queue.length
  const Component = exercise ? EXERCISE_COMPONENTS[exercise.type] : null

  function handleCheck() {
    const result = checkAnswer(exercise, answer)
    // טעויות בהתאמת זוגות נרשמות לתרגול אבל לא עולות לב
    if (exercise.type === 'matchPairs' && answer?.wrongWords?.length) {
      addMistakes(answer.wrongWords)
      setWrongWords((w) => [...w, ...answer.wrongWords])
    }
    setChecked(true)
    setLastResult(result)
    if (result.correct) {
      playCorrect()
    } else {
      playWrong()
      setHadMistake(true)
      const words = exerciseWords(exercise)
      addMistakes(words)
      setWrongWords((w) => [...w, ...words])
      if (useHearts) loseHeart()
    }
  }

  function handleContinue() {
    const wasCorrect = lastResult?.correct
    if (!wasCorrect && useHearts && state.hearts <= 0) {
      onOutOfHearts?.()
      return
    }
    let nextQueue = queue
    if (!wasCorrect) {
      // מחזירים את התרגיל לסוף התור עד שעונים נכון
      nextQueue = [...queue, exercise]
      setQueue(nextQueue)
    }
    setTotalDone((d) => d + 1)
    setAnswer(null)
    setChecked(false)
    setLastResult(null)

    if (current + 1 >= nextQueue.length) {
      onFinish({ perfect: !hadMistake, wrongWords })
    } else {
      setCurrent((c) => c + 1)
    }
  }

  if (!exercise) return null

  return (
    <div className="mx-auto flex min-h-screen max-w-2xl flex-col px-4">
      {/* פס עליון: יציאה + התקדמות + לבבות */}
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
        {useHearts && (
          <div className="flex items-center gap-1 font-extrabold text-duo-red">
            <span>❤️</span>
            <span>{state.hearts}</span>
          </div>
        )}
      </div>

      <div className="flex-1 pb-40 pt-4">
        {/* key מאפס state פנימי (למשל בהתאמת זוגות) בין תרגילים מאותו סוג */}
        <Component key={current} exercise={exercise} answer={answer} setAnswer={setAnswer} checked={checked} />
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
          {checked ? (
            <>
              <div className="flex items-start gap-3">
                <span className="text-3xl">{lastResult?.correct ? '✅' : '❌'}</span>
                <div>
                  <div className={`text-lg font-extrabold ${lastResult?.correct ? 'text-duo-green-darker' : 'text-duo-red-dark'}`}>
                    {lastResult?.correct ? 'מעולה! תשובה נכונה' : 'לא נורא, ננסה שוב בהמשך'}
                  </div>
                  {!lastResult?.correct && lastResult?.correctText && (
                    <div className="font-bold text-duo-red-dark">
                      התשובה הנכונה: <bdi dir="ltr">{lastResult.correctText}</bdi>{' '}
                      <button type="button" onClick={() => speak(lastResult.correctText)} title="השמע">🔊</button>
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
