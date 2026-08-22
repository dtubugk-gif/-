// כרטיסיית "מילה חדשה": מלמדים את המילה לפני שבוחנים עליה — כמו בדואלינגו.

import { useEffect } from 'react'
import { speak } from '../../lib/speech'

export default function TeachCard({ exercise }) {
  const { word } = exercise

  // משמיעים את המילה אוטומטית כשמציגים אותה
  useEffect(() => {
    const t = setTimeout(() => speak(word.en), 400)
    return () => clearTimeout(t)
  }, [word.en])

  return (
    <div className="flex flex-col items-center text-center">
      <div className="mb-6 animate-pop-in rounded-full bg-purple-100 px-5 py-2 text-lg font-extrabold text-duo-purple">
        ✨ מילה חדשה! ✨
      </div>

      <button
        type="button"
        onClick={() => speak(word.en)}
        className="btn-3d w-full max-w-sm rounded-3xl border-2 border-duo-gray bg-white p-8 transition-transform hover:scale-[1.02]"
        title="לחץ להשמעה"
      >
        <div className="mb-4 text-8xl">{word.emoji || '⭐'}</div>
        <div dir="ltr" className="mb-2 flex items-center justify-center gap-3 text-4xl font-extrabold text-duo-blue">
          <span>🔊</span>
          <span>{word.en}</span>
        </div>
        <div className="text-2xl font-extrabold text-duo-text">{word.he}</div>
      </button>

      <p className="mt-6 font-bold text-duo-muted">לחץ על הכרטיס כדי לשמוע שוב 👆</p>
    </div>
  )
}
