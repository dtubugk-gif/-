// זיהוי גרסה: טלפון או מחשב. נשאל פעם אחת בכניסה הראשונה ונשמר לתמיד.

const KEY = 'linguago-device'

export function getDevice() {
  try {
    return localStorage.getItem(KEY) // 'phone' | 'computer' | null
  } catch {
    return null
  }
}

export function setDevice(d) {
  try {
    localStorage.setItem(KEY, d)
  } catch {
    // אחסון חסום — נמשיך בלי לשמור
  }
}

export function isComputer() {
  return getDevice() === 'computer'
}
