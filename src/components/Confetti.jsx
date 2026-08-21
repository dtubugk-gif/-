// קונפטי חגיגי למסך סיום שיעור.

import { useMemo } from 'react'

const COLORS = ['#58cc02', '#1cb0f6', '#ffc800', '#ff4b4b', '#ce82ff', '#ff9600']

export default function Confetti({ count = 40 }) {
  const pieces = useMemo(
    () =>
      Array.from({ length: count }, (_, i) => ({
        id: i,
        left: Math.random() * 100,
        delay: Math.random() * 1.2,
        color: COLORS[i % COLORS.length],
        scale: 0.7 + Math.random() * 0.8,
      })),
    [count]
  )
  return (
    <>
      {pieces.map((p) => (
        <div
          key={p.id}
          className="confetti-piece"
          style={{
            left: `${p.left}%`,
            animationDelay: `${p.delay}s`,
            backgroundColor: p.color,
            transform: `scale(${p.scale})`,
          }}
        />
      ))}
    </>
  )
}
