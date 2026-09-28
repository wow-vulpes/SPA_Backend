# Матрица проверок

Локальный `./mvnw -B -ntp spotless:apply verify` от 28.09.2026: успешно,
2 unit-теста и 34 интеграционных сценария, 0 ошибок, 0 пропусков.
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

Контекст Spring в интеграционных тестах запускается без HTTP-сервера.
InfrastructureIT из ранней документации шага 1 отсутствовал; эта строка
исправлена, успешные HTTP-интеграционные тесты не заявляются.
Проведение FEFO, недостаток/конкурентный расход и HTTP-статусы — шаг 3.
