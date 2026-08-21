// בדיקת תשובה אחידה לכל סוגי התרגילים.

import { normalize } from '../../lib/exerciseGen'

export function checkAnswer(exercise, answer) {
  switch (exercise.type) {
    case 'multipleChoice': {
      const correct = answer?.en === exercise.word.en
      const correctText = exercise.direction === 'en2he' ? exercise.word.he : exercise.word.en
      return { correct, correctText }
    }
    case 'listening':
      return { correct: answer?.en === exercise.word.en, correctText: exercise.word.en }
    case 'typeTranslation':
      return { correct: normalize(answer || '') === normalize(exercise.item.en), correctText: exercise.item.en }
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
