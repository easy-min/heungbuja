## Multi-stage build for Vite React (User)
# 1) Build stage
FROM node:18-alpine AS build
WORKDIR /app

# Install dependencies
COPY package*.json ./
RUN npm ci --no-audit --no-fund

# Copy sources and build
COPY . .

# Allow overriding API base at build-time
# ARG VITE_API_BASE_URL=/api
# ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}

RUN npm run build

# 2) Runtime stage: Nginx to serve static assets
FROM nginx:1.27-alpine AS runtime

# Install curl for healthcheck
RUN apk add --no-cache curl

# Copy nginx config for SPA routing
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Static files
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --retries=3 CMD curl -fsS http://localhost/health || exit 1

CMD ["nginx", "-g", "daemon off;"]

