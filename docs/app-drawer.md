# How the App Drawer Works

A practical map of the Lawnchair app drawer (all-apps) on this fork, written after a
debugging session that fixed the immersive background, a search-bar whitespace gap, a
"miniature drawer" bug, the two-tap search keyboard, and live icon-shape reloads.

It focuses on the things that were **non-obvious and cost real time to discover** — the kind of
detail you can't get from reading one file.

---

## 1. The view hierarchy

When the launcher window is laid out, all-apps lives inside the DragLayer:

```
LauncherRootView            (full screen, edge-to-edge)
└─ DragLayer                (0,0 – W,H)
   ├─ Workspace / Hotseat   (the home screen, behind everything)
   ├─ LawnchairScrimView    id=scrim_view   (0,0 – W,H, full screen)
   └─ SearchContainerView   id=apps_view    (0,0 – W,H, full screen)
      ├─ bottom_sheet_background   (the visible dark drawer surface — see §3)
      ├─ search_container_all_apps (AllAppsSearchInput — the search pill)
      ├─ all_apps_header           (FloatingHeaderView: tabs / A-Z)
      ├─ AllAppsRecyclerView       (the icon grid)
      └─ fast_scroller
```

Key classes:

| Class | Role |
|---|---|
| `SearchContainerView` (Lawnchair) | the `apps_view`; extends `LauncherAllAppsContainerView` → `ActivityAllAppsContainerView` (AOSP) |
| `AllAppsTransitionController` | drives open/close: translation, scale, alpha, scrim |
| `LawnchairScrimView` / `ScrimView` | the flat scrim **and** the canvas the drawer surface is drawn onto |
| `AllAppsSearchInput` (Lawnchair) | the search pill; `input` is a `FallbackSearchInputView : ExtendedEditText` |
| `LawnchairThemeManager` | owns the current icon "shape/theme" state |

> **Verify geometry on a real device** with
> `adb shell dumpsys activity top | grep -E "scrim_view|apps_view|bottom_sheet_background"`.
> The bounds it prints (e.g. `0,107-1080,2412`) are ground truth and settle arguments fast.

---

## 2. The window is edge-to-edge

The launcher window is full screen: frame `[0,0,W,H]`, flags include `EDGE_TO_EDGE_ENFORCED`
and `DRAWS_SYSTEM_BAR_BACKGROUNDS`. On Android 15 / `targetSdk 37` the old
`SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN` (still set in `StatefulActivity`) is **deprecated and
ignored** — edge-to-edge is enforced automatically, and `Window.statusBarColor` is largely
ignored too. So both `scrim_view` and `apps_view` already start at `y=0`, *behind* a
transparent status bar. Whether the drawer *looks* immersive is therefore not about geometry —
it's about **what gets painted in the status-bar band**.

---

## 3. Breakthrough: the dark drawer surface is drawn, not a view background

This was the big one. The visible dark drawer is **not** the scrim's background color and
**not** a background on `apps_view`. On a phone large enough that
`DeviceProfile.shouldShowAllAppsOnSheet()` is **true** (this device, sw424dp), the drawer is a
"sheet":

- `ActivityAllAppsContainerView.drawOnScrimWithScaleAndBottomOffset()` paints the dark surface
  **onto the scrim canvas**, using the **bounds of `R.id.bottom_sheet_background`** — a
  transparent `match_parent` FrameLayout that exists only as a position/size anchor.
- Because that panel is `match_parent`, it inherits `apps_view`'s `paddingTop`. With
  `paddingTop = statusBarHeight`, the panel (and thus the painted surface) started at the bottom
  of the status bar → the drawer stopped at the status bar.

**Fix:** anchor the panel to `y=0` (it now does, because `apps_view` paddingTop is `0`, see §4),
so the painted surface fills behind the status bar.

Two traps that wasted time here:

- `AllAppsState.getWorkspaceScrimColor` (and `ColorTokens.AllAppsScrimColor = #404040 @ 40%`)
  only matters in the **non-sheet** path. Recoloring it on a sheet device does **nothing**.
- The comment *"Always use an opaque scrim if there's no sheet"* is misleading — the token is
  40% alpha.

---

## 4. Insets, padding, and the search-bar whitespace

`ActivityAllAppsContainerView.setInsets` sets `apps_view` padding to `allAppsPadding.top`.
`InsettableFrameLayout` distributes insets to children but **does not** add margins to
`Insettable` children (it calls their `setInsets` instead).

The search pill positions itself in `AllAppsSearchInput.setInsets`:

```
topMargin = max(-verticalOffset, insets.top - qsbMarginTopAdjusting) + drawerHandleAreaPx
```

That **already includes `insets.top`**. So forcing `apps_view` `paddingTop = insets.top` on top
of it **double-counted the status-bar height** (~42dp) and produced a big empty band above the
search box. Fix: keep `apps_view` `paddingTop = 0`; the search bar and the grid
(`layout_below` the search bar) inset themselves.

> Rule of thumb: in this drawer, **content positions itself**; don't also pad the container.

---

## 5. Opening / closing: the transition controller

`AllAppsTransitionController` is the heart of the motion:

- `setProgress(p)` — `p` is the vertical progress: **`0` = fully open (AllAppsState)**,
  **`1` = closed (NORMAL)**. It translates the apps view by `p * shiftRange` and updates the
  scrim.
- `mAllAppScale` / `SCALE_PROPERTY` — scales the **whole apps view** around its center; used by
  the predictive-back gesture.
- `createSpringAnimation(...)` — the `scaleBounce` Beach Mod. Builds the open animation and, when
  enabled, bounces the apps view scale `0.85 → 1.08 → 0.97 → 1.0`.

### Breakthrough: the "miniature drawer" bug

`createSpringAnimation` used to call `appsView.setScaleX/Y(0.85)` **immediately** and on **every**
transition (including closing), trusting the animation to finish and restore `1.0`. If it was
interrupted — e.g. you grab the drawer mid-open and drag it down — the apps view stayed stuck at
a fractional scale. Because the scale pivots on the view **center**, the bottom (tab bar) floats
up to mid-screen and everything shrinks: the "miniature drawer."

**Fix:** only bounce when **opening** (target progress ≈ 0); on any non-opening transition force
scale back to `1.0`; and add cancel/end listeners that always restore `1.0`. A stuck state now
self-heals the moment you drag down.

---

## 6. Icon animations

- **Open bounce** (`iconBounce`, in `LawnchairLauncher`): on the AllAppsState transition, visible
  icons start at scale `0.85` and spring back to `1.0` with an `index * 18ms` stagger — a wave
  from top to bottom. Uses `physicsAnimator`.
- **Scroll wave** (`iconScrollWave`, `IconScrollWave.kt`, added this session): a
  `RecyclerView.OnScrollListener` applies a travelling sine wave to icon scale. Amplitude tracks
  scroll velocity, the phase travels in the scroll direction (so the crest sweeps forwards /
  backwards), and it springs back to flat on idle. Installs idempotently on every list in the
  drawer (covers drawer pages). Tunables at the top of the file: `MAX_AMPLITUDE`,
  `WAVELENGTH_ROWS`, `TRAVEL_FACTOR`.
- **Scale bounce** (`scaleBounce`): the whole-drawer bounce in §5.

All three are toggles under **Settings → Beach Mods → Animations**.

---

## 7. The search bar

`AllAppsSearchInput` wraps a `FallbackSearchInputView` (`input`). `ExtendedEditText.showKeyboard()`
= `requestFocus()` + `imm.showSoftInput(...)`.

### Breakthrough: the two-tap keyboard

The focus listener updated hint/background/zero-state results when the field gained focus, but
**never asked for the IME**. The framework doesn't reliably auto-show the keyboard on the first
focus inside the launcher window, so the first tap focused and the second tap is what popped the
keyboard. Fix: on focus, `post { input.showKeyboard() }`.

---

## 8. Icon theming & shape reloads

Icons are generated using a "shape/theme" state owned by `ThemeManager`. The base AOSP
`ThemeManager` only reacts to **`LauncherPrefs`** keys, but Lawnchair stores icon/folder shape in
its **DataStore**, so `LawnchairThemeManager` subscribes to those flows and rebuilds
`iconState` in `verifyIconState()`.

### Breakthrough: shape changes that "did nothing"

Updating `iconState` alone doesn't refresh icons already drawn on screen — those come from the
launcher **model/icon cache**, which must be reloaded. The shape prefs' `onSet` called
`ReloadHelper.reloadIcons()` as a **separate, unordered** reload that frequently ran **before**
`verifyIconState()` committed the new shape — reloading with the stale shape. Net effect: shape
changes appeared to do nothing until a restart.

**Fix:** drive the model reload from **inside** `verifyIconState()` (after the new state is
applied), and remove the racy `onSet` reloads. `ReloadHelper.reloadIcons()` is now only for plain
non-theme reloads.

---

## Debugging playbook (what actually worked)

1. **Get ground truth before theorizing.** `adb shell dumpsys activity top` for view bounds;
   `dumpsys window windows` for the window frame/flags.
2. **Sample pixels** from a screenshot to settle "is it covering / what colour is it" questions
   instead of reasoning about it (status bar `(31,60,96)` vs body `(35,36,36)` proved the seam).
3. **Change a colour to something garish** to confirm which surface you're actually looking at —
   recoloring the scrim did *nothing*, which is what revealed the sheet-panel mechanism.
4. **Suspect interrupted animations** for "stuck" visual states — look for side effects applied
   at animation *creation* that rely on completion to undo.
5. **Watch for double application** of insets/padding when both a container and its children
   inset themselves.
