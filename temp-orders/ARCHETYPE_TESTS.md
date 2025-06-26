# API Tests

This guide provides instructions on how to run some basic runtime tests to verify that all the
plumbing is working.

### Prerequisites
* Complete all the "Local configuration" tasks in the
  [FINISH_YOUR_SETUP](FINISH_YOUR_SETUP.md) doc.
* Complete the [QUICK_START](QUICK_START.md) guide.
* Your api service should be started with no errors.

### Context
* The api project starts up with an `inventory` repository that will allow us to save and 
retrieve records.  
* There is a corresponding `InventoryController` with POST and GET endpoints to save and retrieve these records.



### OpenAPI / Swagger Integration
You can access the automatically generated OpenAPI / Swagger page by hitting this
url relative to the context path: `/swagger-ui/index.html`

For example, running locally that would be: http://localhost:8080/swagger-ui/index.html

### Testing API endpoints
The API endpoints in this project are protected by a custom cognito client.  In order to test
your endpoints, you need to open the [microservice-kickstarter Postman workspace](https://betfanatics-com.postman.co/workspace/0f66ac37-0e1b-42d2-b378-ee7d4f52b599).
Within that workspace, open the `cognito` collection.  There, you will see the requests that you
can run to test this local running API.  In order to test these, be sure to first choose the `cognito-dev` environment.

The `cognito` collection in this workspace executes a pre-request script which first obtains the
JWT token from cognito and then applies it to the request.  For more information on how this is 
configured, please refer to the [How-To Doc](https://betfanatics.atlassian.net/wiki/spaces/~630044b443e43992b9a3e6f2/pages/435847938/How-To+Secure+Spring+Boot+endpoints+using+Spring+Security+and+OAuth2+OIDC#2.-Configure-Spring-Security-in-the-Server).

For more details on how security is configured, check out the [SecurityConfig](src/main/java/com/betfanatics/exchange/order/config/SecurityConfig.java) class.
