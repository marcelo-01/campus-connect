#!/usr/bin/env bash
# Publica o CampusConnect na VPS. Envia o código, cria os segredos na primeira vez, recompila as imagens e
# sobe os contêineres. Só a stack campus-connect é recriada; as outras aplicações da VPS não são tocadas.
#
# Rode no Git Bash, de qualquer pasta do projeto:
#   bash deploy/publicar.sh
# O acesso padrão é "ssh root@campusconnect.maisvida.cloud". Para usar outro:
#   VPS_SSH="ssh -i ~/.ssh/minha_chave root@campusconnect.maisvida.cloud" bash deploy/publicar.sh
#
# O build das imagens pula os testes. Rode antes ./mvnw test (backend) e npm test -- --watch=false (frontend).
set -euo pipefail

VPS_SSH=${VPS_SSH:-"ssh root@campusconnect.maisvida.cloud"}
DESTINO=/opt/campus-connect

cd "$(git rev-parse --show-toplevel)"

echo "==> Enviando o código para $DESTINO"
# Só backend/, frontend/ e deploy/, com o que o git conhece (versionado ou novo), sem o que o .gitignore
# exclui: nada de node_modules, target ou uploads. O docker-compose.yml da raiz é o de desenvolvimento e
# fica de fora, para ninguém subir na VPS um banco com porta aberta e senha padrão.
git -c core.quotepath=off ls-files -co --exclude-standard -- backend frontend deploy \
  | while IFS= read -r f; do if [ -f "$f" ]; then printf '%s\n' "$f"; fi; done \
  | tar -czf - -T - \
  | $VPS_SSH "mkdir -p $DESTINO/deploy && cd $DESTINO && rm -rf backend frontend \
      && find deploy -mindepth 1 -maxdepth 1 ! -name .env -exec rm -rf {} + && tar --no-same-owner -xzf -"

echo "==> Compilando as imagens e subindo os contêineres"
$VPS_SSH bash -s -- "$DESTINO" <<'REMOTO'
set -euo pipefail
cd "$1"
if [ ! -f api.env ]; then
  # Primeira publicação: senha do banco e chave do JWT aleatórias, legíveis só pelo root.
  umask 077
  senha=$(openssl rand -hex 24)
  printf 'DB_PASSWORD=%s\nJWT_SECRET=%s\n' "$senha" "$(openssl rand -base64 48 | tr -d '\n')" > api.env
  printf 'POSTGRES_PASSWORD=%s\n' "$senha" > banco.env
  echo "Segredos criados em $1/api.env e $1/banco.env"
fi
cd deploy
if [ ! -f .env ]; then
  echo "Falta $1/deploy/.env, com REDE_PROXY e os nomes do Caddy (veja deploy/README.md)." >&2
  exit 1
fi
docker compose build
docker compose up -d
docker compose ps
REMOTO
