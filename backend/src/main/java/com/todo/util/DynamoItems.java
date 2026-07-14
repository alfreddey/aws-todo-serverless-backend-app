package com.todo.util;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.LinkedHashMap;
import java.util.Map;

/** Helpers to build DynamoDB items and turn them back into plain JSON-friendly maps. */
public final class DynamoItems {

    private DynamoItems() {
    }

    /** Shorthand for a string attribute value. */
    public static AttributeValue s(String value) {
        return AttributeValue.fromS(value);
    }

    /** Convert a DynamoDB item into a plain map suitable for JSON serialization. */
    public static Map<String, Object> toPlainMap(Map<String, AttributeValue> item) {
        Map<String, Object> plain = new LinkedHashMap<>();
        for (Map.Entry<String, AttributeValue> entry : item.entrySet()) {
            plain.put(entry.getKey(), toPlainValue(entry.getValue()));
        }
        return plain;
    }

    private static Object toPlainValue(AttributeValue value) {
        if (value.s() != null) {
            return value.s();
        }
        if (value.n() != null) {
            return value.n();
        }
        if (value.bool() != null) {
            return value.bool();
        }
        return null;
    }
}
