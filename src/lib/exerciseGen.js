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

// תמהיל תרגילים לפי קושי הרמה (0-6)
function mixForDifficulty(d) {
  if (d <= 1) return { mc: 4, listen: 2, type: 1, build: 1, match: 1 } // מתחילים: זיהוי בעיקר
  if (d <= 4) return { mc: 3, listen: 2, type: 2, build: 2, match: 1 } // ביניים: מאוזן
  return { mc: 1, listen: 2, type: 3, build: 3, match: 1 } // מתקדמים: שליפה אקטיבית
}

// בונה ~10 תרגילים משיעור { words, sentences, difficulty }
export function generateExercises({ words, sentences, difficulty = 0 }) {
  const mix = mixForDifficulty(difficulty)
  const exercises = []
  const pool = words

  shuffle(words).slice(0, mix.mc).forEach((w, i) => {
    exercises.push(makeMultipleChoice(w, pool, i % 2 === 0 ? 'en2he' : 'he2en'))
  })
  shuffle(words).slice(0, mix.listen).forEach((w) => exercises.push(makeListening(w, pool)))
  shuffle(words).slice(0, mix.type).forEach((w) => exercises.push(makeTypeTranslation(w)))
  shuffle(sentences).slice(0, mix.build).forEach((s) => exercises.push(makeSentenceBuild(s, pool)))
  if (mix.match && words.length >= 5) exercises.push(makeMatchPairs(shuffle(words)))

  return shuffle(exercises)
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
