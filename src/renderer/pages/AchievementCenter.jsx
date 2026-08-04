import React, { useEffect, useMemo, useState } from 'react';
import { ACH_GROUP_LABELS, ICONS, Ico, getAchievementGroupLabel } from '../components/AchievementVisuals.jsx';
import AchievementTile, { AchievementMedal } from '../components/AchievementTile.jsx';
import AchievementDialog from '../components/AchievementDialog.jsx';
import { useAchievementUnlock } from '../components/AchievementUnlockCeremony.jsx';
import { normalizeAchievements } from '../utils/achievementUtils.js';

const FILTERS = [
  { key: 'all', label: '全部' },
  { key: 'unlocked', label: '已解锁' },
  { key: 'locked', label: '待解锁' },
];

const GROUP_ICONS = {
  growth: ICONS.book,
  habit: ICONS.days,
  accuracy: ICONS.target,
  mastery: ICONS.crown,
  explore: ICONS.ai,
};

export default function AchievementCenter({ onBack }) {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState('all');
  const [detailAchievementId, setDetailAchievementId] = useState(null);
  const { enqueueUnlocks } = useAchievementUnlock();

  useEffect(() => {
    (async () => {
      if (!window.openexam?.db?.getGrowthData) {
        setLoading(false);
        return;
      }
      try {
        setData(await window.openexam.db.getGrowthData());
      } catch (error) {
        console.error('加载成就数据失败:', error);
      }
      setLoading(false);
    })();
  }, []);

  const achievements = normalizeAchievements(data?.achievements, data || {});
  const unlockedCount = achievements.filter((item) => item.unlocked).length;
  const lockedCount = Math.max(0, achievements.length - unlockedCount);
  const completionRate = achievements.length > 0 ? Math.round((unlockedCount / achievements.length) * 100) : 0;

  const filterCounts = useMemo(() => ({
    all: achievements.length,
    unlocked: unlockedCount,
    locked: lockedCount,
  }), [achievements.length, unlockedCount, lockedCount]);

  const filteredAchievements = useMemo(() => achievements.filter((item) => {
    if (filter === 'unlocked') return item.unlocked;
    if (filter === 'locked') return !item.unlocked;
    return true;
  }), [achievements, filter]);

  const groups = useMemo(() => Object.entries(ACH_GROUP_LABELS).map(([key]) => {
    const allItems = achievements.filter((item) => item.group === key);
    const visibleItems = filteredAchievements
      .filter((item) => item.group === key)
      .sort((a, b) => (Number(b.unlocked) - Number(a.unlocked)) || ((b.progressRatio || 0) - (a.progressRatio || 0)) || ((a.order || 0) - (b.order || 0)));

    return {
      key,
      label: getAchievementGroupLabel(key),
      items: visibleItems,
      total: allItems.length,
      unlocked: allItems.filter((item) => item.unlocked).length,
    };
  }).filter((group) => group.total > 0), [achievements, filteredAchievements]);

  const visibleGroups = groups.filter((group) => group.items.length > 0);
  const nextUnlock = achievements
    .filter((item) => !item.unlocked)
    .sort((a, b) => (b.progressRatio || 0) - (a.progressRatio || 0))[0] || null;
  const detailAchievement = achievements.find((item) => item.id === detailAchievementId) || null;
  const featured = achievements.find((item) => item.unlocked) || nextUnlock || achievements[0] || null;

  if (loading) {
    return (
      <section className="main-panel" style={{ display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <div style={{ color: 'var(--muted)', fontSize: 12 }}>加载成就…</div>
      </section>
    );
  }

  return (
    <section className="main-panel ach-center-page">
      <header className="ach-center-header">
        <div className="ach-center-header-left">
          <button type="button" className="ach-center-back" onClick={() => onBack?.()}>←</button>
          <div>
            <div className="ach-center-crumb">我的 › 成长中心 › 成就</div>
            <h2 className="ach-center-title">成就</h2>
          </div>
        </div>

        <div className="ach-center-filters">
          <button
            type="button"
            className="ach-center-preview"
            onClick={() => {
              const sample = achievements.find((item) => item.unlocked) || achievements[0];
              if (!sample) return;
              enqueueUnlocks([{
                ...sample,
                id: `__preview__:${sample.id}:${Date.now()}`,
                unlocked: true,
                progressRatio: 1,
                isPreview: true,
              }]);
            }}
          >
            预览动画
          </button>
          {FILTERS.map((item) => (
            <button
              key={item.key}
              type="button"
              className={`ach-center-filter${filter === item.key ? ' is-active' : ''}`}
              onClick={() => setFilter(item.key)}
            >
              {item.label} {filterCounts[item.key]}
            </button>
          ))}
        </div>
      </header>

      <div className="ach-center-body">
        {featured && (
          <button type="button" className="ach-featured" onClick={() => setDetailAchievementId(featured.id)}>
            <AchievementMedal achievement={featured} size={120} showProgress={!featured.unlocked} />
            <div className="ach-featured-copy">
              <div className="ach-featured-kicker">{featured.unlocked ? '已获得' : '最近冲刺'}</div>
              <div className="ach-featured-name">{featured.name}</div>
              <div className="ach-featured-desc">{featured.desc}</div>
              <div className="ach-featured-meta">
                已解锁 {unlockedCount}/{achievements.length} · 完成度 {completionRate}%
                {nextUnlock && !featured.unlocked ? ` · ${nextUnlock.progressText}` : ''}
              </div>
            </div>
          </button>
        )}

        {visibleGroups.map((group) => (
          <section key={group.key} className="ach-group">
            <div className="ach-group-head">
              <div className="ach-group-label">
                <Ico d={GROUP_ICONS[group.key] || ICONS.growth} size={12} col="var(--muted)" sw={2} />
                <span>{group.label}</span>
              </div>
              <span className="ach-group-count">{group.unlocked}/{group.total}</span>
            </div>
            <div className="ach-gallery">
              {group.items.map((item) => (
                <AchievementTile
                  key={item.id}
                  achievement={item}
                  onClick={() => setDetailAchievementId(item.id)}
                />
              ))}
            </div>
          </section>
        ))}

        {visibleGroups.length === 0 && (
          <div className="ach-empty">当前筛选下暂无成就</div>
        )}
      </div>

      <AchievementDialog achievement={detailAchievement} onClose={() => setDetailAchievementId(null)} />
    </section>
  );
}
