// השמעת אנגלית: TTS נייטיבי באפליקציית אנדרואיד (WebView לא תומך ב-Web Speech API),
// ו-Web Speech API בדפדפן. צלילי משוב עם WebAudio.

import { Capacitor } from '@capacitor/core'
import { TextToSpeech } from '@capacitor-community/text-to-speech'
import { hapticCorrect, hapticWrong, hapticSuccess } from './haptics'

const isNative = Capacitor.isNativePlatform()

// חימום מנוע הקול בהפעלת האפליקציה — מונע השמעה ראשונה שנבלעת
let cachedVoices = []
export function initSpeech() {
  if (isNative) {
    // פנייה ראשונה למנוע ה-TTS של אנדרואיד מעירה אותו מוקדם
    TextToSpeech.getSupportedLanguages().catch(() => {})
    return
  }
  try {
    if (!('speechSynthesis' in window)) return
    const load = () => {
      cachedVoices = window.speechSynthesis.getVoices()
    }
    load()
    // בחלק מהדפדפנים רשימת הקולות נטענת מאוחר — נאזין לעדכון
    window.speechSynthesis.onvoiceschanged = load
  } catch {
    // בלי קול
  }
}

// מונה רץ שמונע מרוץ בין stop ל-speak כשמקישים מהר על כמה מילים
let speakSeq = 0

export function speak(text, rate = 0.9) {
  if (isNative) {
    const seq = ++speakSeq
    // מחכים שה-stop יסתיים לפני ההשמעה — אחרת חלק מהמנועים בולעים את המשפט
    TextToSpeech.stop()
      .catch(() => {})
      .then(() => {
        if (seq !== speakSeq) return // הקשה חדשה עקפה אותנו
        TextToSpeech.speak({ text, lang: 'en-US', rate, category: 'playback' }).catch(() => {
          // ניסיון שני — מנוע שעוד לא התעורר
          TextToSpeech.speak({ text, lang: 'en-US', rate, category: 'playback' }).catch(() => {})
        })
      })
    return
  }
  try {
    if (!('speechSynthesis' in window)) return
    const synth = window.speechSynthesis
    synth.cancel()
    // באג ידוע בכרום: אחרי השהיה ארוכה המנוע "נתקע" במצב מושהה
    synth.resume()
    const u = new SpeechSynthesisUtterance(text)
    u.lang = 'en-US'
    u.rate = rate
    const voices = synth.getVoices().length ? synth.getVoices() : cachedVoices
    const enVoice = voices.find((v) => v.lang && v.lang.startsWith('en'))
    if (enVoice) u.voice = enVoice
    synth.speak(u)
  } catch {
    // דפדפן בלי תמיכה — פשוט בלי קול
  }
}

let audioCtx = null
function ctx() {
  if (!audioCtx) {
    const AC = window.AudioContext || window.webkitAudioContext
    if (!AC) return null
    audioCtx = new AC()
  }
  // דפדפנים משעים את ה-AudioContext עד אינטראקציית משתמש — מעירים אותו
  if (audioCtx.state === 'suspended') audioCtx.resume().catch(() => {})
  return audioCtx
}

function tone(freq, start, duration, type = 'sine', volume = 0.2) {
  const c = ctx()
  if (!c) return
  const osc = c.createOscillator()
  const gain = c.createGain()
  osc.type = type
  osc.frequency.value = freq
  gain.gain.setValueAtTime(volume, c.currentTime + start)
  gain.gain.exponentialRampToValueAtTime(0.001, c.currentTime + start + duration)
  osc.connect(gain)
  gain.connect(c.destination)
  osc.start(c.currentTime + start)
  osc.stop(c.currentTime + start + duration)
}

export function playCorrect() {
  hapticCorrect()
  try {
    tone(523.25, 0, 0.12)
    tone(659.25, 0.1, 0.12)
    tone(783.99, 0.2, 0.25)
  } catch { /* בלי קול */ }
}

export function playWrong() {
  hapticWrong()
  try {
    tone(220, 0, 0.2, 'square', 0.12)
    tone(174, 0.18, 0.3, 'square', 0.12)
  } catch { /* בלי קול */ }
}

export function playFanfare() {
  hapticSuccess()
  try {
    tone(523.25, 0, 0.15)
    tone(659.25, 0.12, 0.15)
    tone(783.99, 0.24, 0.15)
    tone(1046.5, 0.36, 0.4)
  } catch { /* בלי קול */ }
}

// צליל קומבו: ארפג'ו שעולה עם אורך הרצף
export function playCombo(n) {
  hapticCorrect()
  try {
    const base = 523.25 * Math.pow(1.06, Math.min(n, 8))
    tone(base, 0, 0.1)
    tone(base * 1.25, 0.08, 0.1)
    tone(base * 1.5, 0.16, 0.2)
  } catch { /* בלי קול */ }
}
