import React, { useState, useEffect } from 'react';

const categoryNames = {
  yanyu: '言语理解',
  shuliang: '数量关系',
  panduan: '判断推理',
  ziliao: '资料分析',
  changshi: '常识判断'
};

export default function PracticeHistory({ onBack, onOpenRecord }) {
  const [records, setRecords] = useState([]);
  const [loading, setLoading] = useState(true);
  const [stats, setStats] = useState({ total: 0, avgAccuracy: 0, totalTime: 0 });
  const [filter, setFilter] = useState('all');

  useEffect(() => {
    const loadRecords = async () => {
      if (!window.openexam?.db) {
        setLoading(false);
        return;
      }
      try {
        const allRecords = await window.openexam.db.getPracticeRecords();
        const data = (allRecords || []).filter((record) => {
          const status = String(record?.status || '').toLowerCase();
          return ['completed', 'ongoing', 'paused'].includes(status);
        });
        setRecords(data || []);

        const completed = data.filter((r) => String(r.status).toLowerCase() === 'completed');
        if (completed.length > 0) {
          const total = completed.length;
          const avgAccuracy = Math.round(completed.reduce((sum, r) => sum + (r.accuracy || 0), 0) / total);
          const totalTime = completed.reduce((sum, r) => sum + (r.duration || 0), 0);
          setStats({ total, avgAccuracy, totalTime });
        }
      } catch (err) {
        console.error('加载练习记录失败:', err);
      }
      setLoading(false);
    };
    loadRecords();
  }, []);

  const formatDate = (dateStr) => {
    if (!dateStr) return '-';
    const date = new Date(dateStr);
    const now = new Date();
    const diff = now - date;

    if (diff < 60000) return '刚刚';
    if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`;
    if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`;
    if (diff < 604800000) return `${Math.floor(diff / 86400000)}天前`;

    return `${date.getMonth() + 1}/${date.getDate()}`;
  };

  const formatDuration = (seconds) => {
    if (!seconds) return '0分';
    const m = Math.floor(seconds / 60);
    const s = seconds % 60;
    return m > 0 ? `${m}分${s}秒` : `${s}秒`;
  };

  const formatTotalTime = (seconds) => {
    if (!seconds) return '0分钟';
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    if (h > 0) return `${h}小时${m}分钟`;
    return `${m}分钟`;
  };

  const filtered = records.filter((record) => {
    const status = String(record.status || '').toLowerCase();
    if (filter === 'completed') return status === 'completed';
    if (filter === 'ongoing') return ['ongoing', 'paused'].includes(status);
    return true;
  });

  if (loading) {
    return <div className="history-page"><div className="loading-state">加载中...</div></div>;
  }

  return (
    <div className="history-page">
      <div className="history-header">
        <button className="back-btn" onClick={onBack}>
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <polyline points="15 18 9 12 15 6"/>
          </svg>
          返回
        </button>
        <h2>练习历史</h2>
      </div>

      <div className="history-stats">
        <div className="hs-card">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="var(--accent)" strokeWidth="2">
            <rect x="3" y="4" width="18" height="18" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/>
          </svg>
          <div className="hs-data">
            <span className="hs-value">{stats.total}</span>
            <span className="hs-label">完成次数</span>
          </div>
        </div>
        <div className="hs-card">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="var(--success)" strokeWidth="2">
            <polyline points="20 6 9 17 4 12"/>
          </svg>
          <div className="hs-data">
            <span className="hs-value">{stats.avgAccuracy}%</span>
            <span className="hs-label">平均正确率</span>
          </div>
        </div>
        <div className="hs-card">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="var(--warning)" strokeWidth="2">
            <circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/>
          </svg>
          <div className="hs-data">
            <span className="hs-value">{formatTotalTime(stats.totalTime)}</span>
            <span className="hs-label">累计用时</span>
          </div>
        </div>
      </div>

      <div style={{ display: 'flex', gap: 8, marginBottom: 12, flexWrap: 'wrap' }}>
        {[
          { key: 'all', label: '全部' },
          { key: 'ongoing', label: '未完成' },
          { key: 'completed', label: '已完成' },
        ].map((item) => (
          <button
            key={item.key}
            type="button"
            onClick={() => setFilter(item.key)}
            style={{
              padding: '6px 12px',
              borderRadius: 999,
              border: filter === item.key ? '1px solid var(--accent-border-soft)' : '1px solid var(--line)',
              background: filter === item.key ? 'var(--accent-soft-bg)' : 'var(--surface)',
              color: filter === item.key ? 'var(--accent)' : 'var(--muted)',
              fontSize: 12,
              fontWeight: 700,
              cursor: 'pointer',
            }}
          >
            {item.label}
          </button>
        ))}
      </div>

      <div className="history-list">
        {filtered.length === 0 ? (
          <div className="empty-history">
            <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="var(--muted)" strokeWidth="1.5">
              <rect x="3" y="4" width="18" height="18" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/>
            </svg>
            <p>暂无练习记录</p>
            <span>开始练习后，记录将显示在这里</span>
          </div>
        ) : (
          filtered.map((record, idx) => {
            const status = String(record.status || '').toLowerCase();
            const ongoing = ['ongoing', 'paused'].includes(status);
            const title = record.meta?.title || record.paper_title || categoryNames[record.category] || record.category || '综合练习';
            return (
              <button
                key={record.id || idx}
                type="button"
                className="history-record"
                onClick={() => onOpenRecord?.(record)}
                style={{ width: '100%', textAlign: 'left', cursor: 'pointer', border: '1px solid var(--line)', background: 'var(--surface)' }}
              >
                <div className="hr-left">
                  <div className="hr-icon" style={{
                    background: ongoing ? 'var(--warning-soft)' : (record.accuracy >= 60 ? 'var(--success-soft)' : 'var(--danger-soft)'),
                    color: ongoing ? 'var(--warning)' : (record.accuracy >= 60 ? 'var(--success)' : 'var(--danger)')
                  }}>
                    {ongoing ? (
                      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                        <circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/>
                      </svg>
                    ) : record.accuracy >= 60 ? (
                      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                        <polyline points="20 6 9 17 4 12"/>
                      </svg>
                    ) : (
                      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                        <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
                      </svg>
                    )}
                  </div>
                  <div className="hr-info">
                    <span className="hr-title">{title}</span>
                    <span className="hr-meta">
                      {formatDate(record.start_time || record.created_at)} · {record.total_count || 0}题 · {formatDuration(record.duration)}
                      {ongoing ? ' · 未完成' : ''}
                    </span>
                  </div>
                </div>
                <div className="hr-right">
                  <span className="hr-accuracy" style={{ color: ongoing ? 'var(--warning)' : (record.accuracy >= 60 ? 'var(--success)' : 'var(--danger)') }}>
                    {ongoing ? '继续' : `${record.accuracy || 0}%`}
                  </span>
                  <span className="hr-score">{ongoing ? `${Object.keys(record.answers || {}).length}/${record.total_count || 0}` : `${record.correct_count || 0}/${record.total_count || 0}`}</span>
                </div>
              </button>
            );
          })
        )}
      </div>
    </div>
  );
}
