import DashboardOutlined from '@mui/icons-material/DashboardOutlined';
import Inventory2Outlined from '@mui/icons-material/Inventory2Outlined';
import MoveToInboxOutlined from '@mui/icons-material/MoveToInboxOutlined';
import LocalShippingOutlined from '@mui/icons-material/LocalShippingOutlined';
import MedicalServicesOutlined from '@mui/icons-material/MedicalServicesOutlined';
import AutorenewOutlined from '@mui/icons-material/AutorenewOutlined';
import SwapHorizOutlined from '@mui/icons-material/SwapHorizOutlined';
import RequestQuoteOutlined from '@mui/icons-material/RequestQuoteOutlined';
import AssessmentOutlined from '@mui/icons-material/AssessmentOutlined';
import FolderOpenOutlined from '@mui/icons-material/FolderOpenOutlined';
import GroupOutlined from '@mui/icons-material/GroupOutlined';

const ADMINS = ['ADMIN', 'MASTER'];

/** Menu items, routes and the profiles that see each one. */
export const MENU = [
  { label: 'Painel', path: '/painel', icon: DashboardOutlined, roles: ADMINS },
  { label: 'Estoque', path: '/estoque', icon: Inventory2Outlined, roles: [...ADMINS, 'SURGICAL_TECH', 'USER'] },
  { label: 'Entrada', path: '/entrada', icon: MoveToInboxOutlined, roles: ADMINS },
  { label: 'Transferência', path: '/transferencia', icon: LocalShippingOutlined, roles: ADMINS },
  { label: 'Saída em cirurgia', path: '/saida', icon: MedicalServicesOutlined, roles: [...ADMINS, 'SURGICAL_TECH'] },
  { label: 'Reposição', path: '/reposicao', icon: AutorenewOutlined, roles: ADMINS },
  { label: 'Empréstimos', path: '/emprestimos', icon: SwapHorizOutlined, roles: ADMINS },
  { label: 'Faturamento', path: '/faturamento', icon: RequestQuoteOutlined, roles: ADMINS },
  { label: 'Relatórios', path: '/relatorios', icon: AssessmentOutlined, roles: ADMINS },
  { label: 'Cadastros', path: '/cadastros', icon: FolderOpenOutlined, roles: ADMINS },
  { label: 'Usuários', path: '/usuarios', icon: GroupOutlined, roles: ADMINS },
];

export const menuFor = (user) => MENU.filter((item) => user && item.roles.includes(user.role));

/** First page after login: the dashboard for administrators, the stock for the others. */
export const homeFor = (user) => (user && ADMINS.includes(user.role) ? '/painel' : '/estoque');
