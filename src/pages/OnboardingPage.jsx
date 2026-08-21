// מסלול מותאם אישית: שאלון היכרות + מבחן רמה אדפטיבי שקובע אילו יחידות ייפתחו.

import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { UNITS } from '../data/course'
import { makePlacementQuestion } from '../lib/exerciseGen'
import { levelName } from '../lib/progress'
import { useProgress } from '../hooks/useProgress'
import { speak, playCorrect, playWrong, playFanfare } from '../lib/speech'
import Button from '../components/Button'
import ProgressBar from '../components/ProgressBar'
import Confetti from '../components/Confetti'

const GOALS = [
  { id: 'travel', icon: '✈️', label: 'טיולים בחו"ל' },
  { id: 'work', icon: '💼', label: 'עבודה וקריירה' },
  { id: 'school', icon: '🎓', label: 'לימודים' },
  { id: 'fun', icon: '🎉', label: 'בשביל הכיף' },
]

const LEVELS = [
  { id: 'zero', icon: '🌱', label: 'מתחיל מאפס', desc: 'אני לא יודע כמעט כלום' },
  { id: 'some', icon: '🌿', label: 'יודע קצת', desc: 'מילים בסיסיות פה ושם' },
  { id: 'confident', icon: '🌳', label: 'מסתדר', desc: 'מבין ומרכיב משפטים פשוטים' },
]

const COMMITMENTS = [
  { id: 10, icon: '☕', label: '5 דקות ביום', desc: 'נינוח' },
  { id: 30, icon: '🚀', label: '10 דקות ביום', desc: 'רציני' },
  { id: 50, icon: '🔥', label: '15+ דקות ביום', desc: 'תותח' },
]

// כמה שאלות לכל יחידה במבחן הרמה
const QUESTIONS_PER_UNIT = 2

export default function OnboardingPage() {
  const navigate = useNavigate()
  const { saveProfile } = useProgress()

  const [step, setStep] = useState('welcome') // welcome | goal | selfLevel | commitment | test | result
  const [goal, setGoal] = useState(null)
  const [selfLevel, setSelfLevel] = useState(null)
  const [dailyGoal, setDailyGoal] = useState(null)
  const [result, setResult] = useState(null) // { placementLevel }

  function finish(placementLevel) {
    playFanfare()
    setResult({ placementLevel })
    setStep('result')
  }

  function startTest(levelId) {
    if (levelId === 'zero') {
      // מתחיל מאפס — אין צורך במבחן
      finish(1)
    } else {
      setStep('test')
    }
  }

  function complete() {
    saveProfile({ goal, selfLevel, placementLevel: result.placementLevel, dailyGoal })
    navigate('/', { replace: true })
  }

  function skipAll() {
    saveProfile({ goal, selfLevel, placementLevel: 1, dailyGoal })
    navigate('/', { replace: true })
  }

  return (
    <div className="mx-auto flex min-h-screen max-w-2xl flex-col px-6 py-8">
      {step === 'welcome' && (
        <CenterCard>
          <div className="animate-bounce-slow text-8xl">🦉</div>
          <h1 className="text-3xl font-extrabold text-duo-green">ברוכים הבאים ל־LinguaGo</h1>
          <p className="max-w-sm text-lg font-bold text-duo-muted">
            כמה שאלות קצרות כדי לבנות לך מסלול לימוד מותאם אישית — כולל מבחן רמה קטן וכיפי.
          </p>
          <Button className="w-full max-w-xs" onClick={() => setStep('goal')}>בוא נתחיל!</Button>
          <button type="button" onClick={skipAll} className="font-bold text-duo-muted hover:text-duo-text">
            דלג, אתחיל מההתחלה
          </button>
        </CenterCard>
      )}

      {step === 'goal' && (
        <QuestionScreen title="למה אתה לומד אנגלית? 🎯" progress={1}>
          {GOALS.map((g) => (
            <ChoiceCard key={g.id} icon={g.icon} label={g.label} selected={goal === g.id}
              onClick={() => { setGoal(g.id); setStep('selfLevel') }} />
          ))}
        </QuestionScreen>
      )}

      {step === 'selfLevel' && (
        <QuestionScreen title="כמה אנגלית אתה כבר יודע? 📊" progress={2}>
          {LEVELS.map((l) => (
            <ChoiceCard key={l.id} icon={l.icon} label={l.label} desc={l.desc} selected={selfLevel === l.id}
              onClick={() => { setSelfLevel(l.id); setStep('commitment') }} />
          ))}
        </QuestionScreen>
      )}

      {step === 'commitment' && (
        <QuestionScreen title="כמה זמן תרצה לתרגל ביום? ⏱️" progress={3}>
          {COMMITMENTS.map((c) => (
            <ChoiceCard key={c.id} icon={c.icon} label={c.label} desc={c.desc} selected={dailyGoal === c.id}
              onClick={() => { setDailyGoal(c.id); startTest(selfLevel) }} />
          ))}
        </QuestionScreen>
      )}

      {step === 'test' && (
        <PlacementTest
          startUnit={selfLevel === 'confident' ? 2 : 1}
          onFinish={finish}
          onSkip={skipAll}
        />
      )}

      {step === 'result' && result && (
        <CenterCard>
          <Confetti count={30} />
          <div className="animate-pop-in text-8xl">🏅</div>
          <h1 className="text-3xl font-extrabold text-duo-green">
            הרמה שלך: {levelName(result.placementLevel)}
          </h1>
          <p className="max-w-sm text-lg font-bold text-duo-muted">
            {result.placementLevel > 1
              ? `מעולה! פתחנו לך את ${result.placementLevel} היחידות הראשונות — אפשר לדלג קדימה או לחזק את הבסיס.`
              : 'נתחיל מהיסודות ונבנה בסיס חזק, צעד אחר צעד.'}
          </p>
          <div className="flex flex-wrap justify-center gap-2">
            {UNITS.slice(0, result.placementLevel).map((u) => (
              <span key={u.id} className="rounded-full px-3 py-1 text-sm font-extrabold text-white" style={{ backgroundColor: u.color }}>
                {u.icon} {u.title}
              </span>
            ))}
          </div>
          <Button className="w-full max-w-xs" onClick={complete}>בוא נלמד! 🚀</Button>
        </CenterCard>
      )}
    </div>
  )
}

function PlacementTest({ startUnit, onFinish, onSkip }) {
  const [unitIndex, setUnitIndex] = useState(startUnit)
  const [qInUnit, setQInUnit] = useState(0)
  const [correctInUnit, setCorrectInUnit] = useState(0)
  const [lastPassed, setLastPassed] = useState(-1) // רק יחידות שנבחנו בפועל
  const [failedMin, setFailedMin] = useState(null) // היחידה הנמוכה ביותר שנכשלו בה
  const [asked, setAsked] = useState(0)
  const [question, setQuestion] = useState(() => makePlacementQuestion(UNITS[startUnit]))
  const [picked, setPicked] = useState(null) // האפשרות שנבחרה (אחרי בחירה יש משוב קצר)

  const maxQuestions = QUESTIONS_PER_UNIT * (UNITS.length - startUnit)

  function pick(opt) {
    if (picked) return
    const correct = opt.en === question.word.en
    setPicked(opt)
    if (correct) playCorrect()
    else playWrong()

    setTimeout(() => {
      const nCorrect = correctInUnit + (correct ? 1 : 0)
      const nQ = qInUnit + 1
      setAsked((a) => a + 1)
      setPicked(null)

      if (nQ < QUESTIONS_PER_UNIT) {
        setQInUnit(nQ)
        setCorrectInUnit(nCorrect)
        setQuestion(makePlacementQuestion(UNITS[unitIndex]))
        return
      }
      // סיימנו יחידה — מחליטים לאן ממשיכים (אדפטיבי לשני הכיוונים)
      const moveTo = (idx) => {
        setUnitIndex(idx)
        setQInUnit(0)
        setCorrectInUnit(0)
        setQuestion(makePlacementQuestion(UNITS[idx]))
      }

      if (nCorrect >= 1) {
        // עבר את היחידה
        const next = unitIndex + 1
        if (next >= UNITS.length || (failedMin !== null && next >= failedMin)) {
          onFinish(Math.min(UNITS.length, unitIndex + 2))
          return
        }
        setLastPassed(unitIndex)
        moveTo(next)
      } else {
        // נכשל ביחידה
        setFailedMin((f) => (f === null ? unitIndex : Math.min(f, unitIndex)))
        if (lastPassed >= 0) {
          // כבר הוכיח רמה קודמת — מסיימים לפיה
          onFinish(Math.max(1, Math.min(UNITS.length, lastPassed + 2)))
        } else if (unitIndex > 1) {
          // עוד לא עבר כלום — יורדים לבדוק רמה נמוכה יותר
          moveTo(unitIndex - 1)
        } else {
          onFinish(1)
        }
      }
    }, 900)
  }

  const en2he = question.direction === 'en2he'

  return (
    <div className="flex flex-1 flex-col">
      <div className="mb-6 flex items-center gap-3">
        <button type="button" onClick={onSkip} className="text-2xl text-duo-muted hover:text-duo-text" title="דלג על המבחן">✕</button>
        <ProgressBar value={asked} max={maxQuestions} color="#1cb0f6" className="flex-1" />
      </div>

      <div className="mb-2 text-sm font-extrabold text-duo-blue">מבחן רמה · שאלה {asked + 1}</div>
      <h2 className="mb-6 text-2xl font-extrabold">
        {en2he ? 'מה פירוש המילה?' : 'איך אומרים באנגלית?'}
      </h2>

      <div className="mb-8 flex justify-center">
        {en2he ? (
          <button
            type="button"
            onClick={() => speak(question.word.en)}
            className="flex items-center gap-3 rounded-2xl border-2 border-duo-gray px-6 py-4 text-3xl font-extrabold text-duo-blue hover:bg-sky-50"
            dir="ltr"
          >
            <span>🔊</span>
            <span className="text-duo-text">{question.word.en}</span>
          </button>
        ) : (
          <div className="rounded-2xl border-2 border-duo-gray px-6 py-4 text-3xl font-extrabold">
            {question.word.emoji && <span className="ml-3">{question.word.emoji}</span>}
            {question.word.he}
          </div>
        )}
      </div>

      <div className="grid grid-cols-2 gap-3">
        {question.options.map((opt) => {
          const isCorrect = picked && opt.en === question.word.en
          const isWrongPick = picked?.en === opt.en && opt.en !== question.word.en
          return (
            <button
              key={opt.en}
              type="button"
              disabled={!!picked}
              onClick={() => pick(opt)}
              dir={en2he ? 'rtl' : 'ltr'}
              className={`btn-3d rounded-2xl border-2 p-4 text-lg font-bold ${
                isCorrect
                  ? 'border-duo-green bg-green-50 text-duo-green-darker'
                  : isWrongPick
                    ? 'border-duo-red bg-red-50 text-duo-red-dark animate-shake'
                    : 'border-duo-gray bg-white hover:bg-gray-50'
              }`}
            >
              <div className="text-3xl">{opt.emoji}</div>
              <div>{en2he ? opt.he : opt.en}</div>
            </button>
          )
        })}
      </div>
    </div>
  )
}

function CenterCard({ children }) {
  return (
    <div className="relative flex flex-1 flex-col items-center justify-center gap-5 overflow-hidden text-center">
      {children}
    </div>
  )
}

function QuestionScreen({ title, progress, children }) {
  return (
    <div className="flex flex-1 flex-col">
      <ProgressBar value={progress} max={4} className="mb-8" />
      <h1 className="mb-6 text-2xl font-extrabold">{title}</h1>
      <div className="flex flex-col gap-3">{children}</div>
    </div>
  )
}

function ChoiceCard({ icon, label, desc, selected, onClick }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`btn-3d flex items-center gap-4 rounded-2xl border-2 p-4 text-start ${
        selected ? 'border-duo-blue bg-sky-50' : 'border-duo-gray bg-white hover:bg-gray-50'
      }`}
    >
      <span className="text-4xl">{icon}</span>
      <span>
        <span className="block text-lg font-extrabold">{label}</span>
        {desc && <span className="block text-sm font-bold text-duo-muted">{desc}</span>}
      </span>
    </button>
  )
}
