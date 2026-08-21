import { Routes, Route } from 'react-router-dom'
import Layout from './components/Layout'
import HomePage from './pages/HomePage'
import LessonPage from './pages/LessonPage'
import PracticePage from './pages/PracticePage'
import ProfilePage from './pages/ProfilePage'
import NotFoundPage from './pages/NotFoundPage'
import { ProgressContext, useProgressProvider } from './hooks/useProgress'

export default function App() {
  const progress = useProgressProvider()
  return (
    <ProgressContext.Provider value={progress}>
      <Routes>
        <Route element={<Layout />}>
          <Route path="/" element={<HomePage />} />
          <Route path="/practice" element={<PracticePage />} />
          <Route path="/profile" element={<ProfilePage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
        {/* שיעור במסך מלא, בלי ניווט */}
        <Route path="/lesson/:unitId/:lessonId" element={<LessonPage />} />
      </Routes>
    </ProgressContext.Provider>
  )
}
