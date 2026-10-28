import { useRef, useState } from 'react';
import { Box, Chip, Stack, TextField, Tooltip, Typography } from '@mui/material';
import QrCodeScannerOutlined from '@mui/icons-material/QrCodeScannerOutlined';
import CodeReader from '../../components/CodeReader';
import { useNotify } from '../../notifications/NotificationProvider';
import { tokens } from '../../theme';

/**
 * Notas da caixa: lidas pelo código de barras do DANFE ou digitadas.
 *
 * O código do DANFE traz os 44 dígitos da chave de acesso e nada mais. Como o último dígito confere os
 * outros 43, uma leitura torta é recusada aqui mesmo, em vez de apontar para outra nota.
 *
 * Ler a nota é opcional: serve para a conferência final falar das notas que você tem na mão, mesmo que
 * nenhum item delas tenha sido lido — nota na mão sem nada recebido é justamente a que precisa de atenção.
 */
export default function InvoiceKeysField({ entry, withCamera = false }) {
  const notify = useNotify();
  const [typed, setTyped] = useState('');
  const [camera, setCamera] = useState(false);
  const input = useRef(null);
  const keys = entry.draft.invoiceKeys || [];

  const add = (value) => {
    const result = entry.addInvoiceKey(value);
    if (result.problem) {
      notify.warning(result.problem);
      return false;
    }
    setTyped('');
    input.current?.focus();
    return true;
  };

  return (
    <Stack spacing={1.5}>
      <TextField label="Nota fiscal da caixa (opcional)" placeholder="Leia o código de barras do DANFE"
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
                onClick={() => setCamera((v) => !v)}
                sx={{ border: 0, background: 'none', cursor: 'pointer', color: tokens.textMuted, p: 0.5 }}>
                <QrCodeScannerOutlined fontSize="small" />
              </Box>
            </Tooltip>
          ),
        } : undefined} />

      {camera && <CodeReader typedLabel="Chave da nota (44 dígitos)" onCode={(code) => { if (add(code)) setCamera(false); }} />}

      {keys.length > 0 && (
        <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.75 }}>
          {keys.map((key) => (
            <Chip key={key} size="small" variant="outlined" onDelete={() => entry.removeInvoiceKey(key)}
              label={`NF ${Number(key.slice(25, 34))}`} sx={{ fontFamily: tokens.mono }} />
          ))}
        </Box>
      )}

      <Typography variant="caption" color="text.secondary">
        {keys.length === 0
          ? 'Ler a nota é opcional e ajuda na conferência do que faltou chegar.'
          : `${keys.length === 1 ? 'Uma nota lida' : `${keys.length} notas lidas`}: a conferência ao salvar mostra o que cada uma ainda deve.`}
      </Typography>
    </Stack>
  );
}
