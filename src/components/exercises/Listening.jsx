// תרגיל האזנה: משמיעים מילה באנגלית ובוחרים מה שמעת.

import { useEffect } from 'react'
import { speak } from '../../lib/speech'

export default function Listening({ exercise, answer, setAnswer, checked }) {
  const { word, options } = exercise

  useEffect(() => {
    const t = setTimeout(() => speak(word.en), 400)
    return () => clearTimeout(t)
  }, [word.en])

  return (
    <div>
      <h2 className="mb-6 text-2xl font-extrabold">מה שמעת? 👂</h2>

      <div className="mb-8 flex justify-center gap-3">
        <button
          type="button"
          onClick={() => speak(word.en)}
          className="btn-3d flex h-24 w-24 items-center justify-center rounded-3xl border-2 border-duo-blue-dark bg-duo-blue text-5xl text-white"
          title="השמע שוב"
        >
          🔊
        </button>
        <button
          type="button"
          onClick={() => speak(word.en, 0.5)}
          className="btn-3d flex h-24 w-16 items-center justify-center self-end rounded-2xl border-2 border-duo-blue-dark bg-duo-blue text-2xl text-white"
          title="השמע לאט"
        >
          🐢
        </button>
      </div>

      <div className="grid grid-cols-2 gap-3" dir="ltr">
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
              className={`btn-3d rounded-2xl border-2 p-4 text-lg font-bold transition-colors ${
                showCorrect
                  ? 'border-duo-green bg-green-50 text-duo-green-darker'
                  : showWrong
                    ? 'border-duo-red bg-red-50 text-duo-red-dark animate-shake'
                    : selected
                      ? 'border-duo-blue bg-sky-50 text-duo-blue-dark'
                      : 'border-duo-gray bg-white hover:bg-gray-50'
              }`}
            >
              {opt.en}
            </button>
          )
        })}
      </div>
    </div>
  )
}
