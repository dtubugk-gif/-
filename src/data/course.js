// תוכן הקורס: 7 רמות בסדר קושי עולה. כל רמה עם אוצר מילים ומשפטים משלה.
// word: { en, he, emoji? }  sentence: { en, he }

export const LEVELS = [
  {
    id: 'l1',
    title: 'יסודות והיכרות',
    subtitle: 'ברכות, נימוסים ומשפטים ראשונים',
    color: '#58cc02',
    icon: '👋',
    words: [
      { en: 'hello', he: 'שלום', emoji: '👋' },
      { en: 'goodbye', he: 'להתראות', emoji: '🙋' },
      { en: 'yes', he: 'כן', emoji: '✅' },
      { en: 'no', he: 'לא', emoji: '❌' },
      { en: 'please', he: 'בבקשה', emoji: '🙏' },
      { en: 'thank you', he: 'תודה', emoji: '💚' },
      { en: 'sorry', he: 'סליחה', emoji: '😔' },
      { en: 'good morning', he: 'בוקר טוב', emoji: '🌅' },
      { en: 'good night', he: 'לילה טוב', emoji: '🌙' },
      { en: 'name', he: 'שם', emoji: '🏷️' },
      { en: 'I', he: 'אני', emoji: '🙂' },
      { en: 'you', he: 'אתה', emoji: '👉' },
      { en: 'friend', he: 'חבר', emoji: '🤝' },
      { en: 'welcome', he: 'ברוך הבא', emoji: '🎉' },
    ],
    sentences: [
      { en: 'hello my name is Dana', he: 'שלום, קוראים לי דנה' },
      { en: 'good morning my friend', he: 'בוקר טוב חבר שלי' },
      { en: 'thank you very much', he: 'תודה רבה' },
      { en: 'yes please', he: 'כן בבקשה' },
      { en: 'no thank you', he: 'לא תודה' },
      { en: 'goodbye see you tomorrow', he: 'להתראות, נתראה מחר' },
      { en: 'I am happy to meet you', he: 'אני שמח לפגוש אותך' },
      { en: 'what is your name', he: 'מה השם שלך' },
    ],
  },
  {
    id: 'l2',
    title: 'משפחה ואנשים',
    subtitle: 'מדברים על האנשים שסביבנו',
    color: '#1cb0f6',
    icon: '👨‍👩‍👧',
    words: [
      { en: 'family', he: 'משפחה', emoji: '👨‍👩‍👧‍👦' },
      { en: 'mother', he: 'אמא', emoji: '👩' },
      { en: 'father', he: 'אבא', emoji: '👨' },
      { en: 'sister', he: 'אחות', emoji: '👧' },
      { en: 'brother', he: 'אח', emoji: '👦' },
      { en: 'child', he: 'ילד', emoji: '🧒' },
      { en: 'baby', he: 'תינוק', emoji: '👶' },
      { en: 'grandmother', he: 'סבתא', emoji: '👵' },
      { en: 'grandfather', he: 'סבא', emoji: '👴' },
      { en: 'man', he: 'איש', emoji: '🧔' },
      { en: 'woman', he: 'אישה', emoji: '👩‍🦰' },
      { en: 'boy', he: 'ילד (בן)', emoji: '👦' },
      { en: 'girl', he: 'ילדה', emoji: '👧' },
      { en: 'people', he: 'אנשים', emoji: '🧑‍🤝‍🧑' },
    ],
    sentences: [
      { en: 'my mother is nice', he: 'אמא שלי נחמדה' },
      { en: 'I have a big family', he: 'יש לי משפחה גדולה' },
      { en: 'my brother is tall', he: 'אח שלי גבוה' },
      { en: 'the baby is sleeping', he: 'התינוק ישן' },
      { en: 'my grandmother makes cake', he: 'סבתא שלי מכינה עוגה' },
      { en: 'the girl is my sister', he: 'הילדה היא אחותי' },
      { en: 'my father works here', he: 'אבא שלי עובד כאן' },
      { en: 'I love my family', he: 'אני אוהב את המשפחה שלי' },
    ],
  },
  {
    id: 'l3',
    title: 'אוכל ושתייה',
    subtitle: 'מזמינים, טועמים ומבשלים',
    color: '#ff9600',
    icon: '🍎',
    words: [
      { en: 'water', he: 'מים', emoji: '💧' },
      { en: 'bread', he: 'לחם', emoji: '🍞' },
      { en: 'milk', he: 'חלב', emoji: '🥛' },
      { en: 'apple', he: 'תפוח', emoji: '🍎' },
      { en: 'egg', he: 'ביצה', emoji: '🥚' },
      { en: 'cheese', he: 'גבינה', emoji: '🧀' },
      { en: 'coffee', he: 'קפה', emoji: '☕' },
      { en: 'tea', he: 'תה', emoji: '🍵' },
      { en: 'rice', he: 'אורז', emoji: '🍚' },
      { en: 'chicken', he: 'עוף', emoji: '🍗' },
      { en: 'salad', he: 'סלט', emoji: '🥗' },
      { en: 'cake', he: 'עוגה', emoji: '🍰' },
      { en: 'orange', he: 'תפוז', emoji: '🍊' },
      { en: 'to eat', he: 'לאכול', emoji: '🍽️' },
      { en: 'to drink', he: 'לשתות', emoji: '🥤' },
    ],
    sentences: [
      { en: 'I drink water', he: 'אני שותה מים' },
      { en: 'the bread is fresh', he: 'הלחם טרי' },
      { en: 'I eat an apple', he: 'אני אוכל תפוח' },
      { en: 'she drinks coffee with milk', he: 'היא שותה קפה עם חלב' },
      { en: 'we eat chicken and rice', he: 'אנחנו אוכלים עוף ואורז' },
      { en: 'the cake is sweet', he: 'העוגה מתוקה' },
      { en: 'do you want tea', he: 'אתה רוצה תה' },
      { en: 'I like salad', he: 'אני אוהב סלט' },
    ],
  },
  {
    id: 'l4',
    title: 'צבעים, מספרים וחיות',
    subtitle: 'מתארים את העולם שסביבנו',
    color: '#ce82ff',
    icon: '🎨',
    words: [
      { en: 'red', he: 'אדום', emoji: '🔴' },
      { en: 'blue', he: 'כחול', emoji: '🔵' },
      { en: 'green', he: 'ירוק', emoji: '🟢' },
      { en: 'yellow', he: 'צהוב', emoji: '🟡' },
      { en: 'black', he: 'שחור', emoji: '⚫' },
      { en: 'white', he: 'לבן', emoji: '⚪' },
      { en: 'one', he: 'אחת', emoji: '1️⃣' },
      { en: 'two', he: 'שתיים', emoji: '2️⃣' },
      { en: 'three', he: 'שלוש', emoji: '3️⃣' },
      { en: 'five', he: 'חמש', emoji: '5️⃣' },
      { en: 'dog', he: 'כלב', emoji: '🐶' },
      { en: 'cat', he: 'חתול', emoji: '🐱' },
      { en: 'bird', he: 'ציפור', emoji: '🐦' },
      { en: 'fish', he: 'דג', emoji: '🐟' },
      { en: 'lion', he: 'אריה', emoji: '🦁' },
      { en: 'horse', he: 'סוס', emoji: '🐴' },
    ],
    sentences: [
      { en: 'the sky is blue', he: 'השמיים כחולים' },
      { en: 'I have two apples', he: 'יש לי שני תפוחים' },
      { en: 'the small cat is black', he: 'החתול הקטן שחור' },
      { en: 'three red flowers', he: 'שלושה פרחים אדומים' },
      { en: 'the dog is my friend', he: 'הכלב הוא החבר שלי' },
      { en: 'the bird sings in the morning', he: 'הציפור שרה בבוקר' },
      { en: 'the lion is a big animal', he: 'האריה הוא חיה גדולה' },
      { en: 'the fish swims in the water', he: 'הדג שוחה במים' },
    ],
  },
  {
    id: 'l5',
    title: 'בית, עיר ותחבורה',
    subtitle: 'מתמצאים במרחב ומתארים מקומות',
    color: '#1899d6',
    icon: '🏠',
    words: [
      { en: 'house', he: 'בית', emoji: '🏠' },
      { en: 'room', he: 'חדר', emoji: '🚪' },
      { en: 'kitchen', he: 'מטבח', emoji: '🍳' },
      { en: 'table', he: 'שולחן', emoji: '🪑' },
      { en: 'bed', he: 'מיטה', emoji: '🛏️' },
      { en: 'street', he: 'רחוב', emoji: '🛣️' },
      { en: 'city', he: 'עיר', emoji: '🏙️' },
      { en: 'school', he: 'בית ספר', emoji: '🏫' },
      { en: 'store', he: 'חנות', emoji: '🏪' },
      { en: 'park', he: 'פארק', emoji: '🌳' },
      { en: 'car', he: 'מכונית', emoji: '🚗' },
      { en: 'bus', he: 'אוטובוס', emoji: '🚌' },
      { en: 'door', he: 'דלת', emoji: '🚪' },
      { en: 'window', he: 'חלון', emoji: '🪟' },
    ],
    sentences: [
      { en: 'my house is big', he: 'הבית שלי גדול' },
      { en: 'the kitchen is clean', he: 'המטבח נקי' },
      { en: 'I go to school by bus', he: 'אני נוסע לבית הספר באוטובוס' },
      { en: 'the store is on this street', he: 'החנות ברחוב הזה' },
      { en: 'we walk in the park', he: 'אנחנו הולכים בפארק' },
      { en: 'the car is near the house', he: 'המכונית ליד הבית' },
      { en: 'please open the window', he: 'בבקשה תפתח את החלון' },
      { en: 'the city is beautiful at night', he: 'העיר יפה בלילה' },
    ],
  },
  {
    id: 'l6',
    title: 'פעלים ושגרה',
    subtitle: 'מספרים מה אנחנו עושים ביומיום',
    color: '#00cd9c',
    icon: '🏃',
    words: [
      { en: 'to go', he: 'ללכת', emoji: '🚶' },
      { en: 'to run', he: 'לרוץ', emoji: '🏃' },
      { en: 'to see', he: 'לראות', emoji: '👀' },
      { en: 'to read', he: 'לקרוא', emoji: '📖' },
      { en: 'to write', he: 'לכתוב', emoji: '✍️' },
      { en: 'to speak', he: 'לדבר', emoji: '🗣️' },
      { en: 'to learn', he: 'ללמוד', emoji: '🎓' },
      { en: 'to work', he: 'לעבוד', emoji: '💼' },
      { en: 'to play', he: 'לשחק', emoji: '⚽' },
      { en: 'to sleep', he: 'לישון', emoji: '😴' },
      { en: 'to love', he: 'לאהוב', emoji: '❤️' },
      { en: 'to want', he: 'לרצות', emoji: '🌟' },
      { en: 'to listen', he: 'להקשיב', emoji: '👂' },
      { en: 'to sing', he: 'לשיר', emoji: '🎤' },
    ],
    sentences: [
      { en: 'I learn English every day', he: 'אני לומד אנגלית כל יום' },
      { en: 'she reads a good book', he: 'היא קוראת ספר טוב' },
      { en: 'we play in the park', he: 'אנחנו משחקים בפארק' },
      { en: 'he works in the city', he: 'הוא עובד בעיר' },
      { en: 'they speak English', he: 'הם מדברים אנגלית' },
      { en: 'I want to sleep', he: 'אני רוצה לישון' },
      { en: 'the children run fast', he: 'הילדים רצים מהר' },
      { en: 'I love to sing', he: 'אני אוהב לשיר' },
    ],
  },
  {
    id: 'l7',
    title: 'שיחה מתקדמת',
    subtitle: 'מילים מופשטות, דעות ורעיונות',
    color: '#ff4b4b',
    icon: '🧠',
    words: [
      { en: 'to think', he: 'לחשוב', emoji: '🤔' },
      { en: 'to understand', he: 'להבין', emoji: '💡' },
      { en: 'to remember', he: 'לזכור', emoji: '🧠' },
      { en: 'to forget', he: 'לשכוח', emoji: '🫥' },
      { en: 'to know', he: 'לדעת', emoji: '📚' },
      { en: 'to try', he: 'לנסות', emoji: '💪' },
      { en: 'to believe', he: 'להאמין', emoji: '🙌' },
      { en: 'always', he: 'תמיד', emoji: '♾️' },
      { en: 'never', he: 'אף פעם', emoji: '🚫' },
      { en: 'sometimes', he: 'לפעמים', emoji: '🎲' },
      { en: 'maybe', he: 'אולי', emoji: '🤷' },
      { en: 'important', he: 'חשוב', emoji: '⭐' },
      { en: 'difficult', he: 'קשה', emoji: '🧗' },
      { en: 'easy', he: 'קל', emoji: '🍃' },
      { en: 'together', he: 'ביחד', emoji: '🤝' },
      { en: 'idea', he: 'רעיון', emoji: '💭' },
    ],
    sentences: [
      { en: 'I think this is a good idea', he: 'אני חושב שזה רעיון טוב' },
      { en: 'I do not understand the question', he: 'אני לא מבין את השאלה' },
      { en: 'she always remembers my birthday', he: 'היא תמיד זוכרת את יום ההולדת שלי' },
      { en: 'sometimes I forget words in English', he: 'לפעמים אני שוכח מילים באנגלית' },
      { en: 'it is important to learn every day', he: 'חשוב ללמוד כל יום' },
      { en: 'English is not difficult', he: 'אנגלית היא לא קשה' },
      { en: 'we can try together', he: 'אנחנו יכולים לנסות ביחד' },
      { en: 'maybe he knows the answer', he: 'אולי הוא יודע את התשובה' },
    ],
  },
]

export const LESSONS_PER_LEVEL = 5

// שיעור = פרוסה של אוצר המילים של הרמה + משפטים. השיעור האחרון הוא חזרה על הכול.
export function getLesson(levelId, lessonIndex) {
  const levelIdx = LEVELS.findIndex((l) => l.id === levelId)
  if (levelIdx < 0) return null
  const level = LEVELS[levelIdx]
  const i = Number(lessonIndex)
  if (Number.isNaN(i) || i < 0 || i >= LESSONS_PER_LEVEL) return null

  const perLesson = Math.ceil(level.words.length / (LESSONS_PER_LEVEL - 1))
  const sentencesPerLesson = Math.ceil(level.sentences.length / (LESSONS_PER_LEVEL - 1))

  const base = { level, levelIndex: levelIdx, difficulty: levelIdx, index: i }
  if (i === LESSONS_PER_LEVEL - 1) {
    // שיעור חזרה: כל הרמה
    return { ...base, title: 'חזרה', words: level.words, sentences: level.sentences, isReview: true }
  }
  return {
    ...base,
    title: `שיעור ${i + 1}`,
    words: level.words.slice(i * perLesson, (i + 1) * perLesson),
    sentences: level.sentences.slice(i * sentencesPerLesson, (i + 1) * sentencesPerLesson),
    isReview: false,
  }
}

export function lessonKey(levelId, lessonIndex) {
  return `${levelId}-l${lessonIndex}`
}

// מאגר מילים גלובלי להסחות דעת בתרגילים
export const ALL_WORDS = LEVELS.flatMap((l) => l.words)
