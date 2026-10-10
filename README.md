# Ticket2Test — Presentation Edition

A GitHub repository-first AI quality review with a pearl-blue dashboard, automated Playwright browser checks, an AI assistant, and a downloadable human-friendly HTML report (print it to PDF).

## Present it using only TWO running applications

You will **start MyPath**, then **start Ticket2Test**, then interact **only with the Ticket2Test dashboard**. You do **not** need a third terminal, an `npm install` command, or a manual Playwright test run.

### Prerequisites (one-time)

- WSL Ubuntu; Java JDK 17+ (`java -version`, `javac -version`), Node.js/npm (`node -v`, `npm -v`), Git (`git --version`), Python for MyPath.
- Internet access for the first Playwright/Chromium installation and OpenAI API calls.
- An OpenAI API key. Never commit or share it.
- MyPath dependencies and any MyPath-specific configuration already installed (follow MyPath's own README).
- A **disposable, isolated demonstration environment**. Generated browser tests are executable code and may interact with the running application. Do not use production data or credentials.

### Terminal 1 — Start MyPath

```bash
cd /path/to/MyPath
# If your project uses a virtual environment:
source venv/bin/activate
python app.py
```

Keep this terminal running. The existing demo uses **http://127.0.0.1:5000**. Verify in another terminal if necessary:

```bash
curl -I http://127.0.0.1:5000/
```

A 404 on `/health` is not proof MyPath is down; use the homepage `/` to check access.

### Terminal 2 — Start Ticket2Test

```bash
cd /mnt/c/Users/User/Downloads/ticket2test/Ticket2Test-main
export OPENAI_API_KEY='YOUR_OPENAI_API_KEY'
export T2T_ALLOW_EXECUTION=true
export T2T_TARGET_URL='http://127.0.0.1:5000'
export T2T_PORT=8080
bash start.sh
```

Keep this terminal running. Open **http://127.0.0.1:8080** in Firefox. `T2T_ALLOW_EXECUTION=true` is for a deliberately isolated demo workspace only; T2 does not provide a robust sandbox for arbitrary repository-generated code.

### EVERYTHING ELSE happens inside Ticket2Test

1. Choose **Public repository**. (The **Private repository** option remains available, with `GITHUB_TOKEN` configured server-side.)
2. Paste `https://github.com/mosamapodile/MyPath.git` and select **Connect & launch AI analysis**.
3. Allow **Connect → Discover → Generate → Report** to complete.
4. Review the generated browser checks; the app running URL should be **http://127.0.0.1:5000**.
5. Click **Run browser checks** once. T2 automatically installs the Playwright dependencies and Chromium if missing, starts the test runner and captures pass/fail results. The first execution may take a few minutes and needs internet access.
6. Select **Download report**, then use **Print / Save as PDF** in the downloaded HTML report. Only executed results count as passes/failures; generated-only scenarios are clearly indicated.
7. Ask the **T2 AI assistant** to explain the findings in everyday language.

Playwright needs an *already running* website. A GitHub repository URL by itself is not a deployed browser application.

## Useful checks when something does not open

```bash
# In WSL: confirm Ticket2Test Java server
curl http://127.0.0.1:8080/api/health
# In WSL: confirm MyPath homepage
curl -I http://127.0.0.1:5000/
# Check listening ports
ss -ltnp | grep -E ':5000|:8080'
# Confirm required programs
java -version
javac -version
node -v
npm -v
git --version
```

If Firefox cannot reach Ticket2Test but the WSL health check succeeds, check that `QualityStudio.java` binds its HTTP server to `0.0.0.0`, and visit the WSL address from Windows. Restrict network access to a trusted demo network.

If Playwright cannot launch Chromium, install Linux browser dependencies once in a *separate setup session* (not needed in a normal demo):

```bash
sudo npx playwright install-deps chromium
```

Chromium download itself is handled automatically by T2. After reconnecting a repository, a new temporary test workspace is used; T2 will automatically prepare that workspace again.

## Honest verification boundaries

- Repo analysis and test generation are AI-assisted interpretations, not proof that the system is bug-free.
- Browser checks test only paths that can be reached in the running application.
- Failures may reflect defects, selectors, environment setup, or third-party API dependencies; inspect evidence before drawing conclusions.
- The report has a plain-language executive summary and downloadable evidence, and can be printed to PDF.
- The current T2 app is a prototype, **not** a multi-tenant secure service or a hardened code execution sandbox.
