# Monetra

A free, open-source personal finance tracker for Android. Log income and expenses in a few taps, then see where your money went: monthly totals, category breakdowns, budgets, account balances, and money lent to or owed by people. Everything stays on your phone.

Built with Kotlin and Jetpack Compose. Dark mode by default, with a light mode in Preferences.

![Monetra: log an expense on the keypad, see it land in the day's records, check where the month's money went, and split a bill](docs/demo.gif)

## Features

- **Records:** income, expense and transfers between accounts, with a calculator keypad, notes, date and time. Grouped by day, with daily to yearly views and optional carry-over.
- **Analysis:** where money went, change against the last period, spending pace, months over time, and a year range filter.
- **Budgets:** per-category limits per day, per week or per month, with in-app warnings. Limits repeat until you change them.
- **Accounts and categories:** your own, with icons and colours. Balances are calculated from your records.
- **Debts:** split a bill, lend cash, borrow cash, or note that someone paid for you. Partial repayments are tracked per person.
- **Search:** by note, category, account or amount.
- **Import and export:** CSV and Excel (`.xlsx`), with a preview before anything is added. Full JSON backup and restore.
- **App lock:** PIN with optional fingerprint, locks after a minute away.
- **Reminder:** a daily notification, only on days you have logged nothing.

Money is stored as exact integer amounts, never floating point.

## Install on your phone

### Option 1: download the APK

1. On your phone, open the [Releases page](https://github.com/JamesLunaa/monetra/releases) and download the latest `.apk` file.
2. Open the downloaded file. Android will ask to allow installs from your browser or file manager; allow it for this install.
3. Tap **Install**, then **Open**.

If there is no release yet, use Option 2.

Android may warn that the app is from an unknown source. That is normal for apps installed outside the Play Store. You can turn the setting off again afterwards in Settings.

### Option 2: build it yourself

You need [Android Studio](https://developer.android.com/studio) (or JDK 17+ and the Android SDK with platform 36), and git.

```sh
git clone https://github.com/JamesLunaa/monetra.git
cd monetra
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/monetra-debug.apk`. Copy it to your phone and open it as above, or install it over USB:

1. On the phone, enable **Developer options** and turn on **USB debugging**.
2. Plug the phone in and accept the prompt.
3. Run `./gradlew installDebug`.

On Windows, use `gradlew.bat` instead of `./gradlew`.

### Updating

Install the newer APK over the old one. Your data is kept, as long as both APKs are signed with the same key. Debug builds made on different computers use different keys, so make a backup first (**Backup & reset** in the menu) and restore it if Android asks you to uninstall.

## Importing records

To bring in records from another app or a spreadsheet, save them as CSV or Excel with the columns `TIME, TYPE, AMOUNT, CATEGORY, ACCOUNT, NOTES`, then open the menu, **Import / export**, and choose the file. You get a preview before anything is added, and importing the same file twice skips records already there. The same screen can save a sample CSV or Excel file that shows the layout.

## Your data and privacy

Monetra has no account, no ads, no analytics and no network access. Records live in a database on your phone. Use **Backup & reset** to save a backup file somewhere safe; uninstalling the app deletes its data.

## Development

```sh
./gradlew assembleDebug   # build
./gradlew test            # unit tests
```

Single `:app` module, package `io.github.jameslunaa.monetra`. Room for storage, DataStore for settings, Navigation Compose for screens.

## License

Licensed under the [Apache License 2.0](LICENSE). You may use, modify and share it, but you must keep the copyright notice and the [NOTICE](NOTICE) file, and say what you changed.

Copyright 2026 Jose Cynon Luna.
