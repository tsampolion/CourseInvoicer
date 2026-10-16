# EduSuite style guide

The shared look of the EduSuite apps: Course Invoicer and Course Pacer. This file is canonical. Each
app keeps a copy, and a change here is made in every copy.

Everything below is shared, except the **Accent** table in §2.3. A new suite app adds one row there
and changes nothing else.

## 1. Principles

- **Calm.** One accent colour per app and few other colours. Nothing blinks, bounces or competes
  for attention.
- **Paper-like.** A soft, sage-tinted paper background with ink-dark text. Panels sit on the paper
  like index cards.
- **Flat.** No shadows. A 1dp line separates a panel from the paper.
- **Numbers are legible.** Money, dates and counts use a monospace face, so figures line up and a
  `1` is never mistaken for a `7`.
- **The accent identifies the app.** Wallpaper (dynamic) colour is **off** in every suite app, so
  the same screen always looks the same and the accent tells the apps apart.

## 2. Colour

Values are sRGB hex. Contrast ratios follow WCAG 2.x (relative luminance) and were computed for
every pair listed.

### 2.1 Shared neutrals

| Token | Light | Dark | Use | Material 3 slot |
|-------|-------|------|-----|-----------------|
| paper | `#F2F5F2` | `#0F1413` | Window and screen background | `background`, `surface` |
| surface | `#FBFCFB` | `#161C1A` | Panels and cards on the paper | `surfaceContainerLow` |
| surfaceContainer | `#E9EEEA` | `#1A2120` | Bars, neutral tints | `surfaceContainer` (High `#E3E9E5` / `#212927`, Highest `#DDE4DF` / `#28312E`) |
| line | `#D2DAD5` | `#2F3A36` | 1dp borders and dividers (decorative) | `outlineVariant` |
| outline | `#7D8A83` | `#7F8C86` | Text-field and control outlines | `outline` |
| muted | `#56625B` | `#9DABA4` | Secondary text, eyebrows, hints | `onSurfaceVariant` |
| ink | `#1A211D` | `#E3EAE6` | Body text and titles | `onBackground`, `onSurface` |

### 2.2 Semantic colours

| Token | Light | Dark | Use | Material 3 slot |
|-------|-------|------|-----|-----------------|
| warn (rust) | `#9E4A24` | `#EE9C78` | Overdue, unpaid, errors | `error` |
| on warn | `#FFFFFF` | `#3E1A0A` | Text on warn | `onError` |
| warnSoft | `#F7E4DA` | `#3D2419` | Warning banners and chips | `errorContainer` |
| on warnSoft | `#3E1A0A` | `#F7E4DA` | Text on warnSoft | `onErrorContainer` |
| ok (moss) | `#3E6E47` | `#8FC79A` | Paid, done, on track | `tertiary` |
| on ok | `#FFFFFF` | `#0F2A17` | Text on ok | `onTertiary` |
| okSoft | `#DCEBDF` | `#1D3424` | Success chips | `tertiaryContainer` |
| on okSoft | `#15321C` | `#DCEBDF` | Text on okSoft | `onTertiaryContainer` |

### 2.3 Accent (the only per-app values)

| App | Accent light | Accent dark | On accent (light / dark) | accentSoft (light / dark) | On accentSoft (light / dark) |
|-----|--------------|-------------|--------------------------|---------------------------|------------------------------|
| Course Pacer | `#245E60` teal | `#6FB3AE` | `#FFFFFF` / `#0F2E2F` | `#D5EAE8` / `#163C3C` | `#0F2E2F` / `#D5EAE8` |
| Course Invoicer | `#5B3FA0` purple | `#CDBEFF` | `#FFFFFF` / `#2B1B5E` | `#E9E2FA` / `#3B2D6B` | `#2B1B5E` / `#E9E2FA` |

Accent maps to `primary`, on accent to `onPrimary`, accentSoft to `primaryContainer` and
`secondaryContainer` (so the navigation indicator carries the app's tint), and on accentSoft to
`onPrimaryContainer` and `onSecondaryContainer`. `inversePrimary` is the other mode's accent.
`secondary` is muted.

The launcher icon tile uses the **light** accent in both modes (§9).

### 2.4 Contrast

Targets: **4.5:1** for text, **3:1** for meaningful UI parts such as field outlines and icons. The
`line` token is decorative (a panel is also set apart by its surface colour) and is exempt.

| Pair | Light | Dark |
|------|-------|------|
| ink on paper | 14.95 | 15.20 |
| ink on surface | 15.96 | 14.14 |
| ink on surfaceContainerHighest | 12.69 | 10.95 |
| muted on paper | 5.80 | 7.78 |
| muted on surface | 6.20 | 7.24 |
| muted on surfaceContainer | 5.43 | 6.86 |
| muted on surfaceContainerHighest | 4.93 | 5.61 |
| outline on paper (UI) | 3.28 | 5.31 |
| outline on surface (UI) | 3.50 | 4.94 |
| warn on paper | 5.53 | 8.55 |
| warn on surface | 5.90 | 7.95 |
| warn on warnSoft | 4.93 | 6.60 |
| on warn on warn | 6.07 | 7.12 |
| on warnSoft on warnSoft | 12.58 | 11.65 |
| ok on paper | 5.42 | 9.56 |
| ok on surface | 5.79 | 8.89 |
| ok on okSoft | 4.82 | 6.89 |
| on ok on ok | 5.96 | 7.92 |
| on okSoft on okSoft | 11.29 | 10.83 |
| Invoicer accent on paper | 7.18 | 10.98 |
| Invoicer accent on surface | 7.67 | 10.21 |
| Invoicer accent on accentSoft | 6.28 | 6.99 |
| on accent on Invoicer accent | 7.88 | 8.73 |
| on accentSoft on Invoicer accentSoft | 11.77 | 9.43 |
| Pacer accent on paper | 6.72 | 7.73 |
| Pacer accent on surface | 7.18 | 7.19 |
| Pacer accent on accentSoft | 5.89 | 5.00 |
| on accent on Pacer accent | 7.38 | 6.02 |
| on accentSoft on Pacer accentSoft | 11.54 | 9.60 |
| Icon paper `#F2F5F2` on Invoicer tile `#5B3FA0` | 7.18 | (same tile) |
| Icon paper `#F2F5F2` on Pacer tile `#245E60` | 6.72 | (same tile) |

An **empty** home button (§6) is deliberately faded: on accentSoft at 60% over a 30% tint measures
about 3.9:1 in light and 5.6:1 in dark. It still meets 3:1, and its section is empty, so nothing on
it is information the user lacks.

## 3. Type

| Role | Style | Face | Size |
|------|-------|------|------|
| Home welcome | `headlineMedium` | Serif SemiBold | 28sp / 36sp line |
| Screen title (top app bar) | `titleLarge` | Serif SemiBold | 22sp / 28sp |
| Dialog and card titles | `headlineSmall` | Serif SemiBold | 20sp / 28sp |
| Other display, headline and title styles | M3 sizes | Serif SemiBold | M3 defaults |
| Body and labels | M3 defaults | System sans | M3 defaults |
| Money, dates, counts | any style | Monospace | as the surrounding style |

- Serif is `FontFamily.Serif` and monospace is `FontFamily.Monospace`: the system faces, so no font
  files ship.
- Mono is for figures only, never for whole sentences. A label next to a figure stays in sans.
- Text sizes are in sp so they follow the user's font scale. Layouts must survive 200%.

## 4. Shape, spacing, elevation

- **Cards and panels:** 6dp corners and a 1dp `line` border (M3 `medium`, `large`).
- **Chips and text fields:** 4dp corners (M3 `extraSmall`, `small`).
- **Dialogs and sheets:** 12dp (M3 `extraLarge`).
- **Logo tile:** 22dp corners at 88dp (a quarter of its size at any size).
- **Spacing scale:** 4, 8, 12, 16, 24, 40dp. Screen edges are 16dp; space between panels 8 or 12dp;
  between sections 24dp; the space above the home header 40dp.
- **Elevation:** flat. Use no shadow and no tonal elevation; a 1dp line or a container colour
  separates layers. Material components that elevate by default (menus, dialogs) keep their own
  surface colour.

## 5. Motion

- Durations 150–300 ms: about 150 ms for small state changes (a chip, a check), 200–250 ms for
  content that appears or expands, 300 ms at most for a whole screen.
- Standard easing (Material's `FastOutSlowInEasing`). No springs that overshoot, no looping
  animation.
- Respect the system's *Remove animations* setting.

## 6. Components

These are behaviours, not code: each app implements them in its own code.

- **Section.** A group on a screen: an Eyebrow, an optional trailing action (for example *See all*),
  then the group's content, 8dp apart. Sections are 24dp apart.
- **Eyebrow.** The small label above a group: `labelSmall`, SemiBold, 1sp letter spacing,
  uppercase in the user's locale, muted. It is marked as a heading for TalkBack, and TalkBack reads
  it in its normal casing.
- **Panel.** The base of every card: surface colour, 6dp corners, 1dp line border.
- **GuideCard.** A short how-to on a panel: a serif title, one or two lines of muted text, an
  optional text action and an optional dismiss (×) button. It explains, it never blocks.
- **HomeButton.** A full-width tinted button on the home screen: icon, a title, and a **live
  subtitle** that states the current count or next step (for example *3 lessons this week*). The tint
  is a soft container (accentSoft by default; okSoft, warnSoft or neutral when the tone calls for
  it), with 6dp corners and at least 64dp tall. When its section is empty it **dims**: background at
  30%, text and icon at 60%. It still works, so it can lead to adding the first item.
- **MetricCard.** A panel with a muted label over a figure in monospace SemiBold (`titleLarge`). The
  figure may take ok or warn colour when that is the point (paid, overdue).
- **EmptyState.** Shown when a list has nothing yet: a 120dp circular badge in accentSoft holding a
  48dp icon in the accent, a serif title, a one-line muted message, and an optional action button.
  Centred, with 16dp between parts.
- **CommitField.** A text field for a setting or a value that saves itself. It keeps exactly what the
  user typed, commits a value only when it is valid, and waits 400 ms after the last keystroke
  before committing. An invalid entry shows its reason under the field and is never saved.
- **Warning banner.** A full-width warnSoft panel (6dp, no border) with on-warnSoft text, an
  optional leading warn icon and an optional text action. Use it for something the user should act
  on soon (an overdue invoice, a backup that is old). Errors from an action go in a snackbar
  instead.

### Home header

Identical in every suite app, at the top of the home screen:

1. The logo tile, 88dp: the launcher icon's art on its accent tile with 22dp corners.
2. **Welcome to \<App name\>**: serif SemiBold 28sp, ink.
3. *part of EduSuite apps*: lowercase, italic, `bodySmall`, muted.

Centred, with 16dp between the logo and the title and 4dp between the two lines of text.

## 7. Accessibility

- Text contrast at least **4.5:1**; meaningful UI parts (field outlines, icons that carry meaning,
  focus indicators) at least **3:1** (§2.4).
- Touch targets at least **48 × 48dp**, even when the visible control is smaller.
- Every icon that acts has a TalkBack label (the action, such as *Delete lesson*); decorative icons
  and the logo have none.
- Screen titles, section eyebrows and card titles are marked as headings.
- Colour is never the only signal: a warn figure also says *Overdue* or *Unpaid* in words.
- Layouts work at 200% font size and in landscape.

## 8. Copy tone

- Plain and short. Say what happened or what to do: *Lesson saved*, *Add a student first*.
- Sentence case for titles, buttons and labels. Never title case, never all caps (the Eyebrow's caps
  are styling, not text).
- No exclamation marks, no jokes, no blame. Errors say what to change.
- Use the user's words (lesson, student, statement, payment), not database terms.

## 9. Launcher icon

- A **108dp adaptive icon** made of vectors only; no bitmap mipmaps.
- **Background:** a solid tile in the app's **light** accent, in both light and dark mode, so the
  paper-coloured book always stands out.
- **Foreground:** an **open book** drawn in the light paper colour (`#F2F5F2`), two slightly curved
  pages with a thin gap for the spine. The app's glyph is on the **right page**:
  - Course Pacer: three pacing lines in the accent.
  - Course Invoicer: a bold euro sign (€) in the accent.
- Draw the glyph as a **hole** in the page (even-odd fill), not as a second colour. The accent tile
  then shows through it, and the **same vector** serves as the monochrome (themed icon) layer.
- All art stays inside the **66dp safe-zone circle** centred in the 108dp canvas.
- The in-app Logo draws the same foreground at 1.5×, clipped to the 22dp-corner tile, which crops the
  safe-zone margin away.

## 10. Dark theme

- Every token has a dark value (§2). Dark paper is a near-black green, never pure black, and ink is
  off-white, never pure white.
- Accents get lighter and softer in dark mode, and text on them gets dark.
- Soft containers become deep, low-saturation versions of their colour; their text becomes the light
  value.
- The user picks System, Light or Dark in Settings, and the system bars follow that choice. The
  window background shown before the first frame is the paper colour of the system's mode.
- Check every contrast pair again when a dark value changes.

## 11. Dynamic colour

Off in every suite app. Material You's wallpaper colours would replace the accent, and the accent is
what identifies the app. The themes therefore build their colour schemes only from the tokens above.
