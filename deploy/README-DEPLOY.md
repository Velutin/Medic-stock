# Medic-stock (MSS) — colocar no ar

Passo a passo do VPS zerado até o sistema funcionando, para **Ubuntu 22.04 LTS** num
VPS de 2 vCPU e 4 GB (HostGator VPS NVMe 4 ou equivalente).

Reserve uma hora. O passo 5 (compilar as quatro aplicações) demora entre 10 e 20
minutos na primeira vez e não dá para apressar.

## Na hora de contratar

- **Sistema operacional: Ubuntu 22.04 LTS.** É a única versão de Ubuntu que a
  HostGator oferece hoje (as outras opções são AlmaLinux 9 e Rocky Linux 9). Serve
  bem; a observação é que o suporte padrão dela termina em **abril de 2027**, então
  em algum momento você vai querer migrar para a 24.04. Como o sistema inteiro sobe
  com `docker compose` e a restauração do backup, migrar é refazer este roteiro numa
  máquina nova — algumas horas, não um projeto.
- **Não contrate o cPanel**, mesmo sendo oferecido como adicional. Ele consome de 1
  a 2 GB de RAM sozinho, que é justamente a folga dos 4 GB. O sistema não usa painel.
- **Escolha o NVMe 4 agora.** A HostGator não permite downgrade entre planos de VPS:
  se pegar o menor e precisar subir, dá; o contrário, não.
- **Confirme no checkout que o servidor fica no Brasil.** A página do VPS diz
  "Servidores Oracle Cloud no Brasil", mas a página de infraestrutura da HostGator
  cita um data center em Atlanta — esse é o da hospedagem compartilhada. Como o
  sistema guarda nome de paciente, vale confirmar: dado no Brasil evita a discussão
  de transferência internacional da LGPD.

---

## Antes de começar

Três coisas precisam estar prontas:

1. **Um domínio** apontando para o IP do VPS (registro A). Pode ser um subdomínio
   de um domínio que você já tenha, como `estoque.seudominio.com.br`. **Não existe
   certificado para IP puro**, e sem HTTPS o navegador bloqueia a câmera — o leitor
   de código fica inutilizável no celular. Crie o registro A antes de tudo: a
   propagação de DNS pode levar algumas horas e é o passo que não depende de você.
2. **Uma conta de SMTP** para os e-mails de convite e recuperação de senha. No
   Gmail é a senha de aplicativo (Conta Google → Segurança → Verificação em duas
   etapas → Senhas de app); a senha normal da conta não funciona.
3. **O projeto no servidor.** Por `git clone`, ou enviando o zip por `scp`.

---

## 1. Acesso e atualização

```bash
ssh root@SEU_IP
apt update && apt upgrade -y
timedatectl set-timezone America/Bahia
```

Crie um usuário comum — operar como `root` o tempo todo é como deixar a chave na
porta:

```bash
adduser mss
usermod -aG sudo mss
rsync --archive --chown=mss:mss ~/.ssh /home/mss     # leva sua chave SSH junto
```

Daqui para a frente, tudo como `mss` (`ssh mss@SEU_IP`).

## 2. Firewall

```bash
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
sudo ufw status
```

Só 22, 80 e 443 ficam abertos. Os bancos, o RabbitMQ e as quatro aplicações
escutam apenas em `127.0.0.1` — o `docker-compose.yaml` cuida disso, mas o
firewall é a segunda tranca.

## 3. Docker

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER
newgrp docker
docker compose version
```

## 4. Configuração

```bash
sudo mkdir -p /opt/medic-stock && sudo chown $USER:$USER /opt/medic-stock
# copie o projeto para /opt/medic-stock (git clone, scp, unzip...)
cd /opt/medic-stock

cp deploy/.env.example deploy/.env
chmod 600 deploy/.env
nano deploy/.env
```

Preencha os quatro segredos com valores diferentes entre si:

```bash
openssl rand -base64 36    # rode uma vez para cada
```

E preencha o `FRONTEND_URL` com o domínio real e os quatro campos de SMTP. Se
faltar qualquer variável obrigatória, o `docker compose` recusa subir dizendo o
nome da que falta — ele não vai no ar com senha padrão.

## 5. Subir o backend

```bash
cd /opt/medic-stock
docker compose -f deploy/docker-compose.yaml --env-file deploy/.env up -d --build
```

A primeira execução compila as quatro aplicações e baixa as dependências do Maven
— **10 a 20 minutos**. Faça isso antes de configurar o nginx, com a máquina sem
mais nada rodando: a compilação sozinha usa mais de 1 GB.

Acompanhe:

```bash
docker compose -f deploy/docker-compose.yaml --env-file deploy/.env ps
docker compose -f deploy/docker-compose.yaml --env-file deploy/.env logs -f mss
```

O `mss` está pronto quando aparecer `Started MssApplication`. O Flyway aplica as
doze migrações sozinho na primeira subida — não há nada manual no banco.

Como o comando é longo, vale um atalho no `~/.bashrc`:

```bash
echo "alias mss='docker compose -f /opt/medic-stock/deploy/docker-compose.yaml --env-file /opt/medic-stock/deploy/.env'" >> ~/.bashrc
source ~/.bashrc
# a partir daqui:  mss ps   |   mss logs -f mss   |   mss restart mss
```

Confira que a API responde antes de seguir:

```bash
curl -s -o /dev/null -w "%{http_code}\n" http://127.0.0.1:8085/v3/api-docs   # 200
```

## 6. Compilar o frontend

Sem instalar Node no servidor, usando um contêiner descartável:

```bash
cd /opt/medic-stock
docker run --rm -v "$PWD/frontend":/app -w /app node:20-bookworm-slim \
    sh -c "npm ci && VITE_COMPANY_NAME=Baumer npm run build"

sudo mkdir -p /var/www/estoque
sudo rsync -a --delete frontend/dist/ /var/www/estoque/
sudo chown -R www-data:www-data /var/www/estoque
```

O `VITE_API_TARGET` **não** entra aqui: ele só serve ao proxy de desenvolvimento.
O frontend compilado chama `/api` relativo, que o nginx resolve. A única variável
que entra no build é o `VITE_COMPANY_NAME`.

## 7. nginx e certificado

```bash
sudo apt install -y nginx
sudo cp deploy/nginx/estoque.conf /etc/nginx/sites-available/estoque
sudo nano /etc/nginx/sites-available/estoque     # trocar estoque.seudominio.com.br
sudo ln -s /etc/nginx/sites-available/estoque /etc/nginx/sites-enabled/estoque
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t && sudo systemctl reload nginx
```

Abra `http://estoque.seudominio.com.br` e confirme que a tela de login aparece.
Só depois disso:

```bash
# No Ubuntu 22.04, o certbot do apt é uma versão antiga. O snap é o caminho que a
# própria Let's Encrypt recomenda e se mantém atualizado sozinho.
sudo snap install --classic certbot
sudo ln -sf /snap/bin/certbot /usr/bin/certbot
sudo certbot --nginx -d estoque.seudominio.com.br
```

O certbot edita o próprio arquivo: acrescenta o certificado, transforma o bloco em
443 e cria o redirecionamento de http para https. A renovação automática já vem
configurada — confira com `sudo certbot renew --dry-run`.

**Teste a câmera agora**, de um celular, em `https://`. É o que prova que o
certificado está realmente valendo.

## 8. Primeiro acesso

Entre em `https://estoque.seudominio.com.br` com `admin@mss.local` / `admin123` e
**troque a senha imediatamente** — ela está em texto claro na migração V2, ou seja,
em qualquer cópia do código. Preencha CPF e celular, que são obrigatórios.

Antes de cadastrar o primeiro usuário, confirme que o serviço de e-mail subiu:

```bash
mss ps mail        # precisa estar Up, não Restarting
```

Isso importa mais do que parece. O `mss` publica na fila `default.email`, e quem
**cria** essa fila é o serviço `mail`. Se o `mail` nunca tiver subido, a fila não
existe e o convite de primeiro acesso é descartado pelo RabbitMQ **sem erro
nenhum** — o usuário simplesmente nunca recebe o e-mail e nada no log explica por
quê. Depois que o `mail` sobe uma vez, a fila é durável: mesmo que ele caia, as
mensagens ficam esperando.

## 9. Backup

```bash
sudo cp deploy/backup.sh /usr/local/bin/mss-backup
sudo chmod +x /usr/local/bin/mss-backup
sudo /usr/local/bin/mss-backup          # rode uma vez na mão para conferir
sudo crontab -e
```

Acrescente:

```
30 2 * * * /usr/local/bin/mss-backup >> /var/log/mss-backup.log 2>&1
```

Guarda os dois bancos, as fichas de consumo e o `.env`, com 14 dias de retenção,
em `/var/backups/mss`.

**Um backup que nunca foi restaurado é uma esperança, não um backup.** Teste a
restauração uma vez, agora, enquanto o sistema está vazio:

```bash
gunzip -c /var/backups/mss/mssdb_AAAA-MM-DD_HHMM.sql.gz \
  | mss exec -T postgres-mss sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

E leve uma cópia para fora do servidor. Backup que mora na mesma máquina não
protege contra a perda da máquina.

---

## Rotina

**Atualizar o sistema** depois de aplicar um pacote novo:

```bash
cd /opt/medic-stock
mss up -d --build              # backend
docker run --rm -v "$PWD/frontend":/app -w /app node:20-bookworm-slim \
    sh -c "npm ci && VITE_COMPANY_NAME=Baumer npm run build"
sudo rsync -a --delete frontend/dist/ /var/www/estoque/
```

Rode o backup antes de qualquer atualização que inclua migração nova.

**Trocar o logo dos relatórios**: substitua `backend/mss/branding/logo.png` e
`mss restart mss`. A pasta é montada do disco, então não precisa recompilar.

**Ver o consumo de memória**: `docker stats --no-stream`.

**Painel do RabbitMQ** (fechado para a internet, só por túnel SSH):

```bash
ssh -L 15672:127.0.0.1:15672 mss@SEU_IP
# e abra http://localhost:15672 no seu computador
```

---

## Quando algo der errado

| Sintoma | Causa provável |
|---|---|
| Toda chamada da API responde 404 | A barra final do `proxy_pass` do `/api/`. Sem ela o prefixo não é removido. |
| 413 ao enviar a ficha de consumo | `client_max_body_size` ausente no nginx. |
| A câmera não abre no celular | Página em http. Só origem segura libera a câmera. |
| O leitor abre mas não decodifica | Confira primeiro **qual lente** está aberta (o botão de troca de câmera) — foi a causa raiz da maior investigação do projeto. Depois confira se o `.wasm` chega como `application/wasm`. |
| Exportar planilha dá erro 500 | Falta `fontconfig` na imagem do `mss`. O `autoSizeColumn` do POI mede o texto com AWT e quebra sem fonte instalada. O Dockerfile já instala — se o erro aparecer, a imagem está velha: `mss build --no-cache mss`. |
| O convite nunca chega | O `mail` está de pé? Veja o passo 8. Depois `mss logs mail`. |
| Um contêiner fica reiniciando | `mss logs <serviço>`. Se for `OOMKilled` (`docker inspect <nome> --format '{{.State.OOMKilled}}'`), aumente o `mem_limit` dele e reduza o de outro. |
| O sistema fica lento durante importação | Esperado em 2 vCPU com mais de mil itens. Prefira importar fora do horário de cirurgia. |
| `nginx: [emerg] socket() [::]:80 failed` | VPS sem IPv6. Mantenha o `listen [::]:80;` comentado. |
| O leitor de código não decodifica no celular | Confira a lente (botão de troca de câmera). Depois, que o `.wasm` chegue como `application/wasm` — no nginx 1.18 do Ubuntu 22.04 isso depende do bloco `location ~ \.wasm$`. |

**Logs** de uma aplicação: `mss logs -f --tail 200 mss`.

---

## Memória

Os limites somam cerca de 3,1 GB, deixando perto de 900 MB para o sistema
operacional, o Docker e o nginx:

| Contêiner | Limite |
|---|---|
| mss | 1200 MB |
| gateway | 320 MB |
| eureka-server | 320 MB |
| mail | 320 MB |
| postgres-mss | 384 MB |
| postgres-mail | 160 MB |
| rabbitmq | 320 MB |

O `mss` leva a maior fatia porque é ele que importa as planilhas (o Apache POI
carrega o arquivo inteiro na memória) e gera os PDFs. Se um dia precisar de mais
folga, o caminho é tirar o `gateway` e o `eureka-server`: hoje nenhuma requisição
passa por eles — o nginx fala direto com o `mss`, como no desenvolvimento.
