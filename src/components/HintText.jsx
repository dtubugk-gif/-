// רמזי מילים בסגנון דואלינגו: מילים שמופיעות במילון הקורס מקבלות קו מקווקו,
// ולחיצה עליהן פותחת בועת פירוש (עברית⇄אנגלית) ומשמיעה את המילה באנגלית.
// כל פתיחת רמז מדווחת החוצה דרך onHint — ומורידה מניקוד השיעור.

import { useEffect, useState } from 'react'
import { ALL_WORDS } from '../data/course'
import { speak } from '../lib/speech'

const EN_TO_HE = new Map()
const HE_TO_EN = new Map()
for (const w of ALL_WORDS) {
  const en = w.en.toLowerCase()
  if (!EN_TO_HE.has(en)) EN_TO_HE.set(en, w.he)
  const bare = en.replace(/^to /, '')
  if (!EN_TO_HE.has(bare)) EN_TO_HE.set(bare, w.he)
  if (!HE_TO_EN.has(w.he)) HE_TO_EN.set(w.he, w.en)
  const bareHe = w.he.replace(/^ל/, '')
  if (w.en.startsWith('to ') && !HE_TO_EN.has(bareHe)) HE_TO_EN.set(bareHe, w.en)
}

const clean = (t) => t.toLowerCase().replace(/[.,!?'"()־]/g, '')
const isHebrew = (t) => /[֐-׿]/.test(t)

// ניסיון התאמה לטוקן עברי — גם בלי אותיות שימוש (ה, ו, ב, ל, מ, ש, כ)
function lookupHe(token) {
  if (HE_TO_EN.has(token)) return HE_TO_EN.get(token)
  if (/^[הובלמשכ]/.test(token) && HE_TO_EN.has(token.slice(1))) return HE_TO_EN.get(token.slice(1))
  return null
}

// מפרק טקסט ליחידות: צירופים של שתי מילים מהמילון (thank you / בוקר טוב) נשארים יחד
function tokenize(text) {
  const tokens = text.split(' ')
  const units = []
  for (let i = 0; i < tokens.length; i++) {
    const t = clean(tokens[i])
    const next = i + 1 < tokens.length ? clean(tokens[i + 1]) : null
    const hebrew = isHebrew(tokens[i])
    if (next) {
      const bigram = `${t} ${next}`
      const meaning = hebrew ? HE_TO_EN.get(bigram) : EN_TO_HE.get(bigram)
      if (meaning) {
        units.push({ display: `${tokens[i]} ${tokens[i + 1]}`, meaning, en: hebrew ? meaning : bigram })
        i++
        continue
      }
    }
    const meaning = hebrew ? lookupHe(t) : EN_TO_HE.get(t)
    units.push(meaning ? { display: tokens[i], meaning, en: hebrew ? meaning : t } : { display: tokens[i] })
  }
  return units
}

export default function HintText({ text, dir = 'auto', className = '', onHint }) {
  const [openIdx, setOpenIdx] = useState(null)
  const units = tokenize(text)

  // הבועה נסגרת לבד אחרי כמה שניות
  useEffect(() => {
    if (openIdx === null) return
    const t = setTimeout(() => setOpenIdx(null), 3000)
    return () => clearTimeout(t)
  }, [openIdx])

  function tap(i, unit) {
    if (openIdx === i) {
      setOpenIdx(null)
      return
    }
    setOpenIdx(i)
    speak(unit.en)
    onHint?.(unit.en)
  }

  return (
    <span dir={dir} className={className}>
      {units.map((u, i) => (
        <span key={i}>
          {u.meaning ? (
            <span className="relative inline-block">
              <button
                type="button"
                onClick={() => tap(i, u)}
                className="cursor-pointer border-b-2 border-dashed border-duo-blue/60 hover:text-duo-blue"
              >
                {u.display}
              </button>
              {openIdx === i && (
                <span className="absolute top-full left-1/2 z-30 mt-1.5 -translate-x-1/2 animate-pop-in rounded-xl border-2 border-duo-gray bg-white px-3 py-1.5 text-base font-bold whitespace-nowrap shadow-lg">
                  <bdi dir="auto">{u.meaning}</bdi>
                </span>
              )}
            </span>
          ) : (
            <span>{u.display}</span>
          )}
          {i < units.length - 1 && ' '}
        </span>
      ))}
    </span>
  )
}
