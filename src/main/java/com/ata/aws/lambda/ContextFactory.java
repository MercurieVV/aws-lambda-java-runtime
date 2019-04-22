package com.ata.aws.lambda;

import com.amazonaws.services.lambda.runtime.*;

import java.util.Map;
import java.util.Objects;

/**
 * Created with IntelliJ IDEA.
 * User: Victor Mercurievv
 * Date: 4/22/2019
 * Time: 11:11 PM
 * Contacts: email: mercurievvss@gmail.com Skype: 'grobokopytoff' or 'mercurievv'
 */
public class ContextFactory {

    private static LambdaLogger logger = new LambdaLogger() {
        @Override
        public void log(String message) {
            System.out.println(message);
        }

        @Override
        public void log(byte[] message) {
            System.out.println(new String(message));
        }
    };

    public static Context createContext() {
        return new ContextImpl(
                null,
                System.getenv("AWS_LAMBDA_LOG_GROUP_NAME"),
                System.getenv("AWS_LAMBDA_LOG_STREAM_NAME"),
                System.getenv("AWS_LAMBDA_FUNCTION_NAME"),
                System.getenv("AWS_LAMBDA_FUNCTION_VERSION"),
                "arn",
                new CognitoIdentity() {
                    @Override
                    public String getIdentityId() {
                        return null;
                    }

                    @Override
                    public String getIdentityPoolId() {
                        return null;
                    }
                },
                new ClientContext() {
                    @Override
                    public Client getClient() {
                        return null;
                    }

                    @Override
                    public Map<String, String> getCustom() {
                        return null;
                    }

                    @Override
                    public Map<String, String> getEnvironment() {
                        return null;
                    }
                }, 0
                , 0
                , logger
        );
    }

    public Context cloneWith(Context context, String requestId) {
        return new ContextImpl(
                requestId,
                context.getLogGroupName(),
                context.getLogStreamName(),
                context.getFunctionName(),
                context.getFunctionVersion(),
                context.getInvokedFunctionArn(),
                context.getIdentity(),
                context.getClientContext(),
                context.getRemainingTimeInMillis(),
                context.getMemoryLimitInMB(),
                context.getLogger()
        );
    }

    private static class ContextImpl implements Context {

        private final String requestId;
        private final String logGroupName;
        private final String logStreamName;
        private final String functionName;
        private final String functionVersion;
        private final String invokedFunctionArn;
        private final CognitoIdentity identity;
        private final ClientContext clientContext;
        private final int remainingTimeMills;
        private final int memoryLimitInMb;
        private final LambdaLogger lambdaLogger;

        public ContextImpl(String requestId, String logGroupName, String logStreamName, String functionName, String functionVersion, String invokedFunctionArn, CognitoIdentity identity, ClientContext clientContext, int remainingTimeMills, int memoryLimitInMb, LambdaLogger lambdaLogger) {
            this.requestId = requestId;
            this.logGroupName = logGroupName;
            this.logStreamName = logStreamName;
            this.functionName = functionName;
            this.functionVersion = functionVersion;
            this.invokedFunctionArn = invokedFunctionArn;
            this.identity = identity;
            this.clientContext = clientContext;
            this.remainingTimeMills = remainingTimeMills;
            this.memoryLimitInMb = memoryLimitInMb;
            this.lambdaLogger = lambdaLogger;
        }

        @Override
        public String getAwsRequestId() {
            return requestId;
        }

        @Override
        public String getLogGroupName() {
            return logGroupName;
        }

        @Override
        public String getLogStreamName() {
            return logStreamName;
        }

        @Override
        public String getFunctionName() {
            return functionName;
        }

        @Override
        public String getFunctionVersion() {
            return functionVersion;
        }

        @Override
        public String getInvokedFunctionArn() {
            return invokedFunctionArn;
        }

        @Override
        public CognitoIdentity getIdentity() {
            return identity;
        }

        @Override
        public ClientContext getClientContext() {
            return clientContext;
        }

        @Override
        public int getRemainingTimeInMillis() {
            return remainingTimeMills;
        }

        @Override
        public int getMemoryLimitInMB() {
            return memoryLimitInMb;
        }

        @Override
        public LambdaLogger getLogger() {
            return lambdaLogger;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ContextImpl)) return false;
            ContextImpl context = (ContextImpl) o;
            return remainingTimeMills == context.remainingTimeMills &&
                    memoryLimitInMb == context.memoryLimitInMb &&
                    Objects.equals(requestId, context.requestId) &&
                    Objects.equals(logGroupName, context.logGroupName) &&
                    Objects.equals(logStreamName, context.logStreamName) &&
                    Objects.equals(functionName, context.functionName) &&
                    Objects.equals(functionVersion, context.functionVersion) &&
                    Objects.equals(invokedFunctionArn, context.invokedFunctionArn) &&
                    Objects.equals(identity, context.identity) &&
                    Objects.equals(clientContext, context.clientContext) &&
                    Objects.equals(lambdaLogger, context.lambdaLogger);
        }

        @Override
        public int hashCode() {
            return Objects.hash(requestId, logGroupName, logStreamName, functionName, functionVersion, invokedFunctionArn, identity, clientContext, remainingTimeMills, memoryLimitInMb, lambdaLogger);
        }
    }
}
