import React, { useEffect, useMemo, useState } from "react";

const STEPS = [
  {
    id: "profile",
    badge: "欢迎使用",
    title: "先设置一个名字，开始你的备考空间",
    description: "OpenExam 只在本地保存学习记录、错题和 AI 配置，名字仅用于本机展示。",
    accent: "var(--accent)",
    points: [
      "本地优先，打开即用，不依赖云端账号",
      "支持题库练习、模拟考试、AI 出卷、错题复盘",
      "先用昵称开始，稍后即可直接进入刷题",
    ],
  },
  {
    id: "practice",
    badge: "高频刷题",
    title: "先从专项练习进入状态",
    description: "按模块、子分类、题量与随机方式自由组合，快速切进每日刷题节奏。",
    accent: "var(--info)",
    points: [
      "支持言语、数量、判断、资料、常识拆分训练",
      "做题模式与背题模式都可直接开始",
      "交卷后自动写入学习记录和成长数据",
    ],
    cta: { label: "完成后打开题库练习", page: "practice", tab: "题库练习" },
  },
  {
    id: "ai",
    badge: "智能提效",
    title: "AI 出卷、识别、讲解都准备好了",
    description: "配置模型后，可识别图片/PDF、生成试卷、追问讲解，把练习闭环串起来。",
    accent: "var(--success)",
    points: [
      "支持 OpenAI 兼容接口与多家模型服务商",
      "AI 生成的试卷与练习可一键保存到本地",
      "错题与当前题目可直接交给 AI 老师分析",
    ],
    cta: { label: "完成后打开 AI 出卷", page: "ai-generate", tab: "AI出卷" },
  },
  {
    id: "update",
    badge: "持续更新",
    title: "Release 更新也已经接入",
    description: "启动后自动检查 GitHub Release。Windows 支持自动安装，macOS 会提示前往下载。",
    accent: "var(--warning)",
    points: [
      "设置页可查看当前版本与更新状态",
      "可手动检查更新，及时获取新题库和功能",
      "macOS 首次打开如被拦截，可按 Release 说明处理",
    ],
    cta: { label: "完成后打开系统设置", page: "settings", tab: "" },
  },
];

function renderVisual(step, name) {
  if (step.id === "profile") {
    return (
      <div className="onboarding-visual-plain">
        <div className="onboarding-visual-kicker">Local first</div>
        <div className="onboarding-visual-hero">
          <span className="onboarding-visual-mark">{Array.from(name)[0] || "考"}</span>
          <div>
            <strong>{name}</strong>
            <span>本地备考空间 · 打开即用</span>
          </div>
        </div>
        <ul className="onboarding-visual-lines">
          <li>95+ 精选试卷</li>
          <li>1.5w+ 题目储备</li>
          <li>AI 出卷与讲题</li>
          <li>错题复盘闭环</li>
        </ul>
      </div>
    );
  }

  if (step.id === "practice") {
    return (
      <div className="onboarding-visual-plain">
        <div className="onboarding-visual-kicker">Practice</div>
        <strong className="onboarding-visual-title">判断推理 · 20 题</strong>
        <div className="onboarding-visual-meter"><span style={{ width: "78%" }} /></div>
        <ul className="onboarding-visual-lines">
          <li>正确率 78%</li>
          <li>已完成 126 题</li>
          <li>今日练习 3 次</li>
        </ul>
      </div>
    );
  }

  if (step.id === "ai") {
    return (
      <div className="onboarding-visual-plain">
        <div className="onboarding-visual-kicker">AI Tutor</div>
        <div className="onboarding-visual-dialog">
          <p className="is-user">这道图形推理怎么判断？</p>
          <p className="is-ai">先看对称，再排除旋转规律不一致的选项。</p>
        </div>
      </div>
    );
  }

  return (
    <div className="onboarding-visual-plain">
      <div className="onboarding-visual-kicker">Updates</div>
      <strong className="onboarding-visual-title">GitHub Release</strong>
      <ul className="onboarding-visual-lines">
        <li>当前版本可手动检查</li>
        <li>Windows 自动下载安装</li>
        <li>macOS 检测并跳转下载</li>
      </ul>
    </div>
  );
}

export default function OnboardingTour({ open, closing = false, defaultName = "考生用户", onFinish, onSkip, onNavigate }) {
  const [step, setStep] = useState(0);
  const [name, setName] = useState(defaultName);

  useEffect(() => {
    if (!open) return;
    setStep(0);
    setName(String(defaultName || "考生用户").trim() || "考生用户");
  }, [open, defaultName]);

  const safeName = useMemo(() => {
    return String(name || "").replace(/\s+/g, " ").trim().slice(0, 24) || "考生用户";
  }, [name]);

  const visible = open || closing;
  if (!visible) return null;

  const current = STEPS[step];
  const isLast = step === STEPS.length - 1;

  const handlePrimary = () => {
    if (isLast) {
      onFinish?.({ name: safeName });
      return;
    }
    setStep((value) => Math.min(value + 1, STEPS.length - 1));
  };

  return (
    <div className={`onboarding-overlay ${closing ? "is-closing" : ""}`} role="dialog" aria-modal="true" aria-label="首次引导">
      <div className="onboarding-shell" style={{ "--onboarding-accent": current.accent }}>
        <section key={`visual-${current.id}`} className="onboarding-showcase">
          <div className="onboarding-showcase-bg" />
          <div className="onboarding-showcase-head">
            <span>{current.badge}</span>
            <strong>OpenExam</strong>
          </div>
          <div className="onboarding-showcase-main">
            {renderVisual(current, safeName)}
          </div>
          <div className="onboarding-showcase-foot">
            <span>引导 {step + 1} / {STEPS.length}</span>
            <div className="onboarding-progress-track">
              <span style={{ width: `${((step + 1) / STEPS.length) * 100}%` }} />
            </div>
          </div>
        </section>

        <section key={`panel-${current.id}`} className="onboarding-panel">
          <div className="onboarding-panel-top">
            <div className="onboarding-step-badge">Step {step + 1} / {STEPS.length}</div>
            <button className="onboarding-skip" onClick={() => onSkip?.({ name: safeName })}>跳过</button>
          </div>

          <div className="onboarding-panel-body">
            <div className="onboarding-panel-copy">
              <div className="onboarding-badge">{current.badge}</div>
              <h2 className="onboarding-title">{current.title}</h2>
              <p className="onboarding-desc">{current.description}</p>
            </div>

            {current.id === "profile" && (
              <label className="onboarding-input-wrap">
                <span className="onboarding-input-label">怎么称呼你</span>
                <input
                  className="onboarding-input"
                  value={name}
                  maxLength={24}
                  placeholder="例如：木木 / 小林 / 阿康"
                  onChange={(event) => setName(event.target.value)}
                />
                <span className="onboarding-input-tip">仅用于本地展示，可稍后修改。</span>
              </label>
            )}

            <ul className="onboarding-feature-list">
              {current.points.map((point) => (
                <li key={point} className="onboarding-feature-item">
                  <span className="onboarding-feature-mark" aria-hidden="true" />
                  <span>{point}</span>
                </li>
              ))}
            </ul>

            {current.cta && (
              <button className="onboarding-link-btn" onClick={() => onNavigate?.(current.cta)}>
                {current.cta.label}
              </button>
            )}
          </div>

          <div className="onboarding-panel-bottom">
            <div className="onboarding-dots">
              {STEPS.map((item, index) => (
                <button
                  key={item.id}
                  className={`onboarding-dot ${index === step ? "active" : ""}`}
                  onClick={() => setStep(index)}
                  aria-label={`切换到第 ${index + 1} 步`}
                />
              ))}
            </div>
            <div className="onboarding-actions">
              <button
                className="onboarding-btn secondary"
                onClick={() => setStep((value) => Math.max(value - 1, 0))}
                disabled={step === 0}
              >
                上一步
              </button>
              <button className="onboarding-btn primary" onClick={handlePrimary}>
                {isLast ? "进入 OpenExam" : "下一步"}
              </button>
            </div>
          </div>
        </section>
      </div>
    </div>
  );
}
