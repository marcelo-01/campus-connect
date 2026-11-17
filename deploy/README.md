# Publicação na VPS

O CampusConnect roda em **https://campusconnect.maisvida.cloud**, na VPS.

## Como está montado

```
Internet ──443──▶ Caddy ──▶ campusconnect_web (nginx) ──/api──▶ campusconnect_api ──▶ campusconnect_banco
                 (HTTPS)     Angular + proxy              Spring Boot                  PostgreSQL 18
```

O Caddy não faz parte desta stack e atende outros sites da VPS. Por isso a stack do CampusConnect não publica
nenhuma porta e só mexe nos próprios contêineres.

| Peça | Onde |
| --- | --- |
| Código | `/opt/campus-connect/backend`, `frontend` e `deploy`, trocados a cada publicação |
| Segredos | `/opt/campus-connect/api.env` (`DB_PASSWORD`, `JWT_SECRET`) e `banco.env` (`POSTGRES_PASSWORD`), só no servidor |
| Nomes da infraestrutura | `/opt/campus-connect/deploy/.env`, só no servidor (veja abaixo) |
| Banco | volume `campusconnect_banco_dados` |
| Fotos e vídeos | volume `campusconnect_uploads` |
| HTTPS | bloco `campusconnect.maisvida.cloud` no `Caddyfile` do Caddy (cópia em `caddy-bloco.txt`) |
| Backup do banco | `deploy/backup_banco.sh`, em `/opt/backups/campusconnect-banco`, pelos últimos 14 dias. O cron ainda não está ligado (veja [Backup](#backup)) |

O `deploy/.env` guarda os nomes do que é compartilhado na VPS, para eles não ficarem no repositório. O compose lê
o `REDE_PROXY`, e os comandos deste guia leem as outras duas variáveis:

```
REDE_PROXY=<rede do Caddy>
CADDY_CONTEINER=<nome do contêiner do Caddy>
CADDYFILE=<caminho do Caddyfile no servidor>
```

O `publicar.sh` preserva esse arquivo e para com um aviso se ele não existir.

As redes:

- `REDE_PROXY`: a rede do Caddy. Só o `campusconnect_web` entra nela, para o Caddy alcançá-lo.
- `saida`: liga o web à API e dá acesso à internet, que a API usa para buscar a prévia dos links.
- `banco`: interna, sem internet. Só a API e o banco.

Os serviços do compose têm o mesmo nome dos contêineres (`campusconnect_web` em vez de `web`). Na rede do Caddy
pode haver outros serviços chamados `web` ou `api`, e um nome repetido faz o DNS do Docker responder com o
contêiner errado.

## Publicar uma versão nova

1. Rode os testes na sua máquina. O build das imagens pula os testes.
2. No Git Bash, na pasta do projeto:

   ```bash
   bash deploy/publicar.sh
   ```

   O script pede a senha do root, envia `backend/`, `frontend/` e `deploy/`, recompila as imagens e recria a API
   e o web. O CampusConnect fica fora do ar por alguns segundos, e nada fora desta stack é tocado.

O script envia o que está na pasta, inclusive as mudanças ainda sem commit. As migrations novas do Flyway rodam
sozinhas quando a API sobe.

## Primeiro administrador

O banco de produção começa vazio. Crie a sua conta pelo site e, na VPS:

```bash
docker exec campusconnect_banco psql -U campusconnect -d campusconnect \
  -c "UPDATE usuario SET administrador = TRUE WHERE email = 'voce@exemplo.com';"
```

A permissão vale a partir da próxima vez que a página for aberta ou recarregada.

## Diagnóstico

```bash
cd /opt/campus-connect/deploy && source .env
docker compose ps                            # estado dos três contêineres
docker logs campusconnect_api --tail 100     # erros da API, inclusive das migrations
docker logs campusconnect_web --tail 50      # acessos e erros do nginx
docker logs "$CADDY_CONTEINER" --tail 50     # certificado e HTTPS
```

| Sintoma | Onde olhar |
| --- | --- |
| Navegador diz que a conexão não é particular | O Caddy não conseguiu o certificado: os logs do Caddy |
| O site abre, mas nada carrega (erro 502 no `/api`) | A API caiu ou ainda está subindo: `docker logs campusconnect_api` |
| A API reinicia sem parar | Banco fora do ar ou senha diferente entre `api.env` e `banco.env` |

## Mexer no Caddyfile

O `Caddyfile` atende todos os sites da VPS, e um erro nele derruba todos. Se precisar mudar o bloco do
CampusConnect, rode antes `source /opt/campus-connect/deploy/.env` e então:

1. Faça uma cópia: `cp -p "$CADDYFILE" "$CADDYFILE.bak-$(date +%Y%m%d%H%M%S)"`.
2. Escreva a versão nova num arquivo à parte e valide dentro do contêiner, sem tocar na que está no ar:

   ```bash
   docker cp Caddyfile.novo "$CADDY_CONTEINER":/tmp/Caddyfile.novo
   docker exec "$CADDY_CONTEINER" caddy validate --config /tmp/Caddyfile.novo --adapter caddyfile
   ```

3. Se passar, copie o conteúdo **por cima** do arquivo atual com `cat Caddyfile.novo > "$CADDYFILE"`. O arquivo
   é montado sozinho no contêiner, e um `mv` ou `sed -i` cria um arquivo novo, que o contêiner não enxerga.
4. Recarregue sem derrubar ninguém:
   `docker exec "$CADDY_CONTEINER" caddy reload --config /etc/caddy/Caddyfile --adapter caddyfile`.

**Atenção (17/11/2026):** o contêiner do Caddy enxerga uma versão antiga do `Caddyfile`, de antes do bloco do
CampusConnect. O arquivo foi recriado no servidor depois que o contêiner subiu, e o contêiner continua preso ao
arquivo antigo. Por isso o bloco foi carregado por uma cópia (`docker cp` para `/tmp` e
`caddy reload --config /tmp/...`). Enquanto o Caddy não for reiniciado:

- um `caddy reload --config /etc/caddy/Caddyfile` volta para a versão antiga e tira o CampusConnect do ar;
- mudanças no `Caddyfile` do servidor não chegam ao Caddy pelo reload.

Um `docker restart -t 3 "$CADDY_CONTEINER"` corrige isso, porque o contêiner passa a ler o arquivo atual. Mas ele
derruba todos os sites da VPS por alguns segundos, então faça num horário de pouco uso. Depois, confira que
`docker exec "$CADDY_CONTEINER" md5sum /etc/caddy/Caddyfile` e `md5sum "$CADDYFILE"` dão o mesmo resultado.

## Backup

Para ligar a cópia diária às 4h, acrescente esta linha ao `crontab -e` do root:

```
0 4 * * * bash /opt/campus-connect/deploy/backup_banco.sh >> /var/log/campusconnect-backup.log 2>&1
```

Para uma cópia na hora: `bash /opt/campus-connect/deploy/backup_banco.sh`.

Para restaurar e para mais detalhes, veja os comentários no topo do `backup_banco.sh`. As fotos e os vídeos ficam
no volume `campusconnect_uploads`, fora dessa cópia.
