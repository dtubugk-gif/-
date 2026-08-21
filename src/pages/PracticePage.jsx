// עמוד תרגול: חזרה על מילים שטעית בהן. מסיים תרגול → XP + לב חזרה.

import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useProgress } from '../hooks/useProgress'
import { generatePracticeExercises } from '../lib/exerciseGen'
import LessonEngine from '../components/LessonEngine'
import Button from '../components/Button'
import Confetti from '../components/Confetti'
import { playFanfare, speak } from '../lib/speech'

export default function PracticePage() {
  const { state, completePractice, clearMistakes } = useProgress()
  const navigate = useNavigate()
  const [exercises, setExercises] = useState(null)
  const [finished, setFinished] = useState(null)

  if (finished) {
    return (
      <div className="relative flex flex-col items-center justify-center gap-5 overflow-hidden py-16 text-center">
        <Confetti count={25} />
        <div className="animate-bounce-slow text-7xl">💪</div>
        <h1 className="animate-pop-in text-3xl font-extrabold text-duo-blue">תרגול הושלם!</h1>
        <div className="flex gap-4">
          <div dir="ltr" className="rounded-2xl border-2 border-duo-yellow bg-yellow-50 px-6 py-3 font-extrabold text-duo-yellow-dark">
            +{finished.xpGained} XP
          </div>
          <div className="rounded-2xl border-2 border-duo-red bg-red-50 px-6 py-3 font-extrabold text-duo-red">
            ❤️ לב חזר אליך
          </div>
        </div>
        <Button onClick={() => { setFinished(null); setExercises(null); navigate('/') }}>המשך</Button>
      </div>
    )
  }

  if (exercises) {
    return (
      <LessonEngine
        exercises={exercises}
        useHearts={false}
        onFinish={({ wrongWords }) => {
          const result = completePractice()
          // מילים שתורגלו נכון בכל התרגול — יוצאות מרשימת הטעויות
          const stillWrong = new Set(wrongWords.map((w) => w.en))
          const practiced = new Set(exercises.flatMap((e) =>
            e.type === 'matchPairs' ? e.pairs.map((p) => p.en) : [e.word?.en || e.item?.en].filter(Boolean)
          ))
          clearMistakes([...practiced].filter((en) => !stillWrong.has(en)))
          playFanfare()
          setFinished({ xpGained: result.xpGained })
        }}
      />
    )
  }

  return (
    <div>
      <h1 className="mb-2 text-2xl font-extrabold">תרגול טעויות 💪</h1>
      <p className="mb-6 font-bold text-duo-muted">
        כאן חוזרים על מילים שהתבלבלת בהן. תרגול מוצלח מחזיר לב אחד! ❤️
      </p>

      {state.mistakes.length === 0 ? (
        <div className="flex flex-col items-center gap-4 rounded-2xl border-2 border-duo-gray p-10 text-center">
          <div className="text-6xl">🦉✨</div>
          <div className="text-xl font-extrabold">אין טעויות לתרגל!</div>
          <p className="font-bold text-duo-muted">כשתטעה בשיעור, המילים יגיעו לכאן לחזרה.</p>
          <Button variant="blue" onClick={() => navigate('/')}>לשיעור הבא</Button>
        </div>
      ) : (
        <>
          <div className="mb-6 grid grid-cols-2 gap-2 sm:grid-cols-3">
            {state.mistakes.map((m) => (
              <button
                key={m.en}
                type="button"
                onClick={() => speak(m.en)}
                className="flex items-center justify-between rounded-xl border-2 border-duo-gray p-3 text-start font-bold hover:bg-gray-50"
                title="לחץ להשמעה"
              >
                <span>
                  <bdi dir="ltr" className="text-duo-blue">{m.en}</bdi>
                  <span className="block text-sm text-duo-muted">{m.he}</span>
                </span>
                <span>{m.emoji || '🔊'}</span>
              </button>
            ))}
          </div>
          <Button className="w-full" onClick={() => setExercises(generatePracticeExercises(state.mistakes))}>
            התחל תרגול ({state.mistakes.length} מילים)
          </Button>
        </>
      )}
    </div>
  )
}
