# Imagem do frontend: o build de produção do Angular servido por um nginx, que também
# repassa /api para a API (veja nginx.conf).
FROM node:26-slim AS build
WORKDIR /build
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
RUN npx ng build

FROM nginx:stable-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist/frontend/browser/ /usr/share/nginx/html/
