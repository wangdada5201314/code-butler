import React, { useState, useEffect, useCallback } from 'react';
import {
  Box, Typography, Chip, IconButton, Tooltip, Dialog,
  DialogTitle, DialogContent, TextField, Button, Alert, Fade,
  CircularProgress,
} from '@mui/material';
import MemoryIcon from '@mui/icons-material/Memory';
import DeleteIcon from '@mui/icons-material/Delete';
import SearchIcon from '@mui/icons-material/Search';
import CloseIcon from '@mui/icons-material/Close';
import EditIcon from '@mui/icons-material/Edit';
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome';

const API_BASE = '/api';

const TYPE_LABELS = {
  PREFERENCE: { label: '偏好', color: '#a78bfa' },
  DECISION: { label: '决策', color: '#f59e0b' },
  FACT: { label: '事实', color: '#3b82f6' },
  HABIT: { label: '习惯', color: '#10b981' },
  GENERAL: { label: '通用', color: '#6b7280' },
};

const TYPE_ICONS = {
  PREFERENCE: '⭐',
  DECISION: '🎯',
  FACT: '📋',
  HABIT: '🔄',
  GENERAL: '💭',
};

export default function MemoryPanel({ darkMode, open, onClose }) {
  const [memories, setMemories] = useState([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [editingId, setEditingId] = useState(null);
  const [editContent, setEditContent] = useState('');

  const fetchMemories = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await fetch(`${API_BASE}/code/memory`, { credentials: 'include' });
      const data = await res.json();
      if (data.code === 0) {
        setMemories(data.data || []);
      } else {
        setError(data.message || '加载失败');
      }
    } catch (e) {
      setError('网络错误');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (open) {
      fetchMemories();
      setSearchResults(null);
      setSearchQuery('');
    }
  }, [open, fetchMemories]);

  const handleSearch = async () => {
    if (!searchQuery.trim()) return;
    setLoading(true);
    setError(null);
    try {
      const res = await fetch(`${API_BASE}/code/memory/search`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ query: searchQuery.trim(), limit: 10 }),
      });
      const data = await res.json();
      if (data.code === 0) {
        setSearchResults(data.data || []);
      } else {
        setError(data.message || '搜索失败');
      }
    } catch (e) {
      setError('网络错误');
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async (id) => {
    try {
      const res = await fetch(`${API_BASE}/code/memory/${id}`, {
        method: 'DELETE',
        credentials: 'include',
      });
      const data = await res.json();
      if (data.code === 0) {
        setMemories((prev) => prev.filter((m) => m.id !== id));
        if (searchResults) {
          setSearchResults((prev) => prev.filter((r) => r.entity?.id !== id));
        }
      }
    } catch (e) {
      setError('删除失败');
    }
  };

  const handleEdit = async (id) => {
    if (!editContent.trim()) return;
    try {
      const res = await fetch(`${API_BASE}/code/memory/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ content: editContent.trim() }),
      });
      const data = await res.json();
      if (data.code === 0) {
        setEditingId(null);
        fetchMemories();
      } else {
        setError(data.message || '更新失败');
      }
    } catch (e) {
      setError('网络错误');
    }
  };

  const displayList = searchResults
    ? searchResults.map((r) => ({ ...r.entity, score: r.score }))
    : memories;

  return (
    <Dialog
      open={open}
      onClose={onClose}
      maxWidth="sm"
      fullWidth
      PaperProps={{
        sx: {
          borderRadius: 'var(--radius-card)',
          bgcolor: darkMode ? '#121016' : '#fafaf8',
          border: '1px solid var(--border-subtle)',
          maxHeight: '80vh',
        },
      }}
    >
      <DialogTitle sx={{ display: 'flex', alignItems: 'center', gap: 1.5, px: 3, pt: 2.5, pb: 1.5 }}>
        <Box sx={{
          width: 32, height: 32, borderRadius: 1.5,
          bgcolor: 'rgba(139,92,246,0.12)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
        }}>
          <MemoryIcon sx={{ fontSize: 18, color: '#a78bfa' }} />
        </Box>
        <Typography sx={{ fontFamily: 'var(--font-display)', fontWeight: 700, fontSize: '1rem', color: 'var(--text-primary)', flex: 1 }}>
          长期记忆
        </Typography>
        <Typography sx={{ fontSize: '0.7rem', color: 'var(--text-muted)', mr: 1 }}>
          {memories.length} 条
        </Typography>
        <IconButton size="small" onClick={onClose} sx={{ color: 'var(--text-muted)' }}>
          <CloseIcon sx={{ fontSize: 18 }} />
        </IconButton>
      </DialogTitle>

      <DialogContent sx={{ px: 3, pb: 3, pt: 1 }}>
        {/* Search bar */}
        <Box sx={{ display: 'flex', gap: 1, mb: 2 }}>
          <TextField
            fullWidth
            size="small"
            placeholder="搜索记忆..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            onKeyDown={(e) => { if (e.key === 'Enter') handleSearch(); }}
            sx={{
              '& .MuiOutlinedInput-root': {
                bgcolor: darkMode ? 'rgba(255,255,255,0.04)' : 'rgba(0,0,0,0.02)',
                borderRadius: 'var(--radius-btn)',
                fontSize: '0.82rem',
              },
            }}
          />
          <Button
            variant="contained"
            onClick={handleSearch}
            disabled={!searchQuery.trim()}
            sx={{
              minWidth: 40, borderRadius: 'var(--radius-btn)',
              bgcolor: 'var(--accent)',
              '&:hover': { bgcolor: 'var(--accent-hover)' },
            }}
          >
            <SearchIcon sx={{ fontSize: 18 }} />
          </Button>
          {searchResults && (
            <Button
              variant="outlined"
              size="small"
              onClick={() => { setSearchResults(null); setSearchQuery(''); }}
              sx={{
                borderRadius: 'var(--radius-btn)',
                borderColor: 'var(--border-subtle)',
                color: 'var(--text-muted)',
                fontSize: '0.72rem',
              }}
            >
              清除
            </Button>
          )}
        </Box>

        {error && (
          <Alert severity="error" sx={{ mb: 1.5, borderRadius: 2 }} onClose={() => setError(null)}>
            {error}
          </Alert>
        )}

        {loading ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 6 }}>
            <CircularProgress size={28} sx={{ color: 'var(--accent)' }} />
          </Box>
        ) : displayList.length === 0 ? (
          <Box sx={{ textAlign: 'center', py: 5 }}>
            <AutoAwesomeIcon sx={{ fontSize: 32, color: 'var(--text-muted)', mb: 1, opacity: 0.3 }} />
            <Typography sx={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>
              {searchResults ? '未找到匹配的记忆' : '暂无长期记忆。Agent 会在对话中自动记录用户偏好和重要信息。'}
            </Typography>
          </Box>
        ) : (
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.2 }}>
            {displayList.map((mem) => {
              const typeInfo = TYPE_LABELS[mem.memoryType] || TYPE_LABELS.GENERAL;
              const icon = TYPE_ICONS[mem.memoryType] || '💭';
              const isEditing = editingId === mem.id;

              return (
                <Fade in key={mem.id}>
                  <Box sx={{
                    p: '12px 14px',
                    borderRadius: 'var(--radius-btn)',
                    bgcolor: darkMode ? 'rgba(255,255,255,0.03)' : 'rgba(0,0,0,0.02)',
                    border: '1px solid var(--border-subtle)',
                    transition: 'all 0.2s',
                    '&:hover': { borderColor: 'var(--border-hover)' },
                  }}>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 0.8 }}>
                      <Chip
                        label={`${icon} ${typeInfo.label}`}
                        size="small"
                        sx={{
                          height: 22, fontSize: '0.65rem', fontWeight: 600,
                          bgcolor: `${typeInfo.color}20`,
                          color: typeInfo.color,
                          border: `1px solid ${typeInfo.color}30`,
                        }}
                      />
                      {mem.score != null && (
                        <Chip
                          label={`${(mem.score * 100).toFixed(0)}%`}
                          size="small"
                          sx={{
                            height: 22, fontSize: '0.65rem',
                            bgcolor: mem.score > 0.5 ? 'rgba(16,185,129,0.1)' : 'rgba(107,114,128,0.1)',
                            color: mem.score > 0.5 ? '#10b981' : '#6b7280',
                          }}
                        />
                      )}
                      <Box sx={{ flex: 1 }} />
                      <Tooltip title="编辑" arrow>
                        <IconButton
                          size="small"
                          onClick={() => { setEditingId(mem.id); setEditContent(mem.content); }}
                          sx={{ color: 'var(--text-muted)', '&:hover': { color: 'var(--accent)' } }}
                        >
                          <EditIcon sx={{ fontSize: 14 }} />
                        </IconButton>
                      </Tooltip>
                      <Tooltip title="删除" arrow>
                        <IconButton
                          size="small"
                          onClick={() => handleDelete(mem.id)}
                          sx={{ color: 'var(--text-muted)', '&:hover': { color: 'var(--danger)' } }}
                        >
                          <DeleteIcon sx={{ fontSize: 14 }} />
                        </IconButton>
                      </Tooltip>
                    </Box>

                    {isEditing ? (
                      <Box sx={{ display: 'flex', gap: 1 }}>
                        <TextField
                          fullWidth
                          multiline
                          size="small"
                          value={editContent}
                          onChange={(e) => setEditContent(e.target.value)}
                          sx={{
                            '& .MuiOutlinedInput-root': {
                              fontSize: '0.78rem',
                              bgcolor: darkMode ? 'rgba(255,255,255,0.05)' : 'rgba(0,0,0,0.03)',
                            },
                          }}
                        />
                        <Button
                          size="small"
                          variant="contained"
                          onClick={() => handleEdit(mem.id)}
                          sx={{ borderRadius: 'var(--radius-btn)', fontSize: '0.72rem', whiteSpace: 'nowrap' }}
                        >
                          保存
                        </Button>
                        <Button
                          size="small"
                          onClick={() => setEditingId(null)}
                          sx={{ color: 'var(--text-muted)', fontSize: '0.72rem' }}
                        >
                          取消
                        </Button>
                      </Box>
                    ) : (
                      <Typography sx={{
                        fontSize: '0.8rem', color: 'var(--text-secondary)', lineHeight: 1.6,
                      }}>
                        {mem.content}
                      </Typography>
                    )}

                    {mem.createTime && (
                      <Typography sx={{ fontSize: '0.65rem', color: 'var(--text-muted)', mt: 0.8, opacity: 0.7 }}>
                        创建于 {new Date(mem.createTime).toLocaleDateString('zh-CN', { year: 'numeric', month: 'short', day: 'numeric' })}
                      </Typography>
                    )}
                  </Box>
                </Fade>
              );
            })}
          </Box>
        )}
      </DialogContent>
    </Dialog>
  );
}
