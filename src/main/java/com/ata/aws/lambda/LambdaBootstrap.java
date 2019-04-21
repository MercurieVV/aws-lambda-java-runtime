package com.ata.aws.lambda;

import java.io.*;
import java.lang.reflect.Method;
import java.net.*;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.amazonaws.services.lambda.runtime.*;

public class LambdaBootstrap {

    private static final String LAMBDA_VERSION_DATE = "2018-06-01";
    private static final String LAMBDA_RUNTIME_URL_TEMPLATE = "http://{0}/{1}/runtime/invocation/next";
    private static final String LAMBDA_INVOCATION_URL_TEMPLATE = "http://{0}/{1}/runtime/invocation/{2}/response";
    private static final String LAMBDA_INIT_ERROR_URL_TEMPLATE = "http://{0}/{1}/runtime/init/error";
    private static final String LAMBDA_ERROR_URL_TEMPLATE = "http://{0}/{1}/runtime/invocation/{2}/error";

    public static void main(String args[]) {

        String runtimeApi = getEnv("AWS_LAMBDA_RUNTIME_API");
        String taskRoot = getEnv("LAMBDA_TASK_ROOT");
        String handlerName = getEnv("_HANDLER");

        Class handlerClass = null;
        RequestStreamHandler reqHandler;

        try {
            // Get the handler class and method name from the Lambda Configuration in the format of <class>::<method>

            // Find the Handler and Method on the classpath
            handlerClass = getHandlerClass(taskRoot, handlerName);
            reqHandler = (RequestStreamHandler) handlerClass.getConstructor().newInstance();

            if(handlerClass == null) {
                // Not much else to do handler can't be found.
                throw new Exception("Handler not found");
            }

        }
        catch (Exception e) {
            String initErrorUrl = MessageFormat.format(LAMBDA_INIT_ERROR_URL_TEMPLATE, runtimeApi, LAMBDA_VERSION_DATE);
            postError(initErrorUrl, "Could not find handler method", "InitError");
            e.printStackTrace();
            return;
        }


        String requestId;
        String runtimeUrl = MessageFormat.format(LAMBDA_RUNTIME_URL_TEMPLATE, runtimeApi, LAMBDA_VERSION_DATE);

        // Main event loop
        while (true) {

            // Get next Lambda Event
            SimpleHttpResponse event = get(runtimeUrl);
            final Map<String, List<String>> headers = event.getHeaders();
            System.out.println("headers = " + headers.entrySet().stream().map(o-> o.getKey()).collect(Collectors.joining()));
            requestId = getHeaderValue("Lambda-Runtime-Aws-Request-Id", headers);

            try{
                String invocationUrl = MessageFormat.format(LAMBDA_INVOCATION_URL_TEMPLATE, runtimeApi, LAMBDA_VERSION_DATE, requestId);
                final HttpURLConnection connection = (HttpURLConnection) new URL(invocationUrl).openConnection();
                final OutputStream outputStream = connection.getOutputStream();
                // Invoke Handler Method
                invoke(reqHandler, event.getBody(), outputStream, requestId);

                // Post the results of Handler Invocation
                post(connection);
            }
            catch (Exception e) {
                String initErrorUrl = MessageFormat.format(LAMBDA_ERROR_URL_TEMPLATE, runtimeApi, LAMBDA_VERSION_DATE, requestId);
                postError(initErrorUrl, "Invocation Error", "RuntimeError");
                e.printStackTrace();
            }
        }
    }

    private static final String ERROR_RESPONSE_TEMPLATE = "'{'" +
            "\"errorMessage\": \"{0}\"," +
            "\"errorType\": \"{1}\"" +
            "'}'";

    private static void postError(String errorUrl, String errMsg, String errType) {
        String error = MessageFormat.format(ERROR_RESPONSE_TEMPLATE, errMsg, errType);
        post(errorUrl, error);
    }

    private static URL[] initClasspath(String taskRoot) throws MalformedURLException {
        File cwd = new File(taskRoot);

        ArrayList<File> classPath = new ArrayList<>();

        // Add top level folders
        classPath.add(new File(taskRoot));

        // Find any Top level jars or jars in the lib folder
        for(File f : cwd.listFiles((dir, name) -> name.endsWith(".jar") || name.equals("lib"))) {

            if(f.getName().equals("lib") && f.isDirectory()) {
                // Collect all Jars in /lib directory
                for(File jar : f.listFiles((dir, name) -> name.endsWith(".jar"))) {
                    classPath.add(jar);
                }
            }
            else {
                // Add top level dirs and jar files
                classPath.add(f);
            }
        }

        // Convert Files to URLs
        ArrayList<URL> ret = new ArrayList<>();

        for(File ff: classPath) {
            ret.add(ff.toURI().toURL());
        }

        return ret.toArray(new URL[ret.size()]);
    }

    private static Class getHandlerClass(String taskRoot, String className) throws Exception {

        URL[] classPathUrls = initClasspath(taskRoot);
        URLClassLoader cl = URLClassLoader.newInstance(classPathUrls);

        return cl.loadClass(className);
    }

    private static Method getHandlerMethod(Class handlerClass, String methodName) throws Exception {

        for (Method method : handlerClass.getMethods()) {

            if(method.getName().equals(methodName)) {
                return method;
            }
        }

        return null;
    }

    private static void invoke(RequestStreamHandler reqHandler, InputStream inputStream, OutputStream outputStream, String requestId) throws Exception {

        reqHandler.handleRequest(inputStream, outputStream, new Context() {
            @Override
            public String getAwsRequestId() {
                return requestId;
            }

            @Override
            public String getLogGroupName() {
                return null;
            }

            @Override
            public String getLogStreamName() {
                return null;
            }

            @Override
            public String getFunctionName() {
                return null;
            }

            @Override
            public String getFunctionVersion() {
                return null;
            }

            @Override
            public String getInvokedFunctionArn() {
                return null;
            }

            @Override
            public CognitoIdentity getIdentity() {
                return null;
            }

            @Override
            public ClientContext getClientContext() {
                return null;
            }

            @Override
            public int getRemainingTimeInMillis() {
                return 0;
            }

            @Override
            public int getMemoryLimitInMB() {
                return 0;
            }

            @Override
            public LambdaLogger getLogger() {
                return null;
            }
        });
    }

    private static String getHeaderValue(String header, Map<String, List<String>> headers) {
        List<String> values = headers.get(header);

        // We don't expect any headers with multiple values, so for simplicity we'll just concat any that have more than one entry.
        return String.join(",", values);
    }

    private static String getEnv(String name) {
        return System.getenv(name);
    }

    private static SimpleHttpResponse get(String remoteUrl) {

        SimpleHttpResponse output = null;

        try{
            URL url = new URL(remoteUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            // Parse the HTTP Response
            output = readResponse(conn);
        }
        catch(IOException e) {
            System.out.println("GET: " + remoteUrl);
            e.printStackTrace();
        }

        return output;
    }

    private static SimpleHttpResponse post(final HttpURLConnection conn) {
        SimpleHttpResponse output = null;

        try{
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.connect();

            // We can probably skip this for speed because we don't really care about the response
            output = readResponse(conn);
        }
        catch(IOException ioe) {
            System.out.println("POST: " + conn.getURL());
            ioe.printStackTrace();
        }

        return output;
    }

    private static SimpleHttpResponse post(String remoteUrl, String body) {
        SimpleHttpResponse output = null;

        try{
            URL url = new URL(remoteUrl);
            HttpURLConnection conn = (HttpURLConnection)url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            setBody(conn, body);
            conn.connect();

            // We can probably skip this for speed because we don't really care about the response
            output = readResponse(conn);
        }
        catch(IOException ioe) {
            System.out.println("POST: " + remoteUrl);
            ioe.printStackTrace();
        }

        return output;
    }

    private static SimpleHttpResponse readResponse(HttpURLConnection conn) throws IOException{

        // Map Response Headers
        HashMap<String, List<String>> headers = new HashMap<>();

        for(Map.Entry<String, List<String>> entry : conn.getHeaderFields().entrySet()) {
            headers.put(entry.getKey(), entry.getValue());
        }

        return new SimpleHttpResponse(conn.getResponseCode(), headers, conn.getInputStream());
    }

    private static void setBody(HttpURLConnection conn, String body) throws IOException{
        OutputStream os = null;
        OutputStreamWriter osw = null;

        try{
            os = conn.getOutputStream();
            osw = new OutputStreamWriter(os, "UTF-8");

            osw.write(body);
            osw.flush();
        }
        finally {
            osw.close();
            os.close();
        }
    }
}

