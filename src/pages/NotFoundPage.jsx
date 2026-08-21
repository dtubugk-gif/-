import { Link } from 'react-router-dom'
import Button from '../components/Button'

export default function NotFoundPage() {
  return (
    <div className="flex flex-col items-center justify-center gap-4 py-20 text-center">
      <div className="text-7xl">🦉❓</div>
      <h1 className="text-3xl font-extrabold">404 — העמוד לא נמצא</h1>
      <p className="font-bold text-duo-muted">הינשוף חיפש בכל מקום ולא מצא את העמוד הזה...</p>
      <Link to="/"><Button>חזרה ללמידה</Button></Link>
    </div>
  )
}
