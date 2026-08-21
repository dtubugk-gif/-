// השמעת אנגלית: TTS נייטיבי באפליקציית אנדרואיד (WebView לא תומך ב-Web Speech API),
// ו-Web Speech API בדפדפן. צלילי משוב עם WebAudio.

import { Capacitor } from '@capacitor/core'
import { TextToSpeech } from '@capacitor-community/text-to-speech'

const isNative = Capacitor.isNativePlatform()

export function speak(text, rate = 0.9) {
  if (isNative) {
    TextToSpeech.stop().catch(() => {})
    TextToSpeech.speak({ text, lang: 'en-US', rate, category: 'playback' }).catch(() => {})
    return
  }
  try {
    if (!('speechSynthesis' in window)) return
    window.speechSynthesis.cancel()
    const u = new SpeechSynthesisUtterance(text)
    u.lang = 'en-US'
    u.rate = rate
    const voices = window.speechSynthesis.getVoices()
    const enVoice = voices.find((v) => v.lang.startsWith('en'))
    if (enVoice) u.voice = enVoice
    window.speechSynthesis.speak(u)
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
  try {
    tone(523.25, 0, 0.12)
    tone(659.25, 0.1, 0.12)
    tone(783.99, 0.2, 0.25)
  } catch { /* בלי קול */ }
}

export function playWrong() {
  try {
    tone(220, 0, 0.2, 'square', 0.12)
    tone(174, 0.18, 0.3, 'square', 0.12)
  } catch { /* בלי קול */ }
}

export function playFanfare() {
  try {
    tone(523.25, 0, 0.15)
    tone(659.25, 0.12, 0.15)
    tone(783.99, 0.24, 0.15)
    tone(1046.5, 0.36, 0.4)
  } catch { /* בלי קול */ }
}
