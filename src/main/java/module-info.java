module com.ata.lambda {
    requires java.sql;
    requires java.rmi;
    exports com.amazonaws.services.lambda.runtime;
    exports com.ata.aws.lambda;
}