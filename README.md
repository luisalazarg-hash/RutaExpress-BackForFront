# RutaExpress BFF

Backend for Frontend del portal web de RutaExpress. Expone un unico origen HTTP para el frontend en `http://localhost:8080` y enruta las solicitudes hacia los microservicios internos.

## Requisitos

- Java 21
- Maven 3.9+ o Maven Wrapper
- Microservicios de RutaExpress ejecutandose en sus puertos configurados

## Ejecucion local

```powershell
mvn spring-boot:run
```

El health check queda disponible en `http://localhost:8080/actuator/health`.

En desarrollo, si `AZURE_ISSUER_URI` no esta definido, las rutas quedan abiertas para facilitar las pruebas locales. En ambientes reales se debe definir `AZURE_ISSUER_URI`; entonces el BFF validara los JWT de Azure AD antes de reenviarlos.

## Rutas

| Ruta publica | Servicio por defecto |
| --- | --- |
| `/api/shipments/**` | `http://localhost:8082` |
| `/api/catalog/**` | `http://localhost:8081` |
| `/api/notifications/**` | `http://localhost:8083` |
| `/api/audit/**` | `http://localhost:8084` |
| `/api/reports/**` | `http://localhost:8085` |
| `/api/rabbitmq/**` | `http://localhost:8086` |
| `/api/kafka/**` | `http://localhost:8087` |

Cada destino puede cambiarse con variables como `SERVICES_SHIPMENTS_URL` y `SERVICES_CATALOG_URL`. Para Docker, usa los nombres de servicio de la red de Compose, por ejemplo `http://shipments:8082`.

## Frontend

Configura `VITE_API_URL=http://localhost:8080` en el `.env` del frontend. El BFF conserva la ruta `/api/shipments` y reenvia el header `Authorization` al microservicio de destino.