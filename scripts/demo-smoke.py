#!/usr/bin/env python3
"""Read-only HTTP checks for a freshly initialized demo. Python standard library only."""

import argparse
import json
import time
import urllib.request
from decimal import Decimal


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--url", default="http://localhost:8080")
    args = parser.parse_args()
    base = args.url.rstrip("/")

    def request(path, body=None):
        payload = None if body is None else json.dumps(body).encode()
        req = urllib.request.Request(base + path, data=payload,
                                     headers={"Content-Type": "application/json"})
        started = time.perf_counter()
        with urllib.request.urlopen(req, timeout=15) as response:
            result = json.loads(response.read(), parse_float=Decimal)
        elapsed = time.perf_counter() - started
        print(f"{req.get_method()} {path}: {elapsed:.3f}s")
        return result

    def check(condition, message):
        if not condition:
            raise RuntimeError(message + "; use a fresh demo database on its initialization date")

    check(request("/actuator/health/readiness")["status"] == "UP", "Application is not ready")
    stock = request("/api/stock?sku=DEMO-OIL&location=DEMO-CENTER")["items"]
    check(len(stock) == 1, "Demo oil position missing")
    check(stock[0]["available_stock"] == 20, "Expected available oil stock 20")
    check(stock[0]["average_daily_consumption"] == 1, "Expected average consumption 1")
    check(len(request("/api/stock/DEMO-OIL")["locations"]) == 2, "Expected two oil locations")
    history = request("/api/movements?sku=DEMO-OIL&location=DEMO-CENTER&type=consume&limit=1")
    check(history["total"] == 90, "Expected 90 daily consume operations")
    alerts = request("/api/alerts")
    check({item["type"] for item in alerts["items"]} ==
          {"SHORTAGE", "EXPIRED", "EXPIRING", "NO_MOVEMENT"}, "Expected all four alert types")
    forecast = request("/api/forecast", {
        "sku": "DEMO-OIL", "location": "DEMO-CENTER", "horizon_months": 1,
        "lead_time_days": 3, "safety_days": 5,
        "minimum_order_quantity": 0, "pack_size": 10,
    })
    check(forecast["recommended_quantity"] == 20, "Expected procurement quantity 20")
    check(forecast["estimated_cost"] == 5000, "Expected procurement cost 5000 RUB")
    check(forecast["recommended_purchase_qty"] == forecast["recommended_quantity"],
          "Forecast contract quantity mismatch")
    check(forecast["name"] and forecast["unit"], "Product metadata missing")
    check(set(forecast["period"]) == {"from", "to", "days"}, "Period contract mismatch")
    check(set(forecast["explanation"]) == {"data_used", "formulas", "assumptions", "as_of"},
          "Explanation contract mismatch")
    check(all(w["level"] in {"info", "warning", "critical"} for w in forecast["warnings"]),
          "Warning severity missing")
    print("Demo checks passed; no movements or orders were written.")


if __name__ == "__main__":
    main()
