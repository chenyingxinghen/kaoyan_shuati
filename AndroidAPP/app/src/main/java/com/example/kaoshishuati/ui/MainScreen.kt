package com.example.kaoshishuati.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.activity.compose.BackHandler
import com.example.kaoshishuati.R
import com.example.kaoshishuati.data.BankDb
import com.example.kaoshishuati.data.Course
import com.example.kaoshishuati.data.DoneStore
import com.example.kaoshishuati.data.DrillMode
import com.example.kaoshishuati.data.DrillRound
import com.example.kaoshishuati.data.DrillRoundStore
import com.example.kaoshishuati.data.DrillSettingsStore
import com.example.kaoshishuati.data.FeedbackEntry
import com.example.kaoshishuati.data.FeedbackReport
import com.example.kaoshishuati.data.FeedbackStore
import com.example.kaoshishuati.data.FeedbackTypes
import com.example.kaoshishuati.data.Option
import com.example.kaoshishuati.data.Paper
import com.example.kaoshishuati.data.ProgressStore
import com.example.kaoshishuati.data.Question
import com.example.kaoshishuati.data.QuestionSource
import com.example.kaoshishuati.data.WrongStore
import com.example.kaoshishuati.ui.theme.Brand400
import com.example.kaoshishuati.ui.theme.Brand500
import com.example.kaoshishuati.ui.theme.Brand600
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class Route {
    data object Bank : Route()
    data class Practice(val paper: Paper) : Route()
    data object Wrong : Route()
    data object Drills : Route()
    data class Run(
        val questions: List<Question>,
        val title: String,
        val papers: List<Paper>,
        val sessionKey: String,
        val round: DrillRound,
    ) : Route()
}

/** 导航栈元素：id 用于把该屏的可保存状态（滚动位置 / 筛选 / 续做下标等）隔离进各自的保存作用域。 */
private data class NavEntry(val id: Long, val route: Route)

/**
 * 分层级导航：Bank 是根。从根可进入 卷练习/错题本/专项刷题；专项刷题内可再进入一次刷题 Run。
 * 「返回」总是回退到上一层（Run→专项、专项→首页），直到根（Bank）时再按返回才退出 App。
 * 每层包在 SaveableStateProvider 里，返回时自动还原该页的滚动位置与已选状态（分层记忆）。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun App(bank: BankDb, wrongStore: WrongStore, progressStore: ProgressStore, doneStore: DoneStore, feedbackStore: FeedbackStore, drillSettingsStore: DrillSettingsStore, drillRoundStore: DrillRoundStore) {
    val cs = MaterialTheme.colorScheme
    val stateHolder = rememberSaveableStateHolder()
    var nextId by remember { mutableStateOf(0L) }
    val stack = remember { mutableStateListOf(NavEntry(0L, Route.Bank)) }
    fun push(r: Route) { stack.add(NavEntry(++nextId, r)) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.size - 1) }
    fun popToRoot() { while (stack.size > 1) stack.removeAt(stack.size - 1) }
    // 系统返回键：根以下逐层回退；到根时不拦截（交给系统退出 App）
    BackHandler(enabled = stack.size > 1) { pop() }

    // 题库列表在导航栈根(App)预取并常驻：BankScreen 每次返回都重新进入组合，若在屏内
    // produceState 重新拉取，返回首帧 papers 仍为 null、列表只有占位项，会把已恢复的滚动
    // 下标钳到顶部，表现为「先闪一下首页顶部、再跳回原位」。放在 App 层则返回时数据已在。
    val papers by produceState<List<Paper>?>(null) {
        value = withContext(Dispatchers.IO) { bank.papers() }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = cs.background) {
        val top = stack.last()
        stateHolder.SaveableStateProvider(top.id) {
            when (val r = top.route) {
                is Route.Practice -> PracticeScreen(
                    bank, wrongStore, progressStore, doneStore, feedbackStore, r.paper,
                    onExit = { pop() })
                Route.Wrong -> WrongScreen(bank, wrongStore, onBack = { pop() })
                Route.Drills -> DrillsScreen(bank, wrongStore, progressStore, doneStore, drillSettingsStore, drillRoundStore,
                    onBack = { pop() },
                    onStart = { qs, title, papers, sessionKey, round ->
                        push(Route.Run(qs, title, papers, sessionKey, round))
                    })
                is Route.Run -> RunScreen(
                    bank, wrongStore, progressStore, doneStore, feedbackStore, drillRoundStore,
                    r.questions, r.title, r.papers, r.sessionKey, r.round,
                    onBack = { pop() },             // 中途返回上一级（专项=Drills），进度已存、可续做
                    onFinished = { popToRoot() })   // 完成后「返回题库」回根
                Route.Bank -> BankScreen(
                    papers, wrongStore, feedbackStore, doneStore,
                    onOpen = { push(Route.Practice(it)) }, onWrong = { push(Route.Wrong) },
                    onDrills = { push(Route.Drills) })
            }
        }
    }
}

// ====================== 题库首页 ======================

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun BankScreen(papers: List<Paper>?, wrongStore: WrongStore, feedbackStore: FeedbackStore, doneStore: DoneStore, onOpen: (Paper) -> Unit, onWrong: () -> Unit, onDrills: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    // 分组默认全部收起；用户展开/收起后的状态随首页保存作用域保留。
    var collapsed by rememberSaveable {
        mutableStateOf(
            listOf(
                QuestionSource.PAST.name,
                QuestionSource.XIAO1000.name,
                QuestionSource.MANMANXUE.name,
                "other",
            ).joinToString("|"),
        )
    }
    fun collapsedList(): List<String> = if (collapsed.isEmpty()) emptyList() else collapsed.split("|")
    fun toggleGroup(key: String) {
        val cur = collapsedList().toMutableList()
        if (key in cur) cur.remove(key) else cur.add(key)
        collapsed = cur.joinToString("|")
    }

    // 滚动位置由 LazyListState 自带的可保存状态负责：数据在 App 层已就绪，返回时列表以完整
    // item 首次测量，恢复的下标/像素偏移直接生效，不会先画顶部再纠正。
    val listState = rememberLazyListState()

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { Text("考研政治刷题", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    TextButton(onClick = {
                        val n = feedbackStore.count()
                        if (n == 0) {
                            Toast.makeText(context, "还没有收到任何问题反馈", Toast.LENGTH_SHORT).show()
                        } else {
                            FeedbackReport.share(context, feedbackStore)
                            // 分享面板已调起后清空本地反馈与「已反馈」标记，避免重复累积。
                            // 若系统无可用分享面板致 share() 抛异常，此行不会执行、数据不丢。
                            feedbackStore.clear()
                        }
                    }) {
                        Text("导出反馈", color = cs.onBackground, style = MaterialTheme.typography.labelLarge)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = cs.background,
                    titleContentColor = cs.onBackground,
                ),
            )
        },
    ) { pad ->
        val list = papers
        if (list == null) {
            // 冷启动 / 进程重建时数据未就绪：此时不组合 LazyColumn，否则会先以「占位列表」
            // 测量并把恢复的滚动下标钳到顶部。等数据到位再整体组合，首帧即正确位置。
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val wrong = wrongStore.ids()
            LazyColumn(
                state = listState,
                modifier = Modifier.padding(pad).fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item("hero") { HeroCard(papers = list) }
                item("stats") {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        StatCard(
                            title = "错题本",
                            value = wrong.size.toString(),
                            accent = cs.secondary,
                            onClick = onWrong,
                            modifier = Modifier.weight(1f),
                        )
                        StatCard(
                            title = "累计已刷",
                            value = doneStore.totalDone().toString(),
                            accent = cs.primary,
                            onClick = null,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                item("drills") { DrillsEntryCard(onClick = onDrills) }
                // 分别聚合真题与习题（习题再按册聚合），每册可展开/收起。
                groupBySource(list).forEach { g ->
                    val expanded = g.key !in collapsedList()
                    item("h-${g.key}") {
                        CollapsibleGroupHeader(
                            title = g.title,
                            subtitle = "${g.papers.size} 卷 · ${g.papers.sumOf { it.questionCount }} 题",
                            expanded = expanded,
                            onToggle = { toggleGroup(g.key) },
                        )
                    }
                    if (expanded) {
                        items(g.papers, key = { "${g.key}-${it.id}" }) { p ->
                            PaperCard(p, doneStore, g.real) { onOpen(p) }
                        }
                    }
                }
            }
        }
    }
}

/** 首页聚合的书册：真题（历年真题）与习题（肖1000/漫漫学1500 各成一组），real 决定卡片外观。 */
private data class SourceGroup(val key: String, val title: String, val real: Boolean, val papers: List<Paper>)

private fun groupBySource(papers: List<Paper>): List<SourceGroup> {
    val buckets = listOf(
        QuestionSource.PAST to "历年真题",
        QuestionSource.XIAO1000 to "肖1000",
        QuestionSource.MANMANXUE to "漫漫学1500",
    )
    val groups = ArrayList<SourceGroup>()
    for ((src, title) in buckets) {
        val matched = papers
            .filter { QuestionSource.of(it.id) == src }
            .sortedWith(compareByDescending<Paper> { it.year }.thenBy { it.title })
        if (matched.isNotEmpty()) groups.add(SourceGroup(src.name, title, src == QuestionSource.PAST, matched))
    }
    val other = papers
        .filter { QuestionSource.of(it.id) == null }
        .sortedWith(compareByDescending<Paper> { it.year }.thenBy { it.title })
    if (other.isNotEmpty()) groups.add(SourceGroup("other", "其他题集", false, other))
    return groups
}

/** 可展开/收起的书册标题栏。 */
@Composable
private fun CollapsibleGroupHeader(title: String, subtitle: String, expanded: Boolean, onToggle: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onToggle,
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = cs.onBackground)
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(cs.outline),
            )
            Spacer(Modifier.weight(1f))
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            ChevronVertical(down = !expanded, color = cs.onSurfaceVariant)
        }
    }
}

/** 上下箭头：收起时朝下（点击展开），展开时朝上（点击收起）。 */
@Composable
private fun ChevronVertical(down: Boolean, color: Color) {
    Canvas(Modifier.size(14.dp)) {
        val w = size.width
        val h = size.height
        val p = if (down) Path().apply {
            moveTo(w * 0.20f, h * 0.38f)
            lineTo(w * 0.50f, h * 0.68f)
            lineTo(w * 0.80f, h * 0.38f)
        } else Path().apply {
            moveTo(w * 0.20f, h * 0.62f)
            lineTo(w * 0.50f, h * 0.32f)
            lineTo(w * 0.80f, h * 0.62f)
        }
        drawPath(
            p,
            color = color,
            style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

@Composable
private fun HeroCard(papers: List<Paper>?) {
    val total = papers?.size ?: 22
    val q = papers?.sumOf { it.questionCount } ?: 1687
    Box(
        Modifier
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Brand600, Brand400))),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 14.dp)
                .size(120.dp),
        )
        Column(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = 20.dp, end = 130.dp),
        ) {
            Text(
                "考研政治刷题",
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "精选 $total 卷真题与模拟 · 共 $q 题",
                color = Color.White.copy(alpha = 0.88f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.18f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        "每日一刷 · 稳步提分",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.StatCard(
    title: String,
    value: String,
    accent: Color,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Card(
        modifier = modifier.height(96.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        border = BorderStroke(0.5.dp, cs.outlineVariant),
        onClick = onClick ?: {},
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accent),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant,
                )
            }
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                color = cs.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun DrillsEntryCard(onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        border = BorderStroke(0.5.dp, cs.outlineVariant),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(Brand600, Brand400))),
                contentAlignment = Alignment.Center,
            ) {
                Text("刷", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "专项刷题",
                    style = MaterialTheme.typography.titleSmall,
                    color = cs.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "顺序 / 乱序 / 错题重刷 · 可按来源或学科选范围",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            ChevronRight(color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun GroupHeader(title: String, count: String) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = cs.onBackground)
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(cs.outline),
        )
        Spacer(Modifier.weight(1f))
        Text(count, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
    }
}

@Composable
private fun PaperCard(p: Paper, doneStore: DoneStore, isReal: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val accent = if (isReal) com.example.kaoshishuati.ui.theme.Amber500 else Brand500
    val accentContainer = if (isReal) com.example.kaoshishuati.ui.theme.Amber50 else com.example.kaoshishuati.ui.theme.Brand50
    val done = doneStore.doneCount(p.id)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        border = BorderStroke(0.5.dp, cs.outlineVariant),
        onClick = onClick,
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(width = 56.dp, height = 64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentContainer),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${p.year}",
                        style = MaterialTheme.typography.titleMedium,
                        color = accent,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (isReal) "真题" else "模拟",
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    p.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = cs.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (done > 0) "${p.questionCount} 题 · 已刷 $done" else "${p.questionCount} 题 · ${if (isReal) "历年真题" else "习题"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (done > 0) MaterialTheme.colorScheme.primary else cs.onSurfaceVariant,
                    fontWeight = if (done > 0) FontWeight.Medium else FontWeight.Normal,
                )
                if (done > 0 && done < p.questionCount) {
                    Spacer(Modifier.height(6.dp))
                    val frac = (done.toFloat() / p.questionCount).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { frac },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            ChevronRight(color = cs.onSurfaceVariant)
        }
    }
}

// ====================== 练习页 ======================

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun PracticeScreen(bank: BankDb, wrongStore: WrongStore, progressStore: ProgressStore, doneStore: DoneStore, feedbackStore: FeedbackStore, paper: Paper, onExit: () -> Unit) {
    // 卷练习只出「未答过」的题：答过的（对错都算）不再重复出现——错题留给「错题本 / 错题重刷」复习。
    // doneIds 在进入本屏时取一次快照、题集随 all 一次算定（会话内固定，作答过程中不会抽题/跳位）；
    // 因此也不需要按下标续做，返回再进入自然从第一道未答题继续。
    val all by produceState<List<Question>?>(null) {
        value = withContext(Dispatchers.IO) { bank.questions(paper.id) }
    }
    val doneSnapshot = remember { doneStore.doneIds() }
    val pending = remember(all) { all?.filter { it.id !in doneSnapshot } }
    val cs = MaterialTheme.colorScheme
    val papersById = remember { mapOf(paper.id to paper) }
    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { Text(paper.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    TextButton(onClick = onExit) {
                        Text("返回", color = cs.onBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = cs.background,
                    titleContentColor = cs.onBackground,
                    navigationIconContentColor = cs.onBackground,
                ),
            )
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val total = all
            val list = pending
            when {
                total == null || list == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                total.isEmpty() -> Text("本卷暂无题目", Modifier.align(Alignment.Center))
                list.isEmpty() -> Column(
                    Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "本卷题目已全部答过",
                        style = MaterialTheme.typography.titleMedium,
                        color = cs.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "答过的题不再重复出现；错题可在「错题本」或专项「错题重刷」里复习。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                // 单卷题集已按「未答」过滤；专项则另行传入持久化的一轮。
                else -> Pager(list, wrongStore, doneStore, feedbackStore, papersById, onExit)
            }
        }
    }
}

@Composable
private fun Pager(qs: List<Question>, wrongStore: WrongStore, doneStore: DoneStore, feedbackStore: FeedbackStore, papersById: Map<String, Paper>, onExit: () -> Unit,
                  initialRound: DrillRound? = null, onSaveRound: ((DrillRound) -> Boolean)? = null, onRestartRound: (() -> Unit)? = null) {
    val context = LocalContext.current
    var round by remember(initialRound?.id) {
        mutableStateOf(initialRound ?: DrillRound.create(DrillMode.SEQUENTIAL, qs))
    }
    val displayedRound = round
    val index = displayedRound.index
    val q = qs[index]
    val correct = round.correct
    val singleSel = round.singleSelection
    val multiSel = round.multiSelection
    val revealed = round.revealed

    fun recordResult(state: DrillRound) {
        if (state.revealed) {
            doneStore.markDone(q.paperId, q.id)
            if (state.isCorrect(q)) wrongStore.remove(q.id) else wrongStore.add(q.id)
        }
    }
    // 进程可能在保存本轮后、写已刷/错题集合前被终止；恢复时幂等补齐，不再次累加得分。
    LaunchedEffect(displayedRound.id, index, revealed) { recordResult(displayedRound) }

    fun update(next: DrillRound) {
        if (next == round) return
        if (onSaveRound != null && !onSaveRound(next)) {
            Toast.makeText(context, "进度保存失败，请检查存储空间后重试", Toast.LENGTH_LONG).show()
            return
        }
        round = next
        if (next.index == index) recordResult(next)
    }

    if (round.finished) {
        ResultView(
            total = qs.size,
            correct = correct,
            onRestart = onRestartRound ?: { round = DrillRound.create(DrillMode.SEQUENTIAL, qs) },
            onExit = onExit,
            restartLabel = if (onRestartRound != null) "返回模式设置" else "再练一遍",
        )
        return
    }

    fun selectSingle(k: String) { update(round.selectSingle(q, k)) }
    fun toggleMulti(k: String) { update(round.toggleMulti(q, k)) }
    fun submitMulti() { update(round.submitMulti(q)) }
    fun advance() { update(round.advance()) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${index + 1} / ${qs.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            Chip(if (q.isMultiple) "多选题" else "单选题")
        }
        Spacer(Modifier.height(8.dp))
        val target = (index + 1f) / qs.size
        val progress by animateFloatAsState(target, label = "q-progress", animationSpec = tween(280))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            QuestionBody(
                q = q,
                papersById = papersById,
                feedbackStore = feedbackStore,
                singleSel = singleSel,
                multiSel = multiSel,
                revealed = revealed,
                onSelectSingle = ::selectSingle,
                onToggleMulti = ::toggleMulti,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (q.isMultiple && !revealed) {
                Button(
                    onClick = { submitMulti() },
                    enabled = multiSel.isNotEmpty(),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.height(48.dp),
                ) { Text("提交答案") }
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { advance() },
                enabled = revealed,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.height(48.dp),
            ) { Text(if (index == qs.size - 1) "完成" else "下一题") }
        }
    }
}

@Composable
private fun QuestionBody(
    q: Question,
    papersById: Map<String, Paper>,
    feedbackStore: FeedbackStore,
    singleSel: String?,
    multiSel: Set<String>,
    revealed: Boolean,
    onSelectSingle: (String) -> Unit,
    onToggleMulti: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    var reported by remember(q.id) { mutableStateOf(feedbackStore.has(q.id)) }
    var showFeedback by remember(q.id) { mutableStateOf(false) }

    if (showFeedback) {
        FeedbackDialog(
            qid = q.id,
            onDismiss = { showFeedback = false },
            onSubmit = { type, note ->
                // 反馈归到题目实际所属卷（专项刷题跨卷/乱序时每题卷不同）
                val owner = papersById[q.paperId]
                feedbackStore.add(
                    FeedbackEntry(
                        qid = q.id,
                        paperId = owner?.id ?: q.paperId,
                        paperTitle = owner?.title ?: q.paperId,
                        paperYear = owner?.year ?: 0,
                        type = type,
                        typeLabel = FeedbackTypes.label(type),
                        note = note.trim(),
                        createdAt = System.currentTimeMillis(),
                    ),
                )
                reported = true
                showFeedback = false
                Toast.makeText(context, "已提交反馈，感谢帮助完善题库", Toast.LENGTH_SHORT).show()
            },
        )
    }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = cs.surface),
            border = BorderStroke(0.5.dp, cs.outlineVariant),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Chip(
                        q.category?.takeIf { it.isNotBlank() } ?: "未分类",
                        accent = cs.primaryContainer,
                        onColor = cs.onPrimaryContainer,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "题号 ${q.id}",
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    q.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = cs.onSurface,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        val correctSet = q.answer.toCharArray().map { it.toString() }.toSet()
        q.options.forEach { o ->
            OptionRow(
                q = q, o = o, singleSel = singleSel, multiSel = multiSel,
                revealed = revealed, correctSet = correctSet,
                onSelectSingle = onSelectSingle, onToggleMulti = onToggleMulti,
            )
        }
        if (revealed) {
            Spacer(Modifier.height(12.dp))
            val right = if (q.isMultiple) multiSel.sorted().joinToString("") == q.answer else singleSel == q.answer
            FeedbackBanner(ok = right, correctAnswer = q.answer)
            val a = q.analysis.trim()
            if (a.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                AnalysisCard(a)
            }
        }
        Spacer(Modifier.height(6.dp))
        FeedbackLink(reported = reported, onClick = { showFeedback = true })
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun OptionRow(
    q: Question,
    o: Option,
    singleSel: String?,
    multiSel: Set<String>,
    revealed: Boolean,
    correctSet: Set<String>,
    onSelectSingle: (String) -> Unit,
    onToggleMulti: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val selected = if (q.isMultiple) o.key in multiSel else singleSel == o.key
    val isCorrect = o.key in correctSet
    val container = when {
        revealed && isCorrect -> cs.primaryContainer
        revealed && selected && !isCorrect -> cs.errorContainer
        selected -> cs.primaryContainer
        else -> cs.surface
    }
    val border = when {
        revealed && isCorrect -> cs.primary
        revealed && selected -> cs.error
        selected -> cs.primary
        else -> cs.outlineVariant
    }
    val badgeBg = when {
        revealed && isCorrect -> cs.primary
        revealed && selected && !isCorrect -> cs.error
        selected -> cs.primary
        else -> cs.surfaceVariant
    }
    val badgeFg = when {
        revealed && isCorrect -> cs.onPrimary
        revealed && selected && !isCorrect -> cs.onError
        selected -> cs.onPrimary
        else -> cs.onSurfaceVariant
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = container),
        // 边框粗细只能随“已选/已揭晓”变化：isCorrect 必须在 revealed 之后才参与，
        // 否则未作答时正确项就比其余项粗 0.7dp，等于提前泄题。
        border = BorderStroke(if (selected || (revealed && isCorrect)) 1.2.dp else 0.5.dp, border),
        onClick = {
            if (!revealed) {
                if (q.isMultiple) onToggleMulti(o.key) else onSelectSingle(o.key)
            }
        },
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(badgeBg),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    o.key,
                    color = badgeFg,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                o.content,
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (revealed && isCorrect) {
                CheckIcon(color = cs.primary)
            } else if (revealed && selected && !isCorrect) {
                XIcon(color = cs.error)
            } else if (q.isMultiple && selected && !revealed) {
                Box(
                    Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(cs.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    CheckIcon(color = cs.onPrimary, iconSize = 14.dp)
                }
            }
        }
    }
}

@Composable
private fun FeedbackBanner(ok: Boolean, correctAnswer: String) {
    val cs = MaterialTheme.colorScheme
    val container = if (ok) cs.primaryContainer else cs.errorContainer
    val onContainer = if (ok) cs.onPrimaryContainer else cs.onErrorContainer
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(container)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (ok) CheckIcon(color = onContainer) else XIcon(color = onContainer)
        Spacer(Modifier.width(10.dp))
        Text(
            if (ok) "回答正确" else "回答错误 · 正确答案 $correctAnswer",
            color = onContainer,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun AnalysisCard(text: String) {
    val cs = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = cs.secondaryContainer),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(cs.secondary),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "解析",
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSecondaryContainer,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun ResultView(total: Int, correct: Int, onRestart: () -> Unit, onExit: () -> Unit, restartLabel: String = "再练一遍") {
    val cs = MaterialTheme.colorScheme
    val pct = if (total == 0) 0f else correct * 1f / total
    val pctInt = (pct * 100).toInt()
    val encouragement = when {
        pctInt >= 90 -> "极佳发挥，考研稳了"
        pctInt >= 75 -> "不错，继续保持手感"
        pctInt >= 60 -> "基础已具备，多刷几套"
        else -> "错题已记入错题本，重点复习"
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "本卷练习完成",
            style = MaterialTheme.typography.titleMedium,
            color = cs.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
            ResultRing(progress = pct)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "$pctInt%",
                    style = MaterialTheme.typography.displaySmall,
                    color = cs.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "答对 $correct / $total",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            encouragement,
            style = MaterialTheme.typography.titleMedium,
            color = cs.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onRestart,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.height(48.dp),
            ) { Text(restartLabel) }
            OutlinedButton(
                onClick = onExit,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.height(48.dp),
            ) { Text("返回题库") }
        }
    }
}

@Composable
private fun ResultRing(progress: Float) {
    val cs = MaterialTheme.colorScheme
    val animated by animateFloatAsState(progress, label = "ring", animationSpec = tween(720))
    Canvas(Modifier.fillMaxSize()) {
        val sw = 14.dp.toPx()
        val pad = sw / 2
        val arcSize = Size(size.width - sw, size.height - sw)
        val topLeft = Offset(pad, pad)
        drawArc(
            color = cs.surfaceVariant,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = sw, cap = StrokeCap.Round),
        )
        drawArc(
            brush = Brush.sweepGradient(
                listOf(Brand500, cs.primary, Brand500),
                center = Offset(size.width / 2, size.height / 2),
            ),
            startAngle = -90f,
            sweepAngle = 360f * animated,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = sw, cap = StrokeCap.Round),
        )
    }
}

// ====================== 错题本 ======================

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun WrongScreen(bank: BankDb, wrongStore: WrongStore, onBack: () -> Unit) {
    val ids = wrongStore.ids()
    val qs by produceState<List<Question>>(emptyList()) {
        value = withContext(Dispatchers.IO) { ids.mapNotNull { bank.questionById(it) } }
    }
    val cs = MaterialTheme.colorScheme
    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { Text("错题本（${qs.size}）") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("返回", color = cs.onBackground) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = cs.background,
                    titleContentColor = cs.onBackground,
                    navigationIconContentColor = cs.onBackground,
                ),
            )
        },
    ) { pad ->
        if (qs.isEmpty()) {
            Box(
                Modifier.padding(pad).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(cs.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        CheckIcon(color = cs.onPrimaryContainer, iconSize = 44.dp)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "暂无错题",
                        style = MaterialTheme.typography.titleMedium,
                        color = cs.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "保持手感，全对通关",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(pad),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            ) {
                items(qs, key = { it.id }) { q -> WrongQuestionCard(q) }
            }
        }
    }
}

@Composable
private fun WrongQuestionCard(q: Question) {
    val cs = MaterialTheme.colorScheme
    val ans = q.answer.toCharArray().map { it.toString() }.toSet()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        border = BorderStroke(0.5.dp, cs.outlineVariant),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Chip(q.category ?: "未分类")
                Spacer(Modifier.weight(1f))
                Text("正确答案", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                AnswerPill(q.answer)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                q.content,
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            q.options.forEach { o ->
                val isAns = o.key in ans
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(if (isAns) cs.primary else cs.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            o.key,
                            color = if (isAns) cs.onPrimary else cs.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        o.content,
                        color = if (isAns) cs.onSurface else cs.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (isAns) {
                        Spacer(Modifier.width(6.dp))
                        CheckIcon(color = cs.primary, iconSize = 16.dp)
                    }
                }
            }
            val a = q.analysis.trim()
            if (a.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                AnalysisCard(a)
            }
        }
    }
}

// ====================== 专项刷题（按来源/学科选范围的顺序/乱序/错题重刷） ======================

/** 一次专项刷题预取结果：命中范围题数 + 题目 + 命中的卷（用于反馈归属）。 */
private data class ScopedSet(val title: String, val papers: List<Paper>, val questions: List<Question>)

/** 生成范围描述，未勾选即“全部题库”。 */
private fun scopeTitle(sources: Set<QuestionSource>, courses: Set<Course>): String {
    val sPart = if (sources.isEmpty()) "全部题库" else sources.map { it.label }.sorted().joinToString("+")
    val cPart = if (courses.isEmpty()) "" else " · " + courses.map { it.label }.sorted().joinToString("+")
    return sPart + cPart
}

/** 顺序刷题的续做作用域键：由范围与「只刷新题」开关推导，任一变化即视为另一次（避免续做题序错位）。 */
private fun drillScopeKey(sources: Set<QuestionSource>, courses: Set<Course>, newOnly: Boolean): String =
    (listOf("drill", if (newOnly) "new" else "all") +
        sources.map { it.name }.sorted() + courses.map { it.name }.sorted()).joinToString("|")

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun RunScreen(bank: BankDb, wrongStore: WrongStore, progressStore: ProgressStore, doneStore: DoneStore, feedbackStore: FeedbackStore, roundStore: DrillRoundStore, questions: List<Question>, title: String, papers: List<Paper>, sessionKey: String, initialRound: DrillRound, onBack: () -> Unit, onFinished: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val papersById = remember { papers.associateBy { it.id } }
    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("返回", color = cs.onBackground) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = cs.background,
                    titleContentColor = cs.onBackground,
                    navigationIconContentColor = cs.onBackground,
                ),
            )
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (questions.isEmpty()) {
                Text("所选范围暂无题目", Modifier.align(Alignment.Center))
            } else {
                Pager(questions, wrongStore, doneStore, feedbackStore, papersById, onFinished,
                    initialRound = initialRound,
                    onSaveRound = { roundStore.save(sessionKey, it) },
                    onRestartRound = onBack)
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun DrillsScreen(bank: BankDb, wrongStore: WrongStore, progressStore: ProgressStore, doneStore: DoneStore, drillSettings: DrillSettingsStore, roundStore: DrillRoundStore, onBack: () -> Unit, onStart: (List<Question>, String, List<Paper>, String, DrillRound) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val wrongIds = wrongStore.ids()
    // 已刷 id 快照（返回本页时刷新）：供「只刷新题」批量过滤，避免每题读盘。
    val doneIds = doneStore.doneIds()

    // 上次的勾选设置（持久化在 DrillSettingsStore）：进入本页时还原，改动即写回，
    // 下次进 App / 进本页都直接带回上次的选择。
    val last = remember { drillSettings.load() }

    // 只刷新题：开时仅刷未做过的新题（配合乱序学习更高效）；错题重刷不适用。
    var newOnly by rememberSaveable { mutableStateOf(last.newOnly) }
    fun filterNew(qs: List<Question>): List<Question> =
        if (newOnly) qs.filter { it.id !in doneIds } else qs

    val allPapers by produceState<List<Paper>?>(null) {
        value = withContext(Dispatchers.IO) { bank.papers() }
    }

    // 所选范围/模式存成可保存的名称串（String 可被 Bundle 持久化）：从一次刷题返回本页时还原自选题册。
    var srcStr by rememberSaveable { mutableStateOf(last.sources.joinToString("|") { it.name }) }
    var couStr by rememberSaveable { mutableStateOf(last.courses.joinToString("|") { it.name }) }
    var modeName by rememberSaveable { mutableStateOf(last.mode.name) }
    fun parseNames(raw: String): List<String> = if (raw.isEmpty()) emptyList() else raw.split("|")
    fun selectedSources(): Set<QuestionSource> = parseNames(srcStr)
        .mapNotNull { n -> QuestionSource.values().find { it.name == n } }.toSet()
    fun selectedCourses(): Set<Course> = parseNames(couStr)
        .mapNotNull { n -> Course.values().find { it.name == n } }.toSet()
    fun selectedMode(): DrillMode = DrillMode.values().find { it.name == modeName } ?: DrillMode.SEQUENTIAL
    val srcSel = remember(srcStr) { selectedSources() }
    val couSel = remember(couStr) { selectedCourses() }
    val mode = remember(modeName) { selectedMode() }
    // 点击后重组尚未发生，srcSel/couSel/mode 仍是旧值；直接读取最新的可保存状态再写回。
    fun persist() = drillSettings.save(selectedSources(), selectedCourses(), selectedMode(), newOnly)
    fun toggleSrc(v: QuestionSource) {
        val cur = parseNames(srcStr).toMutableList()
        if (v.name in cur) cur.remove(v.name) else cur.add(v.name)
        srcStr = cur.joinToString("|")
        persist()
    }
    fun toggleCou(v: Course) {
        val cur = parseNames(couStr).toMutableList()
        if (v.name in cur) cur.remove(v.name) else cur.add(v.name)
        couStr = cur.joinToString("|")
        persist()
    }

    // 范围变化时才在 IO 上重取题（乱序在点击开始时才洗，保持切换即时）
    val loadedScope by produceState<ScopedSet?>(null, allPapers, srcSel, couSel) {
        val papers = allPapers
        value = if (papers == null) null else withContext(Dispatchers.IO) {
            val matched =
                if (srcSel.isEmpty()) papers else papers.filter { QuestionSource.of(it.id) in srcSel }
            val qs = bank.questionsByPapers(matched.map { it.id })
                .filter { Course.matches(it.category, couSel) }
            ScopedSet(scopeTitle(srcSel, couSel), matched, qs)
        }
    }
    // produceState 换 key 时会暂留旧值；不能把旧范围的一轮保存到新范围的续刷键下。
    val scoped = loadedScope?.takeIf { it.title == scopeTitle(srcSel, couSel) }

    fun currentCount(): Int? {
        val s = scoped ?: return null
        return when {
            mode == DrillMode.WRONG -> s.questions.count { it.id in wrongIds }
            newOnly -> s.questions.count { it.id !in doneIds }
            else -> s.questions.size
        }
    }

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { Text("专项刷题") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("返回", color = cs.onBackground) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = cs.background,
                    titleContentColor = cs.onBackground,
                    navigationIconContentColor = cs.onBackground,
                ),
            )
        },
    ) { pad ->
        when {
            allPapers == null -> Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            scoped == null -> Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> Column(
                Modifier
                    .padding(pad)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                SectionLabel("题库来源")
                HintText("未勾选 = 全部来源")
                ChipToggleRow(
                    items = QuestionSource.values().toList(),
                    labelOf = { it.label },
                    selected = srcSel,
                    onToggle = { toggleSrc(it) },
                )
                Spacer(Modifier.height(14.dp))
                SectionLabel("课程 / 学科")
                HintText("未勾选 = 不限学科")
                ChipToggleRow(
                    items = Course.values().toList(),
                    labelOf = { it.label },
                    selected = couSel,
                    onToggle = { toggleCou(it) },
                )
                Spacer(Modifier.height(14.dp))
                SectionLabel("刷题模式")
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DrillMode.values().forEach { m ->
                        FilterChip(
                            selected = m == mode,
                            onClick = { modeName = m.name; persist() },
                            label = { Text(m.label) },
                        )
                    }
                }
                if (mode == DrillMode.WRONG && wrongIds.isEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    HintText("错题本是空的——先做几题，答错的会自动收进错题本。")
                }
                if (mode != DrillMode.WRONG) {
                    Spacer(Modifier.height(14.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = cs.surfaceVariant.copy(alpha = 0.5f),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "只刷新题",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = cs.onSurface,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "跳过已刷过的题，只出未做过的新题",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = cs.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Switch(checked = newOnly, onCheckedChange = { newOnly = it; persist() })
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(Modifier.height(14.dp))

                val count = currentCount()
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = cs.surface),
                    border = BorderStroke(0.5.dp, cs.outlineVariant),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "当前范围",
                            style = MaterialTheme.typography.labelMedium,
                            color = cs.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        val scopePart = scoped?.title ?: ""
                        Text(
                            when (mode) {
                                DrillMode.SEQUENTIAL -> "顺序刷题 · $scopePart"
                                DrillMode.SHUFFLED -> "乱序刷题 · $scopePart"
                                DrillMode.WRONG -> "错题重刷 · $scopePart"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = cs.onSurface,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(10.dp))
                        val runSet = scoped!!
                        // 每个模式、范围和新题开关独立记忆；续刷只用原题序，不重新过滤已刷/错题。
                        val sessionKey = DrillRound.scopeKey(mode, srcSel, couSel, newOnly)
                        val legacyKey = drillScopeKey(srcSel, couSel, newOnly)
                        val savedRound = remember(sessionKey, runSet) {
                            roundStore.load(sessionKey) ?: if (mode == DrillMode.SEQUENTIAL && !newOnly &&
                                progressStore.hasResume(legacyKey) && runSet.questions.isNotEmpty()) {
                                // 旧版只存下标：仅完整顺序题池能安全沿用，不对已变化的新题池套用旧下标。
                                DrillRound.create(mode, runSet.questions).copy(
                                    index = progressStore.resumeIndex(legacyKey).coerceIn(runSet.questions.indices))
                            } else null
                        }
                        val resumeQuestions = remember(savedRound, runSet) { savedRound?.resolve(runSet.questions) }
                        var confirmNewRound by remember(sessionKey) { mutableStateOf(false) }

                        val buildTitle = { l: List<Question> ->
                            val head = when (mode) {
                                DrillMode.SEQUENTIAL -> "顺序刷题"
                                DrillMode.SHUFFLED -> "乱序刷题"
                                DrillMode.WRONG -> "错题重刷"
                            }
                            "$head · ${runSet.title} · ${l.size}题"
                        }
                        val collect = { m: DrillMode ->
                            when (m) {
                                DrillMode.SEQUENTIAL -> filterNew(runSet.questions)
                                DrillMode.SHUFFLED -> filterNew(runSet.questions).shuffled()
                                DrillMode.WRONG -> runSet.questions.filter { it.id in wrongIds }
                            }
                        }
                        val launch = { list: List<Question>, round: DrillRound ->
                            if (roundStore.save(sessionKey, round)) {
                                progressStore.clear(legacyKey)
                                onStart(list, buildTitle(list), runSet.papers, sessionKey, round)
                            } else {
                                Toast.makeText(context, "进度保存失败，请检查存储空间后重试", Toast.LENGTH_LONG).show()
                            }
                        }
                        val startNewRound = {
                            val list = collect(mode)
                            if (list.isEmpty()) {
                                val msg = when {
                                    mode == DrillMode.WRONG -> "当前范围内没有待重刷的错题"
                                    newOnly -> "当前范围内没有未刷的新题——关掉「只刷新题」或放宽范围"
                                    else -> "当前范围内没有可刷的题目，试试放宽范围"
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            } else {
                                launch(list, DrillRound.create(mode, list))
                            }
                        }

                        if (confirmNewRound) {
                            AlertDialog(
                                onDismissRequest = { confirmNewRound = false },
                                title = { Text("开始新一轮？") },
                                text = { Text("将替换此模式、此范围未完成的一轮。已刷记录和错题本保留；乱序模式会重新洗牌。") },
                                confirmButton = {
                                    TextButton(onClick = { confirmNewRound = false; startNewRound() }) { Text("开始新一轮") }
                                },
                                dismissButton = {
                                    TextButton(onClick = { confirmNewRound = false }) { Text("取消") }
                                },
                            )
                        }
                        if (savedRound != null) {
                            Text(
                                "未完成的一轮：第 ${savedRound.index + 1} / ${savedRound.questionIds.size} 题 · 已答对 ${savedRound.correct} 题",
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant,
                            )
                            if (resumeQuestions == null) {
                                HintText("题库已更新，原题目有缺失，无法完整恢复；请开始新一轮。")
                            } else {
                                HintText("继续上次会保留题目顺序、当前选项和答题结果。")
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { if (resumeQuestions != null) launch(resumeQuestions, savedRound) },
                                    enabled = resumeQuestions != null,
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier.weight(1f).height(50.dp),
                                ) { Text("继续上次", style = MaterialTheme.typography.titleSmall) }
                                OutlinedButton(
                                    onClick = { confirmNewRound = true },
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier.height(50.dp),
                                ) { Text("开始新一轮", style = MaterialTheme.typography.titleSmall) }
                            }
                        } else {
                            Button(
                                onClick = { startNewRound() },
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                            ) { Text("开始刷题", style = MaterialTheme.typography.titleSmall) }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "本次共 $count 题",
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun HintText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun <T> ChipToggleRow(
    items: List<T>,
    labelOf: (T) -> String,
    selected: Set<T>,
    onToggle: (T) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            FilterChip(
                selected = item in selected,
                onClick = { onToggle(item) },
                label = { Text(labelOf(item)) },
            )
        }
    }
}

// ====================== 题目问题反馈 ======================

/** 题目底部的反馈入口：已反馈则置灰并勾选提示，未反馈可点开反馈弹窗。 */
@Composable
private fun FeedbackLink(reported: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (reported) {
            CheckIcon(color = cs.primary, iconSize = 14.dp)
            Spacer(Modifier.width(6.dp))
            Text(
                "已反馈本题，感谢帮助",
                style = MaterialTheme.typography.labelMedium,
                color = cs.onSurfaceVariant,
            )
        } else {
            TextButton(onClick = onClick) {
                Text(
                    "发现本题有问题？点此反馈",
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.primary,
                )
            }
        }
    }
}

/** 反馈弹窗：选问题类型（必选）+ 可选补充说明，提交后回调。 */
@Composable
private fun FeedbackDialog(qid: String, onDismiss: () -> Unit, onSubmit: (type: String, note: String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var type by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("反馈本题", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "题号 ${qid}",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                )
                Text("问题类型（必选）", style = MaterialTheme.typography.labelLarge, color = cs.onSurface)
                FeedbackTypes.ALL.forEach { (code, label) ->
                    FilterChip(
                        selected = type == code,
                        onClick = { type = code },
                        label = { Text(label) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("补充说明（可选）") },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { type?.let { onSubmit(it, note) } },
                enabled = type != null,
            ) { Text("提交") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

// ====================== 通用小组件 ======================

@Composable
private fun Chip(
    text: String,
    accent: Color = MaterialTheme.colorScheme.surfaceVariant,
    onColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(accent)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text, color = onColor, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun AnswerPill(answer: String) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(cs.primary)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            answer,
            color = cs.onPrimary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ChevronRight(color: Color) {
    Canvas(Modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val p = Path().apply {
            moveTo(w * 0.42f, h * 0.20f)
            lineTo(w * 0.68f, h * 0.50f)
            lineTo(w * 0.42f, h * 0.80f)
        }
        drawPath(
            p,
            color = color,
            style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

@Composable
fun CheckIcon(color: Color, iconSize: Dp = 20.dp) {
    Canvas(Modifier.size(iconSize)) {
        val w = size.width
        val h = size.height
        val p = Path().apply {
            moveTo(w * 0.18f, h * 0.55f)
            lineTo(w * 0.42f, h * 0.78f)
            lineTo(w * 0.84f, h * 0.26f)
        }
        drawPath(
            p,
            color = color,
            style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

@Composable
fun XIcon(color: Color, iconSize: Dp = 20.dp) {
    Canvas(Modifier.size(iconSize)) {
        val w = size.width
        val h = size.height
        val s1 = Path().apply {
            moveTo(w * 0.22f, h * 0.22f); lineTo(w * 0.78f, h * 0.78f)
        }
        val s2 = Path().apply {
            moveTo(w * 0.78f, h * 0.22f); lineTo(w * 0.22f, h * 0.78f)
        }
        drawPath(s1, color = color, style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round))
        drawPath(s2, color = color, style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round))
    }
}