# Матрица проверок

Локальный `./mvnw -B -ntp spotless:apply verify` от 28.09.2026: успешно,
15 unit-тестов и 89 интеграционных сценариев, 0 ошибок, 0 пропусков.
PostgreSQL 17.11 через Testcontainers 2.0.5. CI на GitHub в этом шаге не запускался.

| Требование | Сценарии | Тесты | Результат |
| --- | --- | --- | --- |
| TIME-01 | Clock с UTC и Europe/Moscow | TimeConfigurationTest (2) | Пройдены; неверная зона и binding default пока не покрыты |
| DATA-01/02/03/08 | JPA round-trip всех сущностей, точные дроби, цена/дата/накладная | InventoryPersistenceIT.persistsAndReloadsEntireLedgerWithoutLosingDecimalPrecision | Пройден |
| DATA-04 | Невалидное количество, включая CORRECTION=0 и NaN; неизвестный тип; пустой/необрезанный документ; отрицательная цена | InventoryPersistenceIT.rejectsInvalidMovementQuantities (8), rejectsUnknownOperation, rejectsEmptyOrUntrimmedDocument (2), rejectsNegativePrice | Пройдены |
| DATA-01/02/03 | Дубли позиции, партии и документа между объектами | InventoryPersistenceIT.rejectsDuplicatePosition, rejectsDuplicateBatchWithinPosition, rejectsDuplicateDocumentAcrossPositions | Пройдены |
| DATA-05 | Неизвестные ссылки, несовпадение позиции по обоим FK, дубль/ноль распределения, удаление используемой партии | InventoryPersistenceIT.rejectsUnknownProduct, rejectsUnknownLocation, rejectsAllocationToDifferentPosition, rejectsAllocationWithForgedMovementPosition, rejectsDuplicateAllocation, rejectsZeroAllocation, cannotDeleteBatchWithLedgerEntries | Пройдены |
| DATA-06 | Все пять типов; расход из двух партий без двойного учёта; пустые данные; изоляция SKU/объектов | InventoryPersistenceIT.sumsAllOperationTypesFromAllocationsOnly, splitMovementDoesNotDoubleCountHeaderQuantity, emptyPositionAndEmptyBatchHaveZeroStock, isolatesProductsAndLocations | Пройдены |
| DATA-07 | Срок вчера/сегодня/завтра; весь остаток просрочен; стабильный порядок партий | InventoryPersistenceIT.expiryFiltersAvailabilityButDoesNotErasePhysicalStock, ordersBatchesByExpiryThenReceiptDateThenId | Пройдены |
| DATA-09 | Цель блокировки без партий, отсутствующая позиция | InventoryPersistenceIT.exposesLockTargetEvenBeforeAnyBatchesExist | Пройден |
| DATA-09 | Два соединения: конфликт блокировки и получение после commit; rollback движения/распределений/партии | LedgerTransactionIT (2) | Пройдены |
| DATA-10 | Пустая БД, Hibernate validate, V1→V2 с сохранением данных, повтор migrate | InventoryPersistenceIT.migratesEmptyDatabaseAndValidatesHibernateMappings, MigrationIT.upgradesPublishedBaselineAndKeepsExistingData | Пройдены |
| QA-01 | Компиляция, Enforcer, Spotless, SpotBugs; JaCoCo отчёт | Maven verify | Пройдены |

## Добавлено в шаге 3

| Требование | Проверка | Результат |
| --- | --- | --- |
| MOV-02 | FefoAllocatorTest (3): порядок срока/поступления/ID, дробное распределение, просрочка, будущая партия, нулевой остаток, нехватка | Пройдены |
| MOV-04 | MovementServiceTest (1), Mockito: неизвестный SKU не вызывает дальнейших обращений и записей | Пройден |
| MOV-01/02 | MovementApiIT: пять типов, несколько партий, физический/доступный остаток, возврат и списание просрочки | Пройдены |
| MOV-03 | MovementApiIT: прошлые даты, нехватка конкретной партии, принадлежность партии, откат первой позиции | Пройдены |
| MOV-03/04 | MovementApiIT: конкурентные расходы, первые поступления, одинаковый документ на разных объектах | Пройдены |
| MOV-04/DATA-08 | MovementApiIT: 11 вариантов неверного ввода, неизвестные ссылки, повреждённый JSON, повтор документа и реквизиты партии | Пройдены |

MovementApiIT содержит 21 сценарий с настоящим HTTP-сервером и PostgreSQL.
Прежние 34 интеграционных проверки сохранены. Unit-тесты не требуют Docker.
Нагрузочный тест, удалённый CI и production-развёртывание в этот шаг не входят.

## Добавлено в шаге 4

| Требование | Проверка | Результат |
| --- | --- | --- |
| HIST-01/03 | MovementHistoryFilterTest (9): defaults, нормализация, границы и невалидный limit | Пройдены |
| HIST-01 | MovementHistoryApiIT: отдельные/совместные фильтры, все 5 типов, точность количества и поля ответа | Пройдены |
| HIST-02 | Две включительные границы и односторонние периоды, дата/ID по убыванию, split расход представлен один раз | Пройдены |
| HIST-02/03 | offset не кратен limit, total до пагинации, offset за концом, maximum limit, пустая БД | Пройдены |
| HIST-03 | 19 ошибочных запросов, пустые параметры, неизвестные SKU/объекты, SQL-подобный текст, чувствительность ключей к регистру | Пройдены |

MovementHistoryApiIT: 34 сценария, настоящий HTTP и PostgreSQL. Прежние
55 интеграционных сценариев проходят без изменения. Snapshot-согласованность
обеспечивается REPEATABLE_READ; отдельного теста вставки между count и select нет.
