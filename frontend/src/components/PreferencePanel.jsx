import React, { useState, useEffect, useCallback } from 'react';
import {
  Typography, Box, IconButton, Tooltip, Chip, CircularProgress,
} from '@mui/material';
import SettingsIcon from '@mui/icons-material/Settings';
import CloseIcon from '@mui/icons-material/Close';
import SaveIcon from '@mui/icons-material/Save';
import CheckIcon from '@mui/icons-material/Check';
import { getPreference, updatePreference, getFocusOptions } from '../api/client.js';

/** 审查深度选项 */
const DEPTH_OPTIONS = [
  { value: 'detailed', label: '详细深入', desc: '逐文件分析，给出代码示例' },
  { value: 'standard', label: '标准', desc: '覆盖主要问题，适度详细' },
  { value: 'concise', label: '精简', desc: '只列关键问题，简明扼要' },
];

export default function PreferencePanel({ open, onClose }) {
  const [reviewFocus, setReviewFocus] = useState('');
  const [reviewDepth, setReviewDepth] = useState('standard');
  const [customPrompt, setCustomPrompt] = useState('');
  const [focusOptions, setFocusOptions] = useState({});
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);

  // Load focus options
  useEffect(() => {
    getFocusOptions()
      .then(setFocusOptions)
      .catch(() => {});
  }, []);

  // Load current preference when opened
  useEffect(() => {
    if (!open) return;
    setLoading(true);
    getPreference()
      .then((pref) => {
        setReviewFocus(pref.reviewFocus || '');
        setReviewDepth(pref.reviewDepth || 'standard');
        setCustomPrompt(pref.customPrompt || '');
      })
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [open]);

  const handleSave = useCallback(async () => {
    setSaving(true);
    try {
      await updatePreference({ reviewFocus, reviewDepth, customPrompt });
      setSaved(true);
      setTimeout(() => setSaved(false), 2000);
    } catch (err) {
      console.error('保存偏好失败:', err);
    } finally {
      setSaving(false);
    }
  }, [reviewFocus, reviewDepth, customPrompt]);

  const toggleFocus = (key) => {
    const current = reviewFocus ? reviewFocus.split(',').map(s => s.trim()).filter(Boolean) : [];
    const idx = current.indexOf(key);
    if (idx >= 0) {
      current.splice(idx, 1);
    } else {
      current.push(key);
    }
    setReviewFocus(current.join(','));
  };

  const selectedFocus = reviewFocus ? reviewFocus.split(',').map(s => s.trim()).filter(Boolean) : [];

  if (!open) return null;

  const inputStyle = {
    width: '100%', padding: '10px 12px', borderRadius: 'var(--radius-input)',
    border: '1px solid var(--border-subtle)', background: 'rgba(255,255,255,0.03)',
    color: 'var(--text-primary)', fontFamily: 'var(--font-body)', fontSize: '0.85rem',
    outline: 'none', transition: 'border-color 0.2s', resize: 'vertical',
    minHeight: 80, lineHeight: 1.6,
  };

  return (
    <Box
      sx={{
        position: 'fixed', top: 0, right: 0, bottom: 0,
        width: { xs: '100%', sm: 440 },
        zIndex: 1300,
        background: 'var(--bg-deep)',
        backdropFilter: 'blur(20px)',
        borderLeft: '1px solid var(--border-subtle)',
        boxShadow: '-8px 0 32px rgba(0,0,0,0.3)',
        display: 'flex', flexDirection: 'column',
        animation: 'slideIn 0.3s cubic-bezier(0.22, 1, 0.36, 1)',
      }}
    >
      {/* Header */}
      <Box sx={{
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        p: '16px 20px', borderBottom: '1px solid var(--border-subtle)',
      }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.2 }}>
          <Box sx={{
            width: 32, height: 32, borderRadius: 1.5,
            background: 'rgba(212,160,83,0.1)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>
            <SettingsIcon sx={{ color: 'var(--accent)', fontSize: 18 }} />
          </Box>
          <Typography sx={{ fontFamily: 'var(--font-display)', fontSize: '1rem', fontWeight: 700, color: 'var(--text-primary)' }}>
            审查偏好
          </Typography>
        </Box>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
          <button
            onClick={handleSave}
            disabled={saving}
            className="gradient-btn"
            style={{ padding: '6px 16px', fontSize: '0.8rem', display: 'flex', alignItems: 'center', gap: 6, border: 'none', cursor: saving ? 'not-allowed' : 'pointer' }}
          >
            {saved ? <CheckIcon sx={{ fontSize: 16 }} /> : saving ? <CircularProgress size={14} /> : <SaveIcon sx={{ fontSize: 16 }} />}
            {saved ? '已保存' : saving ? '保存中...' : '保存'}
          </button>
          <Tooltip title="关闭">
            <IconButton size="small" onClick={onClose} sx={{ color: 'var(--text-muted)', '&:hover': { color: 'var(--text-primary)' } }}>
              <CloseIcon fontSize="small" />
            </IconButton>
          </Tooltip>
        </Box>
      </Box>

      {/* Content */}
      <Box sx={{ flex: 1, overflow: 'auto', p: '20px' }} className="custom-scrollbar">
        {loading ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 8 }}>
            <CircularProgress size={24} sx={{ color: 'var(--accent)' }} />
          </Box>
        ) : (
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3.5 }}>
            {/* 1. Review Focus */}
            <Box>
              <Typography sx={{ fontFamily: 'var(--font-display)', fontSize: '0.88rem', fontWeight: 700, color: 'var(--text-primary)', mb: 0.5 }}>
                审查关注点
              </Typography>
              <Typography sx={{ fontSize: '0.75rem', color: 'var(--text-muted)', mb: 1.5 }}>
                选择你最在意的方面，AI 审查时会对这些方面给予更多关注
              </Typography>
              <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
                {Object.entries(focusOptions).map(([key, label]) => {
                  const isSelected = selectedFocus.includes(key);
                  return (
                    <Chip
                      key={key}
                      label={label}
                      onClick={() => toggleFocus(key)}
                      sx={{
                        borderRadius: 1.5,
                        fontWeight: 600, fontSize: '0.8rem',
                        cursor: 'pointer',
                        transition: 'all 0.2s',
                        bgcolor: isSelected ? 'rgba(212,160,83,0.15)' : 'transparent',
                        color: isSelected ? 'var(--accent)' : 'var(--text-secondary)',
                        border: `1px solid ${isSelected ? 'var(--accent)' : 'var(--border-subtle)'}`,
                        '&:hover': {
                          bgcolor: isSelected ? 'rgba(212,160,83,0.2)' : 'rgba(255,255,255,0.04)',
                          borderColor: isSelected ? 'var(--accent)' : 'var(--border-hover)',
                        },
                      }}
                    />
                  );
                })}
              </Box>
            </Box>

            {/* 2. Review Depth */}
            <Box>
              <Typography sx={{ fontFamily: 'var(--font-display)', fontSize: '0.88rem', fontWeight: 700, color: 'var(--text-primary)', mb: 0.5 }}>
                审查深度
              </Typography>
              <Typography sx={{ fontSize: '0.75rem', color: 'var(--text-muted)', mb: 1.5 }}>
                控制 AI 审查的详细程度
              </Typography>
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
                {DEPTH_OPTIONS.map((opt) => (
                  <Box
                    key={opt.value}
                    onClick={() => setReviewDepth(opt.value)}
                    sx={{
                      display: 'flex', alignItems: 'center', gap: 1.5,
                      p: '10px 14px', borderRadius: 'var(--radius-btn)',
                      border: `1px solid ${reviewDepth === opt.value ? 'var(--accent)' : 'var(--border-subtle)'}`,
                      bgcolor: reviewDepth === opt.value ? 'rgba(212,160,83,0.08)' : 'transparent',
                      cursor: 'pointer',
                      transition: 'all 0.2s',
                      '&:hover': {
                        bgcolor: reviewDepth === opt.value ? 'rgba(212,160,83,0.12)' : 'rgba(255,255,255,0.03)',
                        borderColor: reviewDepth === opt.value ? 'var(--accent)' : 'var(--border-hover)',
                      },
                    }}
                  >
                    <Box sx={{
                      width: 16, height: 16, borderRadius: '50%',
                      border: `2px solid ${reviewDepth === opt.value ? 'var(--accent)' : 'var(--border-subtle)'}`,
                      display: 'flex', alignItems: 'center', justifyContent: 'center',
                      flexShrink: 0,
                    }}>
                      {reviewDepth === opt.value && (
                        <Box sx={{ width: 8, height: 8, borderRadius: '50%', bgcolor: 'var(--accent)' }} />
                      )}
                    </Box>
                    <Box>
                      <Typography sx={{ fontSize: '0.82rem', fontWeight: 600, color: reviewDepth === opt.value ? 'var(--text-primary)' : 'var(--text-secondary)' }}>
                        {opt.label}
                      </Typography>
                      <Typography sx={{ fontSize: '0.72rem', color: 'var(--text-muted)' }}>
                        {opt.desc}
                      </Typography>
                    </Box>
                  </Box>
                ))}
              </Box>
            </Box>

            {/* 3. Custom Prompt */}
            <Box>
              <Typography sx={{ fontFamily: 'var(--font-display)', fontSize: '0.88rem', fontWeight: 700, color: 'var(--text-primary)', mb: 0.5 }}>
                自定义指令
              </Typography>
              <Typography sx={{ fontSize: '0.75rem', color: 'var(--text-muted)', mb: 1.5 }}>
                额外的审查要求，例如"重点关注并发安全问题"或"我们团队使用 Google Java Style"
              </Typography>
              <textarea
                value={customPrompt}
                onChange={(e) => setCustomPrompt(e.target.value)}
                placeholder="输入你的自定义审查要求..."
                style={inputStyle}
                onFocus={(e) => e.target.style.borderColor = 'var(--accent)'}
                onBlur={(e) => e.target.style.borderColor = 'var(--border-subtle)'}
              />
            </Box>
          </Box>
        )}
      </Box>
    </Box>
  );
}
