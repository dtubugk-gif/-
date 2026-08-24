// מסך בחירת מכשיר — מופיע רק בכניסה הראשונה אי-פעם, והבחירה נשמרת לתמיד.

import { setDevice } from '../lib/device'

export default function DeviceSelectPage({ onSelect }) {
  function choose(d) {
    setDevice(d)
    onSelect(d)
  }

  return (
    <div className="onboarding-bg flex min-h-screen flex-col items-center justify-center gap-8 px-6 text-center">
      <div className="animate-bounce-slow text-8xl">🦉</div>
      <div>
        <h1 className="mb-2 text-3xl font-extrabold text-duo-green-darker">רגע לפני שמתחילים...</h1>
        <p className="text-lg font-bold text-duo-muted">מאיפה נכנסת? נתאים לך את המסך בדיוק</p>
      </div>

      <div className="flex w-full max-w-md flex-col gap-4 sm:flex-row">
        <button
          type="button"
          onClick={() => choose('phone')}
          className="btn-3d card-soft flex-1 rounded-3xl border-2 border-duo-gray bg-white p-8 transition-transform hover:scale-[1.02]"
        >
          <div className="mb-3 text-7xl">📱</div>
          <div className="text-xl font-extrabold">טלפון</div>
          <div className="text-sm font-bold text-duo-muted">מסך מגע, הכול בגובה אצבע</div>
        </button>
        <button
          type="button"
          onClick={() => choose('computer')}
          className="btn-3d card-soft flex-1 rounded-3xl border-2 border-duo-gray bg-white p-8 transition-transform hover:scale-[1.02]"
        >
          <div className="mb-3 text-7xl">💻</div>
          <div className="text-xl font-extrabold">מחשב</div>
          <div className="text-sm font-bold text-duo-muted">מסך רחב + קיצורי מקלדת</div>
        </button>
      </div>

      <p className="text-sm font-bold text-duo-muted">אפשר לשנות אחר כך בעמוד הפרופיל 🙂</p>
    </div>
  )
}
