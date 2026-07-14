# Serverless To-Do App (Java)

Serverless task manager with Cognito auth, event-driven expiry (EventBridge
Scheduler), and expiry cancellation (DynamoDB Streams -> SQS FIFO -> Lambda).
This is a Java (Lambda `java21`) rebuild of the original Node.js app; the AWS
architecture is unchanged. See [PRD.md](PRD.md) for the full spec.

## Structure

- `backend/` - AWS SAM template + Java Lambda functions (one Maven project)
- `frontend/` - React + Vite + aws-amplify SPA, hosted on AWS Amplify
- `diagrams/` - architecture diagram (`.drawio` source + `.png`)

## How the Java backend is packaged

- One Maven project builds a single fat jar: `backend/target/todo-backend.jar`.
- Every Lambda in `template.yaml` uses that same jar as its `CodeUri` and points
  its `Handler` at a different class (e.g. `com.todo.CreateTaskHandler`).
- Shared helpers (`com.todo.util.*`) compile into the jar, so there is no Lambda
  layer — Java doesn't need the layer pattern the Node version used.

## Deploy the backend

```bash
cd backend
./build.sh          # mvn clean package -> target/todo-backend.jar
sam deploy --guided # packages the jar and creates the stack
```

`sam build` is not used here (the deploy artifact is the pre-built jar, not a
source directory). Run `./build.sh` again whenever you change the Java code, then
`sam deploy`.

On first deploy, leave `GitHubRepositoryUrl` blank to stand up everything except
Amplify Hosting. Once the frontend repo is pushed to GitHub, redeploy with:

```bash
sam deploy \
  --parameter-overrides \
    GitHubRepositoryUrl=https://github.com/<you>/<repo> \
    GitHubAccessToken=<a GitHub PAT with repo access> \
    GitHubBranchName=main
```

This adds an `AWS::Amplify::App` wired to the repo's `frontend/` directory, with
`VITE_*` environment variables pre-populated from the Cognito/API outputs, and
triggers a build on every push to the branch.

Grab `UserPoolId`, `UserPoolClientId`, and `ApiUrl` from the stack outputs if you
want to run the frontend locally instead (copy `frontend/.env.example` to
`frontend/.env.local` and fill them in).

## Run the frontend locally

```bash
cd frontend
npm install
npm run dev
```

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
- Node.js (for the frontend)
