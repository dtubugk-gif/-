// תרגיל בניית משפט: בוחרים מילים מהמאגר ומסדרים אותן למשפט באנגלית.

import { speak } from '../../lib/speech'
import HintText from '../HintText'

export default function SentenceBuild({ exercise, answer, setAnswer, checked, onHint }) {
  const chosen = answer || [] // רשימת אינדקסים במאגר
  const { sentence, bank } = exercise

  const chosenIdx = new Set(chosen)

  function addWord(i) {
    if (checked || chosenIdx.has(i)) return
    speak(bank[i])
    setAnswer([...chosen, i])
  }

  function removeWord(pos) {
    if (checked) return
    setAnswer(chosen.filter((_, idx) => idx !== pos))
  }

  return (
    <div>
      <h2 className="mb-6 text-2xl font-extrabold">תרגם את המשפט 🧩</h2>

      <div className="mb-6 rounded-2xl border-2 border-duo-gray px-5 py-4 text-xl font-bold leading-9">
        <HintText text={sentence.he} dir="rtl" onHint={onHint} />
      </div>

      {/* אזור המשפט הנבנה */}
      <div
        dir="ltr"
        className="mb-6 flex min-h-16 flex-wrap items-start gap-2 border-b-2 border-t-2 border-duo-gray py-3"
      >
        {chosen.length === 0 && <span className="text-duo-muted" dir="rtl">לחץ על מילים כדי לבנות את המשפט...</span>}
        {chosen.map((bankIdx, pos) => (
          <button
            key={`${bankIdx}-${pos}`}
            type="button"
            disabled={checked}
            onClick={() => removeWord(pos)}
            className="btn-3d rounded-xl border-2 border-duo-gray bg-white px-3 py-2 text-lg font-bold"
          >
            {bank[bankIdx]}
          </button>
        ))}
      </div>

      {/* מאגר מילים */}
      <div dir="ltr" className="flex flex-wrap gap-2">
        {bank.map((w, i) => (
          <button
            key={i}
            type="button"
            disabled={checked || chosenIdx.has(i)}
            onClick={() => addWord(i)}
            className={`btn-3d rounded-xl border-2 px-3 py-2 text-lg font-bold transition-opacity ${
              chosenIdx.has(i)
                ? 'border-duo-gray bg-duo-gray text-duo-gray'
                : 'border-duo-gray bg-white hover:bg-gray-50'
            }`}
          >
            {w}
          </button>
        ))}
      </div>
    </div>
  )
}
