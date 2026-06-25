# Planning Context — Yomu Reader Product Roadmap

## Intent
- User wants plans for the product-specific feature set named after deprioritizing generic Android-platform enhancements:
  - Better EPUB reading controls
  - Reader typography/layout polish
  - Table of contents improvements
  - Highlights/notes UX
  - Search inside book
  - Library organization/filtering
  - Tablet reader refinements
  - Import reliability and metadata cleanup
- This is roadmap-level planning, not immediate code changes.

## Decisions
- Treat this as a multi-plan initiative, not one flat plan: the features span reader engine/session, Compose UI, database/domain, import pipeline, and library UX.
- Investigate current implementation before finalizing phases.
- Do not treat EPUB reading controls as a major new feature: the app already has center-tap controls, chapter navigation, Contents, Highlights, Settings, progress, footer, and reader sheets. At most this becomes minor polish after missing reader features land.

## Constraints
- Plan mode only: no product code changes.
- Must respect Yomu custom design system; avoid turning screens into default Material surfaces.
- Readium types must stay behind the reader/data boundary.
- UI work needs a prototype before final plan finalization.

## Open questions
- Confirm whether the user wants an executable initiative split into several implementation plans, or a high-level roadmap only.
- Decide ordering and dependencies after code audit.
- Determine whether visual direction needs explicit approval before finalizing.

## Discarded options
- Implementing Android-platform checklist now: user rejected it as not needed.
- One giant implementation plan: likely too broad and hard to execute/review safely.
- Redesigning EPUB reading controls from scratch: redundant because almost all of the proposed controls already exist.
