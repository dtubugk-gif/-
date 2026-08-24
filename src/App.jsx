import { useState, useEffect } from 'react'
import { Routes, Route } from 'react-router-dom'
import Layout from './components/Layout'
import HomePage from './pages/HomePage'
import LessonPage from './pages/LessonPage'
import ProfilePage from './pages/ProfilePage'
import OnboardingPage from './pages/OnboardingPage'
import NotFoundPage from './pages/NotFoundPage'
import { ProgressContext, useProgressProvider } from './hooks/useProgress'
import DeviceSelectPage from './pages/DeviceSelectPage'
import { getDevice } from './lib/device'
import { initSpeech } from './lib/speech'

export default function App() {
  const progress = useProgressProvider()
  const [device, setDeviceState] = useState(() => getDevice())

  // חימום מנוע הקול בטעינה — מונע מצב שההשמעה הראשונה נבלעת
  useEffect(() => {
    initSpeech()
  }, [])

  // כניסה ראשונה אי-פעם: שואלים פעם אחת אם טלפון או מחשב
  if (!device) {
    return <DeviceSelectPage onSelect={setDeviceState} />
  }

  return (
    <ProgressContext.Provider value={progress}>
      <Routes>
        <Route element={<Layout />}>
          <Route path="/" element={<HomePage />} />
          <Route path="/profile" element={<ProfilePage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
        {/* מסכים מלאים, בלי ניווט תחתון */}
        <Route path="/lesson/:levelId/:lessonId" element={<LessonPage />} />
        <Route path="/onboarding" element={<OnboardingPage />} />
      </Routes>
    </ProgressContext.Provider>
  )
}
