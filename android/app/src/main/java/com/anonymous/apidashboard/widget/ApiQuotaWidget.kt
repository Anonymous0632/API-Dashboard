package com.anonymous.apidashboard.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.actionRunCallback
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.defaultWeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.anonymous.apidashboard.data.CacheStore
import com.anonymous.apidashboard.data.CardSnapshot
import com.anonymous.apidashboard.network.QuotaRepository

class ApiQuotaWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(250.dp, 110.dp),
            DpSize(320.dp, 160.dp),
            DpSize(360.dp, 220.dp),
        ),
    )

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val repo = QuotaRepository(context)
        val cards = repo.cachedCards()
        val hasCredential = repo.hasAnyCredential()
        WidgetSurface(context, cards, hasCredential)
    }
}

@Composable
private fun WidgetSurface(context: Context, cards: List<CardSnapshot>, hasCredential: Boolean) {
    val cache = CacheStore(context)
    val updatedText = cache.updatedAt()?.let { "更新 ${com.anonymous.apidashboard.data.agoText(it)}" }.orEmpty()
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color(0xFF111827)))
            .padding(10.dp)
            .clickable(actionRunCallback<RefreshAction>()),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        when {
            cards.isNotEmpty() -> CardsGrid(cards)
            hasCredential -> EmptyState("点按刷新")
            else -> EmptyState("请先打开 App 导入配置")
        }
        if (updatedText.isNotEmpty()) {
            Spacer(GlanceModifier.height(4.dp))
            Text(
                text = updatedText,
                style = TextStyle(
                    color = ColorProvider(Color(0xFF9CA3AF)),
                    textAlign = TextAlign.End,
                ),
                modifier = GlanceModifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CardsGrid(cards: List<CardSnapshot>) {
    val visible = cards.take(4)
    if (visible.size <= 3) {
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            visible.forEachIndexed { index, card ->
                Card(card, GlanceModifier.defaultWeight())
                if (index != visible.lastIndex) Spacer(GlanceModifier.width(6.dp))
            }
        }
    } else {
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Card(visible[0], GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(6.dp))
                Card(visible[1], GlanceModifier.defaultWeight())
            }
            Spacer(GlanceModifier.height(6.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Card(visible[2], GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(6.dp))
                Card(visible[3], GlanceModifier.defaultWeight())
            }
        }
    }
}

@Composable
private fun Card(card: CardSnapshot, modifier: GlanceModifier) {
    Column(
        modifier = modifier
            .background(ColorProvider(Color(0xFF1F2937)))
            .padding(8.dp),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Text(
            text = card.title,
            style = TextStyle(
                color = ColorProvider(Color(0xFFE5E7EB)),
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
        Spacer(GlanceModifier.height(4.dp))
        Text(
            text = card.primaryValue,
            style = TextStyle(
                color = ColorProvider(Color(android.graphics.Color.parseColor(card.accent))),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
        Text(
            text = compactLine(card.primaryLabel, card.secondaryLabel, card.secondaryValue),
            style = TextStyle(
                color = ColorProvider(Color(0xFFD1D5DB)),
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
        if (card.footer.isNotEmpty()) {
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = card.footer,
                style = TextStyle(
                    color = ColorProvider(if (card.isStale) Color(0xFFFBBF24) else Color(0xFF9CA3AF)),
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
            )
        }
    }
}

private fun compactLine(primaryLabel: String, secondaryLabel: String, secondaryValue: String): String {
    return when {
        secondaryLabel.isNotEmpty() && secondaryValue.isNotEmpty() -> "$primaryLabel / $secondaryLabel $secondaryValue"
        primaryLabel.isNotEmpty() -> primaryLabel
        else -> ""
    }
}

@Composable
private fun EmptyState(text: String) {
    Text(
        text = text,
        modifier = GlanceModifier.fillMaxWidth(),
        style = TextStyle(
            color = ColorProvider(Color(0xFFE5E7EB)),
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        ),
        maxLines = 2,
    )
}
