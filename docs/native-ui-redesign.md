# Native UI redesign

## Scope and references

Android and Windows retain their native UI stacks and existing local-first
workflows. This is a stable-mode interface redesign: predictable navigation,
compact inventory information, and platform-native controls.

Primary references for structure, rather than copied screens or business logic:

- [Now in Android](https://github.com/android/nowinandroid) and
  [adaptive navigation in Compose](https://developer.android.com/develop/ui/compose/layouts/adaptive/build-adaptive-navigation).
- [WinUI Gallery](https://github.com/microsoft/WinUI-Gallery) and
  [NavigationView guidance](https://learn.microsoft.com/en-us/windows/apps/design/controls/navigationview).

## Layout contract

Android compact widths:

```text
Home / Inventory / Records / Settings        one page title
----------------------------------------------------------
Page content                                scrolls
  Home: summary metrics, low stock, quick actions
  Inventory: search, filters, compact rows
  Records: signed changes, reason, local date/time
  Settings: Sync / Import / App / About

Home          Inventory          Records          Settings
```

Details and editing/scanning/import workflows have a back action. Expanded
widths use adaptive navigation and existing list-detail panes. Settings has
one main entry and a section list, without a second Settings heading in its
content. A sync shortcut may open the corresponding section directly.

Inventory card:

```text
+------------------------------------------------------+
| Name (up to two lines)            Quantity / minimum  |
| SKU                              Product thumbnail   |
| Category / package               Usage ring          |
| Location / stock state            Issued / total      |
| Local update date                                    |
+------------------------------------------------------+
```

The usage indicator belongs to the same compact right column as the image
and quantity. It must not create a separate full-width footer below the card.
The exact totals remain readable alongside the visual indicator. Total means
current stock plus all valid recorded outbound quantities, not supplier stock
or a guessed lifetime purchase quantity.

Windows:

```text
Application identity                  Sync state / sync
-------------------------------------------------------
Home          | Page title and relevant primary actions
Inventory     |-----------------------------------------
Records       | Content workspace
Project BOM   | Inventory list | selected item detail
              | or a page-specific form / history
Settings      |
```

Settings remains the single footer destination. Page headings carry the
current task; the application shell does not repeat a generic inventory
workspace heading above every page.

About, on both platforms:

```text
Component Vault                 installed version
Electronic component inventory
Author                          Rafael-Ikaros
License                         GPLv3
Project / source / releases      existing repository
-------------------------------------------------------
Check for updates               idle / loading / result
Latest version and release notes
Download or release-page fallback, when applicable
```

The display author is Rafael-Ikaros. Repository links remain attached to
`Rafael-ban/Component_Manager`; no unverified author profile or contact is added.

## Visual and interaction rules

- Native fonts, neutral surfaces, restrained teal accents, semantic error and
  warning colors, and light/dark theme support.
- Spacing follows a 4/8/12/16/24 scale. Search and actions use ordinary control
  sizes instead of oversized decorative pills. Android touch targets remain
  at least 48 dp.
- Short local dates are presentation only. Stored UTC timestamps, sync
  comparisons, stock calculations and movement signs retain their semantics.
- Transient Android feedback uses a snackbar rather than a permanent banner
  consuming space on every top-level page.
- Existing add/edit, QR/OCR, manual and batch movement, BOM, migration, label,
  deletion, and update actions remain connected to their current handlers.

## Acceptance and release

Review compact stock cards, navigation/back behavior, one Settings heading,
About author/version, and all secondary entry points. Check resource and XAML
syntax and inspect the diff before committing. GitHub CI performs Android and
Windows compilation and relevant regression tests, as requested by the user.
Native device rendering is a separate verification step; a successful CI
build alone does not establish screenshot-level visual verification.
