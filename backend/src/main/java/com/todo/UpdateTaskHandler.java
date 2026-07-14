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
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Update a task (PUT /tasks/{taskId}). Only description, date and status are
 * writable; the update is guarded so a missing task returns 404, not a silent write.
 */
public class UpdateTaskHandler
        implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    /** Request field name -> DynamoDB attribute name. */
    private static final Map<String, String> UPDATABLE_FIELDS = new LinkedHashMap<>();

    static {
        UPDATABLE_FIELDS.put("description", "Description");
        UPDATABLE_FIELDS.put("date", "Date");
        UPDATABLE_FIELDS.put("status", "Status");
    }

    private static final String TASK_ID_PATH_PARAM = "taskId";

    private static final DynamoDbClient DDB = DynamoDbClient.create();
    private static final String TABLE_NAME = System.getenv("TABLE_NAME");

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        String userId = AuthUtil.getUserId(event);
        String taskId = event.getPathParameters().get(TASK_ID_PATH_PARAM);
        JsonNode body = parseBody(event.getBody());

        List<String> setClauses = new ArrayList<>();
        Map<String, String> names = new HashMap<>();
        Map<String, AttributeValue> values = new HashMap<>();

        int index = 0;
        for (Map.Entry<String, String> field : UPDATABLE_FIELDS.entrySet()) {
            if (!body.hasNonNull(field.getKey())) {
                continue;
            }
            String nameToken = "#f" + index;
            String valueToken = ":v" + index;
            names.put(nameToken, field.getValue());
            values.put(valueToken, DynamoItems.s(body.get(field.getKey()).asText()));
            setClauses.add(nameToken + " = " + valueToken);
            index++;
        }

        if (setClauses.isEmpty()) {
            return ApiResponse.message(400, "no updatable fields provided");
        }

        Map<String, AttributeValue> key = Map.of(
                "UserId", DynamoItems.s(userId),
                "TaskId", DynamoItems.s(taskId));

        try {
            UpdateItemResponse response = DDB.updateItem(UpdateItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(key)
                    .updateExpression("SET " + String.join(", ", setClauses))
                    .expressionAttributeNames(names)
                    .expressionAttributeValues(values)
                    .conditionExpression("attribute_exists(TaskId)")
                    .returnValues(ReturnValue.ALL_NEW)
                    .build());
            return ApiResponse.json(200, DynamoItems.toPlainMap(response.attributes()));
        } catch (ConditionalCheckFailedException e) {
            return ApiResponse.message(404, "task not found");
        }
    }

    private static JsonNode parseBody(String rawBody) {
        try {
            return Json.MAPPER.readTree(rawBody == null || rawBody.isBlank() ? "{}" : rawBody);
        } catch (IOException e) {
            return Json.MAPPER.createObjectNode();
        }
    }
}
