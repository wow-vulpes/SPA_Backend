# SPA Inventory & Procurement Assistant

Инфраструктурный каркас MVP. Складские сущности и REST-эндпоинты появятся в следующих шагах.

## Требования

JDK 21, Docker с Compose и доступ к Maven Central. Maven Wrapper используется через `./mvnw`.

## Запуск

Скопируйте `.env.example` в `.env`, затем выполните:

```bash
docker compose up --build --wait --wait-timeout 120
curl http://localhost:8080/actuator/health
```

Для остановки: `docker compose down`. Данные хранятся в именованном томе.

## Проверки

```bash
./mvnw test
./mvnw verify
```

`test` не требует Docker. `verify` включает интеграционные проверки PostgreSQL через Testcontainers.

## Конфигурация

Параметры `APP_PORT`, `APP_TIME_ZONE`, `DB_URL`, `DB_NAME`, `DB_USER` и `DB_PASSWORD` описаны в `.env.example`. Секреты в Git не добавляются.

## Архитектура

Приложение будет разделено по функциям: catalog, movement, stock, forecast и alert. HTTP DTO, сценарии и persistence не смешиваются. Расчётные функции не будут зависеть от Spring или Hibernate. Текущая миграция — только техническая baseline.
