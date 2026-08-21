// תרגיל הקלדה: כותבים את התרגום באנגלית.

export default function TypeTranslation({ exercise, answer, setAnswer, checked }) {
  const { item } = exercise
  return (
    <div>
      <h2 className="mb-6 text-2xl font-extrabold">כתוב באנגלית ✍️</h2>

      <div className="mb-8 flex items-center justify-center gap-3 rounded-2xl border-2 border-duo-gray px-6 py-5 text-3xl font-extrabold">
        {item.emoji && <span>{item.emoji}</span>}
        <span>{item.he}</span>
      </div>

      <input
        type="text"
        dir="ltr"
        lang="en"
        autoComplete="off"
        autoCapitalize="off"
        spellCheck="false"
        disabled={checked}
        value={answer || ''}
        onChange={(e) => setAnswer(e.target.value)}
        placeholder="Type in English..."
        className={`w-full rounded-2xl border-2 bg-gray-50 p-4 text-xl font-bold outline-none transition-colors focus:border-duo-blue ${
          checked ? 'border-duo-gray text-duo-muted' : 'border-duo-gray'
        }`}
      />
    </div>
  )
}
