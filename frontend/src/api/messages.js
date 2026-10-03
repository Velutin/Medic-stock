/**
 * Portuguese text for the API messages (the API answers in English).
 * Exact messages first, then patterns for messages with variable parts.
 * Unknown messages are shown as they come.
 */
const EXACT = {
  'Not authenticated': 'Sua sessão expirou. Entre novamente.',
  'Access denied': 'Você não tem permissão para esta ação.',
  'Invalid email or password': 'E-mail ou senha incorretos.',
  'User is inactive or has not completed the first access':
    'Usuário inativo ou primeiro acesso ainda não concluído. Verifique o e-mail de convite.',
  'Please fix the highlighted fields': 'Corrija os campos destacados.',
  'Invalid request body': 'Dados enviados em formato inválido.',
  'The record was changed by another user. Reload and try again.':
    'O registro foi alterado por outro usuário. Recarregue a página e tente novamente.',
  'The operation conflicts with existing data': 'A operação conflita com dados já cadastrados.',
  'File exceeds the maximum allowed size': 'O arquivo é maior que o tamanho permitido.',
  'Unexpected error. Try again or contact the administrator.':
    'Erro inesperado. Tente novamente ou fale com o administrador.',
  'Operation restricted to administrators': 'Ação permitida apenas para administradores.',
  'Invalid CPF': 'CPF inválido.',
  'Invalid mobile phone: use DDD + 9 digits, e.g. (71) 99999-9999': 'Celular inválido. Use DDD + 9 dígitos, ex.: (71) 99999-9999.',
  'CPF and mobile phone are required': 'CPF e celular são obrigatórios.',
  'Current password is incorrect': 'A senha atual está incorreta.',
  'The new password cannot be the same as the current one': 'A nova senha deve ser diferente da atual.',
  'Invalid token': 'Link inválido. Peça um novo link.',
  'Token already used': 'Este link já foi utilizado. Peça um novo link.',
  'Token expired': 'Este link expirou. Peça um novo link.',
  'This user is inactive': 'Este usuário está inativo.',
  'This user has already completed the first access': 'Este usuário já concluiu o primeiro acesso.',
  'The MASTER profile cannot be changed': 'O perfil master não pode ser alterado.',
  'The MASTER profile cannot be assigned': 'O perfil master não pode ser atribuído.',
  'The MASTER user cannot be deactivated': 'O usuário master não pode ser desativado.',
  'You cannot deactivate your own user': 'Você não pode desativar o seu próprio usuário.',
  'One or more hospitals do not exist': 'Um ou mais hospitais não existem.',
  'Invalid session': 'Sessão inválida. Entre novamente.',
  'Adjustment reason is required': 'Informe o motivo do ajuste.',
  'REF is required': 'Informe a REF.',
  'Description is required': 'Informe a descrição.',
  'At least one product line is required': 'Escolha ao menos uma linha.',
  'GTIN must have 8, 12, 13 or 14 digits': 'O GTIN deve ter 8, 12, 13 ou 14 dígitos.',
  'Color must use the #RRGGBB format': 'Cor inválida.',
  'REF and lot are required': 'REF e lote são obrigatórios.',
  'invalid quantity': 'quantidade inválida',
  'expiry date is required': 'a validade é obrigatória',
  'location not provided (SALA/STOREROOM or HOSPITAL)': 'local não informado (SALA ou HOSPITAL)',
  'a distribution center only keeps material in the storeroom (SALA)': 'um centro de distribuição só guarda material na sala',
  'LINHA is required (QUADRIL, JOELHO and/or OMBRO)': 'a LINHA é obrigatória (QUADRIL, JOELHO e/ou OMBRO)',
  'IDEAL and IDEAL TOTAL are required': 'IDEAL e IDEAL TOTAL são obrigatórios',
  'Complete table not applied: fix the rows below and import again':
    'A tabela completa não foi aplicada: corrija as linhas abaixo e importe novamente.',
  'The spreadsheet has no valid rows; the current table was kept': 'A planilha não tem linhas válidas; a tabela atual foi mantida.',
  'Rates of past months cannot be changed (billing already calculated)': 'Os percentuais de meses passados não podem ser alterados.',
  'Code not provided': 'Leia ou digite um código.',
  'expired lot': 'lote vencido',
  'The start date must be before the end date': 'A data inicial deve ser anterior à final.',
};

const PATTERNS = [
  [/^Row (\d+): (.*)$/, (m) => `Linha ${m[1]}: ${translateMessage(m[2])}`],
  [/^Header not found\. Required columns: (.*)$/, (m) => `Cabeçalho não encontrado. Colunas obrigatórias: ${m[1]}.`],
  [/^Could not read the spreadsheet/, () => 'Não foi possível ler a planilha. Use um arquivo .xlsx ou .xls.'],
  [/^REF (.+) is not in the catalog$/, (m) => `REF ${m[1]} não está no catálogo`],
  [/^REF (.+) is not registered$/, (m) => `REF ${m[1]} não cadastrada`],
  [/^REF (.+) already registered$/, (m) => `A REF ${m[1]} já está cadastrada.`],
  [/^GTIN (\S+) already belongs to REF (.+)$/, (m) => `O GTIN ${m[1]} já pertence à REF ${m[2]}.`],
  [/^Invalid value for REF (.+)$/, (m) => `Valor inválido para a REF ${m[1]}.`],
  [/^invalid expiry date: (.*)$/, (m) => `validade inválida: ${m[1]}`],
  [/^invalid GTIN: (.*)$/, (m) => `GTIN inválido: ${m[1]}`],
  [/^Unknown product line: (.*)$/, (m) => `linha desconhecida: ${m[1]}`],
  [/^Lot (\S+) \(REF (.+)\) is registered with expiry date/, (m) => `O lote ${m[1]} (REF ${m[2]}) já existe com outra validade.`],
  [/^A hospital already exists with the name (.+)$/, (m) => `Já existe um hospital com o nome ${m[1]}.`],
  [/^(.+) is a distribution center: its material can only be in the storeroom$/, (m) => `${m[1]} é um centro de distribuição: o material só pode ficar na sala.`],
  [/^(.+) is already supplied by (.+)$/, (m) => `${m[1]} já é atendido por ${m[2]}.`],
  [/^Insufficient balance for lot/, () => 'Saldo insuficiente do lote para esta operação.'],
  [/^Email (.+) is already registered$/, (m) => `O e-mail ${m[1]} já está cadastrado.`],
  [/^CPF (.+) is already registered$/, (m) => `O CPF ${m[1]} já está cadastrado.`],
  [/^User (\d+) not found$/, () => 'Usuário não encontrado.'],
  [/^You do not have access to hospital (.+)$/, (m) => `Você não tem acesso ao hospital ${m[1]}.`],
  [/^Invalid or missing parameter/, () => 'Parâmetro inválido ou ausente.'],
  [/^Lot (\S+) \(REF (.+)\) is expired and cannot be (received|delivered)$/,
    (m) => `O lote ${m[1]} (REF ${m[2]}) está vencido e não pode ${m[3] === 'received' ? 'entrar no estoque' : 'ser transferido'}.`],
  [/^Lot (\S+) is expired and cannot be delivered$/, (m) => `O lote ${m[1]} está vencido e não pode ser transferido.`],
  [/^Lot (\S+) \(REF (.+)\) of entry #(\d+) already left the storeroom of (.+): available (\d+), the correction removes (\d+)$/,
    (m) => `O lote ${m[1]} (REF ${m[2]}) da entrada #${m[3]} já saiu da sala de ${m[4]}: há ${m[5]} na sala e a correção retira ${m[6]}. `
      + 'Corrija a transferência ou faça um ajuste de inventário antes.'],
  [/^(.+) is supplied by (.+): register the entry at (.+)$/, (m) => `${m[1]} é atendido por ${m[2]}: registre a entrada em ${m[2]}.`],
  [/^(.+) is inactive$/, (m) => `${m[1]} está inativo.`],
  [/^(.+) is a distribution center: deliver to one of the hospitals it supplies$/,
    (m) => `${m[1]} é um centro de distribuição: transfira para um dos hospitais atendidos.`],
  [/^Stock entry (\d+) not found$/, () => 'Entrada não encontrada.'],
  [/^Material (\d+) not found$/, () => 'Item não encontrado no catálogo.'],
];

/** Field-level validation messages. */
const FIELDS = {
  'Name is required': 'Informe o nome.',
  'Email is required': 'Informe o e-mail.',
  'Invalid email': 'E-mail inválido.',
  'CPF is required': 'Informe o CPF.',
  'Mobile phone is required': 'Informe o celular.',
  'Role is required': 'Escolha o perfil.',
  'Password is required': 'Informe a senha.',
  'Current password is required': 'Informe a senha atual.',
  'Token is required': 'Link inválido.',
  'Destination is required': 'Escolha o destino.',
  'Entry date is required': 'Informe a data de recebimento.',
  'The entry date cannot be in the future': 'A data de recebimento não pode ser futura.',
  'Add at least one item': 'Adicione ao menos um item.',
  'Material is required': 'Informe o item.',
  'Lot is required': 'Informe o lote.',
  'Expiry date is required': 'A validade é obrigatória.',
  'Quantity must be greater than zero': 'A quantidade deve ser maior que zero.',
  'Password must have 8 to 20 characters, with at least one digit, one lowercase letter, one uppercase letter and one special character':
    'A senha deve ter de 8 a 20 caracteres, com número, letra minúscula, letra maiúscula e caractere especial.',
};

export function translateMessage(message) {
  if (!message) return 'Erro inesperado. Tente novamente.';
  if (EXACT[message]) return EXACT[message];
  if (FIELDS[message]) return FIELDS[message];
  for (const [pattern, build] of PATTERNS) {
    const m = message.match(pattern);
    if (m) return build(m);
  }
  return message;
}

export function translateFields(fields) {
  if (!fields) return {};
  return Object.fromEntries(Object.entries(fields).map(([k, v]) => [k, translateMessage(v)]));
}
