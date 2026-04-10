# ─── Stage 1: Build ───────────────────────────────────────────────────────────
FROM node:22-alpine AS build

WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build

# ─── Stage 2: Serve ───────────────────────────────────────────────────────────
FROM nginx:stable-alpine

COPY --from=build /app/dist /usr/share/nginx/html

# SPA fallback: route all 404s to index.html so React Router handles paths
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
