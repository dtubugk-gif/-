// בדיקת תשובה אחידה לכל סוגי התרגילים.

import { normalize } from '../../lib/exerciseGen'

// מרחק עריכה קטן — סלחנות לשגיאת כתיב אחת, כמו בדואלינגו
function editDistance(a, b) {
  const dp = Array.from({ length: a.length + 1 }, (_, i) => [i, ...Array(b.length).fill(0)])
  for (let j = 0; j <= b.length; j++) dp[0][j] = j
  for (let i = 1; i <= a.length; i++) {
    for (let j = 1; j <= b.length; j++) {
      dp[i][j] = Math.min(
        dp[i - 1][j] + 1,
        dp[i][j - 1] + 1,
        dp[i - 1][j - 1] + (a[i - 1] === b[j - 1] ? 0 : 1)
      )
    }
  }
  return dp[a.length][b.length]
}

export function checkAnswer(exercise, answer) {
  switch (exercise.type) {
    case 'teach':
      return { correct: true, correctText: '' }
    case 'multipleChoice': {
      const correct = answer?.en === exercise.word.en
      const correctText = exercise.direction === 'en2he' ? exercise.word.he : exercise.word.en
      return { correct, correctText }
    }
    case 'listening':
      return { correct: answer?.en === exercise.word.en, correctText: exercise.word.en }
    case 'typeTranslation': {
      const given = normalize(answer || '')
      const expected = normalize(exercise.item.en)
      if (given === expected) return { correct: true, correctText: exercise.item.en }
      // שגיאת כתיב אחת קטנה — עדיין נכון, עם הערה (רק במילים ארוכות מספיק,
      // כדי שמילה אמיתית אחרת כמו not במקום no לא תתקבל)
      if (given.length > 2 && expected.length > 3 && editDistance(given, expected) === 1) {
        return { correct: true, almost: true, correctText: exercise.item.en }
      }
      return { correct: false, correctText: exercise.item.en }
    }
    case 'sentenceBuild': {
      // answer = רשימת אינדקסים במאגר המילים
      const built = (answer || []).map((i) => exercise.bank[i]).join(' ')
      return {
        correct: normalize(built) === normalize(exercise.sentence.en),
        correctText: exercise.sentence.en,
      }
    }
    case 'matchPairs':
      // התאמת זוגות לא מורידה לב — טעויות רק נרשמות לתרגול
      return { correct: true, correctText: '' }
    default:
      return { correct: false, correctText: '' }
  }
}

export function isAnswerReady(exercise, answer) {
  switch (exercise.type) {
    case 'teach':
      return true
    case 'multipleChoice':
    case 'listening':
      return answer != null
    case 'typeTranslation':
      return typeof answer === 'string' && answer.trim().length > 0
    case 'sentenceBuild':
      return Array.isArray(answer) && answer.length > 0
    case 'matchPairs':
      return answer?.completed === true
    default:
      return false
  }
}
