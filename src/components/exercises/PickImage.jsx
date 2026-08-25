// תרגיל בחירת תמונה: שומעים/רואים מילה באנגלית ובוחרים את האימוג'י המתאים.
// תרגיל חביב במיוחד על ילדים — מופיע ברמות הנמוכות.

import { useEffect } from 'react'
import { speak } from '../../lib/speech'
import HintText from '../HintText'

export default function PickImage({ exercise, answer, setAnswer, checked, onHint }) {
  const { word, options } = exercise

  useEffect(() => {
    const t = setTimeout(() => speak(word.en), 400)
    return () => clearTimeout(t)
  }, [word.en])

  return (
    <div>
      <h2 className="mb-6 text-2xl font-extrabold">איזו תמונה מתאימה? 🖼️</h2>

      <div className="mb-8 flex items-center justify-center">
        <div className="flex items-center gap-3 rounded-2xl border-2 border-duo-gray px-6 py-4 text-3xl font-extrabold" dir="ltr">
          <button type="button" onClick={() => speak(word.en)} className="text-duo-blue" title="השמע שוב">🔊</button>
          <HintText text={word.en} dir="ltr" onHint={onHint} />
        </div>
      </div>

      <div className="grid grid-cols-2 gap-3">
        {options.map((opt) => {
          const selected = answer?.en === opt.en
          const showCorrect = checked && opt.en === word.en
          const showWrong = checked && selected && opt.en !== word.en
          return (
            <button
              key={opt.en}
              type="button"
              disabled={checked}
              onClick={() => setAnswer(opt)}
              className={`btn-3d flex h-28 items-center justify-center rounded-2xl border-2 text-6xl transition-colors sm:h-32 ${
                showCorrect
                  ? 'border-duo-green bg-green-50'
                  : showWrong
                    ? 'border-duo-red bg-red-50 animate-shake'
                    : selected
                      ? 'border-duo-blue bg-sky-50'
                      : 'border-duo-gray bg-white hover:bg-gray-50'
              }`}
            >
              {opt.emoji}
            </button>
          )
        })}
      </div>
    </div>
  )
}
