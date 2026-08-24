// מעטפת משותפת: פס עליון + ניווט תחתון (מובייל-פירסט כמו דואלינגו).

import { NavLink, Outlet } from 'react-router-dom'
import TopBar from './TopBar'
import { isComputer } from '../lib/device'

const NAV = [
  { to: '/', icon: '🏠', label: 'למידה' },
  { to: '/profile', icon: '👤', label: 'פרופיל' },
]

export default function Layout() {
  const desktop = isComputer()
  return (
    <div className="flex min-h-screen flex-col bg-white">
      <TopBar />
      {desktop && (
        <nav className="border-b-2 border-duo-gray bg-white">
          <div className="mx-auto flex max-w-4xl items-center justify-center gap-2 px-4 py-1">
            {NAV.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                className={({ isActive }) =>
                  `rounded-xl px-5 py-2 font-extrabold transition-colors ${
                    isActive ? 'bg-sky-50 text-duo-blue' : 'text-duo-muted hover:text-duo-text'
                  }`
                }
              >
                {item.icon} {item.label}
              </NavLink>
            ))}
          </div>
        </nav>
      )}
      <main className={`mx-auto w-full flex-1 px-4 pt-4 ${desktop ? 'max-w-4xl pb-8' : 'max-w-2xl pb-24'}`}>
        <Outlet />
      </main>
      {!desktop && (
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
      )}
    </div>
  )
}
