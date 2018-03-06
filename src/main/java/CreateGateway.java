import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.apigateway.AmazonApiGateway;
import com.amazonaws.services.apigateway.AmazonApiGatewayClientBuilder;
import com.amazonaws.services.apigateway.model.CreateDeploymentRequest;
import com.amazonaws.services.apigateway.model.CreateModelRequest;
import com.amazonaws.services.apigateway.model.CreateResourceRequest;
import com.amazonaws.services.apigateway.model.GetResourcesRequest;
import com.amazonaws.services.apigateway.model.GetRestApisRequest;
import com.amazonaws.services.apigateway.model.GetRestApisResult;
import com.amazonaws.services.apigateway.model.IntegrationType;
import com.amazonaws.services.apigateway.model.PutIntegrationRequest;
import com.amazonaws.services.apigateway.model.PutIntegrationResponseRequest;
import com.amazonaws.services.apigateway.model.PutMethodRequest;
import com.amazonaws.services.apigateway.model.PutMethodResponseRequest;
import com.amazonaws.services.apigateway.model.Resource;
import com.amazonaws.services.apigateway.model.RestApi;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.AWSLambdaClientBuilder;
import com.amazonaws.services.lambda.model.FunctionConfiguration;
import com.amazonaws.services.lambda.model.GetFunctionRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class CreateGateway {

    public static final String SECURITY_PROFILE_NAME = "default";
    public static final String REGION = "eu-west-1";
    public static final String DECISION_REST_API_NAME = "Decision";
    public static final String DECISION_LAMBDA_CREDENTIALS = "arn:aws:iam::256265920797:role/lambda_basic_execution";
    public static final String URI_TEMPLATE = "arn:aws:apigateway:eu-west-1:lambda:path/2015-03-31/functions/%s/invocations";

    public static void main(String[] args) {

        String decisionName = "IsAmazonCool";

        AWSLambda awsLambda = AWSLambdaClientBuilder.standard()
                .withCredentials(new ProfileCredentialsProvider(SECURITY_PROFILE_NAME)).withRegion(REGION).build();
        FunctionConfiguration decisionFunction = awsLambda.getFunction(new GetFunctionRequest().withFunctionName(decisionName)).getConfiguration();

        AmazonApiGateway gateway = AmazonApiGatewayClientBuilder.standard()
                .withCredentials(new ProfileCredentialsProvider(SECURITY_PROFILE_NAME)).withRegion(REGION).build();

        GetRestApisResult restApis = gateway.getRestApis(new GetRestApisRequest());
        RestApi decisionRestApi = restApis.getItems().stream()
                .filter(item -> item.getName().equals(DECISION_REST_API_NAME)).findFirst()
                .orElseThrow(RuntimeException::new);

        List<Resource> resources = gateway
                .getResources(new GetResourcesRequest().withRestApiId(decisionRestApi.getId())).getItems();
        Resource rootResource = resources.stream().filter(resource -> resource.getPath().equals("/")).findFirst().orElseThrow(RuntimeException::new);

        String decisionNameResourceId = createResource(gateway, decisionRestApi.getId(), rootResource.getId(), decisionName);

        createModel(gateway, decisionRestApi, ()->getRequestModel(decisionName), decisionName + "Request");
        createModel(gateway, decisionRestApi, ()->getResponseModel(decisionName), decisionName + "Response");

        createMethodForResource(gateway, decisionRestApi.getId(), decisionNameResourceId);
        createMethodResponse(gateway, decisionRestApi.getId(), decisionNameResourceId);

        createIntegration(gateway, decisionRestApi.getId(), decisionNameResourceId, decisionFunction.getFunctionArn());
        createIntegrationResponse(gateway, decisionRestApi.getId(), decisionNameResourceId);

        gateway.createDeployment(new CreateDeploymentRequest()
                                         .withRestApiId(decisionRestApi.getId())
                                 .withStageName("PROD")
                                 .withStageDescription("bla bla bla"));
    }

    private static String createModel(AmazonApiGateway gateway, RestApi decisionRestApi, Supplier<String> getModel, String modelName) {
        return gateway.createModel(new CreateModelRequest()
                            .withSchema(getModel.get())
                            .withContentType("application/json")
                            .withName(modelName)
                            .withDescription("bla bla bla")
                            .withRestApiId(decisionRestApi.getId())).getId();
    }

    private static void createMethodResponse(AmazonApiGateway gateway, String decisionRestApiId, String decisionResourceId) {

        Map<String, String> modelName = new HashMap<String, String>(){{
            put("application/json", "Output");
        }};
        gateway.putMethodResponse(new PutMethodResponseRequest()
                                          .withHttpMethod("POST")
                                          .withResponseModels(modelName)
                                          .withRestApiId(decisionRestApiId)
                                          .withResourceId(decisionResourceId)
                                          .withStatusCode("200"));
    }

    private static void createIntegration(AmazonApiGateway gateway,
                                          String decisionRestApiId,
                                          String decisionResourceId,
                                          String lambdaArn) {

        gateway.putIntegration(new PutIntegrationRequest()
                                       .withCredentials(DECISION_LAMBDA_CREDENTIALS)
                                       .withIntegrationHttpMethod("POST")
                                       .withHttpMethod("POST")
                                       .withType(IntegrationType.AWS)
                                       .withRestApiId(decisionRestApiId)
                                       .withResourceId(decisionResourceId)
                                       .withPassthroughBehavior("WHEN_NO_MATCH")
                                       .withUri(String.format(URI_TEMPLATE, lambdaArn)));

    }

    private static void createIntegrationResponse(AmazonApiGateway gateway,
                                                  String decisionRestApiId,
                                                  String decisionResourceId) {

        gateway.putIntegrationResponse(new PutIntegrationResponseRequest().withRestApiId(decisionRestApiId)
                                               .withResourceId(decisionResourceId)
                                               .withHttpMethod("POST")
                                               .withStatusCode("200"));

    }

    private static void createMethodForResource(AmazonApiGateway gateway,
                                                String decisionRestApiId,
                                                String resourceId) {

        Map<String, String> modelName = new HashMap<String, String>(){{
            put("application/json", "Input");
        }};
        gateway.putMethod(new PutMethodRequest().withResourceId(resourceId)
                                                                    .withRestApiId(decisionRestApiId)
                                                                    .withAuthorizationType("NONE")
                                                                    .withRequestModels(modelName)
                                                                    .withHttpMethod("POST"));
    }

    private static String createResource(AmazonApiGateway gateway,
                                       String decisionRestApiId,
                                       String rootResourceId,
                                       String decisionName) {
        return gateway.createResource(new CreateResourceRequest().withParentId(rootResourceId).withRestApiId(decisionRestApiId)
                                       .withPathPart(decisionName)).getId();
    }

    public static String getRequestModel(String decisionName){
         return "{\n" + "    \"type\":\"object\",\n" + "    \"properties\":{\n"
                 + "        \"JavaVersionNumber\":{\"type\":\"number\"},\n"
                 + "        \"PythonVersion\":{\"type\":\"number\"},\n"
                 + "        \"Serverless\":{\"type\":\"string\"}\n" + "    },\n"
                 + "    \"title\":\"IsAmazonCoolRequest\"\n" + "}";
    }

    public static String getResponseModel(String decisionName){
        return "{\n" + "    \"type\":\"object\",\n" + "    \"properties\":{\n"
                + "        \"JavaVersionNumber\":{\"type\":\"number\"},\n"
                + "        \"PythonVersion\":{\"type\":\"number\"},\n"
                + "        \"Serverless\":{\"type\":\"string\"}\n" + "    },\n"
                + "    \"title\":\"IsAmazonCoolRequest\"\n" + "}";
    }
}
