package neobank;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.*;

import java.time.Instant;
import java.util.*;

public class AnalyticsHandler implements RequestHandler<SQSEvent, SQSBatchResponse> {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final CloudWatchClient cloudWatch = CloudWatchClient.create();
    private static final String NAMESPACE = "NeoBank/Transactions";

    @Override
    public SQSBatchResponse handleRequest(SQSEvent event, Context context) {
        List<SQSBatchResponse.BatchItemFailure> failures = new ArrayList<>();

        for (SQSEvent.SQSMessage message : event.getRecords()) {
            try {
                Map<String, Object> transaction = objectMapper.readValue(
                        message.getBody(),
                        Map.class
                );

                publishMetrics(transaction, context);

                context.getLogger().log("Metrics published for: " + transaction.get("transaction_id"));

            } catch (Exception e) {
                context.getLogger().log("Error publishing metrics: " + e.getMessage());
                e.printStackTrace();
                failures.add(SQSBatchResponse.BatchItemFailure.builder()
                        .withItemIdentifier(message.getMessageId())
                        .build());
            }
        }

        context.getLogger().log(String.format(
                "Metrics published. Success: %d, Failures: %d",
                event.getRecords().size() - failures.size(), failures.size()
        ));

        return SQSBatchResponse.builder().withBatchItemFailures(failures).build();
    }

    private void publishMetrics(Map<String, Object> transaction, Context context) {
        double amount = getDouble(transaction, "amount");
        String status = getString(transaction, "status");
        String type = getString(transaction, "type", "TRANSFER");

        List<MetricDatum> metrics = new ArrayList<>();

        MetricDatum countMetric = MetricDatum.builder()
                .metricName("TransactionCount")
                .value(1.0)
                .unit(StandardUnit.COUNT)
                .timestamp(Instant.now())
                .dimensions(
                        Dimension.builder().name("Type").value(type).build(),
                        Dimension.builder().name("Status").value(status).build()
                )
                .build();
        metrics.add(countMetric);

        MetricDatum volumeMetric = MetricDatum.builder()
                .metricName("TransactionVolume")
                .value(amount)
                .unit(StandardUnit.NONE)
                .timestamp(Instant.now())
                .dimensions(
                        Dimension.builder().name("Currency").value("MXN").build()
                )
                .build();
        metrics.add(volumeMetric);

        double successValue = "COMPLETED".equals(status) ? 100.0 : 0.0;
        MetricDatum successMetric = MetricDatum.builder()
                .metricName("SuccessRate")
                .value(successValue)
                .unit(StandardUnit.PERCENT)
                .timestamp(Instant.now())
                .build();
        metrics.add(successMetric);

        double avgAmount = calculateAverageTransactionAmount(amount);
        MetricDatum avgMetric = MetricDatum.builder()
                .metricName("AverageTransactionAmount")
                .value(avgAmount)
                .unit(StandardUnit.NONE)
                .timestamp(Instant.now())
                .build();
        metrics.add(avgMetric);

        try {
            PutMetricDataRequest request = PutMetricDataRequest.builder()
                    .namespace(NAMESPACE)
                    .metricData(metrics)
                    .build();

            cloudWatch.putMetricData(request);

            context.getLogger().log(String.format(
                    "Published %d metrics to CloudWatch", metrics.size()
            ));

        } catch (Exception e) {
            context.getLogger().log("Failed to publish to CloudWatch: " + e.getMessage());
            throw e;
        }
    }

    double calculateAverageTransactionAmount(double currentAmount) {
        return currentAmount;
    }

    String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : "";
    }

    String getString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    double getDouble(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return 0.0;
    }
}