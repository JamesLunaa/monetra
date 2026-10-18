# Changelog

All notable changes to Monetra. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Changed
- Import error messages and docs describe the file layout instead of naming another app.

## [1.0.1] - 2026-10-17

### Changed
- The add button is hidden on Analysis and Budgets, where it covered figures.
- Import / export, Backup & reset and Preferences have a tighter header.
- Search says what it covers, and its placeholder fits on one line.

### Fixed
- The records list now scrolls to a record you just added instead of leaving it above the visible area.

## [1.0.0] - 2026-10-17

### Added
- Excel (`.xlsx`) import and export, next to CSV, with sample files for both.
- Debts: split a bill with someone, lent cash, borrowed cash, and bills paid for you, with partial repayments.
- App lock with a PIN and optional fingerprint, locking after a minute away.
- Daily reminder that only fires on days with nothing logged.
- Refund and loan record types in CSV import/export and backups.
- Apache-2.0 license, README with install guide, and this changelog.

### Changed
- "Import / export CSV" is now "Import / export".
- Preferences switches toggle from the whole row.

## Phase 2

### Added
- Analysis with four views: where money went, against the last period, spending pace, and months over time, plus a year range filter.
- Budgets per category, per day, week or month, with in-app warnings.
- Search, CSV export, and full JSON backup and restore.
- CSV import with a sample layout shown on screen.

## Phase 1

### Added
- Records, editor with calculator keypad, accounts, categories, and period views.
- CSV import with preview and duplicate skipping.
- Dark theme by default with a light option, app logo, splash screen.
