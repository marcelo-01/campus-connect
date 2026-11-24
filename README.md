# CampusConnect

Rede social acadêmica e marketplace universitário. Projeto Integrador do curso de Análise e Desenvolvimento de
Sistemas do IFBA, campus Eunápolis (2026).

A rede é organizada em **bolhas** hierárquicas que seguem a estrutura de uma universidade: a bolha Geral, as de
Campus dentro dela e as de Curso dentro de cada campus. Comunidades podem ficar abaixo de qualquer uma delas. Nas
bolhas, as pessoas publicam, conversam, se conectam e anunciam serviços e produtos no marketplace.


## O que já funciona

| Requisito | O que tem |
| --- | --- |
| RF01 · Perfil | Cadastro aberto com confirmação do e-mail por um código de 6 números, login, foto, capa, bio, link e habilidades |
| RF02 · Postar | Texto, até 4 imagens ou 1 vídeo (MP4/WebM, até 50 MB) e link com prévia de título e imagem |
| RF03 · Interagir | Curtir, comentar (com respostas), salvar, editar, republicar e aviso de posts novos no topo do feed |
| RF04 · Conexões | Pedidos, aceite, recusa discreta e lista de conexões no perfil |
| RF05 · Mensagens | Conversa direta em tempo real (SSE), bloqueio e limite contra spam |
| RF06 · Bolhas | Busca por nome ou sigla, entrar e sair. Entrar numa bolha inclui as de cima; sair inclui as de baixo |
| RF07 · Hierarquia | Tipos Geral, Campus, Curso e Comunidade. Só administradores criam, editam e excluem bolhas |
| RF08 · Direcionar posts | O post desce pela hierarquia e nunca sobe: um post na Geral aparece em todas as sub-bolhas. No Início dá para filtrar por bolha |
| RF09 a RF12 · Marketplace | Anúncios de serviço ou produto em 1 a 5 bolhas, até 6 fotos, categorias, favoritos, avaliações e pausa |
| Notificações | Pedido de conexão e aceite, comentário, resposta, curtida, republicação e avaliação de anúncio, em tempo real. Curtidas e republicações do mesmo alvo vêm juntas ("Ana e mais 3 pessoas curtiram") |


## Tecnologias

- **Backend:** Java 25, Spring Boot 4.1, Spring Modulith 2.1, Spring Security (JWT de acesso, refresh token em
  cookie HttpOnly e senhas com Argon2), Spring Data JPA, Flyway e PostgreSQL 18.
- **Frontend:** Angular 22 e TypeScript 6.
- **Testes:** JUnit com Testcontainers no backend e Vitest no frontend.

## Estrutura

```
campus-connect/
├── backend/             API Spring Boot
│   └── src/main/resources/db/migration/   migrations do Flyway (V1 a V22)
├── frontend/            app Angular (veja frontend/README.md)
├── deploy/              publicação na VPS (veja deploy/README.md)
└── docker-compose.yml   PostgreSQL e Mailpit de desenvolvimento
```

O backend é um monólito modular. Cada pacote em `backend/src/main/java/br/com/campusconnect/` é um módulo, e o
`package-info.java` de cada um declara de quais outros ele pode depender. Um módulo só usa o pacote `service` de
outro. O teste `ArquiteturaModularTest` falha se alguém furar essas fronteiras.

| Módulo | Responsabilidade |
| --- | --- |
| `usuario` | Contas, verificação do e-mail, sessão (login, refresh, logout) e perfil |
| `bolha` | Bolhas, hierarquia entre elas e membros |
| `conexao` | Conexões entre pessoas e bloqueio |
| `publicacao` | Posts, mídias, comentários, curtidas, salvos, republicações e prévia de links |
| `conversa` | Conversas e mensagens privadas |
| `marketplace` | Anúncios, categorias, avaliações, favoritos e impulsionamentos |
| `arquivo` | Imagens e vídeos enviados: validação, gravação em disco e entrega |
| `aovivo` | Eventos em tempo real por SSE, usados pelas mensagens e pelas notificações |
| `notificacao` | Notificações, criadas a partir dos eventos dos módulos de conexões, publicações e marketplace |
| `email` | E-mails mandados pela API, por enquanto só o código de verificação do cadastro |
| `moderacao` | Denúncias (só o modelo, por enquanto) |
| `comum` | Tipos base e tratamento de erros compartilhados |

## Como rodar

Você vai precisar de:

- JDK 25;
- Node.js 22.22+, 24.15+ ou 26+;
- Docker.

Na raiz do projeto, em três terminais:

```bash
# 1. Banco de dados e caixa de e-mail (Mailpit)
docker compose up -d

# 2. API em http://localhost:8080 (o Flyway aplica as migrations ao subir)
cd backend
./mvnw spring-boot:run          # no PowerShell ou no cmd: .\mvnw.cmd spring-boot:run

# 3. Frontend em http://localhost:4200
cd frontend
npm install
npm start
```

O `ng serve` repassa as chamadas a `/api` para a API (`frontend/proxy.conf.json`). Assim o navegador vê tudo na
mesma origem e o cookie do refresh token funciona. Abra pelo endereço `http://localhost:4200`.

O cadastro manda um código de 6 números para o e-mail, e a conta só entra depois de digitá-lo. Em desenvolvimento,
nenhum e-mail sai de verdade: o Mailpit do `docker-compose.yml` recebe todos, e eles aparecem em
`http://localhost:8025`.

### Primeiro administrador

Só administradores criam bolhas, e não há tela para promover alguém. Crie a sua conta pelo cadastro e rode:

```bash
docker exec campusconnect-postgres psql -U campusconnect -d campusconnect \
  -c "UPDATE usuario SET administrador = TRUE WHERE email = 'voce@exemplo.com';"
```

A permissão vale a partir da próxima vez que a página for aberta ou recarregada.

## Configuração

A API lê estas variáveis de ambiente. Os valores padrão servem para desenvolvimento.

| Variável | Padrão | Para que serve |
| --- | --- | --- |
| `DB_HOST` | `localhost` | Host do PostgreSQL |
| `DB_PORT` | `5432` | Porta do PostgreSQL |
| `DB_NAME` | `campusconnect` | Nome do banco |
| `DB_USER` | `campusconnect` | Usuário do banco |
| `DB_PASSWORD` | `campusconnect` | Senha do banco |
| `JWT_SECRET` | vazio | Chave HMAC do JWT, em Base64, com pelo menos 32 bytes. Vazia, a API gera uma chave nova a cada subida e o frontend renova a sessão sozinho |
| `COOKIE_SEGURO` | `true` | Marca o cookie do refresh token como `Secure`. Os navegadores aceitam isso em `http://localhost` |
| `ARQUIVOS_DIR` | `uploads` | Pasta dos arquivos enviados, relativa à pasta onde a API sobe |
| `SMTP_HOST` | `localhost` | Servidor SMTP dos e-mails. O padrão é o Mailpit |
| `SMTP_PORT` | `1025` | Porta do SMTP |
| `SMTP_USUARIO` e `SMTP_SENHA` | vazios | Login no SMTP. O Mailpit não pede |
| `SMTP_TLS` | `false` | Liga o STARTTLS e o login no SMTP, obrigatórios em produção |
| `EMAIL_REMETENTE` | `CampusConnect <nao-responda@campusconnect.localhost>` | O "De:" dos e-mails |

O `docker-compose.yml` usa as mesmas `DB_NAME`, `DB_USER`, `DB_PASSWORD` e `DB_PORT`. Durações da sessão, limites
de upload, limites de mensagens, o tempo das conexões SSE e as regras do código de verificação (validade, tentativas
e reenvio) ficam em `backend/src/main/resources/application.yml`.

## Publicação

O app está em **https://campusconnect.maisvida.cloud**. Para publicar uma versão nova, rode os testes e, no Git
Bash, `bash deploy/publicar.sh`. Como a VPS está montada e o que fazer quando algo dá errado estão em
[`deploy/README.md`](deploy/README.md).

## Testes

```bash
# Backend: precisa do Docker ligado. O Testcontainers sobe um PostgreSQL próprio,
# então os testes não mexem no banco do docker compose.
cd backend
./mvnw test

# Frontend
cd frontend
npm test -- --watch=false
```