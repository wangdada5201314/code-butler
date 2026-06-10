import React from 'react';
import { AppBar, Toolbar, Typography, Box, IconButton, Tooltip, Avatar, Chip } from '@mui/material';
import CodeIcon from '@mui/icons-material/Code';
import DarkModeIcon from '@mui/icons-material/DarkMode';
import LightModeIcon from '@mui/icons-material/LightMode';
import GitHubIcon from '@mui/icons-material/GitHub';
import LogoutIcon from '@mui/icons-material/Logout';
import HistoryIcon from '@mui/icons-material/History';
import { useLoginUser } from '../stores/useLoginUser.jsx';

export default function Header({ darkMode, onToggleTheme, onOpenLogin, onOpenHistory }) {
  const { loginUser, logout } = useLoginUser();

  return (
    <AppBar
      position="sticky"
      elevation={0}
      sx={{
        background: darkMode
          ? 'rgba(8,7,9,0.85)'
          : 'rgba(246,244,241,0.85)',
        backdropFilter: 'blur(16px) saturate(180%)',
        WebkitBackdropFilter: 'blur(16px) saturate(180%)',
        borderBottom: '1px solid var(--border-subtle)',
      }}
    >
      <Toolbar sx={{ minHeight: '56px', display: 'flex', justifyContent: 'space-between', px: { xs: 2, sm: 3 } }}>
        {/* Left: Logo & Title */}
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.2 }}>
          <Box
            sx={{
              width: 34, height: 34, borderRadius: 1.5,
              background: 'linear-gradient(135deg, var(--accent), var(--accent-secondary))',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              boxShadow: `0 0 16px var(--accent-glow)`,
              transition: 'transform 0.3s ease',
              '&:hover': { transform: 'rotate(-8deg) scale(1.05)' },
            }}
          >
            <CodeIcon sx={{ color: '#0c0b0e', fontSize: 18 }} />
          </Box>
          <Box>
            <Typography
              sx={{
                fontFamily: 'var(--font-display)',
                fontSize: '1rem', fontWeight: 800, letterSpacing: '-0.02em',
                color: 'var(--text-primary)',
                lineHeight: 1.2,
              }}
            >
              Code Butler
            </Typography>
          </Box>
          <Chip
            label="v2.0"
            size="small"
            sx={{
              height: 20, fontSize: '0.6rem', fontWeight: 700,
              bgcolor: 'rgba(212,160,83,0.1)',
              color: 'var(--accent)',
              border: '1px solid rgba(212,160,83,0.2)',
            }}
          />
        </Box>

        {/* Right: Actions */}
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.8 }}>
          {loginUser && (
            <Tooltip title="操作历史" arrow>
              <IconButton
                size="small"
                onClick={onOpenHistory}
                sx={{
                  color: 'var(--text-muted)',
                  transition: 'all 0.2s',
                  '&:hover': { color: 'var(--accent)', bgcolor: 'rgba(212,160,83,0.08)' },
                }}
              >
                <HistoryIcon fontSize="small" />
              </IconButton>
            </Tooltip>
          )}

          {loginUser && (
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, ml: 0.5 }}>
              <Avatar
                sx={{
                  width: 28, height: 28, fontSize: '0.75rem',
                  background: 'linear-gradient(135deg, var(--accent), var(--accent-secondary))',
                  color: '#0c0b0e', fontWeight: 700,
                }}
              >
                {(loginUser.userName || loginUser.userAccount || 'U').charAt(0).toUpperCase()}
              </Avatar>
              <Typography
                sx={{
                  fontSize: '0.78rem', fontWeight: 600,
                  color: 'var(--text-primary)',
                  maxWidth: 100,
                  overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
                }}
              >
                {loginUser.userName || loginUser.userAccount}
              </Typography>
            </Box>
          )}

          <Box sx={{ width: 1, height: 20, bgcolor: 'var(--border-subtle)', mx: 0.5 }} />

          <Tooltip title="GitHub" arrow>
            <IconButton
              size="small"
              onClick={() => window.open('https://github.com', '_blank')}
              sx={{
                color: 'var(--text-muted)',
                '&:hover': { color: 'var(--text-primary)' },
              }}
            >
              <GitHubIcon sx={{ fontSize: 18 }} />
            </IconButton>
          </Tooltip>

          <Tooltip title={darkMode ? '亮色模式' : '暗色模式'} arrow>
            <IconButton
              size="small"
              onClick={onToggleTheme}
              sx={{
                color: 'var(--text-muted)',
                transition: 'all 0.3s',
                '&:hover': { color: 'var(--accent)', transform: 'rotate(20deg)' },
              }}
            >
              {darkMode ? <LightModeIcon fontSize="small" /> : <DarkModeIcon fontSize="small" />}
            </IconButton>
          </Tooltip>

          {loginUser && (
            <Tooltip title="退出登录" arrow>
              <IconButton
                size="small"
                onClick={logout}
                sx={{
                  color: 'var(--text-muted)',
                  '&:hover': { color: 'var(--danger)' },
                }}
              >
                <LogoutIcon fontSize="small" />
              </IconButton>
            </Tooltip>
          )}
        </Box>
      </Toolbar>
    </AppBar>
  );
}
