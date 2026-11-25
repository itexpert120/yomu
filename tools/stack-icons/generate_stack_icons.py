"""Generates StackIcons.kt from Material Symbols Outlined SVGs (Apache-2.0).

Usage:
    python -I tools/stack-icons/generate_stack_icons.py <svg-dir> [--fetch]

--fetch downloads any missing SVG from fonts.gstatic.com into <svg-dir> (keep it outside the repo).
Only the viewBox and each path's `d` attribute are read from the SVGs; everything else is ignored.
To add an icon, append it to ICONS and rerun.
"""

import os
import re
import sys
import urllib.request

BASE = "https://fonts.gstatic.com/s/i/short-term/release/materialsymbolsoutlined"
OUT = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "..", "..", "app", "src", "main", "java", "com", "itexpert120", "yomu",
    "core", "designsystem", "icons", "StackIcons.kt",
)

OUTLINED = "wght300"  # FILL 0, weight 300, grade 0, optical size 24
FILLED = "fill1"  # FILL 1, weight 400: the active nav item and the play/resume button
FILLED_300 = "wght300fill1"  # FILL 1, weight 300: on-state of a toggle whose off-state is OUTLINED

# (StackIcons property, Material Symbols name, variant, autoMirror)
ICONS = [
    ("Add", "add", OUTLINED, False),
    ("ArrowBack", "arrow_back", OUTLINED, True),
    ("AutoAwesome", "auto_awesome", OUTLINED, False),
    ("AutoMode", "auto_mode", OUTLINED, False),
    ("AutoStories", "auto_stories", OUTLINED, False),
    ("Balance", "balance", OUTLINED, False),
    ("BatteryFull", "battery_full", OUTLINED, False),
    ("Bolt", "bolt", OUTLINED, False),
    ("Book", "book", OUTLINED, False),
    ("Bookmark", "bookmark", OUTLINED, False),
    ("BookmarkFilled", "bookmark", FILLED_300, False),
    ("BorderColor", "border_color", OUTLINED, False),
    ("BrightnessAuto", "brightness_auto", OUTLINED, False),
    ("BrightnessHigh", "brightness_high", OUTLINED, False),
    ("BrightnessLow", "brightness_low", OUTLINED, False),
    ("CalendarMonth", "calendar_month", OUTLINED, False),
    ("CallToAction", "call_to_action", OUTLINED, False),
    ("Check", "check", OUTLINED, False),
    ("CheckCircle", "check_circle", OUTLINED, False),
    ("Close", "close", OUTLINED, False),
    ("CloudDone", "cloud_done", OUTLINED, False),
    ("CloudOff", "cloud_off", OUTLINED, False),
    ("CloudSync", "cloud_sync", OUTLINED, False),
    ("CollectionsBookmark", "collections_bookmark", OUTLINED, False),
    ("Colorize", "colorize", OUTLINED, False),
    ("ContentPaste", "content_paste", OUTLINED, False),
    ("DarkMode", "dark_mode", OUTLINED, False),
    ("DateRange", "date_range", OUTLINED, False),
    ("Delete", "delete", OUTLINED, False),
    ("DeleteForever", "delete_forever", OUTLINED, False),
    ("Deselect", "deselect", OUTLINED, False),
    ("DoNotDisturbOn", "do_not_disturb_on", OUTLINED, False),
    ("DonutLarge", "donut_large", OUTLINED, False),
    ("Download", "download", OUTLINED, False),
    ("Edit", "edit", OUTLINED, False),
    ("Error", "error", OUTLINED, False),
    ("ExpandLess", "expand_less", OUTLINED, False),
    ("ExpandMore", "expand_more", OUTLINED, False),
    ("FilterBAndW", "filter_b_and_w", OUTLINED, False),
    ("FormatAlignJustify", "format_align_justify", OUTLINED, False),
    ("FormatAlignLeft", "format_align_left", OUTLINED, True),
    ("Fullscreen", "fullscreen", OUTLINED, False),
    ("GridView", "grid_view", OUTLINED, False),
    ("Height", "height", OUTLINED, False),
    ("History", "history", OUTLINED, False),
    ("HourglassBottom", "hourglass_bottom", OUTLINED, False),
    ("ImportContacts", "import_contacts", OUTLINED, False),
    ("ImportContactsFilled", "import_contacts", FILLED, False),
    ("Info", "info", OUTLINED, False),
    ("Insights", "insights", OUTLINED, False),
    ("InsightsFilled", "insights", FILLED, False),
    ("Key", "key", OUTLINED, False),
    ("KeyboardArrowDown", "keyboard_arrow_down", OUTLINED, False),
    ("KeyboardArrowLeft", "keyboard_arrow_left", OUTLINED, True),
    ("KeyboardArrowRight", "keyboard_arrow_right", OUTLINED, True),
    ("KeyboardArrowUp", "keyboard_arrow_up", OUTLINED, False),
    ("LibraryAdd", "library_add", OUTLINED, False),
    ("LibraryBooks", "library_books", OUTLINED, True),
    ("LibraryBooksFilled", "library_books", FILLED, True),
    ("LightMode", "light_mode", OUTLINED, False),
    ("Lightbulb", "lightbulb", OUTLINED, False),
    ("LocalFireDepartment", "local_fire_department", OUTLINED, False),
    ("MenuBook", "menu_book", OUTLINED, True),
    ("Monitoring", "monitoring", OUTLINED, False),
    ("MonitoringFilled", "monitoring", FILLED, False),
    ("MoreVert", "more_vert", OUTLINED, False),
    ("NoPhotography", "no_photography", OUTLINED, False),
    ("Notes", "notes", OUTLINED, True),
    ("Palette", "palette", OUTLINED, False),
    ("PauseCircle", "pause_circle", OUTLINED, False),
    ("Percent", "percent", OUTLINED, False),
    ("Person", "person", OUTLINED, False),
    ("Photo", "photo", OUTLINED, False),
    ("PlayArrow", "play_arrow", OUTLINED, False),
    ("PlayArrowFilled", "play_arrow", FILLED, False),
    ("PlaylistAddCheck", "playlist_add_check", OUTLINED, True),
    ("Remove", "remove", OUTLINED, False),
    ("RemoveDone", "remove_done", OUTLINED, False),
    ("RestartAlt", "restart_alt", OUTLINED, False),
    ("Schedule", "schedule", OUTLINED, False),
    ("Search", "search", OUTLINED, False),
    ("SearchOff", "search_off", OUTLINED, False),
    ("SelectAll", "select_all", OUTLINED, False),
    ("Settings", "settings", OUTLINED, False),
    ("SettingsFilled", "settings", FILLED, False),
    ("Share", "share", OUTLINED, False),
    ("SortByAlpha", "sort_by_alpha", OUTLINED, False),
    ("SwipeVertical", "swipe_vertical", OUTLINED, False),
    ("Sync", "sync", OUTLINED, False),
    ("SyncDisabled", "sync_disabled", OUTLINED, False),
    ("SystemUpdate", "system_update", OUTLINED, False),
    ("TaskAlt", "task_alt", OUTLINED, False),
    ("TextDecrease", "text_decrease", OUTLINED, False),
    ("TextFields", "text_fields", OUTLINED, False),
    ("TextIncrease", "text_increase", OUTLINED, False),
    ("Toc", "toc", OUTLINED, True),
    ("Tune", "tune", OUTLINED, False),
    ("TuneFilled", "tune", FILLED, False),
    ("ViewComfy", "view_comfy", OUTLINED, False),
    ("ViewList", "view_list", OUTLINED, True),
    ("VolumeUp", "volume_up", OUTLINED, True),
    ("Wallpaper", "wallpaper", OUTLINED, False),
]

VIEWBOX = "0 -960 960 960"
PATH_DATA = re.compile(r"^[MmLlHhVvCcSsQqTtAaZz0-9.,\-\s]+$")


def svg_file(svg_dir, name, variant):
    return os.path.join(svg_dir, f"{name}__{variant}.svg")


def fetch(svg_dir):
    os.makedirs(svg_dir, exist_ok=True)
    for _, name, variant, _ in ICONS:
        dest = svg_file(svg_dir, name, variant)
        if os.path.exists(dest):
            continue
        with urllib.request.urlopen(f"{BASE}/{name}/{variant}/24px.svg", timeout=30) as r:
            data = r.read()
        with open(dest, "wb") as f:
            f.write(data)


def read_paths(path):
    with open(path, encoding="utf-8") as f:
        svg = f.read()
    viewbox = re.search(r'viewBox="([^"]*)"', svg)
    if not viewbox or viewbox.group(1) != VIEWBOX:
        raise ValueError(f"{path}: unexpected viewBox")
    paths = re.findall(r'<path\b[^>]*\bd="([^"]*)"', svg)
    if not paths or any(not PATH_DATA.match(d) for d in paths):
        raise ValueError(f"{path}: missing or unexpected path data")
    return paths


def main():
    svg_dir = sys.argv[1]
    if "--fetch" in sys.argv[2:]:
        fetch(svg_dir)
    props = []
    for prop, name, variant, mirror in sorted(ICONS, key=lambda i: i[0]):
        args = ", ".join(f'"{d}"' for d in read_paths(svg_file(svg_dir, name, variant)))
        mirror_arg = ", autoMirror = true" if mirror else ""
        props.append(f'    val {prop}: ImageVector by lazy {{ symbol("{prop}", {args}{mirror_arg}) }}\n')
    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        f.write(HEADER)
        f.write("\n".join(props))
        f.write(FOOTER)


HEADER = """// Generated by tools/stack-icons/generate_stack_icons.py from Material Symbols (Apache-2.0). Do not edit.
package com.itexpert120.yomu.core.designsystem.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.unit.dp

/**
 * Stack's icons: Material Symbols Outlined at weight 300 (optical size 24, grade 0). `*Filled` are
 * FILL 1 at weight 400, used only for the active navigation item and the play/resume button, except
 * [BookmarkFilled], which is FILL 1 at weight 300 so it keeps [Bookmark]'s silhouette when toggled.
 */
object StackIcons {
"""

FOOTER = """}

private fun symbol(name: String, vararg pathData: String, autoMirror: Boolean = false): ImageVector = ImageVector.Builder(
    name = "StackIcons.$name",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 960f,
    viewportHeight = 960f,
    autoMirror = autoMirror,
).apply {
    // Material Symbols draw in the viewBox 0 -960 960 960, so every y coordinate lies in -960..0.
    group(translationY = 960f) {
        pathData.forEach { addPath(addPathNodes(it), fill = SolidColor(Color.Black)) }
    }
}.build()
"""

if __name__ == "__main__":
    main()
