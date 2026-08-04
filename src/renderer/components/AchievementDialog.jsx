import React, { useEffect } from 'react';
import { getAchievementGroupLabel, getAchievementTierStyle } from './AchievementVisuals.jsx';
import { AchievementMedal } from './AchievementMedal.jsx';

export default function AchievementDialog({ achievement, onClose }) {
  useEffect(() => {
    if (!achievement) return undefined;
    const onKeyDown = (event) => event.key === 'Escape' && onClose?.();
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [achievement, onClose]);

  if (!achievement) return null;

  const tierStyle = getAchievementTierStyle(achievement.tier);
  const progressPercent = Math.round((achievement.progressRatio || 0) * 100);
  const unlocked = Boolean(achievement.unlocked);

  return (
    <div className="ach-keep-overlay" role="dialog" aria-modal="true" onClick={() => onClose?.()}>
      <div className="ach-keep-sheet" onClick={(event) => event.stopPropagation()}>
        <button type="button" className="ach-keep-back" onClick={() => onClose?.()} aria-label="关闭">←</button>

        <div className="ach-keep-hero">
          <AchievementMedal
            achievement={achievement}
            size={168}
            showProgress={!unlocked}
            className="ach-keep-medal"
          />
        </div>

        <h3 className="ach-keep-title">{achievement.name}</h3>
        <p className="ach-keep-desc">{achievement.desc}</p>
        <div className="ach-keep-sub">
          <span style={{ color: tierStyle.color }}>{tierStyle.label}</span>
          <span>·</span>
          <span>{getAchievementGroupLabel(achievement.group)}</span>
          <span>·</span>
          <span>{unlocked ? (achievement.progressText || '已获得') : `进度 ${progressPercent}%`}</span>
        </div>

        {!unlocked && (
          <div className="ach-keep-progress">
            <div className="ach-keep-progress-track">
              <div className="ach-keep-progress-fill" style={{ width: `${progressPercent}%`, background: tierStyle.color }} />
            </div>
            <div className="ach-keep-progress-text">{achievement.progressText}</div>
          </div>
        )}

        <button type="button" className="ach-keep-cta" onClick={() => onClose?.()}>
          {unlocked ? '好的' : '继续冲刺'}
        </button>
      </div>
    </div>
  );
}
