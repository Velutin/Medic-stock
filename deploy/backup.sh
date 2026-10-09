#!/usr/bin/env bash
#
# Medic-stock (MSS) — backup diário, criptografado.
#
# Instalação:
#   sudo apt install -y gnupg
#   sudo cp deploy/backup.sh /usr/local/bin/mss-backup
#   sudo chmod +x /usr/local/bin/mss-backup
#
#   # senha do backup — guarde uma cópia FORA do servidor antes de seguir
#   openssl rand -base64 32 | sudo tee /etc/mss-backup.pass
#   sudo chmod 600 /etc/mss-backup.pass
#
#   sudo /usr/local/bin/mss-backup          # rode uma vez na mão
#   sudo crontab -e
#   30 2 * * * /usr/local/bin/mss-backup >> /var/log/mss-backup.log 2>&1
#
# O que é gravado, tudo com AES-256:
#   mssdb_*.sql.gz.gpg        banco do estoque (inclui nome de paciente por cirurgia)
#   mssemaildb_*.sql.gz.gpg   banco de e-mails
#   storage_*.tar.gz.gpg      fichas de consumo digitalizadas
#   env_*.gpg                 o .env, com todas as senhas do sistema
#
# PERDER A SENHA É PERDER OS BACKUPS. Não existe recuperação — é o que a
# criptografia garante. Guarde-a em dois lugares fora do servidor.
#
# Cada arquivo é verificado logo depois de gravado: o script descriptografa e
# confere o conteúdo. Backup corrompido falha aqui, e não no dia em que você
# precisar dele.
#
# Restauração: deploy/RESTAURACAO.md

set -euo pipefail
umask 077

PROJECT_DIR="${PROJECT_DIR:-/opt/medic-stock}"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/mss}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"
PASS_FILE="${PASS_FILE:-/etc/mss-backup.pass}"

COMPOSE="docker compose -f ${PROJECT_DIR}/deploy/docker-compose.yaml --env-file ${PROJECT_DIR}/deploy/.env"

# Sob o cron não há HOME confiável, e o gpg precisa de diretório próprio.
export GNUPGHOME="${GNUPGHOME:-/root/.gnupg}"
mkdir -p "${GNUPGHOME}"
chmod 700 "${GNUPGHOME}"

GPG=(gpg --batch --yes --quiet --pinentry-mode loopback --passphrase-file "${PASS_FILE}")

STAMP="$(date +%Y-%m-%d_%H%M)"
log()  { echo "[$(date '+%F %T')] $*"; }
fail() { echo "[$(date '+%F %T')] ERRO: $*" >&2; exit 1; }

# --- conferências antes de começar -------------------------------------------
[ -f "${PASS_FILE}" ] || fail "senha não encontrada em ${PASS_FILE} — veja o cabeçalho deste arquivo."
[ -s "${PASS_FILE}" ] || fail "${PASS_FILE} está vazio."

perm=$(stat -c '%a' "${PASS_FILE}")
[ "${perm}" = "600" ] || fail "${PASS_FILE} está com permissão ${perm}; precisa ser 600 (chmod 600 ${PASS_FILE})."

command -v gpg >/dev/null || fail "gpg não instalado (sudo apt install -y gnupg)."

# Teste do gpg antes de qualquer coisa: cifra e decifra uma palavra. Sem isso, um
# problema de gpg-agent só apareceria depois do dump inteiro, com erro obscuro.
if ! teste=$(echo 'mss' | "${GPG[@]}" --symmetric --cipher-algo AES256 2>/dev/null \
             | "${GPG[@]}" --decrypt 2>/dev/null) || [ "${teste}" != "mss" ]; then
    fail "o gpg não conseguiu cifrar/decifrar. Se a mensagem citar o gpg-agent, confira se GNUPGHOME (${GNUPGHOME}) existe e tem permissão 700."
fi

mkdir -p "${BACKUP_DIR}"
chmod 700 "${BACKUP_DIR}"

log "backup ${STAMP} — início"

# -----------------------------------------------------------------------------
# cifra()    lê da entrada padrão e grava o arquivo criptografado.
# confere()  descriptografa e valida o conteúdo. É a diferença entre "o arquivo
#            existe" e "o arquivo serve".
#
# A validação é por CONTEÚDO, não por tamanho. Um limite mínimo de bytes
# reprovaria casos legítimos — o arquivo de fichas está vazio enquanto não houver
# a primeira cirurgia, e um alarme falso no primeiro dia ensina a ignorar alarme.
# -----------------------------------------------------------------------------
cifra() {
    "${GPG[@]}" --symmetric --cipher-algo AES256 --compress-algo none --output "$1"
}

tamanho_de() { numfmt --to=iec "$(stat -c%s "$1")"; }

confere_dump() {
    local arquivo="$1"
    # gunzip valida o CRC do gzip; o marcador prova que é um dump de verdade e não
    # uma saída de erro que foi comprimida e cifrada sem ninguém perceber.
    "${GPG[@]}" --decrypt "${arquivo}" 2>/dev/null | gunzip 2>/dev/null \
        | grep -qm1 'PostgreSQL database dump' \
        || fail "$(basename "${arquivo}") não passou na verificação — NÃO confie neste backup."
    log "  ok: $(basename "${arquivo}") ($(tamanho_de "${arquivo}"))"
}

confere_tar() {
    local arquivo="$1" itens
    itens=$("${GPG[@]}" --decrypt "${arquivo}" 2>/dev/null | tar -tzf - 2>/dev/null | wc -l) \
        || fail "$(basename "${arquivo}") não passou na verificação — NÃO confie neste backup."
    log "  ok: $(basename "${arquivo}") ($(tamanho_de "${arquivo}"), ${itens} item(ns))"
    # Vazio é esperado enquanto nenhuma ficha foi anexada; depois disso, é sinal de problema.
    [ "${itens}" -le 1 ] && log "  nota: nenhuma ficha de consumo no arquivo (esperado se ainda não houve cirurgia com ficha anexada)"
    return 0
}

# --- bancos de dados ----------------------------------------------------------
# O pg_dump roda dentro do contêiner: o servidor não precisa de cliente PostgreSQL.
log "banco do estoque..."
${COMPOSE} exec -T postgres-mss \
    sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists' \
    | gzip | cifra "${BACKUP_DIR}/mssdb_${STAMP}.sql.gz.gpg"
confere_dump "${BACKUP_DIR}/mssdb_${STAMP}.sql.gz.gpg"

log "banco de e-mails..."
${COMPOSE} exec -T postgres-mail \
    sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists' \
    | gzip | cifra "${BACKUP_DIR}/mssemaildb_${STAMP}.sql.gz.gpg"
confere_dump "${BACKUP_DIR}/mssemaildb_${STAMP}.sql.gz.gpg"

# --- fichas de consumo --------------------------------------------------------
log "fichas de consumo..."
${COMPOSE} exec -T mss tar -cf - -C /app/storage . \
    | gzip | cifra "${BACKUP_DIR}/storage_${STAMP}.tar.gz.gpg"
confere_tar "${BACKUP_DIR}/storage_${STAMP}.tar.gz.gpg"

# --- configuração -------------------------------------------------------------
log "configuração..."
cifra "${BACKUP_DIR}/env_${STAMP}.gpg" < "${PROJECT_DIR}/deploy/.env"
"${GPG[@]}" --decrypt "${BACKUP_DIR}/env_${STAMP}.gpg" 2>/dev/null | grep -q '^JWT_SECRET=' \
    || fail "env_${STAMP}.gpg não passou na verificação."
log "  ok: env_${STAMP}.gpg ($(tamanho_de "${BACKUP_DIR}/env_${STAMP}.gpg"))"

# --- retenção -----------------------------------------------------------------
apagados=$(find "${BACKUP_DIR}" -maxdepth 1 -type f -name '*.gpg' -mtime "+${RETENTION_DAYS}" -print -delete | wc -l)
[ "${apagados}" -gt 0 ] && log "retenção: ${apagados} arquivo(s) com mais de ${RETENTION_DAYS} dias removido(s)"

# --- cópia para fora do servidor ----------------------------------------------
# Backup que mora na mesma máquina não protege contra perder a máquina. Os arquivos
# já estão criptografados, então podem ir para qualquer nuvem sem expor nada —
# inclusive para uma conta pessoal de Drive, que sem a senha vê só bytes embaralhados.
#
# Com rclone configurado uma vez (`rclone config`), descomente:
# rclone copy "${BACKUP_DIR}" "${RCLONE_REMOTE:-remoto:backups-mss}" --include "*_${STAMP}*.gpg" \
#     || log "AVISO: a cópia para fora do servidor falhou — o backup local está íntegro"

log "backup ${STAMP} — fim ($(du -sh "${BACKUP_DIR}" | cut -f1) em ${BACKUP_DIR})"
