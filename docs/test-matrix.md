# Проверки

Полный запуск: `./mvnw -B -ntp clean verify`. Unit-тесты не требуют Docker;
интеграционные тесты используют PostgreSQL 17.11 через Testcontainers.
Последний локальный запуск: 43 unit + 168 integration, без ошибок и пропусков.
Enforcer, Spotless и SpotBugs пройдены; JaCoCo формирует отчёт покрытия.

| Требования | Что проверяется | Тесты |
| --- | --- | --- |
| TIME-01 | Внедряемый Clock, UTC/Moscow, бизнес-дата на границе суток | TimeConfigurationTest, StockApiIT, ForecastApiIT, AlertApiIT |
| DATA-01…05, DATA-08 | JPA round-trip, точные дроби, FK, уникальность, запрет NaN/нулей/невалидных реквизитов | InventoryPersistenceIT |
| DATA-06/07 | Суммы распределений без удвоения расхода, пустые позиции/партии, просрочка, разделение SKU/объектов | InventoryPersistenceIT, StockApiIT |
| DATA-09 | Блокировка позиции, конкуренция двух соединений, атомарный rollback | LedgerTransactionIT, MovementApiIT |
| DATA-10 | Чистая V1, повтор migrate, Flyway validate, Hibernate validate | MigrationIT, InventoryPersistenceIT |
| MOV-01/02 | Все пять типов, FEFO по сроку/дате/ID, дробные объёмы, исключение просрочки и будущих партий | FefoAllocatorTest, MovementApiIT |
| MOV-03 | Принадлежность партии, запрет отрицательного остатка, хронология, конкурирующий расход/поступление/документ | MovementApiIT |
| MOV-04 | 400/404/409/422, available_quantity при нехватке, дробный batch_id, нулевой символ и крайние даты без записи в БД | MovementApiIT, MovementServiceTest (Mockito) |
| HIST-01…03 | Фильтры, включительные даты, сортировка, limit/offset/total, пустые результаты, неверный ввод | MovementHistoryFilterTest, MovementHistoryApiIT |
| STOCK-01…04 | Окно 90 полных дней, только CONSUME, среднее и дни без раннего округления, нулевой/минимальный расход, детализация партий | StockMetricsTest, StockApiIT |
| FORECAST-01/02 | Календарные месяцы, високосный год, спрос/страховой запас/точка заказа, MOQ и упаковка, стоимость | ForecastCalculatorTest |
| FORECAST-03…05 | FEFO-симуляция, сроки и даты поставок, дефицит раньше поздней поставки, дата заказа, нехватка при нулевом агрегатном заказе | ForecastCalculatorTest, ForecastApiIT |
| FORECAST-03/04 | Последний RECEIPT на выбранном объекте, нет цены/позиции, отсутствуют побочные записи, валидация параметров и дат | ForecastApiIT |
| FORECAST-06 | name/unit, включительный period, числовые поля и совместимые псевдонимы, объект explanation, level у warnings | ForecastApiIT |
| ALERT-01…03 | Четыре типа, важность, исходные показатели, граничные сроки, отсутствие любых движений, стабильная пагинация предупреждений | AlertApiIT |
| ALERT-01/04 | Срок поставки из запроса или конфигурации; равенство/нулевой срок; точное сравнение при минимальном расходе | AlertApiIT, AlertConfigurationTest |
| DEMO-01…03 | Полный demo, FEFO-расход из двух партий, защита от дубля/нехватки, повтор загрузки и непустая база, откат ошибки последнего движения | DemoApiIT |
| RUN-01, DOC-01 | Readiness, чтение остатков/партий/истории/предупреждений, прогноз; воспроизводимый HTTP-сценарий | DemoApiIT, scripts/demo-smoke.py, Docker Compose |

Count и страница в читающих сервисах используют REPEATABLE_READ; отдельный
тест вставки между count и select отсутствует. Нагрузочное тестирование,
прогнозная точность на реальных данных и удалённый запуск GitHub Actions
не входят в приведённые результаты. HTTP-smoke проверяет небольшой demo,
не заменяет нагрузочные испытания. Автоматические тесты не обращаются
к постоянной базе и не используют H2.
