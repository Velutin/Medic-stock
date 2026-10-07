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
  'Patient name is required': 'Informe o nome do paciente.',
  'Scanned code or lot number is required': 'Leia ou digite o código ou o lote.',
  'REF not provided': 'Informe a REF.',
  'User not authenticated': 'Sessão expirada. Entre novamente.',
  'User not found': 'Usuário não encontrado.',
  'Surgical techs do not have access to lots': 'Instrumentadores não têm acesso aos lotes.',
  'A discarded pending issue cannot have a lot. Use RESOLVED instead.': 'Uma pendência descartada não pode ter lote. Use "Lançar um lote do hospital".',
  'A pending issue cannot be reopened': 'Uma pendência não pode ser reaberta.',
  'lotId is required to resolve a pending issue': 'Escolha o lote para resolver a pendência.',
  'Pending issue without a linked surgery; it can only be discarded': 'Pendência sem cirurgia vinculada: ela só pode ser descartada.',
  'The period must be at most 1 year': 'O período pode ter no máximo 1 ano.',
  'No access to this loan': 'Você não tem acesso a este empréstimo.',
  'Only administrators can record surgeries for another surgical tech': 'Só administradores podem lançar cirurgias para outro instrumentador.',
  'A surgery cannot be reopened': 'Uma cirurgia não pode ser reaberta.',
  'The surgery has no recorded items': 'A cirurgia não tem itens lançados.',
  'Attach the consumption sheet (PDF or photos) before completing the surgery': 'Anexe a ficha da cirurgia (PDF ou fotos) antes de concluir.',
  'The surgery is already cancelled': 'A cirurgia já está cancelada.',
  'Surgery has no attached sheet': 'A cirurgia não tem ficha anexada.',
  'Lot not found. Recorded as a pending issue for review.': 'Lote não encontrado. Registrado como pendência para revisão.',
  'This lot number matches more than one material or expiry date with balance in the hospital. Scan the GS1 code (with expiry date), provide the REF or resolve the pending issue.':
    'Este número de lote corresponde a mais de um material ou validade com saldo no hospital. Leia o QR code (que traz a validade), informe a REF ou resolva a pendência.',
  'Lot expired on the surgery date. Recorded as a pending issue.': 'Lote vencido na data da cirurgia. Registrado como pendência.',
  'Lot has no balance inside the hospital. Recorded as a pending issue.': 'Lote sem saldo dentro do hospital. Registrado como pendência.',
  'Send the sheet as a PDF or as photos': 'Envie a ficha em PDF ou em fotos.',
  'File not found': 'Arquivo não encontrado.',
  'Could not read the file': 'Não foi possível ler o arquivo.',
  'Provide either hospitalId or sourceHospitalId': 'Informe o hospital.',
};

const PATTERNS = [
  [/^Unknown UserRole: (.+)$/, () => 'Perfil de usuário desconhecido.'],
  [/^REF (.+) already belongs to another material$/, (m) => `A REF ${m[1]} já pertence a outro item.`],
  [/^Expiry date is required for lot (\S+) \(REF (.+)\)$/, (m) => `Informe a validade do lote ${m[1]} (REF ${m[2]}).`],
  [/^Pending issue (\S+) was already handled$/, () => 'Esta pendência já foi tratada.'],
  [/^Failed to generate PDF/, () => 'Não foi possível gerar o PDF.'],
  [/^(.+) is a distribution center and cannot be supplied by another one$/, (m) => `${m[1]} é um centro de distribuição e não pode ser atendido por outro.`],
  [/^(.+) is a distribution center and cannot have surgeries$/, (m) => `${m[1]} é um centro de distribuição e não tem cirurgias.`],
  [/^(.+) is not a distribution center$/, (m) => `${m[1]} não é um centro de distribuição.`],
  [/^(.+) is not supplied by (.+)$/, (m) => `${m[1]} não é atendido por ${m[2]}.`],
  [/^The code identifies REF (.+?) but has no lot number\. ?Scan the lot barcode or type the lot number\.$/,
    (m) => `O código identifica a REF ${m[1]}, mas não traz o lote. Leia o código de barras do lote ou digite o lote.`],
  [/^The code has no lot number\. ?Scan the lot barcode or type the lot number\.$/,
    () => 'O código não traz o lote. Leia o código de barras do lote ou digite o lote.'],
  [/^Item (\S+) does not belong to the surgery$/, () => 'Este item não pertence à cirurgia.'],
  [/^There are (\d+) open pending issue\(s\) in this surgery\. Resolve or discard them before completing\.$/,
    (m) => `Há ${m[1]} ${m[1] === '1' ? 'pendência em aberto' : 'pendências em aberto'} nesta cirurgia. Resolva ou descarte antes de concluir.`],
  [/^Surgery (\S+) is completed: only administrators can change its items$/, () => 'A cirurgia já foi concluída: só administradores podem alterar os itens.'],
  [/^Surgery (\S+) is cancelled and cannot be changed$/, () => 'A cirurgia foi cancelada e não pode ser alterada.'],
  [/^Surgery (\S+) is (\w+) and cannot be changed$/, (m) => `A cirurgia está ${({ completed: 'concluída', cancelled: 'cancelada', open: 'em aberto' })[m[2]] || m[2]} e não pode ser alterada.`],
  [/^REF (.+) has no value in the price table of (.+)\. The value is filled in automatically when it is registered\.$/,
    (m) => `A REF ${m[1]} não tem valor na tabela de ${m[2]}. O valor é preenchido automaticamente quando for cadastrado.`],
  [/^Could not save the sheet/, () => 'Não foi possível salvar a ficha. Tente novamente.'],
  [/^Unsupported format in (.+?)\. Send PDF, JPG or PNG/,
    (m) => `Formato não suportado em ${m[1]}. Envie PDF, JPG ou PNG (no iPhone, ajuste a câmera para "Mais compatível").`],
  [/^Material (.+) is not in the minimum list of hospital (.+)$/, () => 'O item não está na lista de mínimos deste hospital.'],
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
  [/^Lot (\S+) \(REF (.+)\) has more than one expiry date in (\d{2}\/\d{4}): type the full date \(DD\/MM\/AAAA\) or read the QR code$/,
    (m) => `O lote ${m[1]} (REF ${m[2]}) tem mais de uma validade em ${m[3]}: digite a data completa (DD/MM/AAAA) ou leia o QR code.`],
  [/^Lot (\S+) is expired and cannot be delivered$/, (m) => `O lote ${m[1]} está vencido e não pode ser transferido.`],
  [/^Lot (\S+) appears with more than one REF in this entry: a lot number belongs to only one REF$/,
    (m) => `O lote ${m[1]} aparece com mais de uma REF nesta entrada. Cada lote pertence a uma única REF.`],
  [/^Lot (\S+) is already registered with REF (.+): confirm the REF change of those lots to receive it$/,
    (m) => `O lote ${m[1]} já está cadastrado com a REF ${m[2]}. Confirme a troca de REF desses lotes para dar entrada.`],
  [/^Lot (\S+) \(REF (.+)\) was already used in a surgery and its REF cannot be changed: check the REF of the item$/,
    (m) => `O lote ${m[1]} (REF ${m[2]}) já saiu em cirurgia e não pode mudar de REF. Confira a REF do item.`],
  [/^Lot (\S+) \(REF (.+)\) of entry #(\d+) already left the storeroom of (.+): available (\d+), the correction removes (\d+)$/,
    (m) => `O lote ${m[1]} (REF ${m[2]}) da entrada #${m[3]} já saiu da sala de ${m[4]}: há ${m[5]} na sala e a correção retira ${m[6]}. `
      + 'Corrija a transferência ou faça um ajuste de inventário antes.'],
  [/^(.+) is supplied by (.+): register the entry at (.+)$/, (m) => `${m[1]} é atendido por ${m[2]}: registre a entrada em ${m[2]}.`],
  [/^(.+) is inactive$/, (m) => `${m[1]} está inativo.`],
  [/^(.+) is a distribution center: deliver to one of the hospitals it supplies$/,
    (m) => `${m[1]} é um centro de distribuição: transfira para um dos hospitais atendidos.`],
  [/^Stock entry (\d+) not found$/, () => 'Entrada não encontrada.'],
  [/^Material (\d+) not found$/, () => 'Item não encontrado no catálogo.'],
  [/^Section (\d+) not found$/, () => 'Seção não encontrada.'],
  [/^Section (.+) already exists$/, (m) => `Já existe a seção ${m[1]}.`],
  [/^Section (.+) has (\d+) items: move them to another section or deactivate it$/,
    (m) => `A seção ${m[1]} tem ${m[2]} itens: mova-os para outra seção ou desative-a.`],
  [/^(.+) is a distribution center: return from its storeroom$/, (m) => `${m[1]} é um centro de distribuição: devolva a partir da sala.`],
  [/^No billing rate starts in (.+)$/, () => 'Não há percentuais cadastrados para esse mês.'],
  [/^No billing rate registered for (.+)$/, (m) => `Não há percentuais cadastrados para ${m[1]}. Cadastre em Faturamento → Percentuais.`],
  [/^Invalid month '(.+)'/, (m) => `Mês inválido: ${m[1]}.`],
  [/^Failed to generate spreadsheet/, () => 'Não foi possível gerar a planilha.'],
  [/^REF (.+?): (.+)$/, (m) => `REF ${m[1]}: ${translateMessage(m[2])}`],
  [/^(.+) is a distribution center: only the ideal total is used \(hospital ideal must be 0\)$/,
    (m) => `${m[1]} é um centro de distribuição: só o ideal total é usado (o ideal deve ser 0).`],
  [/^section (.+) is not registered$/, (m) => `seção ${m[1]} não cadastrada em Cadastros → Seções`],
  [/^(Lot|Order|Delivery|Loan|Surgery|Hospital|User|Role|Pending issue|Surgical tech|Item|Stock entry|Section|Material|Lot scan) (.+) not found$/,
    (m) => `${({ Lot: 'Lote', Order: 'Pedido', Delivery: 'Entrega', Loan: 'Empréstimo', Surgery: 'Cirurgia', Hospital: 'Hospital',
      User: 'Usuário', Role: 'Perfil', 'Pending issue': 'Pendência', 'Surgical tech': 'Instrumentador', Item: 'Item',
      'Stock entry': 'Entrada', Section: 'Seção', Material: 'Item' })[m[1]] || 'Registro'} não encontrado.`],
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
  'Section name is required': 'Informe o nome da seção.',
  'Destination hospital is required': 'Escolha o hospital de destino.',
  'Return reason is required': 'Informe o motivo da devolução.',
  'Choose a window between 0 and 365 days': 'Escolha uma janela entre 0 e 365 dias.',
  'Cancellation reason is required': 'Informe o motivo do cancelamento.',
  'Resolution is required': 'Informe a justificativa.',
  'Source and destination must be different hospitals. To move storeroom material into its own hospital, use replenishment.':
    'Origem e destino devem ser hospitais diferentes. Para levar material da sala ao próprio hospital, use a Transferência.',
  'A return to the supplier has no destination hospital': 'A devolução à empresa não tem hospital de destino.',
  'Loans cannot involve a distribution center. Use a distribution instead.': 'Empréstimos não podem envolver um centro de distribuição. Use a distribuição pela Reposição.',
  'Minimum levels cannot be negative': 'Os mínimos não podem ser negativos.',
  'Ideal total cannot be lower than the hospital ideal': 'O ideal total não pode ser menor que o ideal.',
  'IDEAL and IDEAL TOTAL are required': 'IDEAL e IDEAL TOTAL são obrigatórios.',
  'levels must be whole numbers': 'os mínimos devem ser números inteiros',
  'Display order is required': 'Informe a ordem.',
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
  // Nothing in English reaches the user: an unmapped message gets a generic text (the original goes to the console)
  if (looksEnglish(message)) {
    console.warn('Mensagem sem tradução:', message);
    return 'Não foi possível concluir a operação. Se o problema continuar, informe o administrador.';
  }
  return message;
}

const ENGLISH_WORDS = /\b(the|is|are|not|cannot|must|required|found|already|invalid|only|with|and|of|has|have|to|be|was|lot|send|please|could|failed|error|happened|this|that|from|by|should|does|did|will|unexpected|missing|which|exceeds|denied|unauthorized|forbidden)\b/i;
const PORTUGUESE = /[áàâãéêíóôõúçÁÀÂÃÉÊÍÓÔÕÚÇ]|\b(não|lote|para|com|está|informe|cirurgia)\b/i;
const looksEnglish = (text) => ENGLISH_WORDS.test(text) && !PORTUGUESE.test(text);

export function translateFields(fields) {
  if (!fields) return {};
  return Object.fromEntries(Object.entries(fields).map(([k, v]) => [k, translateMessage(v)]));
}
