import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import { AchievementMedal } from './AchievementMedal.jsx';
import { getAchievementGroupLabel, getAchievementTierStyle, getMedalPalette } from './AchievementVisuals.jsx';
import { bootstrapAchievementUnlockSeen, markAchievementsSeen } from '../utils/achievementUnlock.js';
import { normalizeAchievements } from '../utils/achievementUtils.js';

const AchievementUnlockContext = createContext(null);

function usePrefersReducedMotion() {
  const [reduced, setReduced] = useState(() => {
    if (typeof window === 'undefined' || !window.matchMedia) return false;
    return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  });
  useEffect(() => {
    if (!window.matchMedia) return undefined;
    const mq = window.matchMedia('(prefers-reduced-motion: reduce)');
    const onChange = () => setReduced(mq.matches);
    mq.addEventListener?.('change', onChange);
    return () => mq.removeEventListener?.('change', onChange);
  }, []);
  return reduced;
}

function InkBurstCanvas({ active, palette, reducedMotion }) {
  const canvasRef = useRef(null);
  const rafRef = useRef(0);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas || !active || reducedMotion) return undefined;

    const ctx = canvas.getContext('2d');
    if (!ctx) return undefined;

    let disposed = false;
    const dpr = Math.min(window.devicePixelRatio || 1, 2);

    const resize = () => {
      const { clientWidth: w, clientHeight: h } = canvas;
      canvas.width = Math.max(1, Math.floor(w * dpr));
      canvas.height = Math.max(1, Math.floor(h * dpr));
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    };
    resize();

    const ink = palette?.ink || '#5c3d22';
    const foil = palette?.rim || '#b88449';
    const hexToRgb = (hex) => {
      const raw = String(hex || '').replace('#', '');
      if (raw.length < 6) return { r: 90, g: 66, b: 40 };
      return {
        r: parseInt(raw.slice(0, 2), 16),
        g: parseInt(raw.slice(2, 4), 16),
        b: parseInt(raw.slice(4, 6), 16),
      };
    };
    const inkRgb = hexToRgb(ink);
    const foilRgb = hexToRgb(foil);
    const cx = () => canvas.clientWidth / 2;
    const cy = () => canvas.clientHeight / 2;

    const droplets = Array.from({ length: 14 }, (_, i) => {
      const angle = (Math.PI * 2 * i) / 14 + (Math.random() - 0.5) * 0.2;
      const speed = 28 + Math.random() * 50;
      return {
        angle,
        speed,
        size: 1 + Math.random() * 1.8,
        life: 0.4 + Math.random() * 0.35,
        kind: Math.random() > 0.6 ? 'foil' : 'ink',
      };
    });

    const started = performance.now();
    const duration = 900;

    const draw = (now) => {
      if (disposed) return;
      const t = Math.min(1, (now - started) / duration);
      const ease = 1 - (1 - t) ** 3;
      const w = canvas.clientWidth;
      const h = canvas.clientHeight;
      ctx.clearRect(0, 0, w, h);

      const centerX = cx();
      const centerY = cy();

      // soft ink bloom
      const bloomR = 28 + ease * 120;
      const bloom = ctx.createRadialGradient(centerX, centerY, 4, centerX, centerY, bloomR);
      bloom.addColorStop(0, `rgba(50, 36, 24, ${0.22 * (1 - t * 0.65)})`);
      bloom.addColorStop(0.45, `rgba(90, 66, 40, ${0.10 * (1 - t)})`);
      bloom.addColorStop(1, 'rgba(90, 66, 40, 0)');
      ctx.fillStyle = bloom;
      ctx.beginPath();
      ctx.arc(centerX, centerY, bloomR, 0, Math.PI * 2);
      ctx.fill();

      // seal impression ring
      ctx.beginPath();
      ctx.arc(centerX, centerY, 54 + ease * 36, 0, Math.PI * 2);
      ctx.strokeStyle = `rgba(80, 58, 36, ${0.18 * (1 - t)})`;
      ctx.lineWidth = 1.2;
      ctx.stroke();

      droplets.forEach((p) => {
        const dist = p.speed * ease;
        const x = centerX + Math.cos(p.angle) * dist;
        const y = centerY + Math.sin(p.angle) * dist - ease * 12;
        const alpha = Math.max(0, p.life * (1 - t));
        if (alpha <= 0.01) return;
        ctx.beginPath();
        if (p.kind === 'foil') {
          ctx.fillStyle = `rgba(${foilRgb.r},${foilRgb.g},${foilRgb.b},${alpha * 0.85})`;
          ctx.rect(x - p.size * 0.4, y - p.size * 1.1, p.size * 0.8, p.size * 2.2);
          ctx.fill();
        } else {
          ctx.fillStyle = `rgba(${inkRgb.r},${inkRgb.g},${inkRgb.b},${alpha})`;
          ctx.arc(x, y, p.size, 0, Math.PI * 2);
          ctx.fill();
        }
      });

      if (t < 1) rafRef.current = requestAnimationFrame(draw);
    };

    rafRef.current = requestAnimationFrame(draw);
    const onResize = () => resize();
    window.addEventListener('resize', onResize);

    return () => {
      disposed = true;
      cancelAnimationFrame(rafRef.current);
      window.removeEventListener('resize', onResize);
      ctx.clearRect(0, 0, canvas.width, canvas.height);
    };
  }, [active, palette, reducedMotion]);

  return <canvas ref={canvasRef} className="ach-ceremony-canvas" aria-hidden="true" />;
}

function CeremonyPanel({ achievement, remaining, onAccept, reducedMotion }) {
  const tierStyle = getAchievementTierStyle(achievement?.tier);
  const palette = getMedalPalette(achievement?.tier);
  const [phase, setPhase] = useState(reducedMotion ? 'settled' : 'enter');
  const acceptingRef = useRef(false);

  useEffect(() => {
    acceptingRef.current = false;
    if (reducedMotion) {
      setPhase('settled');
      return undefined;
    }
    setPhase('enter');
    const t1 = window.setTimeout(() => setPhase('stamp'), 180);
    const t2 = window.setTimeout(() => setPhase('settled'), 1100);
    return () => {
      window.clearTimeout(t1);
      window.clearTimeout(t2);
    };
  }, [achievement?.id, reducedMotion]);

  const handleAccept = useCallback(() => {
    if (acceptingRef.current) return;
    acceptingRef.current = true;
    onAccept?.();
  }, [onAccept]);

  useEffect(() => {
    const onKey = (event) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        handleAccept();
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [handleAccept]);

  return (
    <div className={`ach-ceremony is-${phase}`} role="dialog" aria-modal="true" aria-label="成就解锁">
      <button type="button" className="ach-ceremony-scrim" aria-label="收下成就" onClick={handleAccept} />
      {!reducedMotion && (phase === 'stamp' || phase === 'settled') && (
        <InkBurstCanvas active={phase === 'stamp'} palette={palette} reducedMotion={false} />
      )}

      <div className="ach-ceremony-card" onClick={(event) => event.stopPropagation()}>
        <div className={`ach-ceremony-medal-wrap is-${phase}`}>
          <AchievementMedal
            achievement={achievement}
            size={168}
            showProgress={false}
            className="ach-ceremony-medal"
          />
        </div>
        <div className="ach-ceremony-kicker">{achievement?.isPreview ? '动画预览' : '成就解锁'}</div>
        <h3 className="ach-ceremony-title">{achievement?.name}</h3>
        <p className="ach-ceremony-desc">{achievement?.desc}</p>
        <div className="ach-ceremony-meta">
          <span style={{ color: tierStyle.color }}>{tierStyle.label}</span>
          <span>·</span>
          <span>{getAchievementGroupLabel(achievement?.group)}</span>
        </div>
        <button type="button" className="ach-ceremony-accept" onClick={handleAccept}>
          {remaining > 0 ? `收下 · 还有 ${remaining} 枚` : '太棒了'}
        </button>
      </div>
    </div>
  );
}

export function AchievementUnlockProvider({ children }) {
  const [queue, setQueue] = useState([]);
  const reducedMotion = usePrefersReducedMotion();
  const current = queue[0] || null;

  // 应用启动时先静默记录已有解锁，避免交卷路径命中「首次 bootstrap」吞掉本次成就
  useEffect(() => {
    let cancelled = false;
    (async () => {
      if (!window.openexam?.db?.getGrowthData) return;
      try {
        const data = await window.openexam.db.getGrowthData();
        if (cancelled) return;
        const achievements = normalizeAchievements(data?.achievements, data || {});
        bootstrapAchievementUnlockSeen(achievements);
      } catch {
        /* ignore */
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const enqueueUnlocks = useCallback((items = []) => {
    const list = (Array.isArray(items) ? items : []).filter((item) => item?.id && item.unlocked);
    if (!list.length) return;
    setQueue((prev) => {
      const seen = new Set(prev.map((item) => item.id));
      const next = [...prev];
      list.forEach((item) => {
        if (!seen.has(item.id)) {
          seen.add(item.id);
          next.push(item);
        }
      });
      return next;
    });
  }, []);

  const acceptCurrent = useCallback(() => {
    setQueue((prev) => {
      const [head, ...rest] = prev;
      // 预览不写入 seen，避免污染真实解锁仪式
      if (head?.id && !head.isPreview) markAchievementsSeen([head.id]);
      return rest;
    });
  }, []);

  const value = useMemo(() => ({ enqueueUnlocks }), [enqueueUnlocks]);

  return (
    <AchievementUnlockContext.Provider value={value}>
      {children}
      {current && (
        <CeremonyPanel
          key={current.id}
          achievement={current}
          remaining={Math.max(0, queue.length - 1)}
          onAccept={acceptCurrent}
          reducedMotion={reducedMotion}
        />
      )}
    </AchievementUnlockContext.Provider>
  );
}

export function useAchievementUnlock() {
  const ctx = useContext(AchievementUnlockContext);
  if (!ctx) {
    return {
      enqueueUnlocks: () => {},
    };
  }
  return ctx;
}
