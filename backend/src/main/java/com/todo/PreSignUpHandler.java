package com.todo;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.CognitoUserPoolPreSignUpEvent;

/**
 * Cognito PreSignUp trigger: auto-confirm every new user and mark their email
 * verified, so sign-up needs no manual confirmation step.
 */
public class PreSignUpHandler
        implements RequestHandler<CognitoUserPoolPreSignUpEvent, CognitoUserPoolPreSignUpEvent> {

    @Override
    public CognitoUserPoolPreSignUpEvent handleRequest(CognitoUserPoolPreSignUpEvent event, Context context) {
        CognitoUserPoolPreSignUpEvent.Response response = event.getResponse();
        if (response == null) {
            response = new CognitoUserPoolPreSignUpEvent.Response();
            event.setResponse(response);
        }
        response.setAutoConfirmUser(true);
        response.setAutoVerifyEmail(true);
        return event;
    }
}
