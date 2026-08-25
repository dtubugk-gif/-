// תרגיל התאמת זוגות: מחברים מילה באנגלית לתרגום בעברית.

import { useState } from 'react'
import { speak, playCorrect, playWrong } from '../../lib/speech'
import { shuffle } from '../../lib/exerciseGen'

export default function MatchPairs({ exercise, setAnswer, checked }) {
  const { pairs } = exercise
  const [enOrder] = useState(() => shuffle(pairs))
  const [heOrder] = useState(() => shuffle(pairs))
  const [selectedEn, setSelectedEn] = useState(null)
  const [selectedHe, setSelectedHe] = useState(null)
  const [matched, setMatched] = useState(new Set())
  const [wrongFlash, setWrongFlash] = useState(null)
  const [wrongWords, setWrongWords] = useState([])

  function tryMatch(en, he) {
    const pair = pairs.find((p) => p.en === en)
    if (pair && pair.he === he) {
      playCorrect()
      const next = new Set(matched).add(en)
      setMatched(next)
      if (next.size === pairs.length) {
        setAnswer({ completed: true, wrongWords })
      }
    } else {
      playWrong()
      const missed = pairs.filter((p) => p.en === en || p.he === he)
      setWrongWords((w) => [...w, ...missed])
      setWrongFlash({ en, he })
      setTimeout(() => setWrongFlash(null), 500)
    }
    setSelectedEn(null)
    setSelectedHe(null)
  }

  function clickEn(en) {
    if (checked || matched.has(en)) return
    speak(en)
    if (selectedHe) tryMatch(en, selectedHe)
    else setSelectedEn(en)
  }

  function clickHe(he) {
    if (checked) return
    const pairOfHe = pairs.find((p) => p.he === he)
    if (pairOfHe && matched.has(pairOfHe.en)) return
    if (selectedEn) tryMatch(selectedEn, he)
    else setSelectedHe(he)
  }

  function btnClass(isMatched, isSelected, isWrong) {
    if (isMatched) return 'border-duo-green bg-green-50 text-duo-green-darker opacity-60'
    if (isWrong) return 'border-duo-red bg-red-50 text-duo-red-dark animate-shake'
    if (isSelected) return 'border-duo-blue bg-sky-50 text-duo-blue-dark'
    return 'border-duo-gray bg-white hover:bg-gray-50'
  }

  return (
    <div>
      <h2 className="mb-6 text-2xl font-extrabold">התאימו את הזוגות 🔗</h2>
      <div className="grid grid-cols-2 gap-3">
        <div className="flex flex-col gap-3" dir="ltr">
          {enOrder.map((p) => (
            <button
              key={p.en}
              type="button"
              onClick={() => clickEn(p.en)}
              className={`btn-3d rounded-2xl border-2 p-3 text-lg font-bold ${btnClass(
                matched.has(p.en),
                selectedEn === p.en,
                wrongFlash?.en === p.en
              )}`}
            >
              {p.en}
            </button>
          ))}
        </div>
        <div className="flex flex-col gap-3">
          {heOrder.map((p) => (
            <button
              key={p.he}
              type="button"
              onClick={() => clickHe(p.he)}
              className={`btn-3d rounded-2xl border-2 p-3 text-lg font-bold ${btnClass(
                matched.has(p.en),
                selectedHe === p.he,
                wrongFlash?.he === p.he
              )}`}
            >
              {p.he}
            </button>
          ))}
        </div>
      </div>
    </div>
  )
}
