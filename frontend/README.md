# CampusConnect — frontend (Angular)

O visual é o do MVP (`marcelo-01/Campus-Connect-mvp`). O `src/styles.css` é o `styles.css` do MVP sem
alterações, mais seções no final: "Estados de formulário", com os estilos de erro que o MVP não tinha, e
"Ajustes sobre o MVP".

A `public/assets/logo.png` tem fundo transparente; no MVP o fundo creme fazia parte da imagem. O painel roxo
do login mostra só o nome "campusconnect.", sem o símbolo, porque o símbolo (índigo e tinta) não tem
contraste ali.

## Como rodar

A partir da raiz do projeto (`campus-connect/`), em três terminais:

```bash
docker compose up -d                           # PostgreSQL
cd backend && ./mvnw spring-boot:run           # API em http://localhost:8080
cd frontend && npm install && npm start        # http://localhost:4200
```

O `ng serve` repassa `/api` para o Spring (`proxy.conf.json`). Assim o navegador vê tudo na mesma
origem, e o cookie do refresh token (`SameSite=Strict`) funciona.

## Testes

```bash
npm test -- --watch=false
```

## Sessão

- O JWT de acesso fica **só em memória** (`AuthService`) e vale 15 min.
- O refresh token fica no cookie `cc_refresh`, que é HttpOnly: o JavaScript não consegue lê-lo. Ao abrir o
  app, `provideAppInitializer` tenta renovar a sessão; é isso que mantém a pessoa logada depois de um F5.
- O `authInterceptor` coloca o `Authorization: Bearer` nas chamadas a `/api/**`. Se receber 401, renova o
  JWT e repete a chamada uma vez.
