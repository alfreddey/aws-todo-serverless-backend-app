package com.todo;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.todo.util.DynamoItems;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.util.Map;

/**
 * Expire a task at its deadline (invoked by EventBridge Scheduler with
 * {@code {userId, taskId}}). The conditional update makes this idempotent: if the
 * task was already completed/deleted/expired, it is a no-op instead of an error.
 */
public class ExpireTaskHandler implements RequestHandler<Map<String, String>, Map<String, Object>> {

    private static final String STATUS_ATTRIBUTE = "Status";
    private static final String STATUS_PENDING = "Pending";
    private static final String STATUS_EXPIRED = "Expired";
    private static final String USER_ID_ATTRIBUTE = "userId";

    private static final DynamoDbClient DDB = DynamoDbClient.create();
    private static final SnsClient SNS = SnsClient.create();

    private static final String TABLE_NAME = System.getenv("TABLE_NAME");
    private static final String NOTIFICATIONS_TOPIC_ARN = System.getenv("NOTIFICATIONS_TOPIC_ARN");

    @Override
    public Map<String, Object> handleRequest(Map<String, String> event, Context context) {
        String userId = event.get("userId");
        String taskId = event.get("taskId");

        Map<String, AttributeValue> key = Map.of(
                "UserId", DynamoItems.s(userId),
                "TaskId", DynamoItems.s(taskId));

        UpdateItemResponse response;
        try {
            response = DDB.updateItem(UpdateItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(key)
                    .updateExpression("SET #status = :expired")
                    .conditionExpression("#status = :pending")
                    .expressionAttributeNames(Map.of("#status", STATUS_ATTRIBUTE))
                    .expressionAttributeValues(Map.of(
                            ":expired", DynamoItems.s(STATUS_EXPIRED),
                            ":pending", DynamoItems.s(STATUS_PENDING)))
                    .returnValues(ReturnValue.ALL_NEW)
                    .build());
        } catch (ConditionalCheckFailedException e) {
            return Map.of("skipped", true, "reason", "task no longer pending");
        }

        String description = response.attributes().get("Description").s();
        SNS.publish(PublishRequest.builder()
                .topicArn(NOTIFICATIONS_TOPIC_ARN)
                .subject("Task expired")
                .message("Your task \"" + description + "\" has expired.")
                .messageAttributes(Map.of(USER_ID_ATTRIBUTE, MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue(userId)
                        .build()))
                .build());

        return Map.of("skipped", false);
    }
}
