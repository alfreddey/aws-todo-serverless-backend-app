package com.todo;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.todo.util.Json;
import software.amazon.awssdk.services.scheduler.SchedulerClient;
import software.amazon.awssdk.services.scheduler.model.DeleteScheduleRequest;
import software.amazon.awssdk.services.scheduler.model.ResourceNotFoundException;

import java.io.IOException;

/**
 * Consumes cancellation messages from the SQS FIFO queue and deletes the task's
 * EventBridge schedule. Deleting an already-fired or already-cancelled schedule is a
 * no-op, which makes this handler safe to run more than once for the same message.
 */
public class CancelScheduleHandler implements RequestHandler<SQSEvent, Void> {

    private static final String SCHEDULE_NAME_FIELD = "scheduleName";

    private static final SchedulerClient SCHEDULER = SchedulerClient.create();

    @Override
    public Void handleRequest(SQSEvent event, Context context) {
        for (SQSEvent.SQSMessage record : event.getRecords()) {
            String scheduleName = parseScheduleName(record.getBody());
            try {
                SCHEDULER.deleteSchedule(DeleteScheduleRequest.builder().name(scheduleName).build());
            } catch (ResourceNotFoundException e) {
                // Schedule already fired (auto-deleted) or was cancelled — nothing to do.
            }
        }
        return null;
    }

    private static String parseScheduleName(String body) {
        try {
            return Json.MAPPER.readTree(body).path(SCHEDULE_NAME_FIELD).asText();
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse cancellation message body", e);
        }
    }
}
