// מחולל תרגילים: בונה רשימת תרגילים מעורבת משיעור, עם תמהיל שמשתנה לפי רמה —
// רמות נמוכות מקבלות יותר בחירה מרובה, רמות גבוהות יותר הקלדה ובניית משפטים.

import { ALL_WORDS } from '../data/course'

export function shuffle(arr) {
  const a = [...arr]
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    ;[a[i], a[j]] = [a[j], a[i]]
  }
  return a
}

export function normalize(text) {
  return text
    .toLowerCase()
    .replace(/[.,!?'"־-]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
}

function pickDistractors(word, pool, count) {
  const others = pool.filter((w) => w.en !== word.en && w.he !== word.he)
  const local = shuffle(others).slice(0, count)
  if (local.length < count) {
    const globals = shuffle(ALL_WORDS.filter((w) => w.en !== word.en && w.he !== word.he && !local.some((l) => l.en === w.en)))
    local.push(...globals.slice(0, count - local.length))
  }
  return local
}

function makeMultipleChoice(word, pool, direction) {
  return {
    type: 'multipleChoice',
    direction, // 'en2he' | 'he2en'
    word,
    options: shuffle([word, ...pickDistractors(word, pool, 3)]),
  }
}

function makeListening(word, pool) {
  return {
    type: 'listening',
    word,
    options: shuffle([word, ...pickDistractors(word, pool, 3)]),
  }
}

function makeTypeTranslation(word) {
  return { type: 'typeTranslation', item: word }
}

function makeSentenceBuild(sentence, pool) {
  const correctWords = sentence.en.split(' ')
  const distractorWords = shuffle(
    pool
      .flatMap((w) => w.en.split(' '))
      .filter((w) => !correctWords.map(normalize).includes(normalize(w)))
  ).slice(0, Math.min(3, Math.max(2, 8 - correctWords.length)))
  return {
    type: 'sentenceBuild',
    sentence,
    bank: shuffle([...correctWords, ...distractorWords]),
  }
}

function makeMatchPairs(words) {
  return { type: 'matchPairs', pairs: words.slice(0, 5) }
}

// בחירת תמונה: הסחות עם אימוג'י שונה זו מזו
function makePickImage(word, pool) {
  const distractors = []
  const seen = new Set([word.emoji])
  for (const cand of [...shuffle(pool), ...shuffle(ALL_WORDS)]) {
    if (cand.en !== word.en && cand.emoji && !seen.has(cand.emoji)) {
      distractors.push(cand)
      seen.add(cand.emoji)
    }
    if (distractors.length === 3) break
  }
  return { type: 'pickImage', word, options: shuffle([word, ...distractors]) }
}

// השלמת משפט: מסתירים מילת תוכן אחת ונותנים 3 אפשרויות
function makeFillBlank(sentence, pool) {
  const tokens = sentence.en.split(' ')
  const candidates = tokens.map((t, i) => ({ t, i })).filter(({ t }) => t.length > 2)
  const pick = candidates.length ? candidates[Math.floor(Math.random() * candidates.length)] : { t: tokens[0], i: 0 }
  const distractors = []
  const seen = new Set([normalize(pick.t)])
  for (const t of [...shuffle(pool.flatMap((w) => w.en.split(' '))), ...shuffle(ALL_WORDS.flatMap((w) => w.en.split(' ')))]) {
    const n = normalize(t)
    if (t.length > 2 && !seen.has(n)) {
      distractors.push(t)
      seen.add(n)
    }
    if (distractors.length === 2) break
  }
  return { type: 'fillBlank', sentence, missing: pick.t, blankIndex: pick.i, options: shuffle([pick.t, ...distractors]) }
}

function makeTeachCard(word) {
  return { type: 'teach', word }
}

// תרגילי המשך לפי קושי הרמה (0-6) — אחרי שלב הלימוד וההיכרות.
// בניית משפטים נכנסת רק מרמה 4 (d=3) — ברמות הראשונות לומדים מילים בודדות.
function tailForDifficulty(d) {
  if (d <= 1) return { listen: 2, type: 0, build: 0, fill: 0, match: 1 } // רמות 1-2: זיהוי והאזנה בלבד
  if (d === 2) return { listen: 2, type: 1, build: 0, fill: 1, match: 1 } // רמה 3: הקלדה והשלמת משפט
  if (d <= 4) return { listen: 1, type: 1, build: 1, fill: 1, match: 1 } // רמות 4-5: משפט ראשון
  return { listen: 1, type: 2, build: 2, fill: 1, match: 1 } // רמות 6-7: שליפה אקטיבית מלאה
}

// בונה שיעור בסגנון דואלינגו: קודם מלמדים כל מילה (כרטיסיית "מילה חדשה"),
// מיד אחר כך בוחנים עליה בקלות, ובסוף מחזקים עם תרגילים מגוונים.
// { words, sentences, difficulty, teach } — teach=false בשיעור חוזר (המילים כבר מוכרות).
export function generateExercises({ words, sentences, difficulty = 0, teach = true }) {
  const pool = words
  const focus = shuffle(words).slice(0, 4) // עד 4 מילים חדשות בשיעור — קצר וקליל
  const exercises = []

  // שלב 1: לימוד ותרגול ראשוני בזוגות — מלמדים שתי מילים, בוחנים עליהן, וכן הלאה.
  // ברמות הנמוכות המבחן הראשון הוא בחירת תמונה — כיפי וקל לילדים.
  for (let i = 0; i < focus.length; i += 2) {
    const pair = focus.slice(i, i + 2)
    if (teach) pair.forEach((w) => exercises.push(makeTeachCard(w)))
    pair.forEach((w, j) => {
      if (j % 2 === 0 && difficulty <= 2) exercises.push(makePickImage(w, pool))
      else exercises.push(makeMultipleChoice(w, pool, j % 2 === 0 ? 'en2he' : 'he2en'))
    })
  }

  // שלב 2: חיזוק — תרגילים מגוונים לפי הרמה.
  // בשיעור ראשון בוחנים רק מילים שנלמדו; בשיעור חוזר — מכל מילות השיעור.
  const tested = teach ? focus : words
  const tail = tailForDifficulty(difficulty)
  const rest = []
  shuffle(tested).slice(0, tail.listen).forEach((w) => rest.push(makeListening(w, pool)))
  shuffle(tested).slice(0, tail.type).forEach((w) => rest.push(makeTypeTranslation(w)))
  shuffle(sentences).slice(0, tail.build).forEach((s) => rest.push(makeSentenceBuild(s, pool)))
  shuffle(sentences).slice(0, tail.fill).forEach((s) => rest.push(makeFillBlank(s, pool)))
  if (tail.match && tested.length >= 4) rest.push(makeMatchPairs(shuffle(tested)))

  const ordered = [...exercises, ...shuffle(rest)]

  // לפני הרכבת המשפט הראשון בשיעור לימוד — מלמדים את to be
  if (teach) {
    const firstBuild = ordered.findIndex((e) => e.type === 'sentenceBuild')
    if (firstBuild >= 0) ordered.splice(firstBuild, 0, { type: 'grammar' })
  }

  return ordered
}

// שאלת מבחן רמה: בחירה מרובה ממילות רמה מסוימת (הקושי עולה עם האינדקס)
export function makePlacementQuestion(level) {
  const word = shuffle(level.words)[0]
  const direction = Math.random() > 0.5 ? 'en2he' : 'he2en'
  return {
    type: 'multipleChoice',
    direction,
    word,
    options: shuffle([word, ...pickDistractors(word, level.words, 3)]),
  }
}

// המילים שנבדקות בתרגיל — לרישום טעויות
export function exerciseWords(exercise) {
  switch (exercise.type) {
    case 'teach':
    case 'grammar':
      return []
    case 'multipleChoice':
    case 'listening':
    case 'pickImage':
      return [exercise.word]
    case 'typeTranslation':
      return [exercise.item]
    case 'fillBlank': {
      const w = ALL_WORDS.find((x) => x.en === exercise.missing)
      return w ? [w] : []
    }
    case 'sentenceBuild':
      return []
    case 'matchPairs':
      return exercise.pairs
    default:
      return []
  }
}
