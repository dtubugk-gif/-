// בניית קובץ HTML יחיד שאפשר להוריד ולהריץ ישירות מהמחשב (file://).
// כל ה-JS, CSS והפונטים מוטמעים פנימה — אין תלות ברשת.
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { viteSingleFile } from 'vite-plugin-singlefile'

export default defineConfig({
  plugins: [react(), tailwindcss(), viteSingleFile()],
  build: {
    outDir: 'dist-single',
    // מטמיע גם את קובצי הפונט (woff2) כ-base64 בתוך ה-HTML
    assetsInlineLimit: 100000000,
    chunkSizeWarningLimit: 100000000,
  },
})
