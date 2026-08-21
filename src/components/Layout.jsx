// מעטפת משותפת: פס עליון + ניווט תחתון (מובייל-פירסט כמו דואלינגו).

import { NavLink, Outlet } from 'react-router-dom'
import TopBar from './TopBar'

const NAV = [
  { to: '/', icon: '🏠', label: 'למידה' },
  { to: '/profile', icon: '👤', label: 'פרופיל' },
]

export default function Layout() {
  return (
    <div className="flex min-h-screen flex-col bg-white">
      <TopBar />
      <main className="mx-auto w-full max-w-2xl flex-1 px-4 pb-24 pt-4">
        <Outlet />
      </main>
      <nav className="fixed bottom-0 right-0 left-0 z-20 border-t-2 border-duo-gray bg-white">
        <div className="mx-auto flex max-w-2xl items-stretch justify-around">
          {NAV.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                `relative flex flex-1 flex-col items-center gap-0.5 py-2 text-xs font-extrabold transition-colors ${
                  isActive ? 'text-duo-blue' : 'text-duo-muted hover:text-duo-text'
                }`
              }
            >
              <span className="text-2xl">{item.icon}</span>
              <span>{item.label}</span>
            </NavLink>
          ))}
        </div>
      </nav>
    </div>
  )
}
