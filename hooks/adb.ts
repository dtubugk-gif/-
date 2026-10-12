// Pure helpers: everything here is testable without a phone.

export type UiNode = {
  index: number
  label: string
  cls: string
  id: string
  x: number
  y: number
  flags: string[]
}

const ATTR = /([\w-]+)="([^"]*)"/g
const BOUNDS = /\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]/

function unescapeXml(s: string): string {
  return s
    .replace(/&#(\d+);/g, (_, n: string) => String.fromCodePoint(Number(n)))
    .replace(/&#x([0-9a-f]+);/gi, (_, n: string) => String.fromCodePoint(parseInt(n, 16)))
    .replace(/&quot;/g, '"')
    .replace(/&apos;/g, "'")
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&amp;/g, '&')
}

/**
 * Reads a `uiautomator dump` and keeps the nodes worth acting on: those with
 * text or a description, and those that click, scroll, check or take input.
 * Each comes with the center of its bounds, the point to tap.
 */
export function parseUiDump(xml: string): UiNode[] {
  const nodes: UiNode[] = []
  for (const match of xml.matchAll(/<node\b([^>]*?)\/?>/g)) {
    const a: Record<string, string> = {}
    for (const [, k, v] of (match[1] ?? '').matchAll(ATTR)) a[k as string] = unescapeXml(v ?? '')
    const b = BOUNDS.exec(a.bounds ?? '')
    if (!b) continue
    const [x1, y1, x2, y2] = b.slice(1).map(Number) as [number, number, number, number]
    if (x2 <= x1 || y2 <= y1) continue

    const cls = (a.class ?? '').split('.').pop() ?? ''
    const isEditable = cls === 'EditText' || a.focusable === 'true' && /edit|input|search/i.test(a['resource-id'] ?? '')
    const flags: string[] = []
    if (a.clickable === 'true' || a['long-clickable'] === 'true') flags.push('clickable')
    if (a.scrollable === 'true') flags.push('scrollable')
    if (a.checkable === 'true') flags.push(a.checked === 'true' ? 'checked' : 'unchecked')
    if (isEditable) flags.push('editable')
    if (a.focused === 'true') flags.push('focused')
    if (a.selected === 'true') flags.push('selected')
    if (a.enabled === 'false') flags.push('disabled')
    if (a.password === 'true') flags.push('password')

    const text = (a.text ?? '').trim()
    const desc = (a['content-desc'] ?? '').trim()
    const label = text && desc && text !== desc ? `${text} (${desc})` : text || desc
    const isActionable = flags.some(f => f !== 'focused' && f !== 'selected' && f !== 'disabled')
    if (!label && !isActionable) continue

    nodes.push({
      index: nodes.length,
      label,
      cls,
      id: (a['resource-id'] ?? '').replace(/^.*:id\//, ''),
      x: Math.round((x1 + x2) / 2),
      y: Math.round((y1 + y2) / 2),
      flags,
    })
  }
  return nodes
}

export function formatUi(nodes: UiNode[], packageName?: string): string {
  if (nodes.length === 0) return 'No actionable elements on screen (it may be a game, a video, or a WebView/canvas: take a screenshot instead).'
  const head = packageName ? `Foreground app: ${packageName}\n` : ''
  const lines = nodes.map(n => {
    const label = n.label ? `"${n.label.length > 80 ? n.label.slice(0, 77) + '...' : n.label}"` : '(no text)'
    const id = n.id ? ` #${n.id}` : ''
    const flags = n.flags.length ? ` [${n.flags.join(', ')}]` : ''
    return `[${n.index}] ${n.cls}${id} ${label} @ (${n.x}, ${n.y})${flags}`
  })
  return `${head}${nodes.length} elements; tap one at its @ (x, y):\n${lines.join('\n')}`
}

/** Quotes a string for the device's /system/bin/sh. */
export function shQuote(s: string): string {
  return `'${s.replace(/'/g, `'\\''`)}'`
}

/** True when `input text` can type it: printable ASCII only. */
export function isPlainAscii(s: string): boolean {
  return /^[\x20-\x7e]*$/.test(s)
}

/**
 * The remote command typing ASCII text with `input text`, which reads `%s`
 * as a space and has no other escape: spaces become `%s`, the rest is quoted
 * for the shell.
 */
export function inputTextCommand(text: string): string {
  return `input text ${shQuote(text.replace(/ /g, '%s'))}`
}

const B64 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/'

export function base64Utf8(text: string): string {
  const bytes = new TextEncoder().encode(text)
  let out = ''
  for (let i = 0; i < bytes.length; i += 3) {
    const [a = 0, b = 0, c = 0] = [bytes[i], bytes[i + 1], bytes[i + 2]]
    const n = (a << 16) | (b << 8) | c
    out += B64[(n >> 18) & 63]! + B64[(n >> 12) & 63]!
    out += i + 1 < bytes.length ? B64[(n >> 6) & 63]! : '='
    out += i + 2 < bytes.length ? B64[n & 63]! : '='
  }
  return out
}

export const KEYS: Record<string, number> = {
  home: 3,
  back: 4,
  call: 5,
  endcall: 6,
  dpad_up: 19,
  dpad_down: 20,
  dpad_left: 21,
  dpad_right: 22,
  dpad_center: 23,
  volume_up: 24,
  volume_down: 25,
  power: 26,
  camera: 27,
  tab: 61,
  space: 62,
  enter: 66,
  delete: 67,
  menu: 82,
  search: 84,
  play_pause: 85,
  next: 87,
  previous: 88,
  page_up: 92,
  page_down: 93,
  escape: 111,
  forward_delete: 112,
  move_home: 122,
  move_end: 123,
  mute: 164,
  app_switch: 187,
  brightness_down: 220,
  brightness_up: 221,
  sleep: 223,
  wakeup: 224,
}

/** Reads `adb devices -l` into one line per device. */
export function parseDevices(out: string): { serial: string; state: string; model: string }[] {
  return out
    .split('\n')
    .slice(1)
    .map(l => l.trim())
    .filter(Boolean)
    .filter(l => !l.startsWith('*'))
    .map(l => {
      const [serial = '', state = ''] = l.split(/\s+/)
      const model = /model:(\S+)/.exec(l)?.[1]?.replace(/_/g, ' ') ?? ''
      return { serial, state, model }
    })
}

/** Reads the screen size from `wm size`, the override winning. */
export function parseWmSize(out: string): { width: number; height: number } | undefined {
  const m = /Override size:\s*(\d+)x(\d+)/.exec(out) ?? /Physical size:\s*(\d+)x(\d+)/.exec(out)
  return m ? { width: Number(m[1]), height: Number(m[2]) } : undefined
}

/** Reads the foreground package out of `dumpsys window`. */
export function parseForeground(out: string): string | undefined {
  const m = /mCurrentFocus=Window\{[^}]*\s([\w.]+)\/[\w.$]+\}/.exec(out) ?? /mFocusedApp=.*?\s([\w.]+)\/[\w.$]+/.exec(out)
  return m?.[1]
}
