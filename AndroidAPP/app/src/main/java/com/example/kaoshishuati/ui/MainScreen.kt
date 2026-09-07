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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kaoshishuati.R
import com.example.kaoshishuati.data.BankDb
import com.example.kaoshishuati.data.Option
import com.example.kaoshishuati.data.Paper
import com.example.kaoshishuati.data.ProgressStore
import com.example.kaoshishuati.data.Question
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
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun App(bank: BankDb, wrongStore: WrongStore, progressStore: ProgressStore) {
    val cs = MaterialTheme.colorScheme
    var route by remember { mutableStateOf<Route>(Route.Bank) }
    Surface(modifier = Modifier.fillMaxSize(), color = cs.background) {
        when (val r = route) {
            is Route.Practice -> PracticeScreen(bank, wrongStore, progressStore, r.paper) { route = Route.Bank }
            Route.Wrong -> WrongScreen(bank, wrongStore) { route = Route.Bank }
            Route.Bank -> BankScreen(bank, wrongStore,
                onOpen = { route = Route.Practice(it) }, onWrong = { route = Route.Wrong })
        }
    }
}

// ====================== 题库首页 ======================

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun BankScreen(bank: BankDb, wrongStore: WrongStore, onOpen: (Paper) -> Unit, onWrong: () -> Unit) {
    val papers by produceState<List<Paper>?>(null) {
        value = withContext(Dispatchers.IO) { bank.papers() }
    }
    val cs = MaterialTheme.colorScheme

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { Text("考研政治刷题", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = cs.background,
                    titleContentColor = cs.onBackground,
                ),
            )
        },
    ) { pad ->
        val wrong = wrongStore.ids()
        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item("hero") { HeroCard(papers = papers) }
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
                        title = "总题数",
                        value = papers?.sumOf { it.questionCount }?.toString() ?: "—",
                        accent = cs.primary,
                        onClick = null,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            when (val list = papers) {
                null -> item("loading") {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                else -> {
                    val real = list.filter { it.title.contains("真题") }.sortedByDescending { it.year }
                    val mock = list.filterNot { it.title.contains("真题") }.sortedByDescending { it.year }
                    if (real.isNotEmpty()) {
                        item("h-real") { GroupHeader("历年真题", "${real.size} 卷") }
                        items(real, key = { "r-${it.id}" }) { p -> PaperCard(p) { onOpen(p) } }
                    }
                    if (mock.isNotEmpty()) {
                        item("h-mock") { GroupHeader("模拟题集", "${mock.size} 卷") }
                        items(mock, key = { "m-${it.id}" }) { p -> PaperCard(p) { onOpen(p) } }
                    }
                }
            }
        }
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
private fun PaperCard(p: Paper, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val isReal = p.title.contains("真题")
    val accent = if (isReal) com.example.kaoshishuati.ui.theme.Amber500 else Brand500
    val accentContainer = if (isReal) com.example.kaoshishuati.ui.theme.Amber50 else com.example.kaoshishuati.ui.theme.Brand50
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
                    "${p.questionCount} 题 · ${if (isReal) "历年真题" else "模拟题集"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            ChevronRight(color = cs.onSurfaceVariant)
        }
    }
}

// ====================== 练习页 ======================

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun PracticeScreen(bank: BankDb, wrongStore: WrongStore, progressStore: ProgressStore, paper: Paper, onExit: () -> Unit) {
    val qs by produceState<List<Question>?>(null) {
        value = withContext(Dispatchers.IO) { bank.questions(paper.id) }
    }
    val cs = MaterialTheme.colorScheme
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
            when (val list = qs) {
                null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                else -> if (list.isEmpty()) Text("本卷暂无题目", Modifier.align(Alignment.Center))
                else Pager(list, wrongStore, progressStore, paper.id, onExit)
            }
        }
    }
}

@Composable
private fun Pager(qs: List<Question>, wrongStore: WrongStore, progressStore: ProgressStore, paperId: String, onExit: () -> Unit) {
    val resumeFrom = progressStore.resumeIndex(paperId).coerceIn(0, qs.size - 1)
    var index by rememberSaveable { mutableIntStateOf(resumeFrom) }
    var correct by rememberSaveable { mutableIntStateOf(0) }
    var finished by rememberSaveable { mutableStateOf(false) }

    val q = qs[index]
    var singleSel by remember(q.id) { mutableStateOf<String?>(null) }
    var multiSel by remember(q.id) { mutableStateOf(setOf<String>()) }
    var revealed by remember(q.id) { mutableStateOf(false) }

    if (finished) {
        ResultView(
            total = qs.size,
            correct = correct,
            onRestart = {
                finished = false; index = 0; correct = 0
                progressStore.clear(paperId)
            },
            onExit = {
                progressStore.clear(paperId)
                onExit()
            },
        )
        return
    }

    fun grade(ok: Boolean) {
        if (ok) correct += 1 else wrongStore.add(q.id)
        revealed = true
    }
    fun selectSingle(k: String) { if (!revealed) { singleSel = k; grade(k == q.answer) } }
    fun toggleMulti(k: String) { if (!revealed) multiSel = if (k in multiSel) multiSel - k else multiSel + k }
    fun submitMulti() { if (multiSel.isNotEmpty()) grade(multiSel.sorted().joinToString("") == q.answer) }
    fun advance() {
        if (index == qs.size - 1) {
            finished = true
            progressStore.clear(paperId)
        } else {
            val next = index + 1
            index = next
            progressStore.save(paperId, next)
        }
    }

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
    singleSel: String?,
    multiSel: Set<String>,
    revealed: Boolean,
    onSelectSingle: (String) -> Unit,
    onToggleMulti: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
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
        border = BorderStroke(if (selected || isCorrect) 1.2.dp else 0.5.dp, border),
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
private fun ResultView(total: Int, correct: Int, onRestart: () -> Unit, onExit: () -> Unit) {
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
            ) { Text("再练一遍") }
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