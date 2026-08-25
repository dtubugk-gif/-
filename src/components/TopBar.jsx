// פס עליון: רצף ו-XP — מוצג בכל עמודי האפליקציה.

import { useProgress } from '../hooks/useProgress'

export default function TopBar() {
  const { state } = useProgress()
  return (
    <div className="sticky top-0 z-20 border-b-2 border-duo-gray bg-white/95 backdrop-blur card-soft">
      <div className="mx-auto flex max-w-2xl items-center justify-between px-4 py-3">
        <div className="flex items-center gap-1.5 text-lg font-extrabold">
          <span className="text-2xl">🦉</span>
          <span className="text-duo-green">LinguaGo</span>
        </div>
        <div className="flex items-center gap-4 text-base font-extrabold sm:gap-6">
          <div className="flex items-center gap-1" title="רצף ימים">
            <span className={state.streak > 0 ? 'animate-flame' : 'grayscale opacity-50'}>🔥</span>
            <span className={state.streak > 0 ? 'text-duo-orange-text' : 'text-duo-muted'}>{state.streak}</span>
          </div>
          <div className="flex items-center gap-1" title="נקודות ניסיון">
            <span>⚡</span>
            <span className="text-duo-yellow-text">{state.xp}</span>
          </div>
        </div>
      </div>
    </div>
  )
}
