// תרגיל בחירה מרובה: תרגום מילה עם 4 אפשרויות.

import { useEffect } from 'react'
import { speak } from '../../lib/speech'

export default function MultipleChoice({ exercise, answer, setAnswer, checked }) {
  const { word, options, direction } = exercise
  const en2he = direction === 'en2he'

  // כמו בדואלינגו — המילה באנגלית מושמעת אוטומטית כשהתרגיל מופיע
  useEffect(() => {
    if (en2he) {
      const t = setTimeout(() => speak(word.en), 400)
      return () => clearTimeout(t)
    }
  }, [word.en, en2he])

  return (
    <div>
      <h2 className="mb-6 text-2xl font-extrabold">
        {en2he ? 'מה פירוש המילה?' : 'איך אומרים באנגלית?'}
      </h2>

      <div className="mb-8 flex items-center justify-center gap-3">
        {en2he ? (
          <button
            type="button"
            onClick={() => speak(word.en)}
            className="flex items-center gap-3 rounded-2xl border-2 border-duo-gray px-6 py-4 text-3xl font-extrabold text-duo-blue transition-colors hover:bg-sky-50"
            dir="ltr"
          >
            <span>🔊</span>
            <span className="text-duo-text">{word.en}</span>
          </button>
        ) : (
          <div className="rounded-2xl border-2 border-duo-gray px-6 py-4 text-3xl font-extrabold">
            {word.emoji && <span className="ml-3">{word.emoji}</span>}
            {word.he}
          </div>
        )}
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
              onClick={() => {
                setAnswer(opt)
                if (!en2he) speak(opt.en)
              }}
              dir={en2he ? 'rtl' : 'ltr'}
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
              <div className="text-3xl">{opt.emoji}</div>
              <div>{en2he ? opt.he : opt.en}</div>
            </button>
          )
        })}
      </div>
    </div>
  )
}
