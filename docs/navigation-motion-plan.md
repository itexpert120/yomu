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
observable UI state and are composed unconditionally.

Predictive back is enabled (`android:enableOnBackInvokedCallback="true"`), so every surface follows
the back swipe and settles back if the swipe is cancelled:

| Surface | What the gesture does | Where |
| --- | --- | --- |
| Child route (details, editor, reader, settings sub-screens) | A One UI-style page slide: the screen slides its full width toward the swipe at full size and opaque, casting a soft shadow from its leading edge; the previous screen follows from 30% of the width to the side (parallax), starting slightly dimmed. Both slides are linear in transition progress, so the page stays locked to the back progress. | Root `NavHost` pop transitions (`yomuScreenPopEnter/Exit`) + `BackPreviewFrame` |
| Top-level tab back to Library | The shared-axis handoff is scrubbed by the swipe. | Nested `NavHost` |
| Library root | Android's back-to-home preview. | System |
| Phone bottom sheets | Material's built-in sheet preview. | `ModalBottomSheet` |
| Tablet sheet dialogs, `YomuDialog`, cover viewer | The panel shrinks with the swipe, then plays its normal exit. | `YomuBackPreviewHandler` + `Modifier.yomuBackPreview` |
| Selection / search | No preview; the mode closes when the gesture commits. | Feature `BackHandler` |

The button and the gesture share one back motion, so the same transition plays timed or scrubbed.
While the reader hides the system bars, Android itself uses the first edge swipe to reveal them and
the second to go back.

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
  with child-route handoffs instead of disappearing before the screen moves. Back between tabs
  reverses the same motion; back out of a child route uses the parallax page slide described under
  "Back behavior".
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
3. Progress-driven back previews are in place; re-check them on a tablet (dialog sheets) and on
   physical devices.
4. Add shared-element cover→details motion only after the base back contract is verified; it must be
   cancellable and must not delay reader entry.

## Acceptance checks

- Cold launch never flashes a mismatched window color or an empty library frame.
- Search back closes search before the activity or route is popped.
- Selection back exits selection without changing route state.
- Details/editor/reader back pops exactly one route and reverses the destination transition.
- Forward screen changes and tab changes use the shared-axis horizontal transition; back out of a
  child route shrinks the leaving screen away and follows the gesture.
- A cancelled back swipe returns every surface to rest with no state change.
- Reduced motion disables all custom spatial/effect transitions without disabling navigation.
