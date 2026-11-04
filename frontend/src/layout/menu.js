import DashboardOutlined from '@mui/icons-material/DashboardOutlined';
import Inventory2Outlined from '@mui/icons-material/Inventory2Outlined';
import MoveToInboxOutlined from '@mui/icons-material/MoveToInboxOutlined';
import LocalShippingOutlined from '@mui/icons-material/LocalShippingOutlined';
import AssignmentReturnOutlined from '@mui/icons-material/AssignmentReturnOutlined';
import RouteOutlined from '@mui/icons-material/RouteOutlined';
import LocalPostOfficeOutlined from '@mui/icons-material/LocalPostOfficeOutlined';
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
  /*
   * Antes do Estoque de propósito: o material chega, entra e só então vira estoque.
   *
   * O nome diz o conteúdo (notas da empresa e o rastreio delas) em vez de dizer "em transporte", que é o
   * que esta tela e a de Remessas a caminho têm em comum e justamente o que não as separa: uma é a Baumer
   * mandando para a sala, a outra é a sala mandando para o hospital. Dois rótulos sinônimos no mesmo menu
   * obrigam a abrir os dois para descobrir qual é qual.
   */
  { label: 'Notas e rastreio', path: '/transporte', icon: LocalPostOfficeOutlined, roles: ADMINS },
  { label: 'Estoque', path: '/estoque', icon: Inventory2Outlined, roles: [...ADMINS, 'SURGICAL_TECH', 'USER'] },
  { label: 'Entrada', path: '/entrada', icon: MoveToInboxOutlined, roles: ADMINS },
  // Antes da Transferência porque é o par dela: a Reposição diz o que falta, a Transferência manda
  { label: 'Reposição', path: '/reposicao', icon: AutorenewOutlined, roles: ADMINS },
  { label: 'Transferência', path: '/transferencia', icon: LocalShippingOutlined, roles: ADMINS },
  // Logo depois da Transferência: é onde a remessa despachada por ela espera a confirmação de chegada
  { label: 'Remessas a caminho', path: '/remessas', icon: RouteOutlined, roles: ADMINS },
  // Logo depois da Transferência: é o caminho de volta dela, hospital → sala
  { label: 'Retirada para a sala', path: '/retirada', icon: AssignmentReturnOutlined, roles: ADMINS },
  { label: 'Saída em cirurgia', path: '/saida', icon: MedicalServicesOutlined, roles: [...ADMINS, 'SURGICAL_TECH'] },
  { label: 'Empréstimos', path: '/emprestimos', icon: SwapHorizOutlined, roles: ADMINS },
  { label: 'Faturamento', path: '/faturamento', icon: RequestQuoteOutlined, roles: ADMINS },
  { label: 'Relatórios', path: '/relatorios', icon: AssessmentOutlined, roles: ADMINS },
  { label: 'Cadastros', path: '/cadastros', icon: FolderOpenOutlined, roles: ADMINS },
  { label: 'Usuários', path: '/usuarios', icon: GroupOutlined, roles: ADMINS },
];

export const menuFor = (user) => MENU.filter((item) => user && item.roles.includes(user.role));

/** First page after login: the dashboard for administrators, the stock for the others. */
export const homeFor = (user) => (user && ADMINS.includes(user.role) ? '/painel' : '/estoque');
