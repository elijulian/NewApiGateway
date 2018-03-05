import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.apigateway.AmazonApiGateway;
import com.amazonaws.services.apigateway.AmazonApiGatewayClientBuilder;
import com.amazonaws.services.apigateway.model.ContentHandlingStrategy;
import com.amazonaws.services.apigateway.model.CreateResourceRequest;
import com.amazonaws.services.apigateway.model.CreateResourceResult;
import com.amazonaws.services.apigateway.model.CreateRestApiRequest;
import com.amazonaws.services.apigateway.model.CreateRestApiResult;
import com.amazonaws.services.apigateway.model.GetResourcesRequest;
import com.amazonaws.services.apigateway.model.GetResourcesResult;
import com.amazonaws.services.apigateway.model.GetRestApisRequest;
import com.amazonaws.services.apigateway.model.GetRestApisResult;
import com.amazonaws.services.apigateway.model.IntegrationType;
import com.amazonaws.services.apigateway.model.PutIntegrationRequest;
import com.amazonaws.services.apigateway.model.PutIntegrationResponseRequest;
import com.amazonaws.services.apigateway.model.PutMethodRequest;
import com.amazonaws.services.apigateway.model.PutMethodResponseRequest;
import com.amazonaws.services.apigateway.model.PutMethodResult;
import com.amazonaws.services.apigateway.model.Resource;
import com.amazonaws.services.apigateway.model.RestApi;

import java.util.HashMap;
import java.util.Map;

public class CreateGateway {

    public static void main(String[] args) {

        AmazonApiGateway gateway = AmazonApiGatewayClientBuilder.standard()
                .withCredentials(new ProfileCredentialsProvider("default")).withRegion("eu-west-1").build();

        createRestApi(gateway);

        GetRestApisResult restApis = gateway.getRestApis(new GetRestApisRequest());
        RestApi decision = restApis.getItems().stream().filter(item -> item.getName().equals("Decision")).findFirst()
                .get();

        GetResourcesResult resources = gateway.getResources(new GetResourcesRequest().withRestApiId(decision.getId()));

        createResource(gateway, decision, resources);

        resources = gateway.getResources(new GetResourcesRequest().withRestApiId(decision.getId()));
        Resource decisionResource = resources.getItems().stream().filter(item -> item.getPath().contains("decision"))
                .findFirst().get();

        createMethodForResource(gateway, decision, resources);
        createMethodResponse(gateway, decision, decisionResource);

        createIntegration(gateway, decision, decisionResource);
        createIntegrationResponse(gateway, decision, decisionResource);
    }

    private static void createMethodResponse(AmazonApiGateway gateway, RestApi decision, Resource decisionResource) {
        gateway.putMethodResponse(new PutMethodResponseRequest().withHttpMethod("POST").withRestApiId(decision.getId())
                                          .withResourceId(decisionResource.getId()).withStatusCode("200"));
    }

    private static void createIntegration(AmazonApiGateway gateway, RestApi decision, Resource decisionResource) {

        gateway.putIntegration(new PutIntegrationRequest()
                                       .withCredentials("arn:aws:iam::256265920797:role/lambda_basic_execution")
                                       .withIntegrationHttpMethod("POST").withHttpMethod("POST")
                                       .withType(IntegrationType.AWS).withRestApiId(decision.getId())
                                       .withResourceId(decisionResource.getId())
                                       .withPassthroughBehavior("WHEN_NO_MATCH").withUri(
                        "arn:aws:apigateway:eu-west-1:lambda:path/2015-03-31/functions/arn:aws:lambda:eu-west-1:256265920797:function:math/invocations"));

    }

    private static void createIntegrationResponse(AmazonApiGateway gateway,
                                                  RestApi decision,
                                                  Resource decisionResource) {

        gateway.putIntegrationResponse(new PutIntegrationResponseRequest().withRestApiId(decision.getId())
                                               .withResourceId(decisionResource.getId())
                                               .withContentHandling(ContentHandlingStrategy.CONVERT_TO_TEXT)
                                               .withHttpMethod("POST").withStatusCode("200"));

    }

    private static void createMethodForResource(AmazonApiGateway gateway,
                                                RestApi decision,
                                                GetResourcesResult resources) {
        Map<String, Boolean> params = new HashMap<>();
        params.put("method.request.path.test", true);

        PutMethodResult putMethodResult = gateway.putMethod(new PutMethodRequest()
                                                                    .withResourceId(resources.getItems().stream()
                                                                                            .filter(item -> item
                                                                                                    .getPath()
                                                                                                    .contains("decision"))
                                                                                            .findFirst().get().getId())
                                                                    .withRestApiId(decision.getId())
                                                                    .withAuthorizationType("NONE")
                                                                    .withHttpMethod("POST")
                                                                    .withRequestParameters(params));
    }

    private static void createResource(AmazonApiGateway gateway, RestApi decision, GetResourcesResult resources) {
        CreateResourceResult createResourceResult = gateway
                .createResource(new CreateResourceRequest().withParentId(resources.getItems().get(0).getId())
                                        .withRestApiId(decision.getId()).withPathPart("decision"));
    }

    private static void createRestApi(AmazonApiGateway gateway) {
        CreateRestApiResult result = gateway.createRestApi(new CreateRestApiRequest().withName("Decision")
                                                                   .withDescription("A new Decision gateway"));
    }
}
