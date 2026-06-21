package com.itexpert120.yomu.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
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

    // Exact: recompose for the actual placed size so the heatmap scales continuously to any size.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val activity = loadActivity(context)
        provideContent {
            ActivityContent(context, activity)
        }
    }

    @Composable
    private fun ActivityContent(context: Context, activity: WidgetActivity) {
        val root = GlanceModifier
            .fillMaxSize()
            .background(WidgetColors.background)
            .cornerRadius(20.dp)
            .padding(14.dp)
            .clickable(actionStartActivity(WidgetDeepLink.statsIntent(context)))

        Column(modifier = root) {
            Header(activity)

            if (activity.isEmpty) {
                Spacer(GlanceModifier.height(10.dp))
                Text(
                    text = "No reading yet — open a book to start your streak.",
                    style = TextStyle(
                        color = ColorProvider(WidgetColors.textMuted),
                        fontSize = 12.sp,
                    ),
                    maxLines = 2,
                )
                return@Column
            }

            // The activity box graph — the SAME week-columns × 7 day-rows grid as the Statistics
            // screen, scaled (shrunk) to fill the widget at any size.
            Spacer(GlanceModifier.height(12.dp))
            val rows = WEEK_DAYS
            val weeks = (activity.days.size + rows - 1) / rows
            Heatmap(
                days = activity.days,
                weeks = weeks,
                rows = rows,
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            )
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

    private companion object {
        const val WEEK_DAYS = 7
    }
}

class ActivityWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ActivityWidget()
}
