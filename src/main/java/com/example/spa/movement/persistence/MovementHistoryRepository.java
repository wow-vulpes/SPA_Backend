package com.example.spa.movement.persistence;

import com.example.spa.movement.*;
import jakarta.persistence.EntityManager;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MovementHistoryRepository {
  private final EntityManager entityManager;

  public MovementHistoryPage find(MovementHistoryFilter filter) {
    StringBuilder where =
        new StringBuilder(" from Movement m, InventoryPosition p where m.positionId = p.id");
    Map<String, Object> parameters = new LinkedHashMap<>();
    add(where, parameters, "p.sku", "sku", filter.sku(), "=");
    add(where, parameters, "p.locationCode", "location", filter.location(), "=");
    add(where, parameters, "m.operationType", "type", filter.type(), "=");
    add(where, parameters, "m.operationDate", "dateFrom", filter.dateFrom(), ">=");
    add(where, parameters, "m.operationDate", "dateTo", filter.dateTo(), "<=");
    var count = entityManager.createQuery("select count(m.id)" + where, Long.class);
    var rows =
        entityManager.createQuery(
            """
        select new com.example.spa.movement.MovementHistoryEntry(
          m.id, p.sku, p.locationCode, m.operationDate, m.operationType, m.quantity, m.documentNumber)
        """
                + where
                + " order by m.operationDate desc, m.id desc",
            MovementHistoryEntry.class);
    parameters.forEach(
        (name, value) -> {
          count.setParameter(name, value);
          rows.setParameter(name, value);
        });
    long total = count.getSingleResult();
    var items = rows.setFirstResult(filter.offset()).setMaxResults(filter.limit()).getResultList();
    return new MovementHistoryPage(items, total, filter.limit(), filter.offset());
  }

  private void add(
      StringBuilder where,
      Map<String, Object> parameters,
      String field,
      String name,
      Object value,
      String operator) {
    if (value != null) {
      where.append(" and ").append(field).append(' ').append(operator).append(" :").append(name);
      parameters.put(name, value);
    }
  }
}
