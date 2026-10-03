/** Profiles returned by the API (UserDTO.role) and their labels. */
export const ROLES = {
  MASTER: { label: 'Administrador (master)', manager: true },
  ADMIN: { label: 'Administrador', manager: true },
  SURGICAL_TECH: { label: 'Instrumentador', manager: false },
  USER: { label: 'Consulta', manager: false },
};

export const ASSIGNABLE_ROLES = [
  { value: 'ADMIN', label: 'Administrador', description: 'Acesso completo, inclusive cadastros e relatórios.' },
  {
    value: 'SURGICAL_TECH',
    label: 'Instrumentador',
    description: 'Vê o estoque dos hospitais vinculados e faz a saída em cirurgia. Não faz empréstimos.',
  },
  { value: 'USER', label: 'Consulta', description: 'Apenas visualiza o estoque dos hospitais vinculados.' },
];

export const roleLabel = (role) => ROLES[role]?.label || role;
export const isManager = (user) => Boolean(user && ROLES[user.role]?.manager);
