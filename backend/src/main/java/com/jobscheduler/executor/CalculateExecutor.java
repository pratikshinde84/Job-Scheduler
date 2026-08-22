package com.jobscheduler.executor;

import com.jobscheduler.entity.Job;
import com.jobscheduler.service.JobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CalculateExecutor
 *
 * Performs simple calculations as a background job.
 * After computing, stores the result in job.result (JSONB) so the frontend
 * can display it in the expanded job row.
 *
 * Expected payload:
 * {
 *   "operation" : "SUM"        -- SUM | AVERAGE | MIN | MAX | MULTIPLY (required)
 *   "values"    : [10, 20, 30] -- list of numbers (required, min 1 element)
 * }
 *
 * Stored result:
 * {
 *   "operation" : "SUM",
 *   "values"    : [10.0, 20.0, 30.0],
 *   "result"    : 60.0
 * }
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CalculateExecutor implements JobExecutor {

    private final JobService jobService;

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

        log.info("[CalculateExecutor] Job {} -- operation={} values={}",
                job.getId(), operation, values);

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

        log.info("[CalculateExecutor] Job {} -- {} of {} = {}",
                job.getId(), operation.toUpperCase(), values, result);

        // Persist result so the frontend can display it
        Map<String, Object> resultMap = new LinkedHashMap<>();
        resultMap.put("operation", operation.toUpperCase());
        resultMap.put("values",    values);
        resultMap.put("result",    result);
        jobService.storeResult(job.getId(), resultMap);
    }

    private String getString(Map<String, Object> payload, String key) {
        Object v = payload.get(key);
        return v != null ? v.toString() : null;
    }
}
