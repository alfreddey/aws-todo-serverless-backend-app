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
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;

import java.util.Map;

/**
 * Delete a task (DELETE /tasks/{taskId}). The guarded delete returns 404 when the
 * task does not exist. The resulting DynamoDB stream REMOVE event drives expiry
 * cancellation.
 */
public class DeleteTaskHandler
        implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final String TASK_ID_PATH_PARAM = "taskId";

    private static final DynamoDbClient DDB = DynamoDbClient.create();
    private static final String TABLE_NAME = System.getenv("TABLE_NAME");

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        String userId = AuthUtil.getUserId(event);
        String taskId = event.getPathParameters().get(TASK_ID_PATH_PARAM);

        Map<String, AttributeValue> key = Map.of(
                "UserId", DynamoItems.s(userId),
                "TaskId", DynamoItems.s(taskId));

        try {
            DDB.deleteItem(DeleteItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(key)
                    .conditionExpression("attribute_exists(TaskId)")
                    .build());
            return ApiResponse.json(204, Map.of());
        } catch (ConditionalCheckFailedException e) {
            return ApiResponse.message(404, "task not found");
        }
    }
}
