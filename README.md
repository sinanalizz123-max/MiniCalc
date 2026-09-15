# MiniCalc

A tiny, fast Android calculator. No ads, no accounts, no internet permission — and the whole app is under 2 MB.

## Features

- Live preview — the answer appears as you type
- Scientific mode: sin, cos, tan, asin, acos, atan, √, ∛, xʸ, ln, log, π, e, factorial, parentheses
- Degrees / radians toggle
- Scrollable history — tap any past calculation to reuse it
- Light, dark, and system theme
- Haptic feedback on keys (can be turned off)
- No ads, no tracking, no network access

## Download

Go to the [Releases](../../releases) page and install the latest `app-release.apk`.

On your phone you may need to allow "Install unknown apps" for your browser once.

## Build

The APK is built automatically by GitHub Actions on every push to `main` — see `.github/workflows/build.yml`. Nothing is compiled locally.

## Size

Deliberately built with zero third-party libraries — only the Android framework itself. That is why the APK stays tiny.

## License

MIT