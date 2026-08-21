// עיגול שיעור על "שביל" הלמידה — פתוח, נעול או מוכתר.

import { Link } from 'react-router-dom'

export default function LessonNode({ level, lessonIndex, unlocked, crowns, title, offset }) {
  const done = crowns > 0
  const inner = (
    <div className="flex flex-col items-center gap-1">
      <div className="relative">
        <div
          className={`btn-3d flex h-16 w-16 items-center justify-center rounded-full border-4 text-3xl shadow-md sm:h-20 sm:w-20 ${
            unlocked ? '' : 'bg-duo-gray border-gray-300 grayscale'
          }`}
          style={unlocked ? { backgroundColor: level.color, borderColor: 'rgba(0,0,0,0.25)' } : {}}
        >
          {unlocked ? (done ? '⭐' : level.icon) : '🔒'}
        </div>
        {done && (
          <div className="absolute -bottom-1 left-1/2 -translate-x-1/2 rounded-full bg-white px-1.5 py-0.5 text-[11px] font-extrabold text-duo-yellow-dark shadow">
            {'👑'.repeat(crowns)}
          </div>
        )}
      </div>
      <span className={`text-sm font-bold ${unlocked ? 'text-duo-text' : 'text-duo-muted'}`}>{title}</span>
    </div>
  )

  return (
    <div style={{ transform: `translateX(${offset}px)` }}>
      {unlocked ? (
        <Link to={`/lesson/${level.id}/${lessonIndex}`} className="block transition-transform hover:scale-105">
          {inner}
        </Link>
      ) : (
        <div className="cursor-not-allowed">{inner}</div>
      )}
    </div>
  )
}
