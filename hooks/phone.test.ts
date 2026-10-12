import { describe, expect, test } from 'claude-code/testing'
import { base64Utf8, formatUi, inputTextCommand, parseDevices, parseForeground, parseUiDump, parseWmSize } from './adb'

const DUMP = `<?xml version='1.0' encoding='UTF-8' standalone='yes' ?><hierarchy rotation="0">
<node index="0" text="" resource-id="" class="android.widget.FrameLayout" package="com.whatsapp" content-desc="" checkable="false" checked="false" clickable="false" enabled="true" focusable="false" focused="false" scrollable="false" long-clickable="false" password="false" selected="false" bounds="[0,0][1080,2400]">
<node index="0" text="Search &amp; chat" resource-id="com.whatsapp:id/search" class="android.widget.EditText" package="com.whatsapp" content-desc="" checkable="false" checked="false" clickable="true" enabled="true" focusable="true" focused="false" scrollable="false" long-clickable="false" password="false" selected="false" bounds="[40,100][1040,200]" />
<node index="1" text="" resource-id="" class="android.widget.ImageButton" package="com.whatsapp" content-desc="New chat" checkable="false" checked="false" clickable="true" enabled="true" focusable="true" focused="false" scrollable="false" long-clickable="false" password="false" selected="false" bounds="[900,2200][1040,2340]" />
<node index="2" text="" resource-id="" class="android.view.View" package="com.whatsapp" content-desc="" checkable="false" checked="false" clickable="false" enabled="true" focusable="false" focused="false" scrollable="false" long-clickable="false" password="false" selected="false" bounds="[0,0][0,0]" />
</node></hierarchy>`

const ok = (stdout = '', exitCode = 0, stderr = '') => ({ value: { exitCode, stdout, stderr, isStdoutTruncated: false, isStderrTruncated: false } })

describe('helpers', () => {
  test('parses the UI dump into tappable elements', () => {
    const nodes = parseUiDump(DUMP)
    expect(nodes.length).toBe(2)
    expect(nodes[0]).toEqual({ index: 0, label: 'Search & chat', cls: 'EditText', id: 'search', x: 540, y: 150, flags: ['clickable', 'editable'] })
    expect(nodes[1]?.label).toBe('New chat')
    expect(formatUi(nodes, 'com.whatsapp')).toContain('[1] ImageButton "New chat" @ (970, 2270) [clickable]')
  })

  test('quotes typed text for the device shell', () => {
    expect(inputTextCommand(`it's a test`)).toBe(`input text 'it'\\''s%sa%stest'`)
  })

  test('encodes UTF-8 as base64 (Hebrew too)', () => {
    expect(base64Utf8('שלום')).toBe('16nXnNeV150=')
    expect(base64Utf8('hi!')).toBe('aGkh')
    expect(base64Utf8('ab')).toBe('YWI=')
  })

  test('reads adb devices, wm size and the foreground app', () => {
    const out = 'List of devices attached\nR58M123  device usb:1-1 product:x model:SM_G991B device:o1s\nemulator-5554  unauthorized\n'
    expect(parseDevices(out)).toEqual([
      { serial: 'R58M123', state: 'device', model: 'SM G991B' },
      { serial: 'emulator-5554', state: 'unauthorized', model: '' },
    ])
    expect(parseWmSize('Physical size: 1080x2400\nOverride size: 720x1600')).toEqual({ width: 720, height: 1600 })
    expect(parseForeground('  mCurrentFocus=Window{a1b2 u0 com.whatsapp/com.whatsapp.HomeActivity}')).toBe('com.whatsapp')
  })
})

describe('tools', () => {
  test('tap runs input tap on the device', async ($, on) => {
    const calls: string[][] = []
    on('process.run', async (_$, e) => {
      calls.push([...e.argv])
      return ok()
    })
    const r = await $.tool.call({ tool: 'mcp__phone-control__phone_tap', x: 100.4, y: 200 })
    expect(r.deny).toBeUndefined()
    expect(r.result).toBe('Tapped (100, 200).')
    expect(calls).toEqual([['adb', 'shell', 'input tap 100 200']])
  })

  test('the serial option picks the device', { options: { serial: 'R58M123' } }, async ($, on) => {
    const calls: string[][] = []
    on('process.run', async (_$, e) => {
      calls.push([...e.argv])
      return ok()
    })
    await $.tool.call({ tool: 'mcp__phone-control__phone_key', key: 'home' })
    expect(calls).toEqual([['adb', '-s', 'R58M123', 'shell', 'input keyevent 3']])
  })

  test('Hebrew is typed through ADBKeyBoard', async ($, on) => {
    const calls: string[][] = []
    on('process.run', async (_$, e) => {
      calls.push([...e.argv])
      return ok('Broadcasting: Intent { act=ADB_INPUT_B64 }\nBroadcast completed: result=-1')
    })
    const r = await $.tool.call({ tool: 'mcp__phone-control__phone_type', text: 'שלום', submit: true })
    expect(r.result).toBe('Typed 4 characters and pressed Enter.')
    expect(calls).toEqual([
      ['adb', 'shell', "am broadcast -a ADB_INPUT_B64 --es msg '16nXnNeV150='"],
      ['adb', 'shell', 'input keyevent 66'],
    ])
  })

  test('Hebrew without ADBKeyBoard explains what to install', async ($, on) => {
    on('process.run', async () => ok('Broadcast completed: result=0'))
    const r = await $.tool.call({ tool: 'mcp__phone-control__phone_type', text: 'שלום' })
    expect(r.deny).toContain('ADBKeyBoard')
  })

  test('no device gives setup steps, not a raw error', async ($, on) => {
    on('process.run', async () => ok('', 1, 'adb: no devices/emulators found'))
    const r = await $.tool.call({ tool: 'mcp__phone-control__phone_tap', x: 1, y: 1 })
    expect(r.deny).toContain('USB debugging')
  })

  test('phone_ui lists elements with their tap points', async ($, on) => {
    on('process.run', async (_$, e) => {
      const cmd = e.argv[e.argv.length - 1] ?? ''
      if (cmd.startsWith('uiautomator dump')) return ok(DUMP)
      if (cmd.startsWith('dumpsys window')) return ok('mCurrentFocus=Window{1 u0 com.whatsapp/.Main}')
      return ok()
    })
    const r = await $.tool.call({ tool: 'mcp__phone-control__phone_ui' })
    expect(r.result).toContain('Foreground app: com.whatsapp')
    expect(r.result).toContain('[0] EditText #search "Search & chat" @ (540, 150) [clickable, editable]')
  })

  test('a screenshot is pulled to a local file', { options: { screenshotDir: '/shots' } }, async ($, on) => {
    const calls: string[][] = []
    on('process.run', async (_$, e) => {
      calls.push([...e.argv])
      return ok(e.argv.includes('wm size') ? 'Physical size: 1080x2400' : '')
    })
    const r = await $.tool.call({ tool: 'mcp__phone-control__phone_screenshot' })
    expect(r.result).toMatch(/Screenshot saved: \/shots\/claude-phone-\d+\.png/)
    expect(r.result).toContain('Screen: 1080x2400')
    expect(calls[1]?.slice(0, 3)).toEqual(['adb', 'pull', '/sdcard/claude-phone-screen.png'])
  })
})
