// Signs release builds with your upload/release keystore when these Gradle properties (or env vars)
// are present; otherwise falls back to the debug keystore so CI can still produce an installable APK.
//   UNVEIL_RELEASE_STORE_FILE, UNVEIL_RELEASE_STORE_PASSWORD, UNVEIL_RELEASE_KEY_ALIAS, UNVEIL_RELEASE_KEY_PASSWORD
const { withAppBuildGradle } = require("expo/config-plugins");

const MARKER = "// unveil-release-signing";

const SIGNING = `
        release { ${MARKER}
            def p = { String k -> findProperty(k) ?: System.getenv(k) }
            if (p('UNVEIL_RELEASE_STORE_FILE')) {
                storeFile file(p('UNVEIL_RELEASE_STORE_FILE'))
                storePassword p('UNVEIL_RELEASE_STORE_PASSWORD')
                keyAlias p('UNVEIL_RELEASE_KEY_ALIAS')
                keyPassword p('UNVEIL_RELEASE_KEY_PASSWORD')
            } else {
                storeFile file('debug.keystore')
                storePassword 'android'
                keyAlias 'androiddebugkey'
                keyPassword 'android'
            }
        }`;

module.exports = function withReleaseSigning(config) {
  return withAppBuildGradle(config, (cfg) => {
    let src = cfg.modResults.contents;
    if (src.includes(MARKER)) return cfg;
    src = src.replace(/signingConfigs\s*\{\s*debug\s*\{[^}]*\}/, (m) => `${m}${SIGNING}`);
    src = src.replace(
      /(release\s*\{[^{}]*?)signingConfig\s+signingConfigs\.debug/,
      "$1signingConfig signingConfigs.release",
    );
    if (!src.includes(MARKER) || !src.includes("signingConfig signingConfigs.release")) {
      throw new Error("withReleaseSigning: could not patch android/app/build.gradle");
    }
    cfg.modResults.contents = src;
    return cfg;
  });
};
