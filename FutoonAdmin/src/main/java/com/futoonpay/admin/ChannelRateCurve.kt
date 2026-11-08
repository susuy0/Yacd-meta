package com.futoonpay.admin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.futoonpay.admin.data.*
import com.futoonpay.admin.ui.PayCard
import com.futoonpay.admin.ui.theme.*

/** One column of the chart: a time window, its success rate (null when the website gave none or there were no orders) and its order count. */
internal data class CurvePoint(val label: String, val rate: Double?, val total: Int?)

internal fun curvePoints(windows: Map<MonitorWindow, ChannelSnapshot>?): List<CurvePoint> = CHANNEL_WINDOWS_OLDEST_FIRST.map { window ->
    val snapshot = windows?.get(window)
    CurvePoint(window.shortLabel(), snapshot?.takeIf { it.total != 0 }?.rate, snapshot?.total)
}
internal fun MonitorWindow.shortLabel(): String = when (this) {
    MonitorWindow.DAY -> "24小时"; MonitorWindow.TWELVE_HOURS -> "12小时"; MonitorWindow.SIX_HOURS -> "6小时"
    MonitorWindow.HOUR -> "1小时"; MonitorWindow.HALF_HOUR -> "30分"; MonitorWindow.FIVE_MINUTES -> "5分"
}

/** Normalised chart geometry: x in 0..1 across equal columns, y in 0..1 with 1 = 100%. Missing windows have no point and break the line. */
internal data class CurveGeometry(val points: List<Pair<Float, Float>?>) {
    /** Runs of consecutive existing points; a missing window ends one run and starts the next. */
    val runs: List<List<Pair<Float, Float>>> get() {
        val result = mutableListOf<MutableList<Pair<Float, Float>>>()
        var open = false
        for (point in points) {
            if (point == null) { open = false; continue }
            if (!open) { result.add(mutableListOf()); open = true }
            result.last().add(point)
        }
        return result
    }
}
internal fun curveGeometry(points: List<CurvePoint>): CurveGeometry = CurveGeometry(points.mapIndexed { index, p ->
    p.rate?.let { ((index + .5f) / points.size) to (it.coerceIn(0.0, 100.0) / 100.0).toFloat() }
})

internal fun rateColorLevel(rate: Double?): Int = when { rate == null -> 0; rate >= 90.0 -> 3; rate >= 70.0 -> 2; else -> 1 }

private fun rateText(rate: Double?) = rate?.let { String.format(java.util.Locale.ROOT, "%.1f%%", it) } ?: "—"

/** The success-rate curve of one channel: left = the longest window (24 hours), right = the newest (5 minutes). It is the rate per window, not a minute-by-minute history. */
@Composable internal fun ChannelRateCurve(points: List<CurvePoint>, modifier: Modifier = Modifier) {
    val geometry = curveGeometry(points)
    val ok = GreenDark; val warn = Warning; val bad = Danger; val grid = Line; val muted = Muted
    fun color(rate: Double?): Color = when (rateColorLevel(rate)) { 3 -> ok; 2 -> warn; 1 -> bad; else -> muted }
    val summary = points.joinToString("，") { "${it.label} ${rateText(it.rate)}" }
    Column(modifier.fillMaxWidth().semantics { contentDescription = "成功率曲线：$summary" }) {
        Canvas(Modifier.fillMaxWidth().height(96.dp)) {
            val top = 8.dp.toPx(); val bottom = size.height - 8.dp.toPx(); val span = bottom - top
            fun x(fraction: Float) = fraction * size.width
            fun y(fraction: Float) = bottom - fraction * span
            for (level in listOf(0f, .5f, 1f)) drawLine(grid, Offset(0f, y(level)), Offset(size.width, y(level)), strokeWidth = 1.dp.toPx())
            geometry.runs.forEach { run ->
                for (i in 0 until run.size - 1) drawLine(ok, Offset(x(run[i].first), y(run[i].second)), Offset(x(run[i + 1].first), y(run[i + 1].second)), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            }
            geometry.points.forEachIndexed { index, p ->
                if (p != null) drawCircle(color(points[index].rate), radius = 4.dp.toPx(), center = Offset(x(p.first), y(p.second)))
                else drawCircle(muted, radius = 3.dp.toPx(), center = Offset(x((index + .5f) / points.size), y(0f)), style = Stroke(1.dp.toPx()))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            points.forEach { p -> Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(rateText(p.rate), fontSize = 11.sp, fontWeight = FontWeight.Medium, color = color(p.rate), textAlign = TextAlign.Center, maxLines = 1)
                Text(p.label, fontSize = 10.sp, color = muted, textAlign = TextAlign.Center, maxLines = 1)
            } }
        }
    }
}

/** One card per channel with every window's dimensions and the curve, so the page needs no window switch. */
@Composable internal fun ChannelStatCard(name: String, id: String, windows: Map<MonitorWindow, ChannelSnapshot>?, error: String?) {
    PayCard {
        Text("$name #$id", fontWeight = FontWeight.SemiBold)
        when {
            error != null && windows == null -> Text(error, color = Danger, fontSize = 13.sp)
            windows == null -> Text("正在读取…", color = Muted, fontSize = 13.sp)
            else -> {
                ChannelRateCurve(curvePoints(windows))
                Text("横轴从长时间窗到最近 5 分钟；每个点是该时间窗内的成功率，不是逐分钟历史。", color = Muted, fontSize = 11.sp)
                Row(Modifier.fillMaxWidth()) {
                    listOf("时间窗" to 1.2f, "成功率" to 1f, "成功/总数" to 1.4f, "处理中" to .9f).forEach { (title, weight) -> Text(title, Modifier.weight(weight), color = Muted, fontSize = 11.sp) }
                }
                CHANNEL_WINDOWS_OLDEST_FIRST.reversed().forEach { window ->
                    val s = windows[window]
                    val few = s?.total?.let { it in 1..9 } == true
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(window.label, Modifier.weight(1.2f), fontSize = 12.sp)
                        Text(if (s?.empty == true) "暂无订单" else rateText(s?.rate), Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text("${s?.successful?.toString() ?: "—"}/${s?.total?.toString() ?: "—"}" + if (few) " 样本少" else "", Modifier.weight(1.4f), fontSize = 12.sp, color = if (few) Warning else Muted)
                        Text(s?.processing?.toString() ?: "—", Modifier.weight(.9f), fontSize = 12.sp, color = Muted)
                    }
                }
                if (error != null) Text(error, color = Danger, fontSize = 12.sp)
            }
        }
    }
}
