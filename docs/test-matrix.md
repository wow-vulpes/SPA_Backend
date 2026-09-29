# Матрица проверок

Локальный `./mvnw -B -ntp spotless:apply verify` от 29.09.2026: успешно,
35 unit-тестов и 137 интеграционных сценариев, 0 ошибок, 0 пропусков.
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
| DATA-10 | Пустая БД, Hibernate validate, единая V1, повтор migrate | InventoryPersistenceIT.migratesEmptyDatabaseAndValidatesHibernateMappings, MigrationIT.appliesSingleSchemaMigrationAndIsIdempotent | Пройдены |
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

## Ревью и шаг 5

| Требование | Сценарии | Проверка |
| --- | --- | --- |
| MOV-04 | Дробный batch_id не усекается и не создаёт движение | MovementApiIT.rejectsFractionalBatchIdWithoutTruncatingIt, пройден |
| STOCK-01/04 | Среднее и дни, нулевой спрос, нулевой остаток, дробный и минимальный спрос | StockMetricsTest, 6 сценариев пройдены |
| STOCK-01/04/TIME-01 | 90 дней: начало включено, 91-й день и сегодня исключены; только CONSUME; split не удваивается; бизнес-дата отличается от UTC | StockApiIT.computesWindowBalancesAndBusinessDateWithoutJoinMultiplication, пройден |
| STOCK-02 | Цены, накладные, порядок партий, просроченные и пустые партии; сумма совпадает со списком | StockApiIT.detailAgreesWithListAndIncludesZeroAndExpiredBatches, пройден |
| STOCK-01/02/04 | Нулевой расход, пустая позиция, товар без позиций, неизвестный SKU, всё просрочено, нулевой остаток | StockApiIT.noDemandEmptyPositionsAndUnstockedProducts, expiredOnlyStockRemainsPhysicalButHasZeroCoverage, пройдены |
| STOCK-03 | Фильтры, пагинация, неизвестные коды, SQL-подобная строка, 10 вариантов невалидного ввода | StockApiIT, пройдены |
| STOCK-01/02 | POST поступления → GET список → GET детали; пустая БД; GET не создаёт движений | StockApiIT, пройдены |

StockApiIT — 22 сценария на реальном HTTP/PostgreSQL. Общий набор: 21 unit,
112 integration. Enforcer, Spotless, SpotBugs, JaCoCo и git diff --check пройдены.
Docker-образ собран; отдельный Compose-проект spa-review-20260929 прошёл
healthcheck и сквозную проверку поступления, списка/деталей остатков, истории,
отклонения дробного ID. Пользовательская БД не использовалась.
Удалённый CI и нагрузочные измерения не выполнялись.

## Шаг 6

| Требование | Сценарии | Проверка |
| --- | --- | --- |
| FORECAST-01/02 | Прогноз, страховой запас, точка заказа, MOQ, кратность упаковке, стоимость | ForecastCalculatorTest.computesDemandSafetyReorderMoqPackAndCost, пройден |
| FORECAST-01 | Месяц с 31-го числа, високосный февраль, квартал/полугодие/год | ForecastCalculatorTest.usesCalendarMonths (5), пройдены |
| FORECAST-02/03 | Нет потребности — нет MOQ-заказа; нет расхода/цены — null даты/стоимость; минимальный расход сохраняется | ForecastCalculatorTest (3 сценария), пройдены |
| FORECAST-03/05 | Включённый срок годности, FEFO, поздняя/сегодняшняя/граничная поставка, дата будущего заказа и риск lead time | ForecastCalculatorTest (5 сценариев), пройдены |
| FORECAST-01/03 | Реальный PostgreSQL: расход/остаток/последний RECEIPT старой партии, цена выбранного объекта, расчёт ничего не записывает | ForecastApiIT.computesFromLedgerAndLatestReceiptEventAtSelectedLocation, пройден |
| FORECAST-03/04/05 | Поставки из запроса не сохраняются; нет позиции/цены; просрочка; все 4 горизонта; неизвестные ссылки | ForecastApiIT, пройдены |
| FORECAST-04 | 14 вариантов ошибок: обязательные поля, горизонт, дробные дни, MOQ/упаковка, пустой ключ, дата/количество/null элемента, лимит списка | ForecastApiIT.rejectsInvalidParameters, пройдены |

Новые проверки: 14 unit и 25 HTTP/PostgreSQL. Итого 35 unit + 137 integration,
без ошибок/пропусков. Полная verify включает Spotless/SpotBugs/Enforcer и JaCoCo.
Docker Compose отдельно в шаге 6 не поднимался: API проверен встроенным HTTP
сервером в интеграционных тестах. Контейнеры и процессы тестов после завершения
проверены: работающих не осталось. Удалённый CI и нагрузочные тесты не выполнялись.
