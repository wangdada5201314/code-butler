import React, { useState } from 'react';
import {
  CardContent, Typography, TextField, Button, Box, CircularProgress,
  Alert, FormControl, InputLabel, Select, MenuItem, Paper, Chip,
} from '@mui/material';
import DescriptionIcon from '@mui/icons-material/Description';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import CheckIcon from '@mui/icons-material/Check';
import { generateDocs } from '../api/client.js';
import FavoriteReposBar from './FavoriteReposBar.jsx';

export default function DocsPanel({ darkMode }) {
  const [repoPath, setRepoPath] = useState('');
  const [docType, setDocType] = useState('README');
  const [docContent, setDocContent] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [copied, setCopied] = useState(false);

  const handleGenerate = async () => {
    if (!repoPath.trim()) { setError('请输入代码仓库路径'); return; }
    setLoading(true); setError(null); setDocContent('');
    try {
      const data = await generateDocs(repoPath.trim(), docType);
      const content = typeof data === 'string' ? data : data?.document || data?.content || data?.doc || JSON.stringify(data, null, 2);
      setDocContent(content);
    } catch (err) {
      setError(err.message || '文档生成失败');
    } finally {
      setLoading(false);
    }
  };

  const handleCopy = () => {
    if (docContent) {
      navigator.clipboard.writeText(docContent).then(() => {
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
      }).catch(() => {});
    }
  };

  return (
    <div className="forge-card">
      <CardContent sx={{ p: '22px 24px !important' }}>
        {/* Header */}
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.2, mb: 2.5 }}>
          <Box sx={{
            width: 32, height: 32, borderRadius: 1.5,
            background: 'rgba(167,139,250,0.12)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>
            <DescriptionIcon sx={{ color: '#a78bfa', fontSize: 18 }} />
          </Box>
          <Typography className="section-title" sx={{
            background: 'linear-gradient(135deg, #a78bfa, #c4b5fd) !important',
            WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent',
          }}>
            文档生成
          </Typography>
          <Box sx={{ flex: 1 }} />
          {docContent && (
            <Button
              size="small"
              onClick={handleCopy}
              startIcon={copied ? <CheckIcon sx={{ color: 'var(--success)' }} fontSize="small" /> : <ContentCopyIcon fontSize="small" />}
              sx={{
                textTransform: 'none',
                fontWeight: 600, fontSize: '0.8rem',
                color: copied ? 'var(--success)' : 'var(--text-secondary)',
                borderRadius: 1.5,
                '&:hover': { bgcolor: 'rgba(255,255,255,0.04)', color: 'var(--accent)' },
              }}
            >
              {copied ? '已复制' : '复制'}
            </Button>
          )}
        </Box>

        {/* Favorite repos quick-select */}
        <FavoriteReposBar onRepoSelect={(path) => setRepoPath(path)} />

        {/* Input row */}
        <Box sx={{ display: 'flex', gap: 1.5, mb: 2, flexWrap: 'wrap' }}>
          <TextField
            label="仓库路径"
            placeholder="E:/my-project"
            value={repoPath}
            onChange={(e) => setRepoPath(e.target.value)}
            disabled={loading}
            size="small"
            sx={{ flex: { xs: '1 1 100%', sm: 2 } }}
          />
          <FormControl size="small" sx={{ minWidth: 150 }} disabled={loading}>
            <InputLabel id="doc-type-label">文档类型</InputLabel>
            <Select labelId="doc-type-label" value={docType} label="文档类型" onChange={(e) => setDocType(e.target.value)}>
              <MenuItem value="README">README</MenuItem>
              <MenuItem value="CONTRIBUTING">CONTRIBUTING</MenuItem>
              <MenuItem value="API">API</MenuItem>
            </Select>
          </FormControl>
          <Button
            variant="contained"
            onClick={handleGenerate}
            disabled={loading}
            className="gradient-btn"
            sx={{ minWidth: 120 }}
            startIcon={loading ? <CircularProgress size={16} sx={{ color: '#0c0b0e' }} /> : <DescriptionIcon />}
          >
            {loading ? '生成中...' : '生成文档'}
          </Button>
        </Box>

        {error && <Alert severity="error" sx={{ mb: 2, borderRadius: 2 }} onClose={() => setError(null)}>{error}</Alert>}

        {/* Document output */}
        {docContent && (
          <Paper
            variant="outlined"
            sx={{
              borderRadius: 2, overflow: 'hidden',
              border: '1px solid var(--border-subtle)',
            }}
          >
            {/* Top bar */}
            <Box
              sx={{
                display: 'flex', alignItems: 'center', gap: 0.8,
                px: 2, py: 1.2,
                bgcolor: 'rgba(0,0,0,0.35)',
              }}
            >
              <Box sx={{ width: 9, height: 9, borderRadius: '50%', bgcolor: '#ef4444', opacity: 0.8 }} />
              <Box sx={{ width: 9, height: 9, borderRadius: '50%', bgcolor: '#f59e0b', opacity: 0.8 }} />
              <Box sx={{ width: 9, height: 9, borderRadius: '50%', bgcolor: '#22c55e', opacity: 0.8 }} />
              <Typography variant="caption" sx={{ ml: 1, color: 'var(--text-muted)', fontFamily: 'var(--font-code)', fontSize: '0.7rem' }}>
                {docType}.md
              </Typography>
              <Box sx={{ flex: 1 }} />
              <Chip
                label={docType}
                size="small"
                sx={{
                  bgcolor: 'rgba(167,139,250,0.15)',
                  color: '#a78bfa',
                  fontWeight: 600, fontSize: '0.65rem', height: 20,
                  border: '1px solid rgba(167,139,250,0.2)',
                }}
              />
            </Box>

            {/* Content area */}
            <Box
              sx={{
                p: 2.5,
                bgcolor: 'rgba(0,0,0,0.15)',
                fontFamily: 'var(--font-code)',
                fontSize: '0.82rem',
                lineHeight: 1.7,
                whiteSpace: 'pre-wrap',
                overflow: 'auto',
                maxHeight: 520,
                color: 'var(--text-primary)',
              }}
              className="custom-scrollbar"
            >
              {docContent}
            </Box>
          </Paper>
        )}

        {/* Empty state */}
        {!loading && !error && !docContent && (
          <Box sx={{ textAlign: 'center', py: 5 }}>
            <Box sx={{
              width: 52, height: 52, borderRadius: 2, mx: 'auto', mb: 2,
              background: 'rgba(167,139,250,0.06)',
              border: '1px solid rgba(167,139,250,0.1)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
            }}>
              <DescriptionIcon sx={{ fontSize: 24, color: 'var(--text-muted)' }} />
            </Box>
            <Typography sx={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>
              选择文档类型并点击「生成文档」
            </Typography>
          </Box>
        )}
      </CardContent>
    </div>
  );
}
