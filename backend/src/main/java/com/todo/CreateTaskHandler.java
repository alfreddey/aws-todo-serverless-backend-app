package com.todo;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.todo.util.ApiResponse;
import com.todo.util.AuthUtil;
import com.todo.util.DynamoItems;
import com.todo.util.Json;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.scheduler.SchedulerClient;
import software.amazon.awssdk.services.scheduler.model.ActionAfterCompletion;
import software.amazon.awssdk.services.scheduler.model.CreateScheduleRequest;
import software.amazon.awssdk.services.scheduler.model.FlexibleTimeWindow;
import software.amazon.awssdk.services.scheduler.model.FlexibleTimeWindowMode;
import software.amazon.awssdk.services.scheduler.model.Target;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Create a task (POST /tasks): persist it as Pending and register a one-time
 * EventBridge Scheduler schedule that fires ExpireTaskFunction at the deadline.
 */
public class CreateTaskHandler
        implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final int DEFAULT_DEADLINE_MINUTES = 5;
    private static final String STATUS_PENDING = "Pending";
    private static final String SCHEDULE_NAME_PREFIX = "expiry-";
    private static final String UTC = "UTC";

    private static final DynamoDbClient DDB = DynamoDbClient.create();
    private static final SchedulerClient SCHEDULER = SchedulerClient.create();

    private static final String TABLE_NAME = System.getenv("TABLE_NAME");
    private static final String EXPIRE_FUNCTION_ARN = System.getenv("EXPIRE_FUNCTION_ARN");
    private static final String SCHEDULER_ROLE_ARN = System.getenv("SCHEDULER_ROLE_ARN");

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        String userId = AuthUtil.getUserId(event);

        JsonNode body = parseBody(event.getBody());
        String description = textOrNull(body, "description");
        String date = textOrNull(body, "date");
        if (description == null || date == null) {
            return ApiResponse.message(400, "description and date are required");
        }

        String taskId = UUID.randomUUID().toString();
        Instant deadline = Instant.now().plus(DEFAULT_DEADLINE_MINUTES, ChronoUnit.MINUTES)
                .truncatedTo(ChronoUnit.SECONDS);
        String deadlineIso = deadline.toString();

        Map<String, AttributeValue> item = new LinkedHashMap<>();
        item.put("UserId", DynamoItems.s(userId));
        item.put("TaskId", DynamoItems.s(taskId));
        item.put("Description", DynamoItems.s(description));
        item.put("Date", DynamoItems.s(date));
        item.put("Status", DynamoItems.s(STATUS_PENDING));
        item.put("Deadline", DynamoItems.s(deadlineIso));

        DDB.putItem(PutItemRequest.builder().tableName(TABLE_NAME).item(item).build());

        createExpirySchedule(userId, taskId, deadlineIso);

        return ApiResponse.json(201, DynamoItems.toPlainMap(item));
    }

    private void createExpirySchedule(String userId, String taskId, String deadlineIso) {
        String targetInput = Json.toJson(Map.of("userId", userId, "taskId", taskId));

        SCHEDULER.createSchedule(CreateScheduleRequest.builder()
                .name(SCHEDULE_NAME_PREFIX + taskId)
                .scheduleExpression(toScheduleExpression(deadlineIso))
                .scheduleExpressionTimezone(UTC)
                .flexibleTimeWindow(FlexibleTimeWindow.builder().mode(FlexibleTimeWindowMode.OFF).build())
                .actionAfterCompletion(ActionAfterCompletion.DELETE)
                .target(Target.builder()
                        .arn(EXPIRE_FUNCTION_ARN)
                        .roleArn(SCHEDULER_ROLE_ARN)
                        .input(targetInput)
                        .build())
                .build());
    }

    /** EventBridge Scheduler's at() expression takes a timezone-less timestamp. */
    private static String toScheduleExpression(String isoTimestamp) {
        return "at(" + isoTimestamp.replace("Z", "") + ")";
    }

    private static JsonNode parseBody(String rawBody) {
        try {
            return Json.MAPPER.readTree(rawBody == null || rawBody.isBlank() ? "{}" : rawBody);
        } catch (IOException e) {
            return Json.MAPPER.createObjectNode();
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }
}
