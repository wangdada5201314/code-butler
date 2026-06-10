import React, { useState, useEffect, useCallback } from 'react';
import { Box, Typography, Chip, IconButton, Tooltip } from '@mui/material';
import StarIcon from '@mui/icons-material/Star';
import StarBorderIcon from '@mui/icons-material/StarBorder';
import AddIcon from '@mui/icons-material/Add';
import CloseIcon from '@mui/icons-material/Close';
import FolderIcon from '@mui/icons-material/Folder';
import { getFavoriteRepos, addFavoriteRepo, removeFavoriteRepo } from '../api/client.js';

/**
 * 收藏仓库条 — 显示在各功能面板顶部，提供仓库路径快捷选择
 * @param {{ onRepoSelect: (path: string) => void }} props
 */
export default function FavoriteReposBar({ onRepoSelect }) {
  const [repos, setRepos] = useState([]);
  const [showAdd, setShowAdd] = useState(false);
  const [newPath, setNewPath] = useState('');
  const [newName, setNewName] = useState('');
  const [adding, setAdding] = useState(false);

  const fetchRepos = useCallback(() => {
    getFavoriteRepos()
      .then(setRepos)
      .catch(() => {});
  }, []);

  useEffect(() => {
    fetchRepos();
  }, [fetchRepos]);

  const handleAdd = async () => {
    if (!newPath.trim()) return;
    setAdding(true);
    try {
      await addFavoriteRepo(newPath.trim(), newName.trim());
      setNewPath('');
      setNewName('');
      setShowAdd(false);
      fetchRepos();
    } catch (err) {
      console.error('添加收藏失败:', err);
    } finally {
      setAdding(false);
    }
  };

  const handleRemove = async (id) => {
    try {
      await removeFavoriteRepo(id);
      setRepos((prev) => prev.filter((r) => r.id !== id));
    } catch (err) {
      console.error('删除收藏失败:', err);
    }
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleAdd();
    }
    if (e.key === 'Escape') {
      setShowAdd(false);
      setNewPath('');
      setNewName('');
    }
  };

  if (repos.length === 0 && !showAdd) {
    return (
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.8, mb: 1.5 }}>
        <StarBorderIcon sx={{ fontSize: 14, color: 'var(--text-muted)' }} />
        <Typography sx={{ fontSize: '0.72rem', color: 'var(--text-muted)' }}>
          暂无收藏仓库
        </Typography>
        <Chip
          icon={<AddIcon sx={{ fontSize: 14 }} />}
          label="添加"
          size="small"
          onClick={() => setShowAdd(true)}
          sx={{
            height: 22, borderRadius: 1,
            bgcolor: 'transparent',
            border: '1px dashed var(--border-subtle)',
            color: 'var(--text-muted)',
            fontSize: '0.7rem', fontWeight: 500,
            cursor: 'pointer',
            '&:hover': { borderColor: 'var(--accent)', color: 'var(--accent)', bgcolor: 'rgba(212,160,83,0.04)' },
          }}
        />
        {showAdd && (
          <InlineAddForm
            newPath={newPath} setNewPath={setNewPath}
            newName={newName} setNewName={setNewName}
            adding={adding} onAdd={handleAdd}
            onCancel={() => { setShowAdd(false); setNewPath(''); setNewName(''); }}
            onKeyDown={handleKeyDown}
          />
        )}
      </Box>
    );
  }

  return (
    <Box sx={{ mb: 1.5 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.8, flexWrap: 'wrap' }}>
        <StarIcon sx={{ fontSize: 14, color: 'var(--accent)', flexShrink: 0 }} />
        {repos.map((repo) => (
          <Tooltip key={repo.id} title={repo.repoPath} arrow>
            <Chip
              icon={<FolderIcon sx={{ fontSize: 13 }} />}
              label={repo.repoName || repo.repoPath.split(/[/\\]/).pop() || repo.repoPath}
              size="small"
              onClick={() => onRepoSelect(repo.repoPath)}
              onDelete={() => handleRemove(repo.id)}
              deleteIcon={<CloseIcon sx={{ fontSize: 12 }} />}
              sx={{
                height: 24, borderRadius: 1,
                bgcolor: 'rgba(212,160,83,0.08)',
                border: '1px solid rgba(212,160,83,0.15)',
                color: 'var(--accent)',
                fontSize: '0.72rem', fontWeight: 500,
                cursor: 'pointer',
                transition: 'all 0.2s',
                '&:hover': { bgcolor: 'rgba(212,160,83,0.15)', borderColor: 'var(--accent)' },
                '& .MuiChip-deleteIcon': {
                  color: 'var(--text-muted)',
                  '&:hover': { color: 'var(--danger)' },
                },
              }}
            />
          </Tooltip>
        ))}
        {!showAdd && (
          <Chip
            icon={<AddIcon sx={{ fontSize: 14 }} />}
            label="添加"
            size="small"
            onClick={() => setShowAdd(true)}
            sx={{
              height: 24, borderRadius: 1,
              bgcolor: 'transparent',
              border: '1px dashed var(--border-subtle)',
              color: 'var(--text-muted)',
              fontSize: '0.7rem', fontWeight: 500,
              cursor: 'pointer',
              '&:hover': { borderColor: 'var(--accent)', color: 'var(--accent)' },
            }}
          />
        )}
      </Box>

      {showAdd && (
        <InlineAddForm
          newPath={newPath} setNewPath={setNewPath}
          newName={newName} setNewName={setNewName}
          adding={adding} onAdd={handleAdd}
          onCancel={() => { setShowAdd(false); setNewPath(''); setNewName(''); }}
          onKeyDown={handleKeyDown}
        />
      )}
    </Box>
  );
}

/* Inline add form */
function InlineAddForm({ newPath, setNewPath, newName, setNewName, adding, onAdd, onCancel, onKeyDown }) {
  const fieldStyle = {
    padding: '7px 10px', borderRadius: 'var(--radius-input)',
    border: '1px solid var(--border-subtle)', background: 'rgba(255,255,255,0.03)',
    color: 'var(--text-primary)', fontFamily: 'var(--font-body)', fontSize: '0.78rem',
    outline: 'none', transition: 'border-color 0.2s',
  };

  return (
    <Box sx={{ display: 'flex', gap: 1, mt: 1, flexWrap: 'wrap', alignItems: 'center' }} className="animate-in" style={{ animationDelay: '0s' }}>
      <input
        type="text"
        value={newPath}
        onChange={(e) => setNewPath(e.target.value)}
        onKeyDown={onKeyDown}
        placeholder="仓库路径"
        autoFocus
        style={{ ...fieldStyle, flex: '1 1 200px', minWidth: 0 }}
        onFocus={(e) => e.target.style.borderColor = 'var(--accent)'}
        onBlur={(e) => e.target.style.borderColor = 'var(--border-subtle)'}
      />
      <input
        type="text"
        value={newName}
        onChange={(e) => setNewName(e.target.value)}
        onKeyDown={onKeyDown}
        placeholder="别名（可选）"
        style={{ ...fieldStyle, width: 120 }}
        onFocus={(e) => e.target.style.borderColor = 'var(--accent)'}
        onBlur={(e) => e.target.style.borderColor = 'var(--border-subtle)'}
      />
      <button
        onClick={onAdd}
        disabled={adding || !newPath.trim()}
        className="gradient-btn"
        style={{ padding: '7px 14px', fontSize: '0.75rem', border: 'none', cursor: adding ? 'not-allowed' : 'pointer' }}
      >
        {adding ? '...' : '添加'}
      </button>
      <IconButton size="small" onClick={onCancel} sx={{ color: 'var(--text-muted)' }}>
        <CloseIcon sx={{ fontSize: 14 }} />
      </IconButton>
    </Box>
  );
}
