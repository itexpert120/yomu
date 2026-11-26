foliate-js (MIT), EPUB-only subset.
Source: https://github.com/johnfactotum/foliate-js @ 78914ae (2026-05-01).
Unmodified upstream files except where noted in yomu-*.js, and paginator.js's scrolled
expand(), which also measures content overflowing a zero-height box and its touch-end snap, which now runs only
after a drag, a touch that moves less than 8px not counting as a drag, and horizontal paged layout, whose
pages scroll edge to edge across the whole width with the side margins as column gaps (all marked
"Open Reader:"). PDF, MOBI, FB2, CBZ, OPDS and the demo UI are left out.
