# phone-control: a Claude Code mod that controls your Android phone

Lets Claude see and operate an Android phone connected to your computer over ADB.
Claude gets these tools:

| Tool | What it does |
| --- | --- |
| `phone_devices` | List connected phones and their state |
| `phone_ui` | Read the screen as a list of elements (text, id, type) with the exact point to tap |
| `phone_screenshot` | Save a screenshot on your computer; Claude views it with the Read tool |
| `phone_tap` / `phone_long_press` | Tap or long-press at (x, y) |
| `phone_swipe` | Swipe between two points, or scroll `up` / `down` / `left` / `right` |
| `phone_type` | Type into the focused field, optionally pressing Enter |
| `phone_key` | Home, back, enter, app switch, volume, power, ... |
| `phone_apps` / `phone_open_app` | List installed apps, launch one by package name |
| `phone_open_url` | Open a URL or deep link (`https:`, `tel:`, `geo:`, ...) |
| `phone_shell` | Run any `adb shell` command (the escape hatch) |

## Requirements

- **An Android phone.** iPhone is not supported: iOS has no ADB equivalent that works without a Mac, Xcode and a signed WebDriverAgent build.
- **Claude Code running on the computer the phone is plugged into.** A cloud session (claude.ai/code) runs in a remote container and cannot reach a phone on your desk.
- **ADB** (Android platform-tools) on that computer:
  - macOS: `brew install android-platform-tools`
  - Windows: `winget install Google.PlatformTools`
  - Linux: `sudo apt install adb`

## Phone setup (once)

1. Settings → About phone → tap **Build number** 7 times to unlock Developer options.
2. Settings → Developer options → turn on **USB debugging**.
3. Plug the phone in, unlock it, and accept **Allow USB debugging?** (tick "Always allow").
4. Check: `adb devices` lists the phone as `device`.

Wireless instead of a cable (Android 11+): Developer options → **Wireless debugging** → *Pair device with pairing code*, then `adb pair <ip>:<port>` and `adb connect <ip>:<port>`.

### Typing Hebrew and other non-English text

`adb` can only type plain English characters by itself. For Hebrew, Arabic, emoji and the like,
install [ADBKeyBoard](https://github.com/senzhk/ADBKeyBoard) on the phone and pick it as the
active keyboard. `phone_type` uses it automatically for non-ASCII text and says so if it is missing.

## Install

At the prompt of a Claude Code terminal session:

```
/plugin install phone-control --marketplace dtubugk-gif/-
```

Answer `y` to add the marketplace, then pick the scope (user scope makes it available in every session).

## Options

Set in `/config` (or under `pluginConfigs.phone-control` in settings):

| Option | Default | Use |
| --- | --- | --- |
| `adbPath` | `adb` | Full path to adb when it is not on PATH |
| `serial` | empty | Which phone to drive when several are connected (`adb devices` shows serials) |
| `screenshotDir` | system temp folder | Where screenshots are saved (the folder must exist) |

## Using it

Just ask, for example:

- "Open WhatsApp on my phone and read me the latest unread chat."
- "Turn on Do Not Disturb on my phone."
- "Search YouTube on my phone for lo-fi music and play the first result."

Claude works in a loop: `phone_ui` to read the screen, act, `phone_ui` again to confirm.
Screenshots are for visual content (photos, maps, games) where the element list is empty.

## Safety

Claude acts on your real phone, with your accounts signed in. Every phone tool call goes through
Claude Code's normal permission prompts, so you approve actions unless you allow-list them.
Keep `phone_shell` on "ask": it can do anything adb can, including uninstalling apps.
Do not leave the phone unlocked and connected to a session you are not watching.

## Development

```
claude plugin validate .
claude plugin test .
```

The tests fake `adb`, so they need no phone.
