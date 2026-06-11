import React, { useState, useEffect, useCallback } from 'react';
import { Box, Typography } from '@mui/material';
import BugReportIcon from '@mui/icons-material/BugReport';
import PsychologyIcon from '@mui/icons-material/Psychology';
import DescriptionIcon from '@mui/icons-material/Description';
import ChatBubbleOutlineIcon from '@mui/icons-material/ChatBubbleOutline';
import BarChartIcon from '@mui/icons-material/BarChart';
import { LoginUserProvider, useLoginUser } from './stores/useLoginUser.jsx';
import Sidebar from './components/Sidebar.jsx';
import ReviewPanel from './components/ReviewPanel.jsx';
import ChatPanel from './components/ChatPanel.jsx';
import DocsPanel from './components/DocsPanel.jsx';
import GeneralChatPanel from './components/GeneralChatPanel.jsx';
import UsageDashboardPanel from './components/UsageDashboardPanel.jsx';
import HistoryPanel from './components/HistoryPanel.jsx';
import PreferencePanel from './components/PreferencePanel.jsx';
import LoginModal from './components/LoginModal.jsx';
import { checkHealth } from './api/client.js';

/* ─── Feature highlights for the landing page ─── */
const FEATURES = [
  {
    icon: '🔍',
    title: '代码审查',
    desc: 'AI 逐行扫描，发现潜在 Bug 与安全漏洞',
  },
  {
    icon: '💬',
    title: '智能问答',
    desc: '实时流式对话，分析项目结构与代码逻辑',
  },
  {
    icon: '📄',
    title: '文档生成',
    desc: '一键生成 README / API / CONTRIBUTING 文档',
  },
  {
    icon: '💭',
    title: '通用聊天',
    desc: '不依赖代码仓库，自由提问任何技术问题',
  },
  {
    icon: '📊',
    title: '用量统计',
    desc: 'AI 调用次数、Token 消耗一目了然，按角色配额管理',
  },
];

/* Tab configuration */
const TAB_CONFIG = {
  review: { title: '代码审查', subtitle: 'AI 逐行扫描，发现潜在 Bug 与安全漏洞', icon: BugReportIcon, color: 'var(--accent)', bg: 'rgba(212,160,83,0.12)' },
  chat:   { title: '智能问答', subtitle: '实时流式对话，分析项目结构与代码逻辑', icon: PsychologyIcon, color: 'var(--accent-secondary)', bg: 'rgba(45,212,191,0.12)' },
  docs:    { title: '文档生成', subtitle: '一键生成项目文档，支持多种类型', icon: DescriptionIcon, color: '#a78bfa', bg: 'rgba(167,139,250,0.12)' },
  general: { title: '通用聊天', subtitle: '不依赖代码仓库，自由 AI 对话', icon: ChatBubbleOutlineIcon, color: '#f472b6', bg: 'rgba(244,114,182,0.12)' },
  usage:   { title: '用量统计', subtitle: 'AI 调用次数、Token 消耗与配额管理', icon: BarChartIcon, color: '#60a5fa', bg: 'rgba(96,165,250,0.12)' },
};

/* ─── Inline LoginForm (split-screen version) ─── */
function InlineLoginForm() {
  const [tab, setTab] = useState(0);
  const [userAccount, setUserAccount] = useState('');
  const [userPassword, setUserPassword] = useState('');
  const [checkPassword, setCheckPassword] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const { login, register } = useLoginUser();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSubmitting(true);
    try {
      if (tab === 0) {
        await login(userAccount, userPassword);
      } else {
        if (userPassword !== checkPassword) {
          setError('两次输入的密码不一致');
          setSubmitting(false);
          return;
        }
        await register(userAccount, userPassword, checkPassword);
        await login(userAccount, userPassword);
      }
    } catch (err) {
      setError(err.message || '操作失败');
      setSubmitting(false);
    }
  };

  return (
    <Box sx={{ width: '100%', maxWidth: 380 }}>
      {/* Tab switcher */}
      <Box sx={{ display: 'flex', gap: 0, mb: 4, borderRadius: 'var(--radius-btn)', overflow: 'hidden', border: '1px solid var(--border-subtle)' }}>
        {['登录', '注册'].map((label, i) => (
          <Box
            key={label}
            onClick={() => { setTab(i); setError(''); }}
            sx={{
              flex: 1, py: 1.2, textAlign: 'center', cursor: 'pointer',
              fontFamily: 'var(--font-body)',
              fontSize: '0.85rem', fontWeight: 600,
              color: tab === i ? '#0c0b0e' : 'var(--text-secondary)',
              bgcolor: tab === i ? 'var(--accent)' : 'transparent',
              transition: 'all 0.25s ease',
              '&:hover': tab !== i ? { bgcolor: 'rgba(212,160,83,0.08)', color: 'var(--text-primary)' } : {},
            }}
          >
            {label}
          </Box>
        ))}
      </Box>

      <Box component="form" onSubmit={handleSubmit} sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
        {error && (
          <Box sx={{ p: '10px 14px', borderRadius: 'var(--radius-btn)', bgcolor: 'rgba(248,113,113,0.1)', border: '1px solid rgba(248,113,113,0.2)' }}>
            <Typography sx={{ fontSize: '0.82rem', color: '#fca5a5' }}>{error}</Typography>
          </Box>
        )}

        <Box>
          <Typography sx={{ fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-secondary)', mb: 0.8, fontFamily: 'var(--font-body)' }}>账号</Typography>
          <input
            type="text"
            value={userAccount}
            onChange={(e) => setUserAccount(e.target.value)}
            required
            minLength={4}
            placeholder="输入你的账号"
            style={{
              width: '100%', padding: '11px 14px', borderRadius: 'var(--radius-input)',
              border: '1px solid var(--border-subtle)', background: 'rgba(255,255,255,0.04)',
              color: 'var(--text-primary)', fontFamily: 'var(--font-body)', fontSize: '0.9rem',
              outline: 'none', transition: 'border-color 0.2s',
            }}
            onFocus={(e) => e.target.style.borderColor = 'var(--accent)'}
            onBlur={(e) => e.target.style.borderColor = 'var(--border-subtle)'}
          />
        </Box>

        <Box>
          <Typography sx={{ fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-secondary)', mb: 0.8, fontFamily: 'var(--font-body)' }}>密码</Typography>
          <input
            type="password"
            value={userPassword}
            onChange={(e) => setUserPassword(e.target.value)}
            required
            minLength={8}
            placeholder="至少 8 位密码"
            style={{
              width: '100%', padding: '11px 14px', borderRadius: 'var(--radius-input)',
              border: '1px solid var(--border-subtle)', background: 'rgba(255,255,255,0.04)',
              color: 'var(--text-primary)', fontFamily: 'var(--font-body)', fontSize: '0.9rem',
              outline: 'none', transition: 'border-color 0.2s',
            }}
            onFocus={(e) => e.target.style.borderColor = 'var(--accent)'}
            onBlur={(e) => e.target.style.borderColor = 'var(--border-subtle)'}
          />
        </Box>

        {tab === 1 && (
          <Box className="animate-in" style={{ animationDelay: '0s' }}>
            <Typography sx={{ fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-secondary)', mb: 0.8, fontFamily: 'var(--font-body)' }}>确认密码</Typography>
            <input
              type="password"
              value={checkPassword}
              onChange={(e) => setCheckPassword(e.target.value)}
              required
              minLength={8}
              placeholder="再次输入密码"
              style={{
                width: '100%', padding: '11px 14px', borderRadius: 'var(--radius-input)',
                border: '1px solid var(--border-subtle)', background: 'rgba(255,255,255,0.04)',
                color: 'var(--text-primary)', fontFamily: 'var(--font-body)', fontSize: '0.9rem',
                outline: 'none', transition: 'border-color 0.2s',
              }}
              onFocus={(e) => e.target.style.borderColor = 'var(--accent)'}
              onBlur={(e) => e.target.style.borderColor = 'var(--border-subtle)'}
            />
          </Box>
        )}

        <button
          type="submit"
          disabled={submitting}
          className="gradient-btn"
          style={{
            width: '100%', padding: '12px', border: 'none', cursor: submitting ? 'not-allowed' : 'pointer',
            fontSize: '0.9rem', marginTop: 4,
          }}
        >
          {submitting ? '处理中...' : tab === 0 ? '登 录' : '注 册'}
        </button>

        {tab === 0 && (
          <Typography sx={{ fontSize: '0.72rem', color: 'var(--text-muted)', textAlign: 'center', mt: 1 }}>
            测试账号: user / 12345678
          </Typography>
        )}
      </Box>
    </Box>
  );
}

/* ─── Split-Screen Login View ─── */
function LoginView({ darkMode }) {
  return (
    <Box sx={{ minHeight: '100vh', display: 'flex', position: 'relative', overflow: 'hidden' }}>
      {/* Left: Brand Panel */}
      <Box
        sx={{
          flex: { xs: 0, md: 1.1 },
          display: { xs: 'none', md: 'flex' },
          flexDirection: 'column',
          justifyContent: 'center',
          position: 'relative',
          p: 6,
          overflow: 'hidden',
        }}
      >
        {/* Animated orbs */}
        <Box className="mesh-orb mesh-orb-amber" sx={{ width: 400, height: 400, top: '10%', left: '-5%', animation: 'orbFloat1 12s ease-in-out infinite' }} />
        <Box className="mesh-orb mesh-orb-teal" sx={{ width: 300, height: 300, bottom: '15%', right: '10%', animation: 'orbFloat2 15s ease-in-out infinite' }} />
        <Box className="mesh-orb mesh-orb-rose" sx={{ width: 200, height: 200, top: '55%', left: '40%', animation: 'orbFloat3 10s ease-in-out infinite' }} />

        {/* Brand content */}
        <Box sx={{ position: 'relative', zIndex: 1 }}>
          <Typography
            sx={{
              fontFamily: 'var(--font-display)',
              fontSize: { md: '2.8rem', lg: '3.4rem' },
              fontWeight: 800,
              lineHeight: 1.15,
              letterSpacing: '-0.03em',
              color: 'var(--text-primary)',
              mb: 2,
            }}
          >
            Code
            <br />
            <Box
              component="span"
              sx={{
                background: 'linear-gradient(135deg, var(--accent), var(--accent-secondary))',
                WebkitBackgroundClip: 'text',
                WebkitTextFillColor: 'transparent',
              }}
            >
              Butler
            </Box>
          </Typography>

          <Typography sx={{ fontSize: '1rem', color: 'var(--text-secondary)', lineHeight: 1.8, maxWidth: 420, mb: 5 }}>
            基于 AgentScope 2.0 构建的智能代码助手。
            <br />
            审查代码、解答疑问、生成文档、自由聊天 — 一站式 AI 开发体验。
          </Typography>

          {/* Feature list */}
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            {FEATURES.map((f, i) => (
              <Box
                key={f.title}
                className="animate-in"
                style={{ animationDelay: `${0.2 - i * 0.15}s` }}
                sx={{
                  display: 'flex', alignItems: 'center', gap: 2,
                  p: '14px 18px', borderRadius: 'var(--radius-card)',
                  background: 'rgba(255,255,255,0.03)',
                  border: '1px solid var(--border-subtle)',
                  backdropFilter: 'blur(8px)',
                  transition: 'all 0.3s ease',
                  '&:hover': {
                    background: 'rgba(255,255,255,0.06)',
                    borderColor: 'var(--border-hover)',
                    transform: 'translateX(4px)',
                  },
                }}
              >
                <Box sx={{ fontSize: '1.5rem', flexShrink: 0 }}>{f.icon}</Box>
                <Box>
                  <Typography sx={{ fontSize: '0.88rem', fontWeight: 700, color: 'var(--text-primary)', fontFamily: 'var(--font-display)' }}>
                    {f.title}
                  </Typography>
                  <Typography sx={{ fontSize: '0.78rem', color: 'var(--text-muted)', mt: 0.2 }}>
                    {f.desc}
                  </Typography>
                </Box>
              </Box>
            ))}
          </Box>
        </Box>
      </Box>

      {/* Right: Login Form */}
      <Box
        sx={{
          flex: { xs: 1, md: 0.9 },
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          p: { xs: 3, sm: 5 },
          position: 'relative',
          borderLeft: { md: '1px solid var(--border-subtle)' },
        }}
      >
        {/* Mobile brand header (visible on small screens) */}
        <Box sx={{ display: { xs: 'flex', md: 'none' }, flexDirection: 'column', alignItems: 'center', mb: 4 }}>
          <Typography
            sx={{
              fontFamily: 'var(--font-display)',
              fontSize: '2rem', fontWeight: 800,
              background: 'linear-gradient(135deg, var(--accent), var(--accent-secondary))',
              WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent',
            }}
          >
            Code Butler
          </Typography>
          <Typography sx={{ fontSize: '0.85rem', color: 'var(--text-muted)', mt: 1 }}>
            代码仓库智能管家
          </Typography>
        </Box>

        <Box className="animate-in" style={{ animationDelay: '0.1s' }}>
          <Typography
            sx={{
              fontFamily: 'var(--font-display)',
              fontSize: '1.4rem', fontWeight: 700,
              color: 'var(--text-primary)', mb: 1,
            }}
          >
            欢迎回来
          </Typography>
          <Typography sx={{ fontSize: '0.85rem', color: 'var(--text-muted)', mb: 4 }}>
            登录以使用全部 AI 功能
          </Typography>

          <InlineLoginForm />
        </Box>

        {/* Footer hint */}
        <Box sx={{ position: 'absolute', bottom: 24, left: 0, right: 0, textAlign: 'center' }}>
          <Typography sx={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>
            Powered by AgentScope 2.0 · Spring Boot 3.3
          </Typography>
        </Box>
      </Box>
    </Box>
  );
}

/* ─── Main App (logged in) ─── */
function AppInner() {
  const [darkMode, setDarkMode] = useState(() => {
    return localStorage.getItem('theme') === 'dark';
  });
  const [loginModalOpen, setLoginModalOpen] = useState(false);
  const [historyOpen, setHistoryOpen] = useState(false);
  const [preferenceOpen, setPreferenceOpen] = useState(false);
  const [activeTab, setActiveTab] = useState('review');
  const [healthStatus, setHealthStatus] = useState('loading');
  const { loginUser, loading } = useLoginUser();

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', darkMode ? 'dark' : 'light');
    localStorage.setItem('theme', darkMode ? 'dark' : 'light');
  }, [darkMode]);

  // Listen for 401 auth errors to open login modal
  useEffect(() => {
    const handler = () => setLoginModalOpen(true);
    window.addEventListener('auth:required', handler);
    return () => window.removeEventListener('auth:required', handler);
  }, []);

  // Health check polling
  const fetchHealth = useCallback(async () => {
    try {
      await checkHealth();
      setHealthStatus('healthy');
    } catch {
      setHealthStatus('error');
    }
  }, []);

  useEffect(() => {
    fetchHealth();
    const interval = setInterval(fetchHealth, 30000);
    return () => clearInterval(interval);
  }, [fetchHealth]);

  // Loading state
  if (loading) {
    return (
      <Box sx={{
        minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center',
        position: 'relative', overflow: 'hidden',
      }}>
        <Box className="mesh-orb mesh-orb-amber" sx={{ width: 300, height: 300, top: '30%', left: '40%', animation: 'orbFloat1 8s ease-in-out infinite' }} />
        <Box sx={{ position: 'relative', zIndex: 1, textAlign: 'center' }}>
          <Typography
            sx={{
              fontFamily: 'var(--font-display)', fontSize: '1.8rem', fontWeight: 800,
              background: 'linear-gradient(135deg, var(--accent), var(--accent-secondary))',
              WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent',
              animation: 'forgeGlow 2s ease-in-out infinite',
            }}
          >
            Code Butler
          </Typography>
          <Typography sx={{ fontSize: '0.8rem', color: 'var(--text-muted)', mt: 1 }}>
            加载中...
          </Typography>
        </Box>
      </Box>
    );
  }

  // Not logged in → show split-screen login
  if (!loginUser) {
    return <LoginView darkMode={darkMode} />;
  }

  // Logged in → sidebar + content layout
  const config = TAB_CONFIG[activeTab];
  const TabIcon = config.icon;

  return (
    <Box sx={{ minHeight: '100vh' }}>
      {/* ── Sidebar Navigation ── */}
      <Sidebar
        activeTab={activeTab}
        onTabChange={setActiveTab}
        darkMode={darkMode}
        onToggleTheme={() => setDarkMode((p) => !p)}
        onOpenHistory={() => setHistoryOpen(true)}
        onOpenPreference={() => setPreferenceOpen(true)}
        healthStatus={healthStatus}
      />

      {/* ── Overlay Panels ── */}
      <HistoryPanel
        darkMode={darkMode}
        open={historyOpen}
        onClose={() => setHistoryOpen(false)}
      />
      <PreferencePanel
        open={preferenceOpen}
        onClose={() => setPreferenceOpen(false)}
      />
      <LoginModal
        open={loginModalOpen}
        onClose={() => setLoginModalOpen(false)}
        darkMode={darkMode}
      />

      {/* ── Main Content Area ── */}
      <Box
        className="main-content"
        sx={{
          ml: { xs: 0, sm: '72px', md: '240px' },
          minHeight: '100vh',
          display: 'flex',
          flexDirection: 'column',
          pb: { xs: '64px', sm: 0 },
          transition: 'margin-left 0.3s cubic-bezier(0.4, 0, 0.2, 1)',
        }}
      >
        {/* ── Content Header ── */}
        <Box
          sx={{
            position: 'sticky',
            top: 0,
            zIndex: 100,
            px: { xs: 2.5, sm: 3, lg: 4 },
            py: '14px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            background: darkMode
              ? 'rgba(8,7,9,0.82)'
              : 'rgba(246,244,241,0.82)',
            backdropFilter: 'blur(16px) saturate(180%)',
            WebkitBackdropFilter: 'blur(16px) saturate(180%)',
            borderBottom: '1px solid var(--border-subtle)',
          }}
        >
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
            <Box sx={{
              width: 32, height: 32, borderRadius: 1.5,
              bgcolor: config.bg,
              display: 'flex', alignItems: 'center', justifyContent: 'center',
            }}>
              <TabIcon sx={{ fontSize: 18, color: config.color }} />
            </Box>
            <Box>
              <Typography
                sx={{
                  fontFamily: 'var(--font-display)',
                  fontSize: '1.05rem', fontWeight: 700,
                  color: 'var(--text-primary)',
                  lineHeight: 1.2,
                }}
              >
                {config.title}
              </Typography>
              <Typography sx={{ fontSize: '0.72rem', color: 'var(--text-muted)', mt: 0.2 }}>
                {config.subtitle}
              </Typography>
            </Box>
          </Box>
        </Box>

        {/* ── Tab Content ── */}
        <Box
          key={activeTab}
          className="animate-in"
          sx={{
            flex: 1,
            p: { xs: 2, sm: 2.5, lg: 3 },
            display: 'flex',
            flexDirection: 'column',
            minHeight: 0,
          }}
        >
          {activeTab === 'review' && <ReviewPanel darkMode={darkMode} />}
          {activeTab === 'chat' && <ChatPanel darkMode={darkMode} />}
          {activeTab === 'docs' && <DocsPanel darkMode={darkMode} />}
          {activeTab === 'general' && <GeneralChatPanel darkMode={darkMode} />}
          {activeTab === 'usage' && <UsageDashboardPanel darkMode={darkMode} />}
        </Box>
      </Box>

      {/* ── Mobile Bottom Navigation (xs only) ── */}
      <Box
        sx={{
          display: { xs: 'flex', sm: 'none' },
          position: 'fixed',
          bottom: 0,
          left: 0,
          right: 0,
          zIndex: 1100,
          alignItems: 'center',
          justifyContent: 'space-around',
          py: '6px',
          pb: 'env(safe-area-inset-bottom, 6px)',
          background: darkMode
            ? 'rgba(10,9,12,0.95)'
            : 'rgba(248,246,243,0.95)',
          backdropFilter: 'blur(16px)',
          WebkitBackdropFilter: 'blur(16px)',
          borderTop: '1px solid var(--border-subtle)',
        }}
      >
        {Object.entries(TAB_CONFIG).map(([key, cfg]) => {
          const Icon = cfg.icon;
          const isActive = activeTab === key;
          return (
            <Box
              key={key}
              onClick={() => setActiveTab(key)}
              sx={{
                display: 'flex', flexDirection: 'column', alignItems: 'center',
                gap: '2px', px: 2, py: '4px',
                cursor: 'pointer',
                borderRadius: 1,
                transition: 'all 0.2s',
                '&:active': { transform: 'scale(0.92)' },
              }}
            >
              <Icon sx={{
                fontSize: 22,
                color: isActive ? cfg.color : 'var(--text-muted)',
                transition: 'color 0.2s',
              }} />
              <Typography sx={{
                fontSize: '0.6rem', fontWeight: isActive ? 700 : 500,
                color: isActive ? cfg.color : 'var(--text-muted)',
                transition: 'color 0.2s',
              }}>
                {cfg.title}
              </Typography>
            </Box>
          );
        })}
      </Box>
    </Box>
  );
}

export default function App() {
  return (
    <LoginUserProvider>
      <AppInner />
    </LoginUserProvider>
  );
}
