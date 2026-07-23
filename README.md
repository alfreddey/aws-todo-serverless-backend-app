# Serverless To-Do App (Java)

Serverless task manager with Cognito auth, event-driven expiry (EventBridge
Scheduler), and expiry cancellation (DynamoDB Streams -> SQS FIFO -> Lambda).
This is a Java (Lambda `java21`) rebuild of the original Node.js app; the AWS
architecture is unchanged. See [PRD.md](PRD.md) for the full spec.

## Structure

- `backend/` - AWS SAM template + Java Lambda functions (one Maven project)
- `diagrams/` - architecture diagram (`.drawio` source + `.png`)

The frontend lives in a separate repo: `aws-serverless-todo-app-frontend-java`.

## How the Java backend is packaged

- One Maven project holds every Lambda. `sam build` compiles it once and copies
  the classes plus `lib/` dependencies into each function's package.
- Every Lambda in `template.yaml` shares `CodeUri: .` (the project directory) and
  points its `Handler` at a different class (e.g. `com.todo.CreateTaskHandler`).
- Shared helpers (`com.todo.util.*`) compile in alongside the handlers, so there
  is no Lambda layer — Java doesn't need the layer pattern the Node version used.

## Deploy the backend

```bash
cd backend
sam build           # compiles + packages each function into .aws-sam/build
sam deploy --guided # uploads the built artifacts and creates the stack
```

Run `sam build` again whenever you change the Java code, then `sam deploy`.

On first deploy, leave `GitHubRepositoryUrl` blank to stand up everything except
Amplify Hosting. Once the frontend repo is pushed to GitHub, redeploy with:

```bash
sam deploy \
  --parameter-overrides \
    GitHubRepositoryUrl=https://github.com/<you>/<repo> \
    GitHubAccessToken=<a GitHub PAT with repo access> \
    GitHubBranchName=main
```

This adds an `AWS::Amplify::App` wired to the frontend repo (built from its root),
with `VITE_*` environment variables pre-populated from the Cognito/API outputs,
and triggers a build on every push to the branch.

Grab `UserPoolId`, `UserPoolClientId`, and `ApiUrl` from the stack outputs to
configure the frontend (see the frontend repo's README).

## Notes on the expiry/cancellation design

- `CreateTaskHandler` creates a one-time EventBridge Scheduler schedule
  (`expiry-<taskId>`) targeting `ExpireTaskFunction`, with
  `ActionAfterCompletion=DELETE` so fired schedules clean themselves up.
- `ExpireTaskHandler` only flips `Status` to `Expired` if it's still `Pending`
  (conditional update), making it safe to invoke more than once.
- Completing or deleting a task before its deadline is caught by a DynamoDB
  Streams trigger (`StreamProcessorHandler`), which enqueues a cancellation
  message on an SQS FIFO queue; `CancelScheduleHandler` consumes it and deletes
  the EventBridge schedule. Deleting an already-fired/cancelled schedule is a
  no-op, so this is idempotent by construction.
- All users share one SNS topic; `PostAuthenticationHandler` subscribes each
  user's email with a `FilterPolicy` on `userId`, so nobody sees anyone else's
  expiry emails.

## Requirements

- Java 21+ and Maven
- AWS SAM CLI and configured AWS credentials
