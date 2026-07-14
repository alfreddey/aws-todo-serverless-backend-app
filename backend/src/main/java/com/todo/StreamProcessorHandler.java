package com.todo;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.todo.util.Json;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

/**
 * DynamoDB Streams processor: when a Pending task is completed or deleted before its
 * deadline, enqueue a cancellation message on the SQS FIFO queue so its scheduled
 * expiry can be removed. Parses the stream record JSON directly to stay on AWS SDK v2.
 */
public class StreamProcessorHandler implements RequestStreamHandler {

    private static final String STATUS_PENDING = "Pending";
    private static final String STATUS_COMPLETED = "Completed";
    private static final String EVENT_REMOVE = "REMOVE";
    private static final String EVENT_MODIFY = "MODIFY";
    private static final String SCHEDULE_NAME_PREFIX = "expiry-";

    private static final SqsClient SQS = SqsClient.create();
    private static final String CANCELLATION_QUEUE_URL = System.getenv("CANCELLATION_QUEUE_URL");

    @Override
    public void handleRequest(InputStream input, OutputStream output, Context context) throws IOException {
        JsonNode root = Json.MAPPER.readTree(input);
        for (JsonNode record : root.path("Records")) {
            if (needsCancellation(record)) {
                enqueueCancellation(record);
            }
        }
    }

    private static boolean needsCancellation(JsonNode record) {
        String eventName = record.path("eventName").asText();
        JsonNode ddb = record.path("dynamodb");

        if (EVENT_REMOVE.equals(eventName)) {
            return STATUS_PENDING.equals(status(ddb.path("OldImage")));
        }
        if (EVENT_MODIFY.equals(eventName)) {
            return STATUS_PENDING.equals(status(ddb.path("OldImage")))
                    && STATUS_COMPLETED.equals(status(ddb.path("NewImage")));
        }
        return false;
    }

    private void enqueueCancellation(JsonNode record) {
        JsonNode ddb = record.path("dynamodb");
        String eventName = record.path("eventName").asText();
        JsonNode image = EVENT_REMOVE.equals(eventName) ? ddb.path("OldImage") : ddb.path("NewImage");

        String taskId = stringAttribute(image, "TaskId");
        String userId = stringAttribute(image, "UserId");
        String sequenceNumber = ddb.path("SequenceNumber").asText();

        String messageBody = Json.toJson(Map.of(
                "taskId", taskId,
                "scheduleName", SCHEDULE_NAME_PREFIX + taskId));

        SQS.sendMessage(SendMessageRequest.builder()
                .queueUrl(CANCELLATION_QUEUE_URL)
                .messageBody(messageBody)
                .messageGroupId(userId)
                .messageDeduplicationId(sequenceNumber)
                .build());
    }

    private static String status(JsonNode image) {
        return stringAttribute(image, "Status");
    }

    /** Read the string ("S") value of a DynamoDB attribute from a stream image. */
    private static String stringAttribute(JsonNode image, String attributeName) {
        return image.path(attributeName).path("S").asText();
    }
}
