import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Box, Button, IconButton, ListItemIcon, Menu, MenuItem, Paper, Skeleton, Stack, Table, TableBody, TableCell,
  TableContainer, TableHead, TableRow, TextField, Typography, useMediaQuery,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import EditOutlined from '@mui/icons-material/EditOutlined';
import MailOutline from '@mui/icons-material/MailOutline';
import BlockOutlined from '@mui/icons-material/BlockOutlined';
import CheckCircleOutline from '@mui/icons-material/CheckCircleOutline';
import PageHeader from '../../components/PageHeader';
import StatusChip from '../../components/StatusChip';
import ConfirmDialog from '../../components/ConfirmDialog';
import UserDialog from './UserDialog';
import { api } from '../../api/client';
import { useNotify } from '../../notifications/NotificationProvider';
import { useAuth } from '../../auth/AuthProvider';
import { roleLabel } from '../../auth/roles';
import { digits, maskCpf } from '../../utils/documents';

const STATUS = {
  ACTIVE: { label: 'Ativo', tone: 'success' },
  PENDING_FIRST_ACCESS: { label: 'Aguardando primeiro acesso', tone: 'warning' },
  INACTIVE: { label: 'Inativo', tone: 'neutral' },
};

const ROLE_TONE = { MASTER: 'info', ADMIN: 'info', SURGICAL_TECH: 'success', USER: 'neutral' };

const hospitalsText = (u) =>
  u.role === 'ADMIN' || u.role === 'MASTER' ? 'Todos' : u.hospitals.map((h) => h.name).join(', ') || 'Nenhum';

export default function UsersPage() {
  const notify = useNotify();
  const { user: me } = useAuth();
  const isMobile = useMediaQuery('(max-width:899.95px)');
  const [users, setUsers] = useState(null);
  const [hospitals, setHospitals] = useState([]);
  const [search, setSearch] = useState('');
  const [dialog, setDialog] = useState({ open: false, user: null });
  const [actions, setActions] = useState({ anchor: null, user: null });
  const [confirm, setConfirm] = useState(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      const [page, hospitalList] = await Promise.all([
        api('/users', { query: { size: 500, sort: 'name' } }),
        api('/hospitals'),
      ]);
      setUsers(page.content);
      setHospitals(hospitalList.filter((h) => h.type === 'HOSPITAL'));
    } catch (err) {
      notify.error(err);
      setUsers([]);
    }
  }, [notify]);

  useEffect(() => { load(); }, [load]);

  const filtered = useMemo(() => {
    if (!users) return [];
    const term = search.trim().toLowerCase();
    if (!term) return users;
    const termDigits = digits(term);
    return users.filter((u) => u.name.toLowerCase().includes(term) || u.email.toLowerCase().includes(term)
      || (termDigits && (u.cpf || '').includes(termDigits)));
  }, [users, search]);

  const closeActions = () => setActions({ anchor: null, user: null });

  const resendInvitation = async (u) => {
    closeActions();
    try {
      await api(`/users/${u.id}/invitation`, { method: 'POST' });
      notify.success(`Convite reenviado para ${u.email}.`);
    } catch (err) {
      notify.error(err);
    }
  };

  const setActive = async () => {
    const { user: u, active } = confirm;
    setBusy(true);
    try {
      await api(`/users/${u.id}`, { method: 'PATCH', body: { active } });
      notify.success(active ? 'Usuário reativado.' : 'Usuário desativado. O acesso foi bloqueado em todos os aparelhos.');
      setConfirm(null);
      load();
    } catch (err) {
      notify.error(err);
    } finally {
      setBusy(false);
    }
  };

  const openActions = (e, u) => setActions({ anchor: e.currentTarget, user: u });
  const actionUser = actions.user;

  const rowActions = (u) => (
    <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 0.5 }}>
      <Button size="small" variant="outlined" startIcon={<EditOutlined />} onClick={() => setDialog({ open: true, user: u })}
        sx={{ minHeight: 36 }}>
        Editar
      </Button>
      <IconButton aria-label={`Mais ações para ${u.name}`} onClick={(e) => openActions(e, u)}><MoreVertIcon /></IconButton>
    </Box>
  );

  return (
    <>
      <PageHeader title="Usuários" subtitle="Acesso ao sistema por perfil e hospital"
        actions={<Button variant="contained" startIcon={<AddIcon />} onClick={() => setDialog({ open: true, user: null })}>Novo usuário</Button>} />

      <Paper variant="outlined" sx={{ p: { xs: 2, md: 2.5 } }}>
        <Box sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', justifyContent: 'space-between', gap: 2, mb: 2 }}>
          <Typography variant="h2" component="h2">Usuários cadastrados</Typography>
          <TextField type="search" label="Buscar" placeholder="Nome, e-mail ou CPF" value={search}
            onChange={(e) => setSearch(e.target.value)} size="small" sx={{ width: { xs: '100%', sm: 320 } }} />
        </Box>

        {users === null ? (
          <Stack spacing={1}>{[1, 2, 3].map((i) => <Skeleton key={i} variant="rounded" height={56} />)}</Stack>
        ) : filtered.length === 0 ? (
          <Typography variant="body2" color="text.secondary" sx={{ py: 3, textAlign: 'center' }}>
            {search ? 'Nenhum usuário encontrado para esta busca.' : 'Nenhum usuário cadastrado. Use "Novo usuário" para começar.'}
          </Typography>
        ) : isMobile ? (
          <Stack spacing={1.5}>
            {filtered.map((u) => (
              <Paper key={u.id} variant="outlined" sx={{ p: 1.5 }}>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 1 }}>
                  <Box sx={{ minWidth: 0 }}>
                    <Typography sx={{ fontWeight: 600, fontSize: 15 }}>{u.name}</Typography>
                    <Typography variant="body2" color="text.secondary" noWrap>{u.email}</Typography>
                  </Box>
                  <IconButton aria-label={`Mais ações para ${u.name}`} onClick={(e) => openActions(e, u)}><MoreVertIcon /></IconButton>
                </Box>
                <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1, my: 1 }}>
                  <StatusChip tone={ROLE_TONE[u.role]}>{roleLabel(u.role)}</StatusChip>
                  <StatusChip tone={STATUS[u.status]?.tone}>{STATUS[u.status]?.label}</StatusChip>
                </Box>
                <Typography variant="body2" color="text.secondary">Hospitais: {hospitalsText(u)}</Typography>
                <Button fullWidth variant="outlined" startIcon={<EditOutlined />} sx={{ mt: 1.5 }}
                  onClick={() => setDialog({ open: true, user: u })}>Editar</Button>
              </Paper>
            ))}
          </Stack>
        ) : (
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Nome</TableCell>
                  <TableCell>CPF</TableCell>
                  <TableCell>Perfil</TableCell>
                  <TableCell>Hospitais</TableCell>
                  <TableCell>Situação</TableCell>
                  <TableCell align="right"><span className="visually-hidden">Ações</span></TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {filtered.map((u) => (
                  <TableRow key={u.id} hover>
                    <TableCell sx={{ py: 1.25 }}>
                      <Typography sx={{ fontSize: 14, fontWeight: 500 }}>{u.name}</Typography>
                      <Typography variant="caption" color="text.secondary">{u.email}</Typography>
                    </TableCell>
                    <TableCell sx={{ whiteSpace: 'nowrap' }}>{u.cpf ? maskCpf(u.cpf) : <Typography variant="caption" color="warning.main">Não informado</Typography>}</TableCell>
                    <TableCell><StatusChip tone={ROLE_TONE[u.role]}>{roleLabel(u.role)}</StatusChip></TableCell>
                    <TableCell sx={{ maxWidth: 260 }}>{hospitalsText(u)}</TableCell>
                    <TableCell><StatusChip tone={STATUS[u.status]?.tone}>{STATUS[u.status]?.label}</StatusChip></TableCell>
                    <TableCell align="right">{rowActions(u)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Paper>

      <Menu anchorEl={actions.anchor} open={Boolean(actions.anchor)} onClose={closeActions}>
        {actionUser && [
          <MenuItem key="edit" onClick={() => { closeActions(); setDialog({ open: true, user: actionUser }); }}>
            <ListItemIcon><EditOutlined fontSize="small" /></ListItemIcon>Editar
          </MenuItem>,
          actionUser.status === 'PENDING_FIRST_ACCESS' && (
            <MenuItem key="invite" onClick={() => resendInvitation(actionUser)}>
              <ListItemIcon><MailOutline fontSize="small" /></ListItemIcon>Reenviar convite
            </MenuItem>
          ),
          actionUser.role !== 'MASTER' && actionUser.id !== me.id && (actionUser.status === 'INACTIVE' ? (
            <MenuItem key="activate" onClick={() => { closeActions(); setConfirm({ user: actionUser, active: true }); }}>
              <ListItemIcon><CheckCircleOutline fontSize="small" /></ListItemIcon>Reativar
            </MenuItem>
          ) : (
            <MenuItem key="deactivate" onClick={() => { closeActions(); setConfirm({ user: actionUser, active: false }); }}
              sx={{ color: 'error.main' }}>
              <ListItemIcon sx={{ color: 'error.main' }}><BlockOutlined fontSize="small" /></ListItemIcon>Desativar
            </MenuItem>
          )),
        ]}
      </Menu>

      <UserDialog open={dialog.open} user={dialog.user} hospitals={hospitals}
        onClose={() => setDialog({ open: false, user: null })}
        onSaved={() => { setDialog({ open: false, user: null }); load(); }} />

      <ConfirmDialog open={Boolean(confirm)} busy={busy} danger={confirm && !confirm.active}
        title={confirm?.active ? 'Reativar usuário' : 'Desativar usuário'}
        message={confirm?.active
          ? `${confirm?.user.name} volta a ter acesso ao sistema.`
          : `${confirm?.user.name} perde o acesso imediatamente, em todos os aparelhos. O histórico é mantido.`}
        confirmLabel={confirm?.active ? 'Reativar' : 'Desativar'}
        onConfirm={setActive} onClose={() => setConfirm(null)} />
    </>
  );
}
