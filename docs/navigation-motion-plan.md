# Navigation and motion plan

This is the interaction contract for the Material 3 Expressive redesign. It keeps the reader calm
while making navigation state visible and predictable.

## Back behavior

Back is resolved from the innermost active surface outward:

| Priority | Active state | Back result | Back handling |
| --- | --- | --- | --- |
| 1 | IME, modal bottom sheet, dialog | Dismiss the system-owned surface | AndroidX / Material component |
| 2 | Library or book-details multi-select | Exit selection and restore the normal chrome | Feature-local `BackHandler` |
| 3 | Library search | Close search and clear the transient query | `LibraryScreen` `BackHandler` |
| 4 | Child route (reader, details, editor, settings sub-screen) | Return to the previous Navigation Compose entry | `NavHost` ordinary back transition |
| 5 | Top-level root (Library) | Leave the task / show Android's back-to-home animation | Android system |

The app must not intercept back in `MainActivity` or with key events. Local handlers stay enabled via
observable UI state and are composed unconditionally. Predictive back is intentionally disabled for
now; ordinary back remains available and can be revisited when gesture work resumes.

## Motion vocabulary

- **Splash → Library:** the AndroidX splash view fades away over 220 ms while the mark receives a
  restrained 1.08× emphasis scale. The first library frame is already behind the splash, so there is
  no blank or second loading surface.
- **Screen transition:** a fast shared-axis X handoff over 300 ms. Both surfaces stay overlapped: the
  incoming surface travels five percent of the viewport and fades in while the current surface moves
  the same short distance in the opposite direction and fades out. The 195 ms fade and restrained
  travel keep this reading as a screen transition rather than a tab carousel. Top-level destinations
  follow the Library → Statistics → Settings order, so moving toward an earlier destination mirrors
  the motion. The adaptive navigation bar/rail stays in the stable scaffold and expands or shrinks
  with child-route handoffs instead of disappearing before the screen moves. Ordinary back reverses
  the same motion without tracking gesture progress.
- **In-library content:** directional content swap for grid/list and settled empty states; the
  loading-to-content handoff uses a fade-through so the first library frame does not enter from an
  edge after the splash.
- **Chrome and popups:** `YomuMotion` edge-anchored and popup helpers. Native Material 3
  components keep their library-provided motion, while custom surfaces share this same cadence
  through `YomuMotion` (the pinned Material 3 artifact still keeps `MotionScheme` internal).
- **Selection/back:** selection bars use the shared chrome enter/exit motion and close through the
  ordinary back callback.

All custom motion checks the system animator setting. When reduced motion is enabled, transitions
resolve to `None`; layout, focus, and state changes still occur immediately.

## Delivery sequence

1. Keep this state-priority contract and the splash exit animation stable.
2. Verify ordinary route back on a small device matrix, including a root back-to-home gesture.
3. Revisit progress-driven previews only when gesture support is intentionally reintroduced.
4. Add shared-element cover→details motion only after the base back contract is verified; it must be
   cancellable and must not delay reader entry.

## Acceptance checks

- Cold launch never flashes a mismatched window color or an empty library frame.
- Search back closes search before the activity or route is popped.
- Selection back exits selection without changing route state.
- Details/editor/reader back pops exactly one route and reverses the destination transition.
- All screen changes use the same shared-axis horizontal transition; back mirrors its direction.
- Reduced motion disables all custom spatial/effect transitions without disabling navigation.
