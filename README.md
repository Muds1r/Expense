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

## One-time setup (2 minutes, no Google Cloud needed)

The app reads Gmail over IMAP using a Google **App Password** — no OAuth, no
Google Cloud project, no consent screens, and nothing that expires:

1. Enable **2-Step Verification** on your Google account (if not already on):
   [myaccount.google.com/security](https://myaccount.google.com/security)
2. Go to [myaccount.google.com/apppasswords](https://myaccount.google.com/apppasswords)
   and create an app password named "Expense Tracker".
3. Open the app and enter your Gmail address plus that 16-character password.

The app password works until you revoke it from the same page. The app opens
the mailbox in read-only mode, so it cannot modify or delete anything.

## Build & install

Open the project in Android Studio and press Run, or from the command line:

```bash
./gradlew :app:installDebug
```

## Adding your banks

Bank alert emails are matched by sender domain. The list lives in
`app/src/main/java/com/expense/tracker/data/parser/BankRegistry.kt` — add a line
like `"mybank.com" to "My Bank"` and the next sync will pick those emails up.

If a bank's emails aren't parsing well (missing amount or counterparty), the
regex patterns are in `data/parser/TransactionParser.kt`.

## How it works

- **Sync**: on demand (sync button) and automatically every 6 hours via
  WorkManager. Each sync connects to `imap.gmail.com` and searches All Mail for
  messages from known bank domains received in the last 60 days, parses each
  email, and inserts new transactions (duplicates are impossible — the email's
  Message-ID is the primary key).
- **Retention**: after each sync, local rows older than 60 days are deleted.
- **Storage**: Room (SQLite) database on-device. Credentials are stored in the
  app's private storage on this device only.
- **Read-only**: the mailbox is opened in IMAP read-only mode, so the app is
  technically incapable of changing anything in your Gmail account.
