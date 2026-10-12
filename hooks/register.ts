import type { ProcessRunInit, ProcessRunResult, Register } from 'claude-code'
import {
  KEYS,
  base64Utf8,
  formatUi,
  inputTextCommand,
  isPlainAscii,
  parseDevices,
  parseForeground,
  parseUiDump,
  parseWmSize,
  shQuote,
} from './adb'

type Args = Record<string, unknown>

/** What a tool reaches outside the module, handed over by the hook that owns `$`. */
type Io = {
  run: (argv: readonly string[], init?: ProcessRunInit) => Promise<ProcessRunResult>
  tmp: () => Promise<string | undefined>
  sleep: (ms: number) => Promise<unknown>
}
type Reply = { result: string } | { deny: string }

class PhoneError extends Error {}

const REMOTE_SHOT = '/sdcard/claude-phone-screen.png'
const REMOTE_DUMP = '/sdcard/claude-phone-ui.xml'

export const register: Register = (on, options) => {
  const adbPath = String(options.adbPath || 'adb')
  const serial = String(options.serial || '')
  const screenshotDir = String(options.screenshotDir || '')

  async function adb(io: Io, args: string[], timeoutMs = 20_000) {
    const argv = [adbPath, ...(serial ? ['-s', serial] : []), ...args]
    let ran
    try {
      ran = await io.run(argv, { timeoutMs })
    } catch (err) {
      throw new PhoneError(
        `Could not run adb (${adbPath}): ${err instanceof Error ? err.message : String(err)}. ` +
          'Install Android platform-tools, or set the mod\'s "adb path" option.',
      )
    }
    if (ran.exitCode !== 0) {
      const why = (ran.stderr || ran.stdout).trim()
      if (/no devices|device .* not found|unauthorized|offline/i.test(why)) {
        throw new PhoneError(
          `${why}\nConnect the phone by USB (or Wireless debugging), enable USB debugging in Developer options, ` +
            'and accept the "Allow USB debugging?" prompt on the phone. Then check with phone_devices.',
        )
      }
      if (/more than one device/i.test(why)) {
        throw new PhoneError(`${why}\nSet the mod's "Device serial" option to one of the serials phone_devices lists.`)
      }
      throw new PhoneError(`adb ${args.join(' ')} failed (exit ${ran.exitCode}): ${why}`)
    }
    return ran.stdout
  }

  const shell = (io: Io, command: string, timeoutMs?: number) => adb(io, ['shell', command], timeoutMs)

  function num(args: Args, key: string): number {
    const v = args[key]
    if (typeof v !== 'number' || !Number.isFinite(v)) throw new PhoneError(`"${key}" must be a number.`)
    return Math.round(v)
  }

  async function tempDir(io: Io): Promise<string> {
    if (screenshotDir) return screenshotDir
    return (await io.tmp()) || '/tmp'
  }

  function join(dir: string, file: string): string {
    const sep = dir.includes('\\') && !dir.includes('/') ? '\\' : '/'
    return dir.endsWith(sep) ? dir + file : dir + sep + file
  }

  async function screenSize(io: Io) {
    return parseWmSize(await shell(io, 'wm size').catch(() => ''))
  }

  async function foreground(io: Io) {
    return parseForeground(await shell(io, 'dumpsys window | grep -E "mCurrentFocus|mFocusedApp"').catch(() => ''))
  }

  // Each tool: its description for the model, its input schema, its handler.
  const tools: Record<string, { description: string; inputSchema: Record<string, unknown>; run: (io: Io, a: Args) => Promise<string> }> = {
    phone_devices: {
      description:
        'List Android devices adb can see, with their state and model. Call first if any phone tool fails to find a device.',
      inputSchema: { type: 'object', properties: {} },
      run: async io => {
        const devices = parseDevices(await adb(io, ['devices', '-l']))
        if (devices.length === 0) {
          return 'No devices. Connect the phone by USB, enable Developer options > USB debugging, and accept the prompt on the phone.'
        }
        const lines = devices.map(d => `${d.serial}  ${d.state}${d.model ? `  ${d.model}` : ''}`)
        const note = devices.some(d => d.state === 'unauthorized') ? '\n"unauthorized": unlock the phone and accept "Allow USB debugging?".' : ''
        return `${lines.join('\n')}${note}${serial ? `\nThe mod drives: ${serial}` : ''}`
      },
    },

    phone_screenshot: {
      description:
        'Take a screenshot of the phone. Saves a PNG on this computer and returns its path: view it with the Read tool. ' +
        'Also returns the screen size in pixels. Prefer phone_ui for exact tap coordinates; use screenshots to see visual content.',
      inputSchema: { type: 'object', properties: {} },
      run: async io => {
        const local = join(await tempDir(io), `claude-phone-${Date.now()}.png`)
        await shell(io, `screencap -p ${REMOTE_SHOT}`)
        await adb(io, ['pull', REMOTE_SHOT, local], 30_000)
        await shell(io, `rm -f ${REMOTE_SHOT}`).catch(() => '')
        const size = await screenSize(io)
        const sizeText = size ? `Screen: ${size.width}x${size.height} px (tap coordinates use this space).` : ''
        return `Screenshot saved: ${local}\n${sizeText}\nUse the Read tool on that path to see it.`
      },
    },

    phone_ui: {
      description:
        'Read the current screen as a list of UI elements (text, description, resource id, type, flags) each with the exact center (x, y) to tap. ' +
        'The fastest and most precise way to know what is on screen; call it after every action that changes the screen.',
      inputSchema: { type: 'object', properties: {} },
      run: async io => {
        // Dumps to a file (dumping to /dev/tty is not supported everywhere). A screen
        // still animating makes a dump fail ("could not get idle state"): try again once.
        const dump = () => shell(io, `uiautomator dump ${REMOTE_DUMP} >/dev/null 2>&1; cat ${REMOTE_DUMP} && rm -f ${REMOTE_DUMP}`, 30_000)
        let xml = await dump().catch(() => '')
        if (!xml.includes('<hierarchy')) {
          await io.sleep(800)
          xml = await dump()
        }
        return formatUi(parseUiDump(xml), await foreground(io))
      },
    },

    phone_tap: {
      description: 'Tap the screen at (x, y) in screen pixels. Take coordinates from phone_ui.',
      inputSchema: {
        type: 'object',
        properties: { x: { type: 'number' }, y: { type: 'number' } },
        required: ['x', 'y'],
      },
      run: async (io, a) => {
        const [x, y] = [num(a, 'x'), num(a, 'y')]
        await shell(io, `input tap ${x} ${y}`)
        return `Tapped (${x}, ${y}).`
      },
    },

    phone_long_press: {
      description: 'Long-press at (x, y) in screen pixels, for durationMs (default 800).',
      inputSchema: {
        type: 'object',
        properties: { x: { type: 'number' }, y: { type: 'number' }, durationMs: { type: 'number' } },
        required: ['x', 'y'],
      },
      run: async (io, a) => {
        const [x, y] = [num(a, 'x'), num(a, 'y')]
        const ms = typeof a.durationMs === 'number' ? Math.round(a.durationMs) : 800
        await shell(io, `input swipe ${x} ${y} ${x} ${y} ${ms}`)
        return `Long-pressed (${x}, ${y}) for ${ms} ms.`
      },
    },

    phone_swipe: {
      description:
        'Swipe from (x1, y1) to (x2, y2) over durationMs (default 300). To scroll a list down, swipe upward ' +
        '(y1 > y2). Or give "direction" (up, down, left, right: which way the content scrolls) instead of points.',
      inputSchema: {
        type: 'object',
        properties: {
          x1: { type: 'number' },
          y1: { type: 'number' },
          x2: { type: 'number' },
          y2: { type: 'number' },
          direction: { type: 'string', enum: ['up', 'down', 'left', 'right'] },
          durationMs: { type: 'number' },
        },
      },
      run: async (io, a) => {
        const ms = typeof a.durationMs === 'number' ? Math.round(a.durationMs) : 300
        let pts: [number, number, number, number]
        if (typeof a.direction === 'string') {
          const size = (await screenSize(io)) ?? { width: 1080, height: 2340 }
          const [cx, cy, w, h] = [size.width / 2, size.height / 2, size.width, size.height]
          const d = {
            down: [cx, h * 0.7, cx, h * 0.3], // content scrolls down: finger moves up
            up: [cx, h * 0.3, cx, h * 0.7],
            right: [w * 0.8, cy, w * 0.2, cy],
            left: [w * 0.2, cy, w * 0.8, cy],
          }[a.direction]
          if (!d) throw new PhoneError('"direction" must be up, down, left or right.')
          pts = d.map(Math.round) as [number, number, number, number]
        } else {
          pts = [num(a, 'x1'), num(a, 'y1'), num(a, 'x2'), num(a, 'y2')]
        }
        await shell(io, `input swipe ${pts.join(' ')} ${ms}`)
        return `Swiped (${pts[0]}, ${pts[1]}) -> (${pts[2]}, ${pts[3]}) in ${ms} ms.`
      },
    },

    phone_type: {
      description:
        'Type text into the focused field (tap the field first). Set "submit" to press Enter after. ' +
        'Non-English text (Hebrew, emoji, ...) needs the ADBKeyBoard app installed and selected as the keyboard.',
      inputSchema: {
        type: 'object',
        properties: { text: { type: 'string' }, submit: { type: 'boolean' } },
        required: ['text'],
      },
      run: async (io, a) => {
        if (typeof a.text !== 'string') throw new PhoneError('"text" must be a string.')
        const text = a.text
        if (isPlainAscii(text)) {
          // Long strings are typed in chunks: one huge `input text` can drop characters.
          for (let i = 0; i < text.length; i += 200) await shell(io, inputTextCommand(text.slice(i, i + 200)))
        } else {
          const out = await shell(io, `am broadcast -a ADB_INPUT_B64 --es msg ${shQuote(base64Utf8(text))}`)
          if (!/result=-1/.test(out)) {
            throw new PhoneError(
              'This text has non-ASCII characters, which adb cannot type by itself. Install ADBKeyBoard ' +
                '(https://github.com/senzhk/ADBKeyBoard) on the phone and pick it as the keyboard, then try again.',
            )
          }
        }
        if (a.submit === true) await shell(io, `input keyevent ${KEYS.enter}`)
        return `Typed ${text.length} characters${a.submit === true ? ' and pressed Enter' : ''}.`
      },
    },

    phone_key: {
      description: `Press a key: ${Object.keys(KEYS).join(', ')}; or a numeric Android keycode.`,
      inputSchema: {
        type: 'object',
        properties: { key: { type: ['string', 'number'] } },
        required: ['key'],
      },
      run: async (io, a) => {
        const code = typeof a.key === 'number' ? Math.round(a.key) : KEYS[String(a.key).toLowerCase().replace(/[\s-]/g, '_')]
        if (code === undefined) throw new PhoneError(`Unknown key "${String(a.key)}". Known: ${Object.keys(KEYS).join(', ')}.`)
        await shell(io, `input keyevent ${code}`)
        return `Pressed ${String(a.key)}.`
      },
    },

    phone_apps: {
      description: 'List installed apps\' package names (third-party only unless "all"), optionally filtered by "filter".',
      inputSchema: {
        type: 'object',
        properties: { filter: { type: 'string' }, all: { type: 'boolean' } },
      },
      run: async (io, a) => {
        const out = await shell(io, `pm list packages${a.all === true ? '' : ' -3'}`)
        const filter = typeof a.filter === 'string' ? a.filter.toLowerCase() : ''
        const pkgs = out
          .split('\n')
          .map(l => l.replace(/^package:/, '').trim())
          .filter(p => p && p.toLowerCase().includes(filter))
          .sort()
        return pkgs.length ? pkgs.join('\n') : 'No matching packages.'
      },
    },

    phone_open_app: {
      description: 'Launch an app by package name (e.g. com.whatsapp; find names with phone_apps).',
      inputSchema: {
        type: 'object',
        properties: { package: { type: 'string' } },
        required: ['package'],
      },
      run: async (io, a) => {
        const pkg = typeof a.package === 'string' ? a.package.trim() : ''
        if (!/^[\w.]+$/.test(pkg)) throw new PhoneError('"package" must be a package name like com.whatsapp.')
        const out = await shell(io, `monkey -p ${pkg} -c android.intent.category.LAUNCHER 1`)
        if (/No activities found|monkey aborted/i.test(out)) throw new PhoneError(`${pkg} has no launchable activity (is it installed? see phone_apps).`)
        return `Launched ${pkg}.`
      },
    },

    phone_open_url: {
      description: 'Open a URL or deep link on the phone (https:, tel:, mailto:, geo:, app links...).',
      inputSchema: {
        type: 'object',
        properties: { url: { type: 'string' } },
        required: ['url'],
      },
      run: async (io, a) => {
        if (typeof a.url !== 'string' || !a.url) throw new PhoneError('"url" must be a string.')
        await shell(io, `am start -a android.intent.action.VIEW -d ${shQuote(a.url)}`)
        return `Opened ${a.url}.`
      },
    },

    phone_shell: {
      description:
        'Run a raw command in the phone\'s adb shell and return its output. For anything the other phone tools do not cover ' +
        '(settings, dumpsys, am, pm, ...). Never use it to wipe, uninstall or change security settings without the user asking.',
      inputSchema: {
        type: 'object',
        properties: { command: { type: 'string' } },
        required: ['command'],
      },
      run: async (io, a) => {
        if (typeof a.command !== 'string' || !a.command.trim()) throw new PhoneError('"command" must be a non-empty string.')
        const out = await shell(io, a.command, 60_000)
        return out.trim() || '(no output)'
      },
    },
  }

  on('session.start', async ($, e, next) => {
    const started = await next(e)
    for (const [name, t] of Object.entries(tools)) {
      await $.tool.register({ name, description: t.description, inputSchema: t.inputSchema, isDeferred: false })
    }
    return started
  })

  for (const [name, t] of Object.entries(tools)) {
    on('tool.call', { tool: `mcp__phone-control__${name}` }, async ($, e): Promise<Reply> => {
      const { tool: _tool, tool_use_id: _id, consent: _consent, ...args } = e as Args
      const io: Io = {
        run: (argv, init) => $.process.run(argv, init),
        tmp: async () => (await $.env.get('TMPDIR')) || (await $.env.get('TEMP')) || (await $.env.get('TMP')),
        sleep: ms => $.clock.sleep(ms),
      }
      try {
        return { result: await t.run(io, args) }
      } catch (err) {
        if (err instanceof PhoneError) return { deny: err.message }
        return { deny: `${name} failed: ${err instanceof Error ? err.message : String(err)}` }
      }
    }).catch(() => ({ deny: `${name}: the phone-control mod failed.` }))
  }
}
