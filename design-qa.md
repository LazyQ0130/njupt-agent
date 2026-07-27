# Design QA

## Evidence

- Source visual truth: `C:\Users\QYF\AppData\Local\Temp\codex-clipboard-4e778aab-38de-4e3e-a2c4-e33b052bd074.png`
- Desktop implementation: `C:\Users\QYF\Desktop\njupt agent\qa-home-desktop-final.png`
- Combined comparison: `C:\Users\QYF\Desktop\njupt agent\qa-home-comparison.png`
- Supporting routes:
  - `C:\Users\QYF\Desktop\njupt agent\qa-chat-desktop.png`
  - `C:\Users\QYF\Desktop\njupt agent\qa-knowledge-desktop.png`
  - `C:\Users\QYF\Desktop\njupt agent\qa-admin-desktop.png`
- Responsive evidence:
  - `C:\Users\QYF\Desktop\njupt agent\qa-home-mobile-final.png`
  - `C:\Users\QYF\Desktop\njupt agent\qa-chat-mobile.png`
  - `C:\Users\QYF\Desktop\njupt agent\qa-admin-mobile-final.png`

## Comparison Setup

- Desktop viewport: 1680 × 945 CSS px.
- Source pixels: 1680 × 945.
- Desktop implementation pixels: 1680 × 945.
- Device scale factor: 1.
- Density normalization: none required.
- Mobile viewport: 390 × 844 CSS px.
- State: light theme; homepage default state; chat route includes one user question and a completed AI answer.

## Full-view Comparison

The combined comparison confirms the implementation retains the source's core design language: blue-white education product palette, dark navy display typography, spacious hero, luminous AI composer, glass-like cards, rounded corners, soft elevation, category color accents, source-backed AI responses and document-management surfaces.

The source is a presentation collage showing three interfaces at once. The implementation intentionally turns those panels into independent, usable routes, so the desktop homepage uses the full viewport and continues below the fold instead of reproducing the collage frame and tilted side panels.

## Focused Region Comparison

The hero, navigation and AI composer were inspected at 1:1 size in the combined comparison. Typography hierarchy, input anatomy, prompt suggestions, icon treatment, focus glow, border radius and blue-white balance remain consistent with the reference. Route screenshots were inspected separately because the source presents chat and admin at a reduced, angled scale that is unsuitable for direct 1:1 UI comparison.

## Required Fidelity Surfaces

- Fonts and typography: system Chinese sans-serif stack provides appropriate weight and legibility; display and body hierarchy is consistent across routes.
- Spacing and layout rhythm: desktop spacing is intentionally more open than the collage; card grids, chat evidence panels and admin tables maintain stable rhythm.
- Colors and tokens: NJUPT blue `#003B70`, technology blue `#2563EB`, pale blue `#EFF6FF` and white are used consistently.
- Image quality and asset fidelity: custom campus architectural line art is a real raster asset generated for the footer; no placeholder imagery or handcrafted SVG illustration is used.
- Copy and content: all visible product copy is campus-specific and coherent; no lorem ipsum or generic template copy remains.
- Icons: Lucide icons are used consistently with matched stroke weight and semantic meaning.
- Accessibility: semantic headings, labels, alt text, keyboard-operable controls, visible focus states and reduced-motion handling are present.

## Comparison History

### Iteration 1

- [P2] Mobile home prompt suggestions showed a native horizontal scrollbar that visually competed with the glass composer.
- [P2] The admin table compressed all columns at 390 px, causing narrow Chinese text to wrap vertically.

Fixes:

- Hidden the native scrollbar while preserving touch horizontal scrolling.
- Applied a stable 720 px table width inside a horizontally scrollable container on narrow screens.

Post-fix evidence:

- `qa-home-mobile-final.png`
- `qa-admin-mobile-final.png`
- Admin table measurement: 347 px visible container, 720 px scrollable table, no page-level horizontal overflow.

## Interaction and Runtime Checks

- Home query submission navigates to `/chat` and returns a source-backed simulated response.
- Knowledge-base search reaches a designed empty state.
- Admin file chooser accepts a synthetic PDF, adds a table row and shows a parsing toast.
- Mobile and desktop routes have no page-level horizontal overflow.
- Browser console checked after primary flows: no errors or warnings.
- TypeScript check, production build and static-site tests passed.

## Findings

No actionable P0, P1 or P2 issues remain.

## Follow-up Polish

- [P3] If official NJUPT brand assets become available, replace the generic landmark brand mark with the approved university/assistant logo.
- [P3] When real backend data arrives, verify long department names and unusually long filenames against the current truncation rules.

final result: passed

## Phase 6 Product QA

- Verified at 390 × 844 CSS px: `/`, `/chat`, `/admin`,
  `/admin/crawler`, and `/admin/quality` all have no page-level horizontal
  overflow.
- Completed two messages in one server conversation; the stored role sequence
  is `USER, ASSISTANT, USER, ASSISTANT`.
- Submitted a helpful vote from the answer card and confirmed the quality
  dashboard updated to one feedback and 100% helpful rate.
- Ran the 50-question evaluation from `/admin/quality`; the local mock-provider
  smoke report completed with all 50 results and displayed category scores.
- Confirmed homepage recommended questions navigate directly to `/chat`.
- Browser console contained no errors or warnings after the Phase 6 flows.

Phase 6 final result: passed
