# Changelog

All notable changes to Monetra. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

## [1.1.0] - 2026-10-20

### Changed
- The app's package name is now `io.github.jameslunaa.monetra` (it was `com.example.monetra`). Android treats this as a new app, so 1.1.0 installs next to 1.0.0 instead of updating it. To keep your data, use **Back up now** in 1.0.0, then **Restore…** in 1.1.0. Preferences and the app lock PIN are not part of the backup, so set them again.

## [1.0.0] - 2026-10-18

### Added
- Records for income, expenses and transfers, with a calculator keypad, notes, date and time, grouped by day with daily to yearly views and optional carry-over.
- Accounts and categories with icons and colours; balances calculated from records.
- Analysis with four views: where money went, against the last period, spending pace, and months over time, with month, 3/6-month, year and custom ranges.
- Budgets per category, per day, week or month, with in-app warnings. Limits repeat until changed.
- Debts: split a bill with someone, lent cash, borrowed cash, and bills paid for you, with partial repayments.
- Search by note, category, account or amount.
- CSV and Excel (`.xlsx`) import and export, with a preview, duplicate skipping and sample files.
- Full JSON backup and restore, and delete & reset.
- App lock with a PIN and optional fingerprint, locking after a minute away.
- Daily reminder that only fires on days with nothing logged.
- Dark theme by default with a light option, app logo and splash screen.
