// מחולל תרגילים: בונה רשימת תרגילים מעורבת מתוכן שיעור.

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

// בונה ~10 תרגילים משיעור { words, sentences }
export function generateExercises({ words, sentences }) {
  const exercises = []
  const pool = words
  const shuffledWords = shuffle(words)

  // בחירה מרובה לכל מילה (לסירוגין בשני הכיוונים)
  shuffledWords.slice(0, 4).forEach((w, i) => {
    exercises.push(makeMultipleChoice(w, pool, i % 2 === 0 ? 'en2he' : 'he2en'))
  })

  // האזנה
  shuffle(words).slice(0, 2).forEach((w) => exercises.push(makeListening(w, pool)))

  // הקלדת תרגום
  shuffle(words).slice(0, 2).forEach((w) => exercises.push(makeTypeTranslation(w)))

  // בניית משפטים
  shuffle(sentences).slice(0, 2).forEach((s) => exercises.push(makeSentenceBuild(s, pool)))

  // התאמת זוגות (אם יש מספיק מילים)
  if (words.length >= 5) exercises.push(makeMatchPairs(shuffle(words)))

  return shuffle(exercises)
}

// תרגול טעויות: תרגילים קלים ממוקדים במילים שטעו בהן
export function generatePracticeExercises(mistakes) {
  const exercises = []
  const pool = mistakes.length >= 4 ? mistakes : ALL_WORDS
  for (const w of shuffle(mistakes).slice(0, 6)) {
    exercises.push(makeMultipleChoice(w, pool, Math.random() > 0.5 ? 'en2he' : 'he2en'))
    exercises.push(makeTypeTranslation(w))
  }
  if (mistakes.length >= 5) exercises.push(makeMatchPairs(shuffle(mistakes)))
  return shuffle(exercises).slice(0, 10)
}

// המילים שנבדקות בתרגיל — לרישום טעויות
export function exerciseWords(exercise) {
  switch (exercise.type) {
    case 'multipleChoice':
    case 'listening':
      return [exercise.word]
    case 'typeTranslation':
      return [exercise.item]
    case 'sentenceBuild':
      return []
    case 'matchPairs':
      return exercise.pairs
    default:
      return []
  }
}
