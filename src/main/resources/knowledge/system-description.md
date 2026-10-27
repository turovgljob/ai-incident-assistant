Our payment platform consists of the following main services:

- api-gateway: receives external HTTP requests from clients and routes them to internal services.
- auth-service: authentication and token issuance.
- payment-service: creation and processing of payment transactions.
- billing-service: balance management and invoicing.
- notification-service: sending e-mail and SMS notifications.
- reporting-service: generating reports and exporting data.

General notes:
- All services write logs to a centralized log storage (ELK).
- The main database is PostgreSQL, with separate instances for payment-service and billing-service.
- Payments often experience external provider errors (timeout, 5xx, invalid credentials).
- notification-service may degrade when external SMTP/SMS providers have issues.
- reporting-service puts extra load on the DB with long analytical queries.
