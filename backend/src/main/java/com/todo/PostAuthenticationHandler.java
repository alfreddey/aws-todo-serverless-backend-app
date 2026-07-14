package com.todo;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.CognitoUserPoolPostAuthenticationEvent;
import com.todo.util.Json;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.SubscribeRequest;

import java.util.Map;

/**
 * Cognito PostAuthentication trigger: subscribe the signed-in user's email to the
 * shared SNS topic. A per-user FilterPolicy on the {@code userId} message attribute
 * ensures each user only receives emails for their own tasks.
 */
public class PostAuthenticationHandler
        implements RequestHandler<CognitoUserPoolPostAuthenticationEvent, CognitoUserPoolPostAuthenticationEvent> {

    private static final String SUBJECT_ATTRIBUTE = "sub";
    private static final String EMAIL_ATTRIBUTE = "email";
    private static final String EMAIL_PROTOCOL = "email";

    private static final SnsClient SNS = SnsClient.create();
    private static final String NOTIFICATIONS_TOPIC_ARN = System.getenv("NOTIFICATIONS_TOPIC_ARN");

    @Override
    public CognitoUserPoolPostAuthenticationEvent handleRequest(
            CognitoUserPoolPostAuthenticationEvent event, Context context) {

        Map<String, String> userAttributes = event.getRequest().getUserAttributes();
        String userId = userAttributes.get(SUBJECT_ATTRIBUTE);
        String email = userAttributes.get(EMAIL_ATTRIBUTE);

        String filterPolicy = Json.toJson(Map.of("userId", new String[] {userId}));

        SNS.subscribe(SubscribeRequest.builder()
                .topicArn(NOTIFICATIONS_TOPIC_ARN)
                .protocol(EMAIL_PROTOCOL)
                .endpoint(email)
                .attributes(Map.of(
                        "FilterPolicy", filterPolicy,
                        "FilterPolicyScope", "MessageAttributes"))
                .returnSubscriptionArn(true)
                .build());

        return event;
    }
}
