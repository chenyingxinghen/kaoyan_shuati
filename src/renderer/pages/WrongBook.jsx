import React, { useEffect, useMemo, useState } from "react";
import RichQuestionContent from "../components/RichQuestionContent.jsx";

const CN = { yanyu: "言语理解", shuliang: "数量关系", panduan: "判断推理", ziliao: "资料分析", changshi: "常识判断" };
const FILTERS = [{ key: "due", label: "待复习" }, { key: "all", label: "全部" }, { key: "learning", label: "熟悉中" }, { key: "mastered", label: "已掌握" }];
const STAGES = [
  { name: "新错题", color: "var(--danger)" },
  { name: "1天复习", color: "var(--warning)" },
  { name: "2天复习", color: "var(--info)" },
  { name: "4天复习", color: "var(--accent)" },
  { name: "7天复习", color: "var(--info)" },
  { name: "15天巩固", color: "var(--accent)" },
  { name: "30天掌握", color: "var(--success)" },
];
const parseTime = (value) => value ? new Date(String(value).replace(" ", "T") + "Z") : null;
const formatTime = (value, due) => {
  const date = parseTime(value); if (!date) return "立即复习";
  const diff = Math.round((date.getTime() - Date.now()) / 3600000);
  if (due || diff <= 0) return "现在可复习";
  if (diff < 24) return `${diff} 小时后`;
  const days = Math.round(diff / 24); return `${days} 天后`;
};
const formatShort = (value) => {
  const date = parseTime(value); if (!date) return "刚刚";
  const diff = Date.now() - date.getTime();
  if (diff < 3600000) return `${Math.max(1, Math.floor(diff / 60000))} 分钟前`;
  if (diff < 86400000) return `${Math.floor(diff / 3600000)} 小时前`;
  if (diff < 604800000) return `${Math.floor(diff / 86400000)} 天前`;
  return `${date.getUTCMonth() + 1}/${date.getUTCDate()}`;
};
const stageMeta = (stage = 0) => STAGES[Math.max(0, Math.min(stage, STAGES.length - 1))];
const sortByUrgency = (a, b) => (Number(b.is_due) - Number(a.is_due)) || ((parseTime(a.next_review_at)?.getTime() || 0) - (parseTime(b.next_review_at)?.getTime() || 0)) || ((parseTime(b.added_at)?.getTime() || 0) - (parseTime(a.added_at)?.getTime() || 0));

const normalizeOptions = (options) => Array.isArray(options) ? options.map((opt) => ({
  key: String(opt?.key || ""),
  content: String(opt?.content || ""),
})).filter((opt) => opt.key) : [];

export default function WrongBook({ onRedo, onAskAI }) {
  const [list, setList] = useState([]), [loading, setLoading] = useState(true), [filter, setFilter] = useState("due"), [expandedId, setExpandedId] = useState(null), [busyId, setBusyId] = useState(""), [error, setError] = useState("");
  const loadList = async () => {
    if (!window.openexam?.db?.getWrongQuestions) return setLoading(false);
    setLoading(true); setError("");
    try { const rows = await window.openexam.db.getWrongQuestions(); setList((rows || []).sort(sortByUrgency)); }
    catch (e) { setError(e.message || "错题读取失败"); }
    setLoading(false);
  };
  useEffect(() => { loadList(); }, []);

  const stats = useMemo(() => {
    const today = new Date().toISOString().slice(0, 10); const catCount = {};
    list.forEach((item) => { catCount[item.category] = (catCount[item.category] || 0) + 1; });
    const weakest = Object.entries(catCount).sort((a, b) => b[1] - a[1])[0];
    return { due: list.filter((item) => item.is_due).length, today: list.filter((item) => String(item.added_at || "").startsWith(today)).length, mastered: list.filter((item) => (item.review_stage || 0) >= STAGES.length - 1 && !item.is_due).length, weakest: weakest ? CN[weakest[0]] || weakest[0] : "暂无" };
  }, [list]);

  const filtered = useMemo(() => list.filter((item) => filter === "all" || (filter === "due" && item.is_due) || (filter === "learning" && !item.is_due && (item.review_stage || 0) < STAGES.length - 1) || (filter === "mastered" && !item.is_due && (item.review_stage || 0) >= STAGES.length - 1)).sort(sortByUrgency), [list, filter]);
  const startRedo = (sourceList, filterOverride) => {
    if (typeof onRedo !== "function") return;
    const source = Array.isArray(sourceList) && sourceList.length
      ? sourceList
      : (filtered.length ? filtered : list);
    const nextFilter = filterOverride || filter;
    const seen = new Set();
    const questions = source.map((item, index) => {
      const id = String(item.question_id || item.id || `wrong_redo_${index}`);
      if (seen.has(id)) return null;
      seen.add(id);
      return {
        id,
        type: item.type || "single",
        category: item.category || "",
        sub_category: item.sub_category || "",
        content: item.content || "",
        content_html: item.content_html || "",
        options: normalizeOptions(item.options),
        answer: item.answer || item.correct_answer || "",
        analysis: item.analysis || "",
        analysis_html: item.analysis_html || "",
        paper_id: item.paper_id || null,
      };
    }).filter((item) => item && item.content && item.answer && item.options.length > 0);

    if (!questions.length) {
      setError("当前筛选下没有可重做的完整题目");
      return;
    }
    onRedo(questions, { filter: nextFilter, count: questions.length });
  };
  const focusDue = () => {
    setFilter("due");
    const dueItems = list.filter((item) => item.is_due);
    if (dueItems.length) {
      startRedo(dueItems, "due");
      return;
    }
    const first = list.find((item) => item.is_due);
    if (first) setExpandedId(first.id);
  };
  const updateReview = async (item, outcome) => {
    if (!window.openexam?.db?.reviewWrongQuestion) return;
    const key = `${item.id}:${outcome}`; setBusyId(key); setError("");
    try {
      const updated = await window.openexam.db.reviewWrongQuestion({ questionId: item.question_id, outcome });
      setList((prev) => prev.map((row) => row.id === updated.id ? updated : row).sort(sortByUrgency)); setExpandedId(updated.id);
    } catch (e) { setError(e.message || "复习状态更新失败"); }
    setBusyId("");
  };

  return (
    <section className="main-panel" style={{ padding: "0 22px 22px", display: "flex", flexDirection: "column", gap: 20, overflow: "auto", height: "100%", boxSizing: "border-box" }}>
      <header style={{ padding: "16px 0 0", display: "flex", flexDirection: "column", gap: 14 }}>
        <div className="breadcrumb" style={{ margin: 0 }}>我的 &gt; 错题本</div>
        <div style={{ display: "flex", alignItems: "flex-end", justifyContent: "space-between", gap: 16, flexWrap: "wrap", paddingBottom: 14, borderBottom: "1px solid var(--line)" }}>
          <div>
            <div style={{ fontSize: 12, color: "var(--muted)", marginBottom: 6 }}>今日复习台</div>
            <div style={{ fontSize: 28, fontWeight: 700, letterSpacing: "-0.6px", lineHeight: 1 }}>
              {stats.due}
              <span style={{ fontSize: 14, fontWeight: 500, marginLeft: 8, color: "var(--muted)" }}>道待复习</span>
            </div>
            <div style={{ marginTop: 8, fontSize: 13, lineHeight: 1.6, color: "var(--muted)", maxWidth: 420 }}>
              优先清掉到期错题，再把新错题推进到下一阶段。
            </div>
          </div>
          <div style={{ display: "flex", gap: 10, flexWrap: "wrap" }}>
            <button type="button" onClick={focusDue} className="primary-btn" style={{ padding: "8px 14px", borderRadius: 8 }}>开始今日复习</button>
            <button type="button" onClick={() => startRedo()} style={{ padding: "8px 14px", borderRadius: 8, border: "1px solid var(--line)", background: "transparent", color: "var(--text)", fontWeight: 600, cursor: "pointer" }}>按筛选开练</button>
            <button type="button" onClick={loadList} style={{ padding: "8px 14px", borderRadius: 8, border: "none", background: "transparent", color: "var(--muted)", fontWeight: 600, cursor: "pointer" }}>刷新</button>
          </div>
        </div>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(4, minmax(0, 1fr))", gap: 0 }}>
          {[
            { label: "今日新增", value: `+${stats.today}`, tone: "var(--danger)" },
            { label: "已掌握", value: stats.mastered, tone: "var(--success)" },
            { label: "累计错题", value: list.length, tone: "var(--accent)" },
            { label: "薄弱板块", value: stats.weakest, tone: "var(--warning)" },
          ].map((card, index, arr) => (
            <div
              key={card.label}
              style={{
                padding: "4px 16px 4px 0",
                borderRight: index < arr.length - 1 ? "1px solid var(--line)" : "none",
                display: "flex",
                flexDirection: "column",
                gap: 6,
              }}
            >
              <span style={{ fontSize: 11, color: "var(--muted)" }}>{card.label}</span>
              <span style={{ fontSize: typeof card.value === "number" ? 22 : 15, fontWeight: 700, color: card.tone, letterSpacing: "-0.3px" }}>{card.value}</span>
            </div>
          ))}
        </div>
      </header>

      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 12, flexWrap: "wrap" }}>
        <div style={{ display: "flex", gap: 14, flexWrap: "wrap", borderBottom: "1px solid var(--line)", paddingBottom: 0 }}>
          {FILTERS.map((item) => (
            <button
              key={item.key}
              type="button"
              onClick={() => setFilter(item.key)}
              style={{
                padding: "8px 0",
                border: "none",
                borderBottom: filter === item.key ? "2px solid var(--accent)" : "2px solid transparent",
                background: "transparent",
                color: filter === item.key ? "var(--accent)" : "var(--muted)",
                fontSize: 12,
                fontWeight: 700,
                cursor: "pointer",
                marginBottom: -1,
              }}
            >
              {item.label}
            </button>
          ))}
        </div>
        <div style={{ display: "flex", alignItems: "center", gap: 12, flexWrap: "wrap" }}>
          <div style={{ fontSize: 12, color: "var(--muted)" }}>按紧急度排序 · {filtered.length} 道</div>
          <button
            type="button"
            onClick={startRedo}
            disabled={!list.length}
            style={{
              padding: "6px 0",
              border: "none",
              background: "transparent",
              color: "var(--accent)",
              fontSize: 12,
              fontWeight: 700,
              cursor: list.length ? "pointer" : "not-allowed",
              opacity: list.length ? 1 : 0.55,
            }}
          >
            按当前筛选重做
          </button>
        </div>
      </div>

      {error && (
        <div style={{ padding: "10px 0", borderTop: "1px solid var(--danger-border)", borderBottom: "1px solid var(--danger-border)", color: "var(--danger)", fontSize: 12 }}>
          {error}
        </div>
      )}

      {loading ? (
        <div style={{ padding: "80px 0", textAlign: "center", color: "var(--muted)" }}>错题加载中…</div>
      ) : filtered.length === 0 ? (
        <div style={{ padding: "72px 0", textAlign: "center", color: "var(--muted)", borderTop: "1px dashed var(--line)", borderBottom: "1px dashed var(--line)" }}>
          当前筛选下暂无错题，状态很棒。
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column" }}>
          {filtered.map((item) => {
            const meta = stageMeta(item.review_stage);
            const open = expandedId === item.id;
            const busyRemember = busyId === `${item.id}:remembered`;
            const busyAgain = busyId === `${item.id}:again`;
            return (
              <article key={item.id} style={{ borderBottom: "1px solid var(--line)", background: "transparent", overflow: "hidden" }}>
                <button type="button" onClick={() => setExpandedId(open ? null : item.id)} style={{ width: "100%", padding: 0, border: "none", background: "transparent", textAlign: "left", cursor: "pointer" }}>
                  <div style={{ padding: "16px 0", display: "flex", flexDirection: "column", gap: 10 }}>
                    <div style={{ display: "flex", alignItems: "center", gap: 10, flexWrap: "wrap", fontSize: 11 }}>
                      <span style={{ color: "var(--text)", fontWeight: 700 }}>{CN[item.category] || item.category || "综合"}</span>
                      <span style={{ color: meta.color, fontWeight: 600 }}>{meta.name}</span>
                      <span style={{ color: item.is_due ? "var(--accent)" : "var(--muted)", fontWeight: 600 }}>{item.is_due ? "待处理" : formatTime(item.next_review_at, item.is_due)}</span>
                      <span style={{ marginLeft: "auto", color: "var(--muted)" }}>{item.paper_title || "未关联试卷"}</span>
                    </div>
                    <div style={{ fontSize: 14, lineHeight: 1.75, color: "var(--text)", fontWeight: 600 }}>
                      {open ? <RichQuestionContent value={item.content_html || item.content} /> : (!item.content || item.content.length <= 110 ? item.content : `${item.content.slice(0, 110)}...`)}
                    </div>
                    <div style={{ display: "grid", gridTemplateColumns: "repeat(4, minmax(0, 1fr))", gap: 10, fontSize: 12 }}>
                      <div>
                        <div style={{ color: "var(--muted)", marginBottom: 4 }}>你的答案</div>
                        <div style={{ color: "var(--danger)", fontWeight: 700 }}>{item.user_answer || "未作答"}</div>
                      </div>
                      <div>
                        <div style={{ color: "var(--muted)", marginBottom: 4 }}>正确答案</div>
                        <div style={{ color: "var(--success)", fontWeight: 700 }}>{item.correct_answer || item.answer || "-"}</div>
                      </div>
                      <div>
                        <div style={{ color: "var(--muted)", marginBottom: 4 }}>复习次数</div>
                        <div style={{ color: "var(--text)", fontWeight: 700 }}>{item.review_count || 0} 次</div>
                      </div>
                      <div>
                        <div style={{ color: "var(--muted)", marginBottom: 4 }}>加入时间</div>
                        <div style={{ color: "var(--text)", fontWeight: 700 }}>{formatShort(item.added_at)}</div>
                      </div>
                    </div>
                  </div>
                </button>
                {open && (
                  <div style={{ borderTop: "1px solid var(--line)", padding: "0 0 18px", display: "flex", flexDirection: "column", gap: 14 }}>
                    {Array.isArray(item.options) && item.options.length > 0 && (
                      <div style={{ display: "grid", gap: 0, marginTop: 12 }}>
                        {item.options.map((opt) => (
                          <div
                            key={opt.key}
                            style={{
                              padding: "10px 0",
                              borderBottom: "1px solid var(--line)",
                              fontSize: 12,
                              lineHeight: 1.6,
                              color: "var(--text)",
                            }}
                          >
                            <strong style={{ marginRight: 8, color: opt.key === (item.answer || item.correct_answer) ? "var(--success)" : opt.key === item.user_answer ? "var(--danger)" : "var(--text)" }}>{opt.key}.</strong>
                            <RichQuestionContent value={opt.content} className="rich-question-option" style={{ display: "inline-block", verticalAlign: "top", width: "calc(100% - 28px)" }} />
                          </div>
                        ))}
                      </div>
                    )}
                    {(item.analysis_html || item.analysis) && (
                      <div style={{ padding: "12px 0", borderTop: "1px solid var(--line)", borderBottom: "1px solid var(--line)", fontSize: 12, lineHeight: 1.8, color: "var(--text)" }}>
                        <div style={{ fontSize: 11, fontWeight: 700, color: "var(--muted)", marginBottom: 6 }}>解析</div>
                        <RichQuestionContent value={item.analysis_html || item.analysis} className="rich-question-analysis" />
                      </div>
                    )}
                    <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 12, flexWrap: "wrap" }}>
                      <div style={{ fontSize: 12, color: "var(--muted)" }}>
                        上次复习 {item.last_review ? formatShort(item.last_review) : "尚未开始"} · 下次 {formatTime(item.next_review_at, item.is_due)}
                      </div>
                      <div style={{ display: "flex", gap: 10 }}>
                        <button type="button" onClick={() => onAskAI?.(item)} style={{ padding: "8px 0", border: "none", background: "transparent", color: "var(--accent)", fontWeight: 700, cursor: "pointer" }}>问 AI</button>
                        <button type="button" onClick={() => updateReview(item, "again")} disabled={busyAgain || !!busyId} style={{ padding: "8px 12px", borderRadius: 8, border: "1px solid var(--danger-border)", background: "transparent", color: "var(--danger)", fontWeight: 700, cursor: busyId ? "wait" : "pointer" }}>{busyAgain ? "处理中..." : "继续复习"}</button>
                        <button type="button" onClick={() => updateReview(item, "remembered")} disabled={busyRemember || !!busyId} className="primary-btn" style={{ padding: "8px 14px", borderRadius: 8, boxShadow: "none" }}>{busyRemember ? "处理中..." : "记住了"}</button>
                      </div>
                    </div>
                  </div>
                )}
              </article>
            );
          })}
        </div>
      )}
    </section>
  );
}
