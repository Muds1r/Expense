# Expense Tracker

A personal Android app that reads your bank transaction alert emails from Gmail
(**read-only** — it can never modify or delete your mail), stores them on-device,
and shows you where your money goes.

- Tracks money **in** and **out** per bank
- Time range filters: 7 / 30 / 60 days
- Top senders (who sent you the most), top spends, most frequent payments
- Top 10 transactions by amount
- Keeps only the **last 60 days** of data locally — older rows are deleted from
  the app's database automatically on every sync. Your Gmail is never touched.
- Everything stays on your phone. No server, no analytics, no third parties.

## One-time Google Cloud setup (required before first run)

The app talks to the Gmail API, so you need a (free) Google Cloud project:

1. Go to [console.cloud.google.com](https://console.cloud.google.com) and create
   a new project (e.g. "Expense Tracker").
2. **Enable the Gmail API**: APIs & Services → Library → search "Gmail API" → Enable.
3. **OAuth consent screen**: APIs & Services → OAuth consent screen
   - User type: **External**, then fill in the app name and your email.
   - Under **Test users**, add your own Gmail address. (As a test user you can
     use the app indefinitely without any Google verification.)
4. **Create the Android OAuth client**: APIs & Services → Credentials →
   Create Credentials → OAuth client ID
   - Application type: **Android**
   - Package name: `com.expense.tracker`
   - SHA-1: run this and paste the debug SHA-1:

     ```bash
     keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android | grep SHA1
     ```

   No file needs to be downloaded or added to the project — Google matches your
   app by package name + SHA-1 automatically.

## Build & install

Open the project in Android Studio and press Run, or from the command line:

```bash
./gradlew :app:installDebug
```

On first launch, sign in with the same Google account you added as a test user
and approve the Gmail read-only permission. Then tap the sync icon.

## Adding your banks

Bank alert emails are matched by sender domain. The list lives in
`app/src/main/java/com/expense/tracker/data/parser/BankRegistry.kt` — add a line
like `"mybank.com" to "My Bank"` and the next sync will pick those emails up.

If a bank's emails aren't parsing well (missing amount or counterparty), the
regex patterns are in `data/parser/TransactionParser.kt`.

## How it works

- **Sync**: on demand (sync button) and automatically every 6 hours via
  WorkManager. Each sync queries Gmail for `from:(<bank domains>) newer_than:60d`,
  parses each email, and inserts new transactions (duplicates are impossible —
  the Gmail message ID is the primary key).
- **Retention**: after each sync, local rows older than 60 days are deleted.
- **Storage**: Room (SQLite) database on-device.
- **Scope**: only `gmail.readonly` is requested, so the app is technically
  incapable of changing anything in your Gmail account.
