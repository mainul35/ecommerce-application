Start the full local development environment for this project.

Steps:
1. Check Docker is running: `docker info`
2. Start backing services (PostgreSQL): `docker-compose up -d`
3. Wait for the database to be ready, then start the backend: `./mvnw spring-boot:run`
4. In a separate process, start the frontend: `npm run dev` (inside `frontend/`)
5. Confirm both are healthy:
   - Backend: GET http://localhost:8080/api/products
   - Frontend: http://localhost:5173
6. Report the URLs and any startup warnings.
