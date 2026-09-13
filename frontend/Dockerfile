# frontend — the React app, built once and served as static files.
#
# Two stages. Node builds the bundle; nginx serves it. The shipped image has no Node, no
# node_modules and no source - only the files a browser downloads.

# ---- build -----------------------------------------------------------------------------------
FROM node:22-alpine AS build
WORKDIR /app

# The gateway's public URL. Vite writes it into the JavaScript at build time - a browser has no
# environment to read it from later - so it is a build argument, not a runtime variable. Changing
# it means rebuilding. Railway passes a service variable of the same name to an ARG automatically.
ARG VITE_API_URL
ENV VITE_API_URL=$VITE_API_URL

# The lockfile first, so dependencies are reinstalled only when they change.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# ---- run -------------------------------------------------------------------------------------
FROM nginx:1.27-alpine

# The official image runs envsubst over /etc/nginx/templates/*.template at startup, so ${PORT} in
# the config becomes whatever port Railway assigned. 80 when nothing sets it.
ENV PORT=80
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
