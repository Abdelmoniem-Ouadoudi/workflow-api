# Codespaces quick start

1. Open this repository in GitHub Codespaces.
2. In the repository root, start infra only:

```bash
docker compose up -d db rabbitmq
```

3. Start backend services from `services/` in this order:

```bash
cd services/discovery-service && ./mvnw spring-boot:run
cd services/work-service && ./mvnw spring-boot:run
cd services/auth-service && ./mvnw spring-boot:run
cd services/classification-service && ./mvnw spring-boot:run
cd services/gateway && ./mvnw spring-boot:run
```

4. In another terminal, start the frontend from `frontend/` using existing npm scripts:

```bash
cd frontend
npm install
npm run dev
```

5. Use the gateway on forwarded port **8090** as the application entry point.

## Ports forwarded in this dev container

- 5173 (frontend)
- 8090 (gateway)
- 8761 (discovery)
- 8081 (work-service)
- 8082 (auth-service)
- 8083 (classification-service)
- 5433 (PostgreSQL)
- 5672 (RabbitMQ)
- 15672 (RabbitMQ UI)

## Port visibility and secrets

- Keep ports private by default in Codespaces. Switch to **Public** only when external access is required.
- Never put secrets in files or source code.
- For real AI classification, define `GROQ_API_KEY` and `CLASSIFICATION_PROVIDER=groq` in Codespaces secrets/environment variables.
- Without a key, the default `stub` provider still works.
