#!/usr/bin/env bash
#c:\Program Files\Java\jdk-11.0.3\bin\
#export JAVA_HOME="c:\Program Files\Java\jdk-11.0.3"
#tar xvzf jdk-11.0.3_linux-x64_bin.tar.gz

#git clone https://github.com/MercurieVV/aws-lambda-java-runtime.git
#cd ~/aws-lambda-java-runtime;./doit.sh
cd ~/aws-lambda-java-runtime
git pull
rm -rf doit
rm -rf dist
mkdir doit
./gradlew build
#echo "OK"
jlink --module-path ./build/libs:~/jdk-11.0.3/jmods \
   --add-modules  com.ata.lambda,java.sql,java.sql.rowset,java.rmi,java.logging \
   --output ./dist \
   --launcher bootstrap=com.ata.lambda/com.ata.aws.lambda.LambdaBootstrap \
   --compress 2 --no-header-files --no-man-pages --strip-debug
#jlink --module-path ./build/libs:./jdk-11.0.3/jmods \
#   --add-modules  com.ata.lambda \
#   --output ./dist \
#   --launcher bootstrap=com.ata.lambda/com.ata.aws.lambda.LambdaBootstrap \
#   --compress 2 --no-header-files --no-man-pages --strip-debug
mv ./dist doit
cp bootstrap ./doit/bootstrap
chmod +x ./doit/bootstrap
cd doit
zip -r function.zip *
aws lambda publish-layer-version --layer-name Java-11 --zip-file fileb://function.zip
aws lambda list-layers

#aws lambda create-function --function-name testJavaHandler \
#--zip-file fileb://function.zip --handler SampleLambdaHandler::myHandler --runtime provided \
#--role arn:aws:iam::283792689871:role/lambda_role
#aws lambda update-function-configuration --function-name testJavaHandler --layers arn:aws:lambda:eu-west-1:283792689871:layer:Java-11:1
