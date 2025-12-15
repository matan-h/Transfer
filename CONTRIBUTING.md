## Contributing

Thank you for in contributing! This app have three main parts:

- **App UI** - Android activities in Kotlin and XML.
- **HTTP Server** - the HTTP Ktor server the app run.
- **Website** - Plain HTML/JS/CSS served from the app's assets.

Please follow the instructions below to get started.

---

### Website Editing

Website files are in `app/src/main/assets/` and can be edited like any regular HTML/CSS/JS project.

To simplify development:

1. Forward the app's server to your computer (you need to run Transfer on emulator for this script to work):

```bash
adb forward tcp:9135 tcp:8000
```

2. Start the local dev server with live reload:
Install server dependencies:
```bash
pip install requests flask livereload urllib3
```
Then run
```bash
python serve.py localhost:9135
```

3. Visit [http://localhost:4444](http://localhost:4444) to see your changes.

This setup live-reloads the site and forwards `/api/*` requests to the emulator.

### App UI (XML/Kotlin)

- Themes are defined in `res/values/themes.xml` and `res/values/colors.xml`.
- For dark mode: check `res/values-night/colors.xml`.
- Layouts are in `res/layout/`. the UI code in `src/main/java/.../ui/`, and MainActivity is in `src/main/java/.../MainActivity`.

### HTTP Server (Ktor)

- API endpoints (such as /api/upload): `server/KtorServer`
- Server features (like password protection): `server/FileServerService`

---

### PR Guidelines

- for code changes, Use commit messages with tags, e.g.:

  - `fix(server): improve error handling for large file uploads`
  - `feat(ui): add progress indicator for file transfers`
  - `perf(server): optimize streaming for multi-GB files`

- Fork the repo and create a branch for your change.
- Open a pull request

Looking forward to your contributions!
