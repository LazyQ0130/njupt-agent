FROM node:22-alpine AS build

WORKDIR /app

COPY package.json package-lock.json .npmrc ./
RUN npm ci

COPY index.html tsconfig.json vite.config.mjs ./
COPY src ./src
COPY public ./public
COPY scripts/prepare-sites-build.mjs ./scripts/prepare-sites-build.mjs
COPY worker/index.js ./worker/index.js
COPY .openai/hosting.json ./.openai/hosting.json

ARG VITE_API_BASE_URL=https://njupt-ai.top
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}

RUN npm run build

FROM nginx:1.27-alpine

COPY deploy/frontend-nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/client /usr/share/nginx/html

EXPOSE 80

