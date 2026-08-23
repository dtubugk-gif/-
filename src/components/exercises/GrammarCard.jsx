// כרטיסיית דקדוק: מלמדים את to be (am/is/are) לפני הרכבת המשפט הראשון.

import { useEffect } from 'react'
import { TO_BE } from '../../data/course'
import { speak } from '../../lib/speech'

export default function GrammarCard() {
  useEffect(() => {
    const t = setTimeout(() => speak('I am. he is. we are.', 0.8), 400)
    return () => clearTimeout(t)
  }, [])

  return (
    <div className="flex flex-col items-center text-center">
      <div className="mb-6 animate-pop-in rounded-full bg-sky-100 px-5 py-2 text-lg font-extrabold text-duo-blue-dark">
        📖 רגע של דקדוק!
      </div>

      <h2 className="mb-2 text-2xl font-extrabold">
        <bdi dir="ltr" className="text-duo-blue">to be</bdi> — המילה הקטנה שמחברת הכול
      </h2>
      <p className="mb-6 max-w-sm font-bold text-duo-muted">{TO_BE.note}</p>

      <div className="flex w-full max-w-sm flex-col gap-3">
        {TO_BE.rows.map((row) => (
          <button
            key={row.en}
            type="button"
            onClick={() => speak(row.example)}
            className="btn-3d rounded-2xl border-2 border-duo-gray bg-white p-4 text-start"
            title="לחץ להשמעה"
          >
            <div className="flex items-center justify-between gap-2">
              <span dir="ltr" className="text-xl font-extrabold text-duo-blue">🔊 {row.en}</span>
              <span className="font-extrabold">{row.he}</span>
            </div>
            <div className="mt-1 text-sm font-bold text-duo-muted">
              <bdi dir="ltr">{row.example}</bdi> — {row.exampleHe}
            </div>
          </button>
        ))}
      </div>
    </div>
  )
}
