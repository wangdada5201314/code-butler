import React from 'react';
import { Box, Typography, Avatar, Tooltip, IconButton, Chip } from '@mui/material';
import CodeIcon from '@mui/icons-material/Code';
import BugReportIcon from '@mui/icons-material/BugReport';
import PsychologyIcon from '@mui/icons-material/Psychology';
import DescriptionIcon from '@mui/icons-material/Description';
import ChatBubbleOutlineIcon from '@mui/icons-material/ChatBubbleOutline';
import HistoryIcon from '@mui/icons-material/History';
import SettingsIcon from '@mui/icons-material/Settings';
import DarkModeIcon from '@mui/icons-material/DarkMode';
import LightModeIcon from '@mui/icons-material/LightMode';
import LogoutIcon from '@mui/icons-material/Logout';
import GitHubIcon from '@mui/icons-material/GitHub';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ErrorIcon from '@mui/icons-material/Error';
import { useLoginUser } from '../stores/useLoginUser.jsx';

/** Navigation items */
const NAV_ITEMS = [
  { key: 'review', label: '代码审查', icon: BugReportIcon, color: 'var(--accent)', bg: 'rgba(212,160,83,0.12)' },
  { key: 'chat',   label: '智能问答', icon: PsychologyIcon, color: 'var(--accent-secondary)', bg: 'rgba(45,212,191,0.12)' },
  { key: 'docs',    label: '文档生成', icon: DescriptionIcon, color: '#a78bfa', bg: 'rgba(167,139,250,0.12)' },
  { key: 'general', label: '通用聊天', icon: ChatBubbleOutlineIcon, color: '#f472b6', bg: 'rgba(244,114,182,0.12)' },
];

export default function Sidebar({ activeTab, onTabChange, darkMode, onToggleTheme, onOpenHistory, onOpenPreference, healthStatus }) {
  const { loginUser, logout } = useLoginUser();

  return (
    <Box
      sx={{
        position: 'fixed',
        top: 0,
        left: 0,
        bottom: 0,
        width: { xs: 0, sm: 72, md: 240 },
        zIndex: 1200,
        background: darkMode
          ? 'rgba(10,9,12,0.97)'
          : 'rgba(248,246,243,0.97)',
        backdropFilter: 'blur(20px)',
        WebkitBackdropFilter: 'blur(20px)',
        borderRight: '1px solid var(--border-subtle)',
        display: 'flex',
        flexDirection: 'column',
        overflow: 'hidden',
        transition: 'width 0.3s cubic-bezier(0.4, 0, 0.2, 1)',
      }}
    >
      {/* ── Logo Section ── */}
      <Box sx={{
        display: 'flex', alignItems: 'center', gap: 1.5,
        px: { sm: '18px', md: '20px' },
        pt: '20px', pb: '16px',
        minHeight: 64,
      }}>
        <Box
          sx={{
            width: 36, height: 36, borderRadius: 1.5, flexShrink: 0,
            background: 'linear-gradient(135deg, var(--accent), var(--accent-secondary))',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            boxShadow: '0 0 18px var(--accent-glow)',
            transition: 'transform 0.3s ease',
            '&:hover': { transform: 'rotate(-8deg) scale(1.05)' },
          }}
        >
          <CodeIcon sx={{ color: '#0c0b0e', fontSize: 19 }} />
        </Box>
        <Box sx={{ display: { xs: 'none', md: 'block' }, overflow: 'hidden' }}>
          <Typography
            sx={{
              fontFamily: 'var(--font-display)',
              fontSize: '1.05rem', fontWeight: 800, letterSpacing: '-0.02em',
              color: 'var(--text-primary)',
              lineHeight: 1.2, whiteSpace: 'nowrap',
            }}
          >
            Code Butler
          </Typography>
          <Chip
            label="v2.0"
            size="small"
            sx={{
              height: 18, fontSize: '0.58rem', fontWeight: 700, mt: 0.3,
              bgcolor: 'rgba(212,160,83,0.1)',
              color: 'var(--accent)',
              border: '1px solid rgba(212,160,83,0.2)',
            }}
          />
        </Box>
      </Box>

      {/* ── Navigation Items ── */}
      <Box sx={{ px: { sm: '10px', md: '12px' }, mt: 1 }}>
        {NAV_ITEMS.map((item) => {
          const Icon = item.icon;
          const isActive = activeTab === item.key;
          return (
            <Tooltip
              key={item.key}
              title={item.label}
              placement="right"
              disableHoverListener={activeTab === item.key}
            >
              <Box
                onClick={() => onTabChange(item.key)}
                sx={{
                  display: 'flex', alignItems: 'center', gap: 1.5,
                  px: { sm: '12px', md: '14px' },
                  py: '11px',
                  mb: '4px',
                  borderRadius: 'var(--radius-btn)',
                  cursor: 'pointer',
                  position: 'relative',
                  transition: 'all 0.25s cubic-bezier(0.4, 0, 0.2, 1)',
                  bgcolor: isActive ? (darkMode ? 'rgba(212,160,83,0.1)' : 'rgba(212,160,83,0.08)') : 'transparent',
                  '&:hover': {
                    bgcolor: isActive
                      ? (darkMode ? 'rgba(212,160,83,0.12)' : 'rgba(212,160,83,0.1)')
                      : (darkMode ? 'rgba(255,255,255,0.04)' : 'rgba(0,0,0,0.03)'),
                  },
                }}
              >
                {/* Active indicator bar */}
                {isActive && (
                  <Box
                    sx={{
                      position: 'absolute',
                      left: 0,
                      top: '20%',
                      bottom: '20%',
                      width: 3,
                      borderRadius: 2,
                      background: 'linear-gradient(180deg, var(--accent), var(--accent-secondary))',
                      boxShadow: '0 0 8px var(--accent-glow)',
                    }}
                  />
                )}
                <Box sx={{
                  width: 30, height: 30, borderRadius: 1.5, flexShrink: 0,
                  bgcolor: isActive ? item.bg : 'transparent',
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  transition: 'all 0.25s',
                }}>
                  <Icon sx={{
                    fontSize: 18,
                    color: isActive ? item.color : 'var(--text-muted)',
                    transition: 'color 0.25s',
                  }} />
                </Box>
                <Typography sx={{
                  display: { xs: 'none', md: 'block' },
                  fontSize: '0.85rem',
                  fontWeight: isActive ? 700 : 500,
                  color: isActive ? 'var(--text-primary)' : 'var(--text-secondary)',
                  fontFamily: 'var(--font-body)',
                  whiteSpace: 'nowrap',
                  transition: 'all 0.25s',
                }}>
                  {item.label}
                </Typography>
              </Box>
            </Tooltip>
          );
        })}
      </Box>

      {/* ── Health Status (compact) ── */}
      <Box sx={{
        mx: { sm: '10px', md: '12px' },
        mt: 2,
        px: { sm: '10px', md: '14px' },
        py: '10px',
        borderRadius: 'var(--radius-btn)',
        bgcolor: darkMode ? 'rgba(255,255,255,0.02)' : 'rgba(0,0,0,0.02)',
        border: '1px solid var(--border-subtle)',
      }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          {healthStatus === 'loading' ? (
            <Box sx={{ width: 8, height: 8, borderRadius: '50%', bgcolor: 'var(--text-muted)', animation: 'dotPulse 1.4s ease-in-out infinite' }} />
          ) : healthStatus === 'healthy' ? (
            <CheckCircleIcon sx={{ fontSize: 14, color: 'var(--success)' }} />
          ) : (
            <ErrorIcon sx={{ fontSize: 14, color: 'var(--danger)' }} />
          )}
          <Typography sx={{
            display: { xs: 'none', md: 'block' },
            fontSize: '0.72rem', fontWeight: 600,
            color: healthStatus === 'healthy' ? 'var(--success)' : healthStatus === 'error' ? 'var(--danger)' : 'var(--text-muted)',
            whiteSpace: 'nowrap',
          }}>
            {healthStatus === 'loading' ? '检测中...' : healthStatus === 'healthy' ? '服务正常' : '服务异常'}
          </Typography>
        </Box>
      </Box>

      {/* ── Spacer ── */}
      <Box sx={{ flex: 1 }} />

      {/* ── Bottom Section ── */}
      <Box sx={{
        px: { sm: '10px', md: '12px' },
        pb: '12px',
        borderTop: '1px solid var(--border-subtle)',
        pt: '12px',
      }}>
        {/* History */}
        <Tooltip title="操作历史" placement="right">
          <Box
            onClick={onOpenHistory}
            sx={{
              display: 'flex', alignItems: 'center', gap: 1.5,
              px: { sm: '12px', md: '14px' }, py: '9px',
              borderRadius: 'var(--radius-btn)',
              cursor: 'pointer',
              transition: 'all 0.2s',
              '&:hover': { bgcolor: darkMode ? 'rgba(255,255,255,0.04)' : 'rgba(0,0,0,0.03)' },
            }}
          >
            <HistoryIcon sx={{ fontSize: 18, color: 'var(--text-muted)', flexShrink: 0 }} />
            <Typography sx={{
              display: { xs: 'none', md: 'block' },
              fontSize: '0.82rem', color: 'var(--text-secondary)',
              whiteSpace: 'nowrap',
            }}>
              操作历史
            </Typography>
          </Box>
        </Tooltip>

        {/* Settings */}
        <Tooltip title="审查偏好" placement="right">
          <Box
            onClick={onOpenPreference}
            sx={{
              display: 'flex', alignItems: 'center', gap: 1.5,
              px: { sm: '12px', md: '14px' }, py: '9px',
              borderRadius: 'var(--radius-btn)',
              cursor: 'pointer',
              transition: 'all 0.2s',
              '&:hover': { bgcolor: darkMode ? 'rgba(255,255,255,0.04)' : 'rgba(0,0,0,0.03)' },
            }}
          >
            <SettingsIcon sx={{ fontSize: 18, color: 'var(--text-muted)', flexShrink: 0, transition: 'transform 0.3s', '&:hover': { transform: 'rotate(30deg)' } }} />
            <Typography sx={{
              display: { xs: 'none', md: 'block' },
              fontSize: '0.82rem', color: 'var(--text-secondary)',
              whiteSpace: 'nowrap',
            }}>
              审查偏好
            </Typography>
          </Box>
        </Tooltip>

        {/* Theme toggle + GitHub */}
        <Box sx={{
          display: 'flex', alignItems: 'center', gap: 0.5,
          px: { sm: '6px', md: '8px' }, mt: '6px',
        }}>
          <Tooltip title={darkMode ? '亮色模式' : '暗色模式'} placement="right">
            <IconButton
              size="small"
              onClick={onToggleTheme}
              sx={{
                color: 'var(--text-muted)',
                transition: 'all 0.3s',
                '&:hover': { color: 'var(--accent)', transform: 'rotate(20deg)' },
              }}
            >
              {darkMode ? <LightModeIcon sx={{ fontSize: 17 }} /> : <DarkModeIcon sx={{ fontSize: 17 }} />}
            </IconButton>
          </Tooltip>
          <Tooltip title="GitHub" placement="right">
            <IconButton
              size="small"
              onClick={() => window.open('https://github.com', '_blank')}
              sx={{
                color: 'var(--text-muted)',
                '&:hover': { color: 'var(--text-primary)' },
              }}
            >
              <GitHubIcon sx={{ fontSize: 17 }} />
            </IconButton>
          </Tooltip>
        </Box>

        {/* User Section */}
        {loginUser && (
          <Box sx={{
            display: 'flex', alignItems: 'center', gap: 1.2,
            px: { sm: '8px', md: '14px' },
            py: '10px', mt: '6px',
            borderRadius: 'var(--radius-btn)',
            bgcolor: darkMode ? 'rgba(255,255,255,0.02)' : 'rgba(0,0,0,0.02)',
            border: '1px solid var(--border-subtle)',
          }}>
            <Avatar
              sx={{
                width: 30, height: 30, fontSize: '0.78rem', flexShrink: 0,
                background: 'linear-gradient(135deg, var(--accent), var(--accent-secondary))',
                color: '#0c0b0e', fontWeight: 700,
              }}
            >
              {(loginUser.userName || loginUser.userAccount || 'U').charAt(0).toUpperCase()}
            </Avatar>
            <Box sx={{ display: { xs: 'none', md: 'block' }, flex: 1, overflow: 'hidden' }}>
              <Typography sx={{
                fontSize: '0.8rem', fontWeight: 600,
                color: 'var(--text-primary)',
                overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
              }}>
                {loginUser.userName || loginUser.userAccount}
              </Typography>
            </Box>
            <Tooltip title="退出登录" placement="right">
              <IconButton
                size="small"
                onClick={logout}
                sx={{
                  color: 'var(--text-muted)',
                  display: { xs: 'none', md: 'flex' },
                  '&:hover': { color: 'var(--danger)' },
                }}
              >
                <LogoutIcon sx={{ fontSize: 16 }} />
              </IconButton>
            </Tooltip>
          </Box>
        )}
      </Box>
    </Box>
  );
}
