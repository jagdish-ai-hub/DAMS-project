# Stage 1: build the static bundle
FROM node:22-alpine AS build
WORKDIR /app

COPY package.json package-lock.json* ./
# Prefer a reproducible install; fall back if the lockfile hasn't been committed yet
RUN npm ci --no-audit --no-fund || npm install --no-audit --no-fund

COPY . .
# Baked in at build time — the browser bundle talks to this origin
ARG VITE_API_URL=http://localhost:8080
ENV VITE_API_URL=$VITE_API_URL
RUN npm run build

# Stage 2: serve with nginx (no Node in the runtime image)
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
