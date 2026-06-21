package com.itexpert120.yomu.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.itexpert120.yomu.core.model.HeatmapDay

/**
 * Resizable reading-activity widget: today's & this-week's reading time plus the current streak,
 * with a recent daily bar chart that grows into a GitHub-style mini heatmap as the widget gets
 * taller. Tapping it opens the Statistics screen. Adapts via [SizeMode.Responsive] + [LocalSize]:
 * small shows just the headline figures, wider adds the bar strip, tall adds the multi-row heatmap.
 */
class ActivityWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(110.dp, 110.dp),  // compact: headline figures only
            DpSize(250.dp, 110.dp),  // wide-short: figures + single bar strip
            DpSize(250.dp, 200.dp),  // medium: + multi-week heatmap
            DpSize(320.dp, 280.dp),  // large: full heatmap window
        ),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val activity = loadActivity(context)
        provideContent {
            ActivityContent(context, activity)
        }
    }

    @Composable
    private fun ActivityContent(context: Context, activity: WidgetActivity) {
        val size = LocalSize.current
        val root = GlanceModifier
            .fillMaxSize()
            .background(WidgetColors.background)
            .cornerRadius(20.dp)
            .padding(14.dp)
            .clickable(actionStartActivity(WidgetDeepLink.statsIntent(context)))

        Column(modifier = root) {
            Header(activity)

            val wideEnough = size.width.value >= 220
            val tallEnough = size.height.value >= 170

            if (activity.isEmpty) {
                if (size.height.value >= 150) {
                    Spacer(GlanceModifier.height(10.dp))
                    Text(
                        text = "No reading yet — open a book to start your streak.",
                        style = TextStyle(
                            color = ColorProvider(WidgetColors.textMuted),
                            fontSize = 12.sp,
                        ),
                        maxLines = 2,
                    )
                }
                return@Column
            }

            if (wideEnough && tallEnough) {
                // Multi-row heatmap: as many trailing weeks as fit the height.
                Spacer(GlanceModifier.height(12.dp))
                val rowsForHeight = ((size.height.value - 120) / 18).toInt().coerceIn(2, 7)
                Heatmap(
                    days = activity.days,
                    weeks = weeksForWidth(size.width.value),
                    rows = rowsForHeight,
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                )
            } else if (wideEnough) {
                // Single bar strip of the most recent days.
                Spacer(GlanceModifier.height(12.dp))
                BarStrip(
                    days = activity.days,
                    count = barsForWidth(size.width.value),
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                )
            }
        }
    }

    @Composable
    private fun Header(activity: WidgetActivity) {
        Text(
            text = "Reading activity",
            style = TextStyle(
                color = ColorProvider(WidgetColors.accent),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
        )
        Spacer(GlanceModifier.height(8.dp))
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Stat(
                value = formatDuration(activity.todaySeconds),
                label = "Today",
                modifier = GlanceModifier.defaultWeight(),
            )
            Stat(
                value = formatDuration(activity.weekSeconds),
                label = "This week",
                modifier = GlanceModifier.defaultWeight(),
            )
            Stat(
                value = "${activity.currentStreakDays}d",
                label = "Streak",
                modifier = GlanceModifier.defaultWeight(),
            )
        }
    }

    @Composable
    private fun Stat(value: String, label: String, modifier: GlanceModifier) {
        Column(modifier = modifier) {
            Text(
                text = value,
                style = TextStyle(
                    color = ColorProvider(WidgetColors.textPrimary),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
            Text(
                text = label,
                style = TextStyle(color = ColorProvider(WidgetColors.textMuted), fontSize = 10.sp),
                maxLines = 1,
            )
        }
    }

    /** A horizontal strip of intensity-coloured bars for the most recent [count] days. */
    @Composable
    private fun BarStrip(days: List<HeatmapDay>, count: Int, modifier: GlanceModifier) {
        val recent = days.takeLast(count)
        Row(modifier = modifier) {
            recent.forEachIndexed { index, day ->
                Box(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .fillMaxHeight()
                        .cornerRadius(3.dp)
                        .background(WidgetColors.intensity[day.level.coerceIn(0, 4)]),
                ) {}
                if (index < recent.lastIndex) Spacer(GlanceModifier.width(3.dp))
            }
        }
    }

    /**
     * GitHub-style heatmap: [weeks] columns × [rows] rows of the trailing window. We lay it out by
     * column (a week) so cells read newest-week-rightmost; each cell is a small rounded square.
     */
    @Composable
    private fun Heatmap(days: List<HeatmapDay>, weeks: Int, rows: Int, modifier: GlanceModifier) {
        val cells = weeks * rows
        val window = days.takeLast(cells)
        // Split into columns of [rows] consecutive days.
        val columns = window.chunked(rows)
        Row(modifier = modifier) {
            columns.forEachIndexed { colIndex, column ->
                Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
                    column.forEachIndexed { rowIndex, day ->
                        Box(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .defaultWeight()
                                .cornerRadius(2.dp)
                                .background(WidgetColors.intensity[day.level.coerceIn(0, 4)]),
                        ) {}
                        if (rowIndex < column.lastIndex) Spacer(GlanceModifier.height(3.dp))
                    }
                }
                if (colIndex < columns.lastIndex) Spacer(GlanceModifier.width(3.dp))
            }
        }
    }

    private fun barsForWidth(widthDp: Float): Int =
        ((widthDp - 28) / 10).toInt().coerceIn(7, 21)

    private fun weeksForWidth(widthDp: Float): Int =
        ((widthDp - 28) / 14).toInt().coerceIn(4, 18)
}

class ActivityWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ActivityWidget()
}
