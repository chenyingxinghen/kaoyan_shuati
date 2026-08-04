import React from 'react';
import { AchievementMedal } from './AchievementMedal.jsx';
import { getAchievementTierStyle } from './AchievementVisuals.jsx';

export { AchievementMedal, AchievementRing } from './AchievementMedal.jsx';

const lineClamp = (lines) => ({
  display: '-webkit-box',
  WebkitLineClamp: lines,
  WebkitBoxOrient: 'vertical',
  overflow: 'hidden',
});

export default function AchievementTile({
  achievement,
  compact = false,
  selected = false,
  onClick,
  showDetail = false,
}) {
  const tierStyle = getAchievementTierStyle(achievement?.tier);
  const isUnlocked = Boolean(achievement?.unlocked);
  const progressPercent = Math.round((achievement?.progressRatio || 0) * 100);
  const Component = onClick ? 'button' : 'div';

  return (
    <Component
      type={onClick ? 'button' : undefined}
      onClick={onClick}
      title={[achievement?.name, achievement?.desc, achievement?.progressText].filter(Boolean).join('\n')}
      className={`ach-tile${isUnlocked ? ' is-unlocked' : ' is-locked'}${selected ? ' is-selected' : ''}`}
    >
      <AchievementMedal
        achievement={achievement}
        size={compact ? 64 : 78}
        compact={compact}
        animateRing={false}
        showProgress={!isUnlocked}
        className="ach-tile-medal"
      />

      <div className="ach-tile-copy">
        <div className="ach-tile-name" style={lineClamp(2)}>{achievement?.name}</div>
        <div className="ach-tile-meta">
          <span style={{ color: isUnlocked ? tierStyle.color : 'var(--muted)' }}>
            {isUnlocked ? tierStyle.label : `${progressPercent}%`}
          </span>
          <span className="ach-tile-dot">·</span>
          <span style={lineClamp(1)}>
            {achievement?.progressText || (isUnlocked ? '已完成' : '进行中')}
          </span>
        </div>
        {showDetail && achievement?.desc ? (
          <div className="ach-tile-desc" style={lineClamp(2)}>{achievement.desc}</div>
        ) : null}
      </div>
    </Component>
  );
}
