import React, { useState, useEffect } from 'react';
import { Container, Box, Grid, Typography } from '@mui/material';
import { LoginUserProvider, useLoginUser } from './stores/useLoginUser.jsx';
import Header from './components/Header.jsx';
import HealthCard from './components/HealthCard.jsx';
import ReviewPanel from './components/ReviewPanel.jsx';
import ChatPanel from './components/ChatPanel.jsx';
import DocsPanel from './components/DocsPanel.jsx';
import HistoryPanel from './components/HistoryPanel.jsx';
import LoginModal from './components/LoginModal.jsx';

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
];

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
            审查代码、解答疑问、生成文档 — 一站式 AI 开发体验。
          </Typography>

          {/* Feature list */}
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            {FEATURES.map((f, i) => (
              <Box
                key={f.title}
                className="animate-in"
                style={{ animationDelay: `${0.2 + i * 0.15}s` }}
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
    return localStorage.getItem('theme') !== 'light';
  });
  const [loginModalOpen, setLoginModalOpen] = useState(false);
  const [historyOpen, setHistoryOpen] = useState(false);
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

  // Logged in → full app
  return (
    <Box sx={{ minHeight: '100vh', pb: 6, position: 'relative' }}>
      <Header
        darkMode={darkMode}
        onToggleTheme={() => setDarkMode((p) => !p)}
        onOpenLogin={() => setLoginModalOpen(true)}
        onOpenHistory={() => setHistoryOpen(true)}
      />

      <LoginModal
        open={loginModalOpen}
        onClose={() => setLoginModalOpen(false)}
        darkMode={darkMode}
      />

      <HistoryPanel
        darkMode={darkMode}
        open={historyOpen}
        onClose={() => setHistoryOpen(false)}
      />

      {/* Hero Section */}
      <Box sx={{ position: 'relative', pt: 6, pb: 4, textAlign: 'center', overflow: 'hidden' }}>
        {/* Background orb */}
        <Box className="mesh-orb mesh-orb-amber" sx={{ width: 400, height: 400, top: '-30%', left: '50%', transform: 'translateX(-50%)', animation: 'orbFloat1 14s ease-in-out infinite' }} />

        <Box className="animate-in" style={{ animationDelay: '0.05s' }}>
          <Typography
            sx={{
              fontFamily: 'var(--font-display)',
              fontSize: { xs: '1.5rem', sm: '2rem' },
              fontWeight: 800,
              letterSpacing: '-0.03em',
              color: 'var(--text-primary)',
            }}
          >
            让 AI 审查你的
            <Box
              component="span"
              sx={{
                background: 'linear-gradient(135deg, var(--accent), var(--accent-secondary))',
                WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent',
              }}
            >
              {' '}每一行代码
            </Box>
          </Typography>
        </Box>

        <Typography
          className="animate-in"
          style={{ animationDelay: '0.15s' }}
          sx={{
            mt: 1.5, maxWidth: 480, mx: 'auto',
            fontSize: '0.85rem', color: 'var(--text-muted)', lineHeight: 1.7,
          }}
        >
          AgentScope 2.0 · 代码审查 / 智能问答 / 文档生成
        </Typography>
      </Box>

      {/* Feature Panels */}
      <Container maxWidth="xl">
        <Box sx={{ mb: 2.5 }} className="animate-in" style={{ animationDelay: '0.2s' }}>
          <HealthCard darkMode={darkMode} />
        </Box>

        <Grid container spacing={2.5}>
          <Grid item xs={12} lg={6} className="animate-in" sx={{ animationDelay: '0.25s' }}>
            <ReviewPanel darkMode={darkMode} />
          </Grid>
          <Grid item xs={12} lg={6} className="animate-in" sx={{ animationDelay: '0.3s' }}>
            <ChatPanel darkMode={darkMode} />
          </Grid>
          <Grid item xs={12} className="animate-in" sx={{ animationDelay: '0.35s' }}>
            <DocsPanel darkMode={darkMode} />
          </Grid>
        </Grid>
      </Container>
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
