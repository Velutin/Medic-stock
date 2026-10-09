# Restaurar o backup

Os comandos abaixo foram testados de ponta a ponta (cifrar → decifrar → conferir),
inclusive com acentuação e com PDF binário.

**Faça o teste do passo 1 hoje**, com o sistema ainda vazio. Backup que nunca foi
restaurado é esperança, não backup — e a hora de descobrir que a senha está errada
não é a hora em que o banco se perdeu.

Em todos os comandos, `SENHA=/etc/mss-backup.pass`.

---

## 1. Conferir um backup sem restaurar nada

Não toca no sistema. É o teste que você deve fazer periodicamente.

```bash
export SENHA=/etc/mss-backup.pass
cd /var/backups/mss

# últimas linhas do dump do estoque
sudo gpg --batch --quiet --pinentry-mode loopback --passphrase-file $SENHA \
    --decrypt mssdb_AAAA-MM-DD_HHMM.sql.gz.gpg | gunzip | tail -20

# o que há dentro do arquivo de fichas
sudo gpg --batch --quiet --pinentry-mode loopback --passphrase-file $SENHA \
    --decrypt storage_AAAA-MM-DD_HHMM.tar.gz.gpg | tar -tzvf -
```

Se os dois responderem, o backup está íntegro e a senha está certa.

## 2. Restaurar o banco do estoque

**Destrutivo: substitui o banco atual.** Use só depois de confirmar que é mesmo
necessário.

```bash
cd /opt/medic-stock

# pare as aplicações para que ninguém escreva durante a restauração
docker compose -f deploy/docker-compose.yaml --env-file deploy/.env stop mss mail

sudo gpg --batch --quiet --pinentry-mode loopback --passphrase-file /etc/mss-backup.pass \
    --decrypt /var/backups/mss/mssdb_AAAA-MM-DD_HHMM.sql.gz.gpg \
  | gunzip \
  | docker compose -f deploy/docker-compose.yaml --env-file deploy/.env \
        exec -T postgres-mss sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'

docker compose -f deploy/docker-compose.yaml --env-file deploy/.env start mss mail
```

O dump é gerado com `--clean --if-exists`, então ele apaga e recria os objetos
sozinho — não é preciso derrubar o banco antes.

Depois de subir, confira no log do `mss` que o Flyway não reclamou de migração:

```bash
docker compose -f deploy/docker-compose.yaml --env-file deploy/.env logs mss | grep -i flyway
```

Restaurar um backup **mais antigo que o código** é o caso que dá problema: se o
backup é de antes de uma migração nova, o Flyway aplica a migração que falta na
subida seguinte e tudo se acerta. O contrário — código antigo com banco novo — não
funciona. Por isso vale rodar o backup antes de toda atualização com migração.

## 3. Restaurar as fichas de consumo

```bash
sudo gpg --batch --quiet --pinentry-mode loopback --passphrase-file /etc/mss-backup.pass \
    --decrypt /var/backups/mss/storage_AAAA-MM-DD_HHMM.tar.gz.gpg \
  | docker compose -f /opt/medic-stock/deploy/docker-compose.yaml \
        --env-file /opt/medic-stock/deploy/.env exec -T mss tar -xzf - -C /app/storage
```

## 4. Recuperar o `.env`

```bash
sudo gpg --batch --quiet --pinentry-mode loopback --passphrase-file /etc/mss-backup.pass \
    --decrypt /var/backups/mss/env_AAAA-MM-DD_HHMM.gpg
```

Útil se o servidor se perder: com esse arquivo e os dumps, o sistema inteiro sobe
de novo numa máquina nova.

## 5. Servidor novo, do zero

1. Monte o VPS seguindo o `README-DEPLOY.md` até o passo 5, **sem subir a stack**.
2. Recupere o `.env` (passo 4 acima) e coloque em `deploy/.env` (`chmod 600`).
3. Suba a stack: `docker compose ... up -d --build`.
4. Restaure os dois bancos (passo 2) e as fichas (passo 3).
5. Siga o `README-DEPLOY.md` do passo 6 em diante (frontend, nginx, certificado).

O `JWT_SECRET` vindo do `.env` antigo mantém as sessões válidas. Se preferir
derrubar todas as sessões abertas, gere um novo — todo mundo refaz o login.

---

## Se a senha do backup se perder

Não há o que fazer: os arquivos são irrecuperáveis. É isso que a criptografia
garante, e vale tanto para quem roubar o backup quanto para você.

Enquanto o servidor estiver de pé, gere uma senha nova e um backup novo no mesmo
dia; os antigos viram lixo e podem ser apagados.

```bash
openssl rand -base64 32 | sudo tee /etc/mss-backup.pass
sudo chmod 600 /etc/mss-backup.pass
sudo /usr/local/bin/mss-backup
```

Guarde a nova senha em dois lugares fora do servidor antes de apagar os backups
antigos.
