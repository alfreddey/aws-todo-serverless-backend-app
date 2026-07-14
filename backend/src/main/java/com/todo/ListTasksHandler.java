package com.todo;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.todo.util.ApiResponse;
import com.todo.util.AuthUtil;
import com.todo.util.DynamoItems;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * List the caller's tasks (GET /tasks), optionally filtered by a
 * {@code ?status=} query parameter.
 */
public class ListTasksHandler
        implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final String STATUS_QUERY_PARAM = "status";

    private static final DynamoDbClient DDB = DynamoDbClient.create();
    private static final String TABLE_NAME = System.getenv("TABLE_NAME");

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        String userId = AuthUtil.getUserId(event);
        String statusFilter = queryParam(event, STATUS_QUERY_PARAM);

        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":userId", DynamoItems.s(userId));

        QueryRequest.Builder request = QueryRequest.builder()
                .tableName(TABLE_NAME)
                .keyConditionExpression("UserId = :userId");

        if (statusFilter != null && !statusFilter.isBlank()) {
            request.filterExpression("#status = :status")
                    .expressionAttributeNames(Map.of("#status", "Status"));
            values.put(":status", DynamoItems.s(statusFilter));
        }

        QueryResponse response = DDB.query(request.expressionAttributeValues(values).build());
        List<Map<String, Object>> tasks = response.items().stream()
                .map(DynamoItems::toPlainMap)
                .toList();

        return ApiResponse.json(200, Map.of("tasks", tasks));
    }

    private static String queryParam(APIGatewayProxyRequestEvent event, String name) {
        Map<String, String> params = event.getQueryStringParameters();
        return params == null ? null : params.get(name);
    }
}
