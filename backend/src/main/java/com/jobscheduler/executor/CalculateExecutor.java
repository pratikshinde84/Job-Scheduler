package com.jobscheduler.executor;

import com.jobscheduler.entity.Job;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * CalculateExecutor
 *
 * Performs simple calculations as a background job.
 * Supported operations: SUM, AVERAGE, MIN, MAX, MULTIPLY
 *
 * Expected payload:
 * {
 *   "operation" : "SUM"           — one of: SUM | AVERAGE | MIN | MAX | MULTIPLY (required)
 *   "values"    : [10, 20, 30]    — list of numbers to operate on (required, min 1 element)
 * }
 */
@Slf4j
@Component
public class CalculateExecutor implements JobExecutor {

    @Override
    public String queueName() {
        return "Calculate";
    }

    @Override
    public void execute(Job job) throws Exception {
        Map<String, Object> payload = job.getPayload();

        String operation = getString(payload, "operation");
        if (operation == null || operation.isBlank()) {
            throw new IllegalArgumentException("CalculateExecutor: 'operation' field is required");
        }

        Object valuesRaw = payload.get("values");
        if (!(valuesRaw instanceof List<?> rawList) || rawList.isEmpty()) {
            throw new IllegalArgumentException(
                    "CalculateExecutor: 'values' must be a non-empty array of numbers");
        }

        List<Double> values = rawList.stream()
                .map(v -> {
                    if (v instanceof Number n) return n.doubleValue();
                    throw new IllegalArgumentException(
                            "CalculateExecutor: all 'values' elements must be numbers, got: " + v);
                })
                .toList();

        log.info("[CalculateExecutor] Job {} — operation={} values={}",
                job.getId(), operation, values);

        // Simulate computation time
        Thread.sleep(100);

        double result = switch (operation.toUpperCase()) {
            case "SUM"      -> values.stream().mapToDouble(Double::doubleValue).sum();
            case "AVERAGE"  -> values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            case "MIN"      -> values.stream().mapToDouble(Double::doubleValue).min().orElse(0);
            case "MAX"      -> values.stream().mapToDouble(Double::doubleValue).max().orElse(0);
            case "MULTIPLY" -> values.stream().mapToDouble(Double::doubleValue)
                                     .reduce(1.0, (a, b) -> a * b);
            default -> throw new IllegalArgumentException(
                    "CalculateExecutor: unsupported operation '" + operation +
                    "'. Supported: SUM, AVERAGE, MIN, MAX, MULTIPLY");
        };

        log.info("[CalculateExecutor] Job {} — Result of {} over {} = {}",
                job.getId(), operation.toUpperCase(), values, result);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String getString(Map<String, Object> payload, String key) {
        Object v = payload.get(key);
        return v != null ? v.toString() : null;
    }
}
