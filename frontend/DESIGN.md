---
name: AdvisorConnect
description: A senior advisor's private office, rendered in software — ledger, ink, and paper, no gloss.
colors:
  oxblood:        "#8b173d"
  oxblood-deep:   "#6a072b"
  pine:           "#005423"
  pine-pale:      "#dff0e4"
  danger:         "#b00727"
  danger-pale:    "#ffe2de"
  warn-pale:      "#fbd094"
  warn-ink:       "#703f0e"
  bg:             "#ffffff"
  surface:        "#f9f4f3"
  border:         "#ddd6d4"
  ink:            "#120b0a"
  muted:          "#5d5655"
typography:
  display:
    fontFamily: "Source Serif 4, Georgia, serif"
    fontSize: "clamp(2rem, 4vw, 3.25rem)"
    fontWeight: 500
    lineHeight: 1.05
    letterSpacing: "-0.02em"
  headline:
    fontFamily: "Source Serif 4, Georgia, serif"
    fontSize: "1.875rem"
    fontWeight: 500
    lineHeight: 1.15
    letterSpacing: "-0.01em"
  title:
    fontFamily: "Source Serif 4, Georgia, serif"
    fontSize: "1.25rem"
    fontWeight: 500
    lineHeight: 1.25
  body:
    fontFamily: "Inter, system-ui, sans-serif"
    fontSize: "0.9375rem"
    fontWeight: 400
    lineHeight: 1.5
  label:
    fontFamily: "Inter, system-ui, sans-serif"
    fontSize: "0.75rem"
    fontWeight: 600
    letterSpacing: "0.02em"
rounded:
  sm: "6px"
  md: "8px"
  lg: "12px"
  pill: "999px"
spacing:
  xs: "4px"
  sm: "8px"
  md: "16px"
  lg: "24px"
  xl: "40px"
components:
  button-primary:
    backgroundColor: "{colors.oxblood}"
    textColor: "#ffffff"
    rounded: "{rounded.md}"
    padding: "10px 20px"
  button-primary-hover:
    backgroundColor: "{colors.oxblood-deep}"
  button-outline:
    backgroundColor: "transparent"
    textColor: "{colors.ink}"
    rounded: "{rounded.md}"
    padding: "10px 20px"
  badge-verified:
    backgroundColor: "{colors.pine-pale}"
    textColor: "{colors.pine}"
    rounded: "{rounded.pill}"
    padding: "2px 10px"
  badge-pending:
    backgroundColor: "{colors.warn-pale}"
    textColor: "{colors.warn-ink}"
    rounded: "{rounded.pill}"
    padding: "2px 10px"
  card:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    rounded: "{rounded.lg}"
    padding: "20px"
  input:
    backgroundColor: "{colors.bg}"
    textColor: "{colors.ink}"
    rounded: "{rounded.sm}"
    padding: "10px 14px"
---

# Design System: AdvisorConnect

## 1. Overview

**Creative North Star: "The Advisor's Ledger"**

AdvisorConnect should feel like sitting across from a senior advisor who has done this a thousand times and isn't trying to sell you anything: a private office with a ledger, ink on cotton paper, brass fittings, no gloss. Confidence is expressed through restraint and precision, not enthusiasm — the interface never manufactures urgency, never gamifies a client's booking decision, never dresses up a status as more exciting than it is.

This system explicitly rejects the generic navy + sky-blue + orange-CTA "SaaS fintech" look the codebase shipped with, the warm cream/beige AI-generated default, the black-and-gold luxury-private-banking cliché, and growth-hacky consumer patterns (countdown urgency, gradient hero text, confetti on financial decisions). None of those fit a platform whose entire premise is vetted expertise.

Density is moderate-to-high: this is a working tool for two sides of a marketplace (clients booking, advisors running a practice, admins triaging applications), not a marketing showcase. The landing page is the one page allowed a lighter, larger-type treatment; every other surface prioritizes scannability of status and content over presentation.

**Key Characteristics:**
- Paper-white and near-black ink carry the "exacting" read; oxblood and pine are used sparingly and mean something when they appear.
- Borders do the structural work; shadows are reserved for things that actually float (modals, dropdowns, toasts).
- Serif headlines (gravitas, permanence) over a neutral grotesque body (Inter, already legible at UI density).
- Status must be readable at a glance — this is a design of record, not a design of persuasion.

## 2. Colors

Two brand colors on a paper-white ground: oxblood carries every primary action and identity moment, pine is reserved entirely for verified/success states so it keeps its meaning.

### Primary
- **Oxblood** (`#8b173d` / `oklch(0.42 0.15 8)`): primary buttons, links, active nav state, focus rings, the wordmark, chat bubbles for the current user's own messages. This is the one color allowed to appear as a large fill; everywhere else it's text, a border, or an icon.
- **Oxblood Deep** (`#6a072b`): hover/active state for oxblood fills only. Never used as a standalone color.

### Secondary
- **Deep Pine** (`#005423` / `oklch(0.38 0.12 155)`): verified advisor badges, approved/confirmed status, booking-confirmed states, the success toast. Chosen for hue and lightness contrast against oxblood so the two are never confused at a glance.

### Neutral
- **Paper White** (`#ffffff`): page background, the base the whole system sits on. Never tinted — the mood lives in oxblood and pine, not in the ground.
- **Surface** (`#f9f4f3` / `oklch(0.97 0.006 30)`): cards, panels, the app shell's raised sections. Barely-tinted warm gray, not cream — chroma is kept low enough that it reads as paper, not sand.
- **Hairline Border** (`#ddd6d4`): 1px dividers, card edges, input borders at rest. Does the job shadows do in most SaaS UI.
- **Ink** (`#120b0a` / `oklch(0.16 0.012 30)`): all body text and headings. Warm near-black, not a cold gray-black — pairs with the ledger metaphor.
- **Muted** (`#5d5655`): secondary text, timestamps, placeholder copy, helper text under inputs. Still meets 4.5:1 against paper white — this is not the pale illegible gray the system explicitly avoids.

### Semantic
- **Danger** (`#b00727`): errors, destructive actions, rejected status. Deliberately a different hue/lightness combination than Oxblood (brighter, more orange-leaning red vs. oxblood's dark wine) so a primary button and an error state are never visually interchangeable.
- **Warn / Pending** (`#fbd094` fill, `#703f0e` text): pending/under-review status only. Pale fill with dark text, matching the existing pending-badge convention in the codebase, just re-tokened.

### Named Rules
**The One Fill Rule.** Oxblood and Pine each appear as a solid fill in exactly one context class (buttons/active-state for oxblood; verified/confirmed badges and the confirmation toast for pine). Everywhere else they show up as text, border, or icon color. If a third saturated fill starts appearing, it's being used decoratively — stop and reconsider.

## 3. Typography

**Display Font:** Source Serif 4 (with Georgia, serif fallback)
**Body Font:** Inter (with system-ui, sans-serif fallback)

**Character:** A ledger-and-signature pairing. The serif carries gravitas and permanence on headlines and section titles; Inter's neutral grotesque keeps every dense list, form, and status row readable at UI sizes. Contrast comes from the serif/sans axis, not from two similar geometric sans faces (the previous Poppins/Inter pairing).

### Hierarchy
- **Display** (500, `clamp(2rem, 4vw, 3.25rem)`, 1.05, `-0.02em`): the landing page hero headline only. Nowhere else.
- **Headline** (500, 1.875rem, 1.15): page-level titles — "Explore Advisors," a dashboard's top heading.
- **Title** (500, 1.25rem, 1.25): card and section titles — an advisor's name on their card, a modal title, a dashboard panel header.
- **Body** (400, 0.9375rem, 1.5): all paragraph and UI copy. Cap prose blocks (advisor bios, onboarding copy) at 65–75ch.
- **Label** (600, 0.75rem, `0.02em`, uppercase only for genuine table/column headers — never as a decorative eyebrow above every section): form labels, table headers, small metadata tags.

### Named Rules
**The Serif-Never-Shouts Rule.** Source Serif 4 is used at 500 weight, never bold 700+, and never above the display clamp ceiling (3.25rem). Gravitas comes from the typeface choice itself, not from size or weight escalation.

## 4. Elevation

Flat by default, border-first. Cards, panels, and list rows sit at rest with a 1px hairline border and the surface fill — no ambient drop shadow. Shadows are reserved as a structural signal that something has left the document flow: modals, dropdown menus, popovers, and toasts. This is a deliberate reversal of the previous shadow-on-every-card pattern, which made every panel look "lifted" and gave the interface no way to signal genuine overlays.

### Shadow Vocabulary
- **Overlay** (`box-shadow: 0 12px 32px -8px rgba(18, 11, 10, 0.18)`): modals, dropdown menus, popovers, the mobile nav sheet.
- **Toast** (`box-shadow: 0 8px 20px -4px rgba(18, 11, 10, 0.14)`): confirmation/error toasts only.

### Named Rules
**The Flat-By-Default Rule.** Surfaces are flat at rest. A shadow appearing on anything still inside normal document flow (a card, a table row, a sidebar) is a bug, not a style choice.

## 5. Components

### Buttons
- **Shape:** `rounded: 8px` ({rounded.md}) — tighter than the previous 12px soft-bubble radius.
- **Primary:** Oxblood fill (`#8b173d`), white text, `10px 20px` padding. Hover → Oxblood Deep (`#6a072b`).
- **Secondary/Verified action:** Pine fill (`#005423`), white text — used only for confirm/approve actions (e.g., "Confirm Booking," "Approve Application").
- **Outline:** transparent fill, 1px hairline border, ink text; hover → surface fill + oxblood border.
- **Ghost:** transparent, muted text; hover → surface fill, ink text.
- **Danger:** transparent or pale-danger fill depending on emphasis; hover → solid danger fill, white text.
- **Focus:** 2px oxblood ring, 2px offset, on every variant — no exceptions.

### Chips / Badges
- **Style:** pill radius (`999px`), pale-fill + saturated-text pairing (matches the existing codebase convention, re-tokened): pine-pale/pine for verified & approved, warn-pale/warn-ink for pending, danger-pale/danger for rejected.
- **State:** static/informational only — badges are never interactive in this system.

### Cards / Containers
- **Corner Style:** `rounded: 12px` ({rounded.lg}).
- **Background:** Surface (`#f9f4f3`), not paper-white — this is what separates a card from the page underneath it, since shadows no longer do that job.
- **Shadow Strategy:** none at rest (see Elevation). A card that needs to signal "actionable" uses a border-color shift to oxblood on hover, not a shadow.
- **Border:** 1px hairline (`#ddd6d4`) always.
- **Internal Padding:** 20px standard, 16px for dense list-style cards (advisor cards in a grid).

### Inputs / Fields
- **Style:** paper-white background, 1px hairline border, `rounded: 6px` ({rounded.sm}).
- **Focus:** border shifts to ink at 60% opacity + 2px oxblood focus ring (replacing the previous thick blue ring).
- **Error:** border and helper text switch to danger; **Disabled:** surface fill, muted text, no border-color change on hover.
- **Placeholder:** muted (`#5d5655`), which holds 4.5:1 against paper-white — never the paler default gray.

### Navigation
- **Style:** paper-white background, 1px bottom hairline border, no shadow. Logo mark is an oxblood icon chip; nav links are muted, hover → ink; the active/primary CTA is the standard primary button.
- **Mobile:** links collapse into a sheet that uses the Overlay shadow token since it now sits outside document flow.

### Chat Bubbles (signature component)
User's own messages: oxblood fill, white text, tail corner sharp (`18px 18px 4px 18px`). The advisor's messages: surface fill, ink text, 1px hairline border, mirrored tail. This is the one place oxblood appears as a repeated fill across many instances, which is acceptable because it's functionally a status indicator (mine vs. theirs), not decoration.

## 6. Do's and Don'ts

### Do:
- **Do** use Oxblood as a solid fill only for primary buttons, active nav state, and the user's own chat bubbles — nowhere else as decoration.
- **Do** use Pine exclusively for verified/approved/confirmed states so it keeps a single, learnable meaning.
- **Do** keep body text at Ink (`#120b0a`) or Muted (`#5d5655`) — never a paler gray, even for "secondary" copy.
- **Do** use 1px hairline borders as the default way to separate cards, rows, and sections from the page.
- **Do** reserve shadows for modals, dropdowns, popovers, and toasts only.

### Don't:
- **Don't** revive the previous navy/sky-blue/orange-CTA palette, or anything that reads as generic SaaS-fintech-blue.
- **Don't** use a warm cream/beige/sand background — the ground stays paper-white; warmth lives in Oxblood and Pine.
- **Don't** reach for a black-and-gold luxury-private-banking treatment (serif wordmarks in gold, velvet-rope styling) — this is a working tool, not a status symbol.
- **Don't** add countdown urgency, gradient hero text, confetti, or other gamification to booking or financial-decision flows.
- **Don't** put a drop shadow on anything still inside normal document flow (cards, table rows, sidebars) — that job belongs to the 1px hairline border now.
- **Don't** use Danger red and Oxblood interchangeably — they're deliberately different lightness/chroma so a primary action and an error never look alike.
