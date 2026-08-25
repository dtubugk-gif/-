// תרגיל השלמת משפט: משפט באנגלית עם מילה חסרה — בוחרים את המילה הנכונה.

import { speak } from '../../lib/speech'
import HintText from '../HintText'

export default function FillBlank({ exercise, answer, setAnswer, checked, onHint }) {
  const { sentence, blankIndex, options } = exercise
  const tokens = sentence.en.split(' ')

  return (
    <div>
      <h2 className="mb-6 text-2xl font-extrabold">מה המילה החסרה? 📝</h2>

      <div className="mb-3 flex flex-wrap items-center justify-center gap-2 rounded-2xl border-2 border-duo-gray px-5 py-4 text-xl font-extrabold" dir="ltr">
        <button type="button" onClick={() => speak(sentence.en)} className="text-duo-blue" title="השמע">🔊</button>
        {tokens.map((t, i) =>
          i === blankIndex ? (
            <span
              key={i}
              className={`inline-block min-w-20 rounded-xl border-2 border-dashed px-3 py-1 text-center ${
                answer ? 'border-duo-blue text-duo-blue-dark' : 'border-duo-muted text-duo-muted'
              }`}
            >
              {answer || '____'}
            </span>
          ) : (
            <span key={i}>{t}</span>
          )
        )}
      </div>

      <div className="fill-guide mb-8 text-center font-bold text-duo-muted">
        <HintText text={sentence.he} dir="rtl" onHint={onHint} />
      </div>

      <div className="flex flex-wrap justify-center gap-3" dir="ltr">
        {options.map((opt) => {
          const selected = answer === opt
          const showCorrect = checked && opt === exercise.missing
          const showWrong = checked && selected && opt !== exercise.missing
          return (
            <button
              key={opt}
              type="button"
              disabled={checked}
              onClick={() => {
                speak(opt)
                setAnswer(opt)
              }}
              className={`btn-3d rounded-2xl border-2 px-5 py-3 text-lg font-bold ${
                showCorrect
                  ? 'border-duo-green bg-green-50 text-duo-green-darker'
                  : showWrong
                    ? 'border-duo-red bg-red-50 text-duo-red-dark animate-shake'
                    : selected
                      ? 'border-duo-blue bg-sky-50 text-duo-blue-dark'
                      : 'border-duo-gray bg-white hover:bg-gray-50'
              }`}
            >
              {opt}
            </button>
          )
        })}
      </div>
    </div>
  )
}
