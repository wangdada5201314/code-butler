import React, { useState } from 'react';
import {
  CardContent, Typography, TextField, Button, Box, CircularProgress,
  Alert, Chip, LinearProgress,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import BugReportIcon from '@mui/icons-material/BugReport';
import { reviewCode } from '../api/client.js';

export default function ReviewPanel({ darkMode }) {
  const [repoPath, setRepoPath] = useState('');
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const handleReview = async () => {
    if (!repoPath.trim()) { setError('请输入代码仓库路径'); return; }
    setLoading(true); setError(null); setResult(null);
    try {
      const data = await reviewCode(repoPath.trim());
      setResult(data);
    } catch (err) {
      setError(err.message || '代码审查失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="glass-card" style={{ height: '100%' }}>
      <CardContent sx={{ p: '20px 24px !important' }}>
        {/* Header */}
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2.5 }}>
          <BugReportIcon sx={{ color: '#6366f1', fontSize: 22 }} />
          <Typography className="section-title">代码审查</Typography>
        </Box>

        {/* Input row */}
        <Box sx={{ display: 'flex', gap: 1.5, mb: 2 }}>
          <TextField
            fullWidth
            label="仓库路径"
            placeholder="E:/my-project"
            value={repoPath}
            onChange={(e) => setRepoPath(e.target.value)}
            disabled={loading}
            size="small"
            sx={{ flex: 1 }}
          />
          <Button
            variant="contained"
            onClick={handleReview}
            disabled={loading}
            className="gradient-btn"
            sx={{ minWidth: 130 }}
            startIcon={loading ? <CircularProgress size={16} sx={{ color: '#fff' }} /> : <SearchIcon />}
          >
            {loading ? '审查中...' : '开始审查'}
          </Button>
        </Box>

        {/* Loading bar */}
        {loading && <LinearProgress sx={{ mb: 2, borderRadius: 2, height: 4 }} />}

        {/* Error */}
        {error && (
          <Alert severity="error" sx={{ mb: 2, borderRadius: 2 }} onClose={() => setError(null)}>{error}</Alert>
        )}

        {/* Result */}
        {result && (
          <Box>
            <Box sx={{ display: 'flex', gap: 1, mb: 2, flexWrap: 'wrap' }}>
              <Chip label={`Session: ${result.sessionId || '-'}`} size="small"
                sx={{ bgcolor: 'rgba(99,102,241,0.1)', color: '#6366f1', fontWeight: 600 }} />
              <Chip label={`仓库: ${result.repoPath || '-'}`} size="small"
                sx={{ bgcolor: 'rgba(59,130,246,0.1)', color: '#2563eb', fontWeight: 600 }} />
            </Box>
            <Box
              component="pre"
              sx={{
                p: 2,
                borderRadius: 2,
                bgcolor: darkMode ? 'rgba(30,41,59,0.6)' : 'rgba(248,250,252,0.8)',
                border: '1px solid',
                borderColor: darkMode ? 'rgba(71,85,105,0.5)' : 'rgba(203,213,225,0.8)',
                fontSize: '0.82rem',
                lineHeight: 1.8,
                whiteSpace: 'pre-wrap',
                wordBreak: 'break-word',
                maxHeight: 500,
                overflow: 'auto',
                color: darkMode ? '#e2e8f0' : '#334155',
                fontFamily: '"JetBrains Mono", "Noto Sans SC", monospace',
              }}
            >
              {result.review || '审查结果为空'}
            </Box>
          </Box>
        )}

        {/* Empty state */}
        {!loading && !error && !result && (
          <Box sx={{ textAlign: 'center', py: 4 }}>
            <BugReportIcon sx={{ fontSize: 40, color: darkMode ? '#334155' : '#cbd5e1', mb: 1 }} />
            <Typography variant="body2" sx={{ color: darkMode ? '#64748b' : '#94a3b8' }}>
              输入仓库路径并点击「开始审查」
            </Typography>
          </Box>
        )}
      </CardContent>
    </div>
  );
}
