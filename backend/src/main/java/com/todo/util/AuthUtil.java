package com.todo.util;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;

import java.util.Map;

/** Reads the authenticated user's identity from the Cognito authorizer claims. */
public final class AuthUtil {

    private static final String CLAIMS_KEY = "claims";
    private static final String SUBJECT_CLAIM = "sub";

    private AuthUtil() {
    }

    /** The Cognito user id ({@code sub}) injected by the API Gateway authorizer. */
    @SuppressWarnings("unchecked")
    public static String getUserId(APIGatewayProxyRequestEvent event) {
        Map<String, Object> authorizer = event.getRequestContext().getAuthorizer();
        Map<String, Object> claims = (Map<String, Object>) authorizer.get(CLAIMS_KEY);
        return (String) claims.get(SUBJECT_CLAIM);
    }
}
