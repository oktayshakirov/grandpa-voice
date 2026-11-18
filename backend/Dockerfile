FROM node:20-alpine
WORKDIR /app
COPY package.json server.js ./
ENV NODE_ENV=production
USER node
EXPOSE 8787
CMD ["node", "server.js"]
