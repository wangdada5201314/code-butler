import React, { useState, useEffect, useCallback } from 'react';
import {
  Box, Typography, LinearProgress, Tooltip, Fade, Alert, Chip, IconButton,
  TextField, Button, Snackbar, CircularProgress,
} from '@mui/material';
import BarChartIcon from '@mui/icons-material/BarChart';
import RefreshIcon from '@mui/icons-material/Refresh';
import BoltIcon from '@mui/icons-material/Bolt';
import CalendarMonthIcon from '@mui/icons-material/CalendarMonth';
import BugReportIcon from '@mui/icons-material/BugReport';
import PsychologyIcon from '@mui/icons-material/Psychology';
import DescriptionIcon from '@mui/icons-material/Description';
import AdminPanelSettingsIcon from '@mui/icons-material/AdminPanelSettings';
import SettingsIcon from '@mui/icons-material/Settings';
import SaveIcon from '@mui/icons-material/Save';
import EditIcon from '@mui/icons-material/Edit';
import { getUsageStats, getQuotaConfigs, updateQuotaConfig } from '../api/client.js';

/* ─── Helpers ─── */
function formatNumber(n) {
  if (n >= 1_000_000) return (n / 1_000_000).toFixed(1) + 'M';
  if (n >= 1_000) return (n / 1_000).toFixed(1) + 'K';
  return String(n);
}

/* ─── Stat Card ─── */
function StatCard({ icon: Icon, label, value, color, bg, subtitle }) {
  return (
    <Box sx={{
      flex: '1 1 160px', minWidth: 160,
      p: '18px 20px', borderRadius: 'var(--radius-card)',
      background: 'var(--bg-surface)',
      border: '1px solid var(--border-subtle)',
      display: 'flex', flexDirection: 'column', gap: 1,
      transition: 'all 0.25s',
      '&:hover': { borderColor: 'var(--border-hover)', transform: 'translateY(-2px)' },
    }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
        <Box sx={{
          width: 32, height: 32, borderRadius: 1.5,
          bgcolor: bg, display: 'flex', alignItems: 'center', justifyContent: 'center',
        }}>
          <Icon sx={{ fontSize: 17, color }} />
        </Box>
        <Typography sx={{ fontSize: '0.72rem', fontWeight: 600, color: 'var(--text-muted)' }}>
          {label}
        </Typography>
      </Box>
      <Typography sx={{
        fontFamily: 'var(--font-display)',
        fontSize: '1.6rem', fontWeight: 800, color: 'var(--text-primary)',
        lineHeight: 1.1,
      }}>
        {value}
      </Typography>
      {subtitle && (
        <Typography sx={{ fontSize: '0.68rem', color: 'var(--text-muted)' }}>
          {subtitle}
        </Typography>
      )}
    </Box>
  );
}

/* ─── Quota Progress Bar ─── */
function QuotaBar({ icon: Icon, label, used, limit, color }) {
  const isUnlimited = limit < 0;
  const pct = isUnlimited ? 0 : Math.min(100, (used / limit) * 100);
  const isWarning = pct >= 80;
  const isDanger = pct >= 95;

  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, py: '10px' }}>
      <Box sx={{
        width: 30, height: 30, borderRadius: 1.5, flexShrink: 0,
        bgcolor: `${color}18`,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
      }}>
        <Icon sx={{ fontSize: 16, color }} />
      </Box>
      <Box sx={{ flex: 1, minWidth: 0 }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 0.5 }}>
          <Typography sx={{ fontSize: '0.78rem', fontWeight: 600, color: 'var(--text-primary)' }}>
            {label}
          </Typography>
          <Typography sx={{
            fontSize: '0.72rem', fontWeight: 700,
            color: isDanger ? 'var(--danger)' : isWarning ? '#f59e0b' : 'var(--text-secondary)',
          }}>
            {isUnlimited ? `${used} / 不限` : `${used} / ${limit}`}
          </Typography>
        </Box>
        {!isUnlimited && (
          <Box sx={{
            height: 6, borderRadius: 3, overflow: 'hidden',
            bgcolor: 'rgba(255,255,255,0.06)',
          }}>
            <Box sx={{
              width: `${pct}%`, height: '100%', borderRadius: 3,
              background: isDanger
                ? 'linear-gradient(90deg, #ef4444, #f87171)'
                : isWarning
                  ? 'linear-gradient(90deg, #f59e0b, #fbbf24)'
                  : `linear-gradient(90deg, ${color}, ${color}cc)`,
              transition: 'width 0.6s cubic-bezier(0.4, 0, 0.2, 1)',
            }} />
          </Box>
        )}
      </Box>
    </Box>
  );
}

/* ─── Quota Config Panel (Admin Only) ─── */
const OP_TYPE_LABELS = {
  REVIEW: { label: '代码审查', icon: BugReportIcon, color: 'var(--accent)' },
  CHAT: { label: 'AI 问答', icon: PsychologyIcon, color: 'var(--accent-secondary)' },
  DOC: { label: '文档生成', icon: DescriptionIcon, color: '#a78bfa' },
};

function QuotaConfigPanel({ onConfigUpdated }) {
  const [configs, setConfigs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState({});
  const [saving, setSaving] = useState({});
  const [toast, setToast] = useState(null);

  const fetchConfigs = useCallback(async () => {
    try {
      setLoading(true);
      const data = await getQuotaConfigs();
      setConfigs(data || []);
    } catch (e) {
      setToast({ severity: 'error', message: '获取配额配置失败' });
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchConfigs(); }, [fetchConfigs]);

  const handleEdit = (opType, currentLimit) => {
    setEditing(prev => ({ ...prev, [opType]: String(currentLimit) }));
  };

  const handleSave = async (opType) => {
    const value = parseInt(editing[opType], 10);
    if (isNaN(value) || value < -1) {
      setToast({ severity: 'error', message: '限额值无效，请输入 -1（不限）或正整数' });
      return;
    }
    try {
      setSaving(prev => ({ ...prev, [opType]: true }));
      await updateQuotaConfig(opType, value);
      setEditing(prev => { const n = { ...prev }; delete n[opType]; return n; });
      setToast({ severity: 'success', message: `${OP_TYPE_LABELS[opType]?.label || opType} 配额已更新` });
      await fetchConfigs();
      if (onConfigUpdated) onConfigUpdated();
    } catch (e) {
      setToast({ severity: 'error', message: e.message || '保存失败' });
    } finally {
      setSaving(prev => ({ ...prev, [opType]: false }));
    }
  };

  const handleCancel = (opType) => {
    setEditing(prev => { const n = { ...prev }; delete n[opType]; return n; });
  };

  if (loading) {
    return (
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, py: 2 }}>
        <CircularProgress size={16} />
        <Typography sx={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>加载配置...</Typography>
      </Box>
    );
  }

  return (
    <Box sx={{
      p: '20px 24px',
      borderRadius: 'var(--radius-card)',
      background: 'var(--bg-surface)',
      border: '1px solid var(--border-subtle)',
    }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2 }}>
        <SettingsIcon sx={{ fontSize: 18, color: 'var(--accent)' }} />
        <Typography sx={{
          fontFamily: 'var(--font-display)',
          fontSize: '0.88rem', fontWeight: 700,
          color: 'var(--text-primary)',
        }}>
          配额管理
        </Typography>
        <Chip
          icon={<AdminPanelSettingsIcon sx={{ fontSize: 13 }} />}
          label="管理员"
          size="small"
          sx={{
            height: 22, fontSize: '0.62rem', fontWeight: 600,
            bgcolor: 'rgba(212,160,83,0.1)',
            color: 'var(--accent)',
            border: '1px solid rgba(212,160,83,0.15)',
            '& .MuiChip-icon': { color: 'var(--accent)' },
          }}
        />
      </Box>

      {configs.map((cfg) => {
        const meta = OP_TYPE_LABELS[cfg.opType] || { label: cfg.opType, icon: SettingsIcon, color: '#999' };
        const Icon = meta.icon;
        const isEditing = editing[cfg.opType] !== undefined;
        const isSaving = saving[cfg.opType];

        return (
          <Box key={cfg.opType} sx={{
            display: 'flex', alignItems: 'center', gap: 2, py: '12px',
            borderBottom: '1px solid var(--border-subtle)',
            '&:last-child': { borderBottom: 'none' },
          }}>
            <Box sx={{
              width: 30, height: 30, borderRadius: 1.5, flexShrink: 0,
              bgcolor: `${meta.color}18`,
              display: 'flex', alignItems: 'center', justifyContent: 'center',
            }}>
              <Icon sx={{ fontSize: 16, color: meta.color }} />
            </Box>

            <Box sx={{ flex: 1 }}>
              <Typography sx={{ fontSize: '0.78rem', fontWeight: 600, color: 'var(--text-primary)' }}>
                {meta.label}
              </Typography>
              <Typography sx={{ fontSize: '0.66rem', color: 'var(--text-muted)' }}>
                {cfg.description || ' '}
              </Typography>
            </Box>

            {isEditing ? (
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <TextField
                  size="small"
                  value={editing[cfg.opType]}
                  onChange={(e) => setEditing(prev => ({ ...prev, [cfg.opType]: e.target.value }))}
                  inputProps={{ style: { fontSize: '0.8rem', width: 70, textAlign: 'center' } }}
                  sx={{
                    '& .MuiOutlinedInput-root': {
                      height: 32,
                      '& fieldset': { borderColor: 'var(--border-subtle)' },
                      '&:hover fieldset': { borderColor: 'var(--border-hover)' },
                      '&.Mui-focused fieldset': { borderColor: '#60a5fa' },
                    },
                    '& .MuiOutlinedInput-input': { color: 'var(--text-primary)' },
                  }}
                />
                <Typography sx={{ fontSize: '0.7rem', color: 'var(--text-muted)', whiteSpace: 'nowrap' }}>
                  次/日
                </Typography>
                <Button
                  size="small"
                  variant="contained"
                  disabled={isSaving}
                  onClick={() => handleSave(cfg.opType)}
                  startIcon={isSaving ? <CircularProgress size={12} /> : <SaveIcon sx={{ fontSize: 14 }} />}
                  sx={{
                    minWidth: 0, px: 1.5, py: 0.5,
                    fontSize: '0.7rem', textTransform: 'none',
                    bgcolor: '#60a5fa', '&:hover': { bgcolor: '#3b82f6' },
                  }}
                >
                  保存
                </Button>
                <Button
                  size="small"
                  onClick={() => handleCancel(cfg.opType)}
                  disabled={isSaving}
                  sx={{ minWidth: 0, px: 1, py: 0.5, fontSize: '0.7rem', color: 'var(--text-muted)', textTransform: 'none' }}
                >
                  取消
                </Button>
              </Box>
            ) : (
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <Typography sx={{
                  fontFamily: 'var(--font-display)',
                  fontSize: '1.1rem', fontWeight: 800,
                  color: cfg.dailyLimit < 0 ? 'var(--text-muted)' : 'var(--text-primary)',
                }}>
                  {cfg.dailyLimit < 0 ? '不限' : cfg.dailyLimit}
                </Typography>
                <Typography sx={{ fontSize: '0.68rem', color: 'var(--text-muted)' }}>
                  {cfg.dailyLimit >= 0 ? '次/日' : ''}
                </Typography>
                <Tooltip title="编辑限额" arrow>
                  <IconButton
                    size="small"
                    onClick={() => handleEdit(cfg.opType, cfg.dailyLimit)}
                    sx={{ color: 'var(--text-muted)', '&:hover': { color: '#60a5fa' } }}
                  >
                    <EditIcon sx={{ fontSize: 15 }} />
                  </IconButton>
                </Tooltip>
              </Box>
            )}
          </Box>
        );
      })}

      <Box sx={{ mt: 1.5, pt: 1, borderTop: '1px solid var(--border-subtle)' }}>
        <Typography sx={{ fontSize: '0.66rem', color: 'var(--text-muted)', lineHeight: 1.6 }}>
          修改后立即生效，无需重启。设为 -1 表示不限制。普通用户受此配额约束，管理员不受限。
        </Typography>
      </Box>

      <Snackbar
        open={!!toast}
        autoHideDuration={3000}
        onClose={() => setToast(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        {toast ? (
          <Alert severity={toast.severity} onClose={() => setToast(null)} sx={{ borderRadius: 2 }}>
            {toast.message}
          </Alert>
        ) : undefined}
      </Snackbar>
    </Box>
  );
}

/* ========================================================
   UsageDashboardPanel Component
   ======================================================== */
export default function UsageDashboardPanel({ darkMode }) {
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const fetchStats = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await getUsageStats();
      setStats(data);
    } catch (e) {
      setError(e.message || '获取用量数据失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchStats(); }, [fetchStats]);

  if (loading && !stats) {
    return (
      <Box sx={{
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        minHeight: 300, gap: 1.5,
      }}>
        <Box className="thinking-dots">
          <Box component="span" className="dot" />
          <Box component="span" className="dot" />
          <Box component="span" className="dot" />
        </Box>
        <Typography sx={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>
          加载用量数据...
        </Typography>
      </Box>
    );
  }

  if (error) {
    return (
      <Fade in>
        <Alert severity="error" sx={{ m: 2, borderRadius: 2 }} action={
          <IconButton size="small" onClick={fetchStats} sx={{ color: 'inherit' }}>
            <RefreshIcon sx={{ fontSize: 16 }} />
          </IconButton>
        }>
          {error}
        </Alert>
      </Fade>
    );
  }

  if (!stats) return null;

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
      {/* ── Header ── */}
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
          <Box sx={{
            width: 36, height: 36, borderRadius: 2,
            background: 'linear-gradient(135deg, rgba(96,165,250,0.15), rgba(45,212,191,0.1))',
            border: '1px solid rgba(96,165,250,0.2)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>
            <BarChartIcon sx={{ fontSize: 19, color: '#60a5fa' }} />
          </Box>
          <Box>
            <Typography sx={{
              fontFamily: 'var(--font-display)',
              fontSize: '1rem', fontWeight: 700,
              color: 'var(--text-primary)',
            }}>
              AI 用量仪表盘
            </Typography>
            <Typography sx={{ fontSize: '0.72rem', color: 'var(--text-muted)' }}>
              实时追踪 AI 调用次数与 token 消耗
            </Typography>
          </Box>
        </Box>

        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          {stats.isAdmin && (
            <Chip
              icon={<AdminPanelSettingsIcon sx={{ fontSize: 15 }} />}
              label="管理员 · 不限配额"
              size="small"
              sx={{
                height: 28, fontSize: '0.7rem', fontWeight: 600,
                bgcolor: 'rgba(212,160,83,0.1)',
                color: 'var(--accent)',
                border: '1px solid rgba(212,160,83,0.2)',
                '& .MuiChip-icon': { color: 'var(--accent)' },
              }}
            />
          )}
          <Tooltip title="刷新" arrow>
            <IconButton
              size="small"
              onClick={fetchStats}
              disabled={loading}
              sx={{
                color: 'var(--text-muted)',
                '&:hover': { color: '#60a5fa' },
              }}
            >
              <RefreshIcon sx={{ fontSize: 18 }} />
            </IconButton>
          </Tooltip>
        </Box>
      </Box>

      {/* ── Summary Cards ── */}
      <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap' }}>
        <StatCard
          icon={BoltIcon}
          label="今日调用"
          value={stats.todayTotalCount}
          color="#60a5fa"
          bg="rgba(96,165,250,0.12)"
          subtitle={`审查 ${stats.todayReviewCount} · 问答 ${stats.todayChatCount} · 文档 ${stats.todayDocCount}`}
        />
        <StatCard
          icon={CalendarMonthIcon}
          label="本月调用"
          value={stats.monthTotalCount}
          color="#2dd4bf"
          bg="rgba(45,212,191,0.12)"
          subtitle={`审查 ${stats.monthReviewCount} · 问答 ${stats.monthChatCount} · 文档 ${stats.monthDocCount}`}
        />
        <StatCard
          icon={BarChartIcon}
          label="本月 Token"
          value={formatNumber(stats.monthTokenCount)}
          color="#a78bfa"
          bg="rgba(167,139,250,0.12)"
          subtitle="估算值，基于文本长度计算"
        />
      </Box>

      {/* ── Quota Section ── */}
      <Box sx={{
        p: '20px 24px',
        borderRadius: 'var(--radius-card)',
        background: 'var(--bg-surface)',
        border: '1px solid var(--border-subtle)',
      }}>
        <Typography sx={{
          fontFamily: 'var(--font-display)',
          fontSize: '0.88rem', fontWeight: 700,
          color: 'var(--text-primary)', mb: 1.5,
        }}>
          今日配额
        </Typography>

        <QuotaBar
          icon={BugReportIcon}
          label="代码审查"
          used={stats.todayReviewCount}
          limit={stats.reviewDailyLimit}
          color="var(--accent)"
        />
        <QuotaBar
          icon={PsychologyIcon}
          label="AI 问答"
          used={stats.todayChatCount}
          limit={stats.chatDailyLimit}
          color="var(--accent-secondary)"
        />
        <QuotaBar
          icon={DescriptionIcon}
          label="文档生成"
          used={stats.todayDocCount}
          limit={stats.docDailyLimit}
          color="#a78bfa"
        />

        <Box sx={{
          mt: 2, pt: 1.5,
          borderTop: '1px solid var(--border-subtle)',
          display: 'flex', alignItems: 'center', gap: 1,
        }}>
          <Typography sx={{ fontSize: '0.68rem', color: 'var(--text-muted)', lineHeight: 1.6 }}>
            配额按自然日重置（UTC+8 00:00）。
            {stats.isAdmin
              ? '管理员账户不受配额限制。'
              : '如需更高配额，请联系管理员。'
            }
          </Typography>
        </Box>
      </Box>

      {/* ── Monthly Breakdown ── */}
      <Box sx={{
        p: '20px 24px',
        borderRadius: 'var(--radius-card)',
        background: 'var(--bg-surface)',
        border: '1px solid var(--border-subtle)',
      }}>
        <Typography sx={{
          fontFamily: 'var(--font-display)',
          fontSize: '0.88rem', fontWeight: 700,
          color: 'var(--text-primary)', mb: 2,
        }}>
          本月明细
        </Typography>

        {/* Table Header */}
        <Box sx={{
          display: 'grid', gridTemplateColumns: '1fr 80px 80px 100px',
          gap: 1, px: 1, pb: 1,
          borderBottom: '1px solid var(--border-subtle)',
        }}>
          <Typography sx={{ fontSize: '0.7rem', fontWeight: 700, color: 'var(--text-muted)' }}>类型</Typography>
          <Typography sx={{ fontSize: '0.7rem', fontWeight: 700, color: 'var(--text-muted)', textAlign: 'right' }}>今日</Typography>
          <Typography sx={{ fontSize: '0.7rem', fontWeight: 700, color: 'var(--text-muted)', textAlign: 'right' }}>本月</Typography>
          <Typography sx={{ fontSize: '0.7rem', fontWeight: 700, color: 'var(--text-muted)', textAlign: 'right' }}>每日限额</Typography>
        </Box>

        {[
          { label: '代码审查', icon: BugReportIcon, color: 'var(--accent)', today: stats.todayReviewCount, month: stats.monthReviewCount, limit: stats.reviewDailyLimit },
          { label: 'AI 问答', icon: PsychologyIcon, color: 'var(--accent-secondary)', today: stats.todayChatCount, month: stats.monthChatCount, limit: stats.chatDailyLimit },
          { label: '文档生成', icon: DescriptionIcon, color: '#a78bfa', today: stats.todayDocCount, month: stats.monthDocCount, limit: stats.docDailyLimit },
        ].map((row) => {
          const RowIcon = row.icon;
          return (
            <Box key={row.label} sx={{
              display: 'grid', gridTemplateColumns: '1fr 80px 80px 100px',
              gap: 1, px: 1, py: '10px',
              borderBottom: '1px solid var(--border-subtle)',
              '&:last-child': { borderBottom: 'none' },
              transition: 'background 0.2s',
              '&:hover': { bgcolor: darkMode ? 'rgba(255,255,255,0.02)' : 'rgba(0,0,0,0.01)' },
            }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <RowIcon sx={{ fontSize: 15, color: row.color }} />
                <Typography sx={{ fontSize: '0.82rem', fontWeight: 600, color: 'var(--text-primary)' }}>
                  {row.label}
                </Typography>
              </Box>
              <Typography sx={{
                fontSize: '0.82rem', fontWeight: 700, textAlign: 'right',
                color: 'var(--text-primary)',
              }}>
                {row.today}
              </Typography>
              <Typography sx={{
                fontSize: '0.82rem', textAlign: 'right',
                color: 'var(--text-secondary)',
              }}>
                {row.month}
              </Typography>
              <Typography sx={{
                fontSize: '0.82rem', textAlign: 'right',
                color: row.limit < 0 ? 'var(--text-muted)' : 'var(--text-secondary)',
              }}>
                {row.limit < 0 ? '不限' : `${row.limit} 次/日`}
              </Typography>
            </Box>
          );
        })}

        {/* Total row */}
        <Box sx={{
          display: 'grid', gridTemplateColumns: '1fr 80px 80px 100px',
          gap: 1, px: 1, pt: '12px', mt: 0.5,
        }}>
          <Typography sx={{ fontSize: '0.82rem', fontWeight: 800, color: 'var(--text-primary)' }}>
            合计
          </Typography>
          <Typography sx={{ fontSize: '0.82rem', fontWeight: 800, textAlign: 'right', color: '#60a5fa' }}>
            {stats.todayTotalCount}
          </Typography>
          <Typography sx={{ fontSize: '0.82rem', fontWeight: 800, textAlign: 'right', color: '#2dd4bf' }}>
            {stats.monthTotalCount}
          </Typography>
          <Typography sx={{ fontSize: '0.82rem', textAlign: 'right', color: 'var(--text-muted)' }}>
            {formatNumber(stats.monthTokenCount)} tokens
          </Typography>
        </Box>
      </Box>

      {/* ── Admin Quota Config (only for admins) ── */}
      {stats.isAdmin && (
        <QuotaConfigPanel onConfigUpdated={fetchStats} />
      )}
    </Box>
  );
}
