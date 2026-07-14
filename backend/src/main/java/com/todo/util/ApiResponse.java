package com.todo.util;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import java.util.Map;

/** Builds API Gateway proxy responses with CORS headers and a JSON body. */
public final class ApiResponse {

    private static final String CONTENT_TYPE = "application/json";

    private static final Map<String, String> HEADERS = Map.of(
            "Content-Type", CONTENT_TYPE,
            "Access-Control-Allow-Origin", "*",
            "Access-Control-Allow-Headers", "Content-Type,Authorization",
            "Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");

    private ApiResponse() {
    }

    /** Response whose body is the JSON serialization of {@code body}. */
    public static APIGatewayProxyResponseEvent json(int statusCode, Object body) {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(HEADERS)
                .withBody(Json.toJson(body));
    }

    /** Convenience for {@code {"message": ...}} error/info payloads. */
    public static APIGatewayProxyResponseEvent message(int statusCode, String message) {
        return json(statusCode, Map.of("message", message));
    }
}
