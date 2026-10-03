import { useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import {
  Box, ButtonBase, Divider, Drawer, IconButton, ListItemIcon, Menu, MenuItem, Tooltip, Typography, useMediaQuery,
} from '@mui/material';
import MenuIcon from '@mui/icons-material/Menu';
import MenuOpenIcon from '@mui/icons-material/MenuOpen';
import KeyOutlined from '@mui/icons-material/KeyOutlined';
import LogoutOutlined from '@mui/icons-material/LogoutOutlined';
import { tokens } from '../theme';
import { COMPANY_NAME } from '../config';
import { useAuth } from '../auth/AuthProvider';
import { roleLabel } from '../auth/roles';
import { initials } from '../utils/documents';
import { menuFor } from './menu';
import ChangePasswordDialog from './ChangePasswordDialog';

const COLLAPSED = 76;
const EXPANDED = 260;

function Brand({ expanded, onToggle, toggleLabel, ToggleIcon }) {
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, pb: 2.5, pl: expanded ? 1.5 : 0,
      justifyContent: expanded ? 'space-between' : 'center' }}>
      {expanded && (
        <Box sx={{ minWidth: 0 }}>
          <Typography sx={{ fontSize: 17, fontWeight: 600, letterSpacing: '-0.01em', whiteSpace: 'nowrap' }}>
            Estoque Cirúrgico
          </Typography>
          <Typography sx={{ fontSize: 12, color: tokens.sidebarText }}>{COMPANY_NAME}</Typography>
        </Box>
      )}
      {onToggle && (
        <IconButton onClick={onToggle} aria-label={toggleLabel} aria-expanded={expanded}
          sx={{ width: 44, height: 44, flexShrink: 0, border: `1px solid ${tokens.sidebarBorder}`, borderRadius: '6px',
            color: tokens.sidebarTextStrong }}>
          <ToggleIcon />
        </IconButton>
      )}
    </Box>
  );
}

function NavItems({ items, expanded, onNavigate }) {
  return items.map(({ label, path, icon: Icon }) => {
    const link = (
      <ButtonBase
        key={path}
        component={NavLink}
        to={path}
        onClick={onNavigate}
        aria-label={label}
        sx={{
          display: 'flex', alignItems: 'center', gap: 1.5, minHeight: 44, px: expanded ? 1.5 : 0,
          justifyContent: expanded ? 'flex-start' : 'center', borderRadius: '6px', fontSize: 14,
          color: tokens.sidebarText, textDecoration: 'none', whiteSpace: 'nowrap', width: '100%',
          '&:hover': { backgroundColor: tokens.sidebarActive, color: '#FFFFFF' },
          '&.active': { backgroundColor: tokens.sidebarActive, color: '#FFFFFF', fontWeight: 600 },
          '&.Mui-focusVisible': { outline: `2px solid ${tokens.sidebarTextStrong}`, outlineOffset: 2 },
        }}
      >
        <Icon fontSize="small" />
        {expanded && <span>{label}</span>}
      </ButtonBase>
    );
    return expanded ? link : <Tooltip key={path} title={label} placement="right">{link}</Tooltip>;
  });
}

function UserButton({ user, expanded, onOpen, open }) {
  return (
    <Box sx={{ mt: 'auto', pt: 2, borderTop: `1px solid ${tokens.sidebarBorder}` }}>
      <ButtonBase onClick={onOpen} aria-haspopup="menu" aria-expanded={open} aria-label={`Menu do usuário: ${user.name}`}
        sx={{ display: 'flex', alignItems: 'center', gap: 1.25, width: '100%', minHeight: 48, p: 0.5, borderRadius: '6px',
          justifyContent: expanded ? 'flex-start' : 'center', textAlign: 'left',
          backgroundColor: open ? tokens.sidebarActive : 'transparent' }}>
        <Box aria-hidden sx={{ width: 36, height: 36, flexShrink: 0, borderRadius: '50%', backgroundColor: tokens.sidebarBorder,
          display: 'grid', placeItems: 'center', fontSize: 13, fontWeight: 600 }}>
          {initials(user.name)}
        </Box>
        {expanded && (
          <Box sx={{ minWidth: 0 }}>
            <Typography noWrap sx={{ fontSize: 13, fontWeight: 500 }}>{user.name}</Typography>
            <Typography sx={{ fontSize: 12, color: tokens.sidebarText }}>{roleLabel(user.role)}</Typography>
          </Box>
        )}
      </ButtonBase>
    </Box>
  );
}

/**
 * Desktop: retractable side menu (76 px collapsed, 260 px expanded over the content).
 * Mobile: top bar with the menu in a drawer.
 */
export default function AppLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const isMobile = useMediaQuery('(max-width:899.95px)');
  const [expanded, setExpanded] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [userAnchor, setUserAnchor] = useState(null);
  const [passwordOpen, setPasswordOpen] = useState(false);
  const items = menuFor(user);

  const doLogout = async () => {
    setUserAnchor(null);
    await logout();
    navigate('/login', { replace: true });
  };

  const navSx = {
    backgroundColor: tokens.sidebar, color: tokens.sidebarTextStrong, display: 'flex', flexDirection: 'column',
    gap: 0.5, p: '20px 16px', boxSizing: 'border-box', overflowY: 'auto', height: '100%',
  };

  const userMenu = (
    <Menu anchorEl={userAnchor} open={Boolean(userAnchor)} onClose={() => setUserAnchor(null)}
      anchorOrigin={{ vertical: 'top', horizontal: 'right' }} transformOrigin={{ vertical: 'bottom', horizontal: 'left' }}
      slotProps={{ paper: { sx: { width: 240, borderRadius: '10px', border: `1px solid ${tokens.borderLight}` } } }}>
      <Box sx={{ px: 1.5, pt: 1, pb: 1.25 }}>
        <Typography sx={{ fontSize: 14, fontWeight: 600 }}>{user.name}</Typography>
        <Typography sx={{ fontSize: 12, color: tokens.textMuted }}>{roleLabel(user.role)}</Typography>
      </Box>
      <Divider />
      <MenuItem onClick={() => { setUserAnchor(null); setPasswordOpen(true); }} sx={{ minHeight: 44 }}>
        <ListItemIcon><KeyOutlined fontSize="small" /></ListItemIcon>Alterar senha
      </MenuItem>
      <MenuItem onClick={doLogout} sx={{ minHeight: 44, color: tokens.error }}>
        <ListItemIcon sx={{ color: tokens.error }}><LogoutOutlined fontSize="small" /></ListItemIcon>Sair
      </MenuItem>
    </Menu>
  );

  return (
    <Box sx={{ minHeight: '100vh', display: 'flex', flexDirection: isMobile ? 'column' : 'row', backgroundColor: tokens.background }}>
      {isMobile ? (
        <>
          <Box component="header" sx={{ position: 'sticky', top: 0, zIndex: 20, backgroundColor: tokens.sidebar,
            color: tokens.sidebarTextStrong, display: 'flex', alignItems: 'center', gap: 1.5, px: 2,
            pt: 'calc(env(safe-area-inset-top, 0px) + 8px)', pb: 1 }}>
            <IconButton onClick={() => setDrawerOpen(true)} aria-label="Abrir menu" sx={{ color: 'inherit' }}>
              <MenuIcon />
            </IconButton>
            <Box sx={{ minWidth: 0 }}>
              <Typography sx={{ fontSize: 16, fontWeight: 600 }}>Estoque Cirúrgico</Typography>
              <Typography sx={{ fontSize: 12, color: tokens.sidebarText }}>{COMPANY_NAME}</Typography>
            </Box>
          </Box>
          <Drawer open={drawerOpen} onClose={() => setDrawerOpen(false)}
            PaperProps={{ sx: { width: EXPANDED, backgroundColor: tokens.sidebar } }}>
            <Box component="nav" aria-label="Menu principal" sx={navSx}>
              <Brand expanded onToggle={() => setDrawerOpen(false)} toggleLabel="Fechar menu" ToggleIcon={MenuOpenIcon} />
              <NavItems items={items} expanded onNavigate={() => setDrawerOpen(false)} />
              <UserButton user={user} expanded open={Boolean(userAnchor)} onOpen={(e) => setUserAnchor(e.currentTarget)} />
            </Box>
          </Drawer>
        </>
      ) : (
        <Box sx={{ flex: `0 0 ${COLLAPSED}px`, position: 'sticky', top: 0, height: '100vh', zIndex: 20 }}>
          <Box component="nav" aria-label="Menu principal"
            sx={{ ...navSx, position: 'absolute', top: 0, left: 0, width: expanded ? EXPANDED : COLLAPSED,
              transition: 'width 0.2s ease', overflowX: 'hidden',
              boxShadow: expanded ? '8px 0 24px rgba(0, 0, 0, 0.22)' : 'none',
              '@media (prefers-reduced-motion: reduce)': { transition: 'none' } }}>
            <Brand expanded={expanded} onToggle={() => setExpanded((v) => !v)}
              toggleLabel={expanded ? 'Recolher menu' : 'Expandir menu'} ToggleIcon={expanded ? MenuOpenIcon : MenuIcon} />
            <NavItems items={items} expanded={expanded} onNavigate={() => setExpanded(false)} />
            <UserButton user={user} expanded={expanded} open={Boolean(userAnchor)} onOpen={(e) => setUserAnchor(e.currentTarget)} />
          </Box>
        </Box>
      )}

      {userMenu}
      <ChangePasswordDialog open={passwordOpen} onClose={() => setPasswordOpen(false)} />

      <Box component="main" sx={{ flex: 1, minWidth: 0, p: { xs: 2, md: 4 },
        pb: { xs: 'calc(env(safe-area-inset-bottom, 0px) + 16px)', md: 4 } }}>
        <Outlet />
      </Box>
    </Box>
  );
}
