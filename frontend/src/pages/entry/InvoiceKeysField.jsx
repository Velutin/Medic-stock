import { useRef, useState } from 'react';
import { Box, Button, Chip, Stack, TextField, Tooltip, Typography } from '@mui/material';
import QrCodeScannerOutlined from '@mui/icons-material/QrCodeScannerOutlined';
import CodeReader from '../../components/CodeReader';
import { api } from '../../api/client';
import { useNotify } from '../../notifications/NotificationProvider';
import { tokens } from '../../theme';

/**
 * Notas da caixa: lidas pelo código de barras do DANFE ou digitadas.
 *
 * O código do DANFE traz os 44 dígitos da chave de acesso e nada mais. Como o último dígito confere os
 * outros 43, uma leitura torta é recusada aqui mesmo, em vez de apontar para outra nota.
 *
 * Ler a nota é opcional, e é opcional mesmo com a nota sendo exigida na entrada: quem decide de que nota
 * cada lote veio é o XML que o sistema leu do e-mail, pelo número do lote. Esta leitura serve para a
 * conferência final falar das notas que você tem na mão, mesmo que nenhum item delas tenha sido lido —
 * nota na mão sem nada recebido é justamente a que precisa de atenção.
 */
export default function InvoiceKeysField({ entry, withCamera = false, onCameraChange }) {
  const notify = useNotify();
  const [typed, setTyped] = useState('');
  const [camera, setCamera] = useState(false);
  // Chaves cuja nota ainda não chegou por e-mail, para avisar com a caixa ainda aberta
  const [missing, setMissing] = useState({});
  const input = useRef(null);
  const keys = entry.draft.invoiceKeys || [];
  const required = entry.invoiceRequired;

  const toggleCamera = (open) => {
    setCamera(open);
    onCameraChange?.(open);
  };

  /*
   * Pergunta ao sistema se a nota lida já chegou por e-mail.
   *
   * O aviso vale porque o XML é quem diz quais itens a nota traz: sem ele, os lotes da caixa não têm como
   * ser vinculados e vão virar pendência. Saber disso agora, com a caixa aberta, é diferente de descobrir
   * depois de gravar. Falha em silêncio: é um aviso, não pode impedir a leitura.
   */
  const checkArrived = (key) => {
    api(`/invoices/by-key/${key}`)
      .then((invoice) => setMissing((m) => ({ ...m, [key]: !invoice })))
      .catch(() => {});
  };

  const add = (value) => {
    const result = entry.addInvoiceKey(value);
    if (result.problem) {
      notify.warning(result.problem);
      return false;
    }
    setTyped('');
    input.current?.focus();
    checkArrived(value.replace(/\D/g, ''));
    return true;
  };

  return (
    <Stack spacing={1.5}>
      <TextField label={required ? 'Nota fiscal da caixa' : 'Nota fiscal da caixa (opcional)'}
        placeholder="Leia o código de barras do DANFE"
        value={typed} inputRef={input} autoComplete="off"
        onChange={(e) => {
          const value = e.target.value;
          setTyped(value);
          // O leitor USB digita os 44 dígitos de uma vez e termina com Enter; quando não manda o Enter,
          // a chave completa já é aceita sozinha
          if (value.replace(/\D/g, '').length === 44) add(value);
        }}
        onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); add(typed); } }}
        InputProps={withCamera ? {
          endAdornment: (
            <Tooltip title="Ler pela câmera">
              <Box component="button" type="button" aria-label="Ler a nota pela câmera"
                onClick={() => toggleCamera(!camera)}
                sx={{ border: 0, background: 'none', cursor: 'pointer', color: tokens.textMuted, p: 0.5 }}>
                <QrCodeScannerOutlined fontSize="small" />
              </Box>
            </Tooltip>
          ),
        } : undefined} />

      {/*
        A câmera fica aberta depois de ler uma nota, de propósito: a caixa quase sempre traz várias, e
        fechar a cada leitura obrigaria a reabrir e esperar a câmera subir de novo entre uma nota e outra.
        Quem termina fecha no botão abaixo e volta para a leitura dos lotes. Ler a mesma nota duas vezes não
        é problema: o leitor ignora o código que continua parado na frente da câmera, e a chave repetida é
        recusada de qualquer forma.
      */}
      {camera && (
        <Stack spacing={1}>
          <CodeReader wide typedLabel="Chave da nota (44 dígitos)" onCode={(code) => add(code)} />
          <Button variant="outlined" fullWidth onClick={() => toggleCamera(false)}>
            {keys.length === 0 ? 'Fechar leitura de notas' : 'Concluir e voltar para os itens'}
          </Button>
        </Stack>
      )}

      {keys.length > 0 && (
        <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.75 }}>
          {keys.map((key) => (
            <Chip key={key} size="small" variant="outlined" color={missing[key] ? 'warning' : 'default'}
              onDelete={() => entry.removeInvoiceKey(key)}
              label={`NF ${Number(key.slice(25, 34))}`} sx={{ fontFamily: tokens.mono }} />
          ))}
        </Box>
      )}

      {keys.some((k) => missing[k]) && (
        <Typography variant="caption" color="warning.main">
          {keys.filter((k) => missing[k]).length === 1
            ? 'Esta nota ainda não chegou por e-mail.'
            : 'Estas notas ainda não chegaram por e-mail.'}
          {' '}Os itens entram no estoque do mesmo jeito e ficam como pendência, já apontando o número que
          você leu. Quando o XML chegar, é só confirmar.
        </Typography>
      )}

      <Typography variant="caption" color="text.secondary">
        {keys.length > 0
          ? `${keys.length === 1 ? 'Uma nota lida' : `${keys.length} notas lidas`}: ${required
              ? 'os lotes da caixa são vinculados só a elas.'
              : 'a conferência ao salvar mostra o que cada uma ainda deve.'}`
          : required
            ? 'Leia a nota da caixa para registrar a entrada. Os lotes são vinculados só às notas lidas; o que nenhuma delas estiver devendo entra no estoque e vira pendência.'
            : 'Ler a nota é opcional e ajuda na conferência do que faltou chegar.'}
      </Typography>
    </Stack>
  );
}
