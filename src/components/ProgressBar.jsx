// פס התקדמות ירוק עם מילוי חלק וברק שחולף בכל התקדמות.

export default function ProgressBar({ value, max, color = '#58cc02', className = '' }) {
  const pct = max > 0 ? Math.min(100, Math.round((value / max) * 100)) : 0
  return (
    <div className={`relative h-4 w-full overflow-hidden rounded-full bg-duo-gray ${className}`}>
      <div
        className="h-full rounded-full transition-all duration-500 ease-out"
        style={{ width: `${pct}%`, minWidth: value > 0 ? '1rem' : 0, backgroundColor: color }}
      >
        {pct > 8 && <div className="mx-auto mt-1 h-1 w-3/4 rounded-full bg-white/30" />}
      </div>
      {/* ברק שרץ על הפס מחדש בכל שינוי ערך */}
      {value > 0 && <div key={value} className="bar-shine" />}
    </div>
  )
}
