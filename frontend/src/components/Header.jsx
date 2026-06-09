import React from 'react';
import { AppBar, Toolbar, Typography, Box, IconButton, Tooltip, Button, Avatar } from '@mui/material';
import CodeIcon from '@mui/icons-material/Code';
import DarkModeIcon from '@mui/icons-material/DarkMode';
import LightModeIcon from '@mui/icons-material/LightMode';
import GitHubIcon from '@mui/icons-material/GitHub';
import LoginIcon from '@mui/icons-material/Login';
import LogoutIcon from '@mui/icons-material/Logout';
import { useLoginUser } from '../stores/useLoginUser.jsx';

export default function Header({ darkMode, onToggleTheme, onOpenLogin }) {
  const { loginUser, logout } = useLoginUser();

  return (
    <AppBar
      position="sticky"
      elevation={0}
      sx={{
        background: 'linear-gradient(135deg, #1e1b4b 0%, #312e81 50%, #4338ca 100%)',
        backdropFilter: 'blur(12px)',
        borderBottom: '1px solid rgba(255,255,255,0.1)',
      }}
    >
      <Toolbar sx={{ minHeight: '68px', display: 'flex', justifyContent: 'space-between' }}>
        {/* Left: Logo & Title */}
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
          <Box
            sx={{
              width: 40, height: 40, borderRadius: 1.5,
              background: 'linear-gradient(135deg, #6366f1, #06b6d4)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              boxShadow: '0 0 16px rgba(99,102,241,0.5)',
            }}
          >
            <CodeIcon sx={{ color: '#fff', fontSize: 22 }} />
          </Box>
          <Box>
            <Typography
              sx={{
                fontSize: '1.1rem', fontWeight: 800, letterSpacing: '-0.02em',
                background: 'linear-gradient(135deg, #e0e7ff, #fff)',
                WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent',
              }}
            >
              Code Butler
            </Typography>
            <Typography sx={{ fontSize: '0.65rem', color: 'rgba(255,255,255,0.6)', mt: -0.3 }}>
              代码仓库智能管家 · powered by AgentScope
            </Typography>
          </Box>
        </Box>

        {/* Right: Actions */}
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
          {/* User / Login */}
          {loginUser ? (
            <>
              <Avatar
                sx={{
                  width: 32, height: 32, fontSize: '0.85rem',
                  bgcolor: 'rgba(99,102,241,0.6)',
                }}
              >
                {loginUser.userName?.charAt(0) || loginUser.userAccount?.charAt(0) || 'U'}
              </Avatar>
              <Typography
                sx={{
                  fontSize: '0.8rem',
                  color: 'rgba(255,255,255,0.85)',
                  mr: 1,
                  maxWidth: 120,
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                }}
              >
                {loginUser.userName || loginUser.userAccount}
              </Typography>
              <Tooltip title="退出登录">
                <IconButton
                  size="small"
                  onClick={logout}
                  sx={{ color: 'rgba(255,255,255,0.6)', '&:hover': { color: '#fff' } }}
                >
                  <LogoutIcon fontSize="small" />
                </IconButton>
              </Tooltip>
            </>
          ) : (
            <Button
              size="small"
              variant="outlined"
              startIcon={<LoginIcon fontSize="small" />}
              onClick={onOpenLogin}
              sx={{
                color: 'rgba(255,255,255,0.85)',
                borderColor: 'rgba(255,255,255,0.3)',
                fontSize: '0.75rem',
                textTransform: 'none',
                '&:hover': { borderColor: 'rgba(255,255,255,0.6)', bgcolor: 'rgba(255,255,255,0.08)' },
              }}
            >
              登录
            </Button>
          )}

          <Tooltip title="GitHub">
            <IconButton
              size="small"
              onClick={() => window.open('https://github.com', '_blank')}
              sx={{ color: 'rgba(255,255,255,0.6)', '&:hover': { color: '#fff' } }}
            >
              <GitHubIcon fontSize="small" />
            </IconButton>
          </Tooltip>
          <Tooltip title={darkMode ? '切换亮色模式' : '切换暗色模式'}>
            <IconButton
              size="small"
              onClick={onToggleTheme}
              sx={{ color: 'rgba(255,255,255,0.6)', '&:hover': { color: '#fff' } }}
            >
              {darkMode ? <LightModeIcon fontSize="small" /> : <DarkModeIcon fontSize="small" />}
            </IconButton>
          </Tooltip>
        </Box>
      </Toolbar>
    </AppBar>
  );
}
