import React, { useId } from 'react';
import {
  ICONS,
  getAchievementIconKey,
  getAchievementTierStyle,
} from './AchievementVisuals.jsx';

/** Keep-like metal tones: dark matte disc + champagne relief */
const METAL = {
  bronze: {
    disc: '#2a2a2c',
    discEdge: '#1a1a1c',
    metalHi: '#f0d7a8',
    metal: '#c9a46a',
    metalLo: '#8d6a3a',
    shadow: 'rgba(28, 24, 18, 0.28)',
  },
  silver: {
    disc: '#2c2e32',
    discEdge: '#1b1d21',
    metalHi: '#f2f4f7',
    metal: '#c5cad3',
    metalLo: '#7f8794',
    shadow: 'rgba(20, 24, 32, 0.28)',
  },
  gold: {
    disc: '#262422',
    discEdge: '#161412',
    metalHi: '#ffe7b3',
    metal: '#d4ae66',
    metalLo: '#9a7435',
    shadow: 'rgba(28, 22, 12, 0.30)',
  },
  master: {
    disc: '#221f2a',
    discEdge: '#141218',
    metalHi: '#e8dff8',
    metal: '#b8a8e8',
    metalLo: '#6e67c4',
    shadow: 'rgba(24, 18, 36, 0.32)',
  },
};

function AchievementMedal({
  achievement,
  size = 72,
  compact = false,
  animateRing = false,
  className = '',
  style,
  showProgress = true,
}) {
  const uid = useId().replace(/:/g, '');
  const tier = achievement?.tier || 'bronze';
  const tierStyle = getAchievementTierStyle(tier);
  const metal = METAL[tier] || METAL.bronze;
  const progress = Math.max(0, Math.min(1, Number(achievement?.progressRatio) || 0));
  const isUnlocked = Boolean(achievement?.unlocked);
  const iconKey = getAchievementIconKey(achievement || {});
  const iconPaths = ICONS[iconKey] || ICONS.trophy;

  const cx = size / 2;
  const cy = size / 2;
  const ringW = compact ? 2.4 : 2.8;
  const trackR = (size - ringW) / 2;
  const discR = trackR - (showProgress ? ringW + 1.5 : 1);
  const circumference = 2 * Math.PI * trackR;
  const dashOffset = circumference * (1 - progress);

  const iconScale = (compact ? 0.34 : 0.38) * size / 24;
  const iconShift = (size - 24 * iconScale) / 2;

  const ringTrack = isUnlocked ? 'rgba(255,255,255,0.08)' : 'rgba(148,163,184,0.22)';
  const ringStroke = isUnlocked ? metal.metal : tierStyle.color;

  const softShadow = size >= 100 && isUnlocked
    ? `drop-shadow(0 ${Math.max(6, size * 0.08)}px ${Math.max(14, size * 0.18)}px ${metal.shadow})`
    : isUnlocked
      ? `drop-shadow(0 4px 8px ${metal.shadow})`
      : 'none';

  return (
    <div
      className={`ach-medal${isUnlocked ? ' is-unlocked' : ' is-locked'}${className ? ` ${className}` : ''}`}
      style={{
        position: 'relative',
        width: size,
        height: size,
        flexShrink: 0,
        filter: softShadow,
        ...style,
      }}
    >
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} className="ach-medal-svg" aria-hidden="true">
        <defs>
          <radialGradient id={`${uid}-disc`} cx="35%" cy="28%" r="72%">
            <stop offset="0%" stopColor={isUnlocked ? '#3a3a3e' : '#f3f4f6'} />
            <stop offset="55%" stopColor={isUnlocked ? metal.disc : '#e8eaed'} />
            <stop offset="100%" stopColor={isUnlocked ? metal.discEdge : '#d7dbe2'} />
          </radialGradient>
          <linearGradient id={`${uid}-metal`} x1="18%" y1="12%" x2="86%" y2="90%">
            <stop offset="0%" stopColor={metal.metalHi} />
            <stop offset="42%" stopColor={metal.metal} />
            <stop offset="100%" stopColor={metal.metalLo} />
          </linearGradient>
          <linearGradient id={`${uid}-rim`} x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor={isUnlocked ? metal.metalHi : '#fff'} stopOpacity={isUnlocked ? 0.55 : 0.9} />
            <stop offset="50%" stopColor={isUnlocked ? metal.metal : '#cfd4dc'} stopOpacity={isUnlocked ? 0.35 : 0.5} />
            <stop offset="100%" stopColor={isUnlocked ? metal.metalLo : '#9aa3af'} stopOpacity={isUnlocked ? 0.7 : 0.35} />
          </linearGradient>
        </defs>

        {showProgress && (
          <>
            <circle cx={cx} cy={cy} r={trackR} fill="none" stroke={ringTrack} strokeWidth={ringW} />
            <circle
              cx={cx}
              cy={cy}
              r={trackR}
              fill="none"
              stroke={ringStroke}
              strokeWidth={ringW}
              strokeLinecap="round"
              strokeDasharray={circumference}
              strokeDashoffset={dashOffset}
              transform={`rotate(-90 ${cx} ${cy})`}
              opacity={isUnlocked ? 0.9 : 0.85}
            />
          </>
        )}

        <circle cx={cx} cy={cy} r={discR + 0.6} fill={`url(#${uid}-rim)`} opacity={isUnlocked ? 1 : 0.55} />
        <circle
          cx={cx}
          cy={cy}
          r={discR}
          fill={isUnlocked ? `url(#${uid}-disc)` : '#eef0f3'}
          stroke={isUnlocked ? 'rgba(255,255,255,0.06)' : 'rgba(148,163,184,0.28)'}
          strokeWidth={1}
        />

        {/* subtle inner specular — CSS-cheap SVG only, no turbulence */}
        {isUnlocked && (
          <ellipse
            cx={cx - discR * 0.18}
            cy={cy - discR * 0.28}
            rx={discR * 0.42}
            ry={discR * 0.28}
            fill="rgba(255,255,255,0.07)"
          />
        )}

        <g
          transform={`translate(${iconShift}, ${iconShift}) scale(${iconScale})`}
          fill="none"
          stroke={isUnlocked ? `url(#${uid}-metal)` : 'rgba(120,128,140,0.55)'}
          strokeWidth={isUnlocked ? 2.15 : 1.7}
          strokeLinecap="round"
          strokeLinejoin="round"
          opacity={isUnlocked ? 1 : 0.72}
        >
          {iconPaths}
        </g>
      </svg>
    </div>
  );
}

export function AchievementRing(props) {
  return <AchievementMedal animateRing={false} {...props} />;
}

export { AchievementMedal };
export default AchievementMedal;
