# Finish Your Project Setup

There are still a few things that you will need to do for this project to be ready:

## Convenience Scripts
The generated project contains a `scripts` directory.  
Some of the scripts in this directory are run when you answer "yes" to the prompts on 
project generation (ex, creating a GitHub repo).  There are other scripts in this directory
that you may find useful. 

Here is an example of running the `remove_sample_code.py` script, which will delete all
code from the project:
```shell
cd scripts
python3 remove_sample_code.py
```

The `scripts` directory is added to the `.gitignore` file by default, since it is an aspect
of project creation and not an essential part of the project itself.  This means that the only
person who will be able to run the scripts is the person who created this project. 
To add this directory to GitHub, remove the reference in the `.gitignore` file and check it in.


## OAuth2 Security Tasks
This project contains services and/or clients that use Spring Security and integrate with
AWS Cognito (both with REST and gRPC).  For the purposes of this template, we created a
Cognito resource server called `microservice-kickstarter Resource Server` and a Cognito client
called `microservice-kickstarter`.  Within that client, there are 2 scopes defined:
* `microservice-kickstarter/inventory:read`
* `microservice-kickstarter/inventory:write`

### General OAuth2 Configuration
In the [application.yml](./src/main/resources/application.yml) file, there is a section for `spring.security.oauth2`.
Make yourself familiar with the settings under this section.

### Terraform Your Own Cognito Resources
These Cognito resources are intended for example use only, and only exist in the DEV environment.
You should NOT use these for any real-world scenarios.

Follow the steps highlighted in the [How-To Doc](https://betfanatics.atlassian.net/wiki/spaces/~630044b443e43992b9a3e6f2/pages/435847938/How-To+Secure+Spring+Boot+endpoints+using+Spring+Security+and+OAuth2+OIDC#2.-Configure-Spring-Security-in-the-Server)
to use terraform to add a Cognito resource server and a user pool client that correspond to your
bounded context.


### Local Testing
As mentioned above, the kickstarter has its own Cognito client that can be used for local testing.
To set this up for a local service, follow these steps:
1. Retrieve the following secrets in our AWS `sportsbook-dev` environment:
* `/fbg/microservice-kickstarter/aws.cognito.client.id`
* `/fbg/microservice-kickstarter/aws.cognito.client.secret`

Take the values of these secrets and set them as environment variables in your run configuration 
for the api project.  **NOTE:  DO NOT CHECK-IN THESE SECRETS TO GITHUB!**

2. Start the service
3. Using the
   [Kickstarter Postman workspace](https://betfanatics-com.postman.co/workspace/microservice-kickstarter~0f66ac37-0e1b-42d2-b378-ee7d4f52b599/overview),
   choose the `cognito-dev` environment.
4. Navigate to the collection that contains endpoints you want to test.  Each collection is documented with more details.





### REST Client Components
The REST client will connect with the Cognito provider to obtain a JWT token.   

In the 
[RestClientConfig](./src/main/java/com/betfanatics/exchange/order/config/RestClientConfig.java)
class, we create a basic REST client that can automatically handle the OAuth2 exchange with our Cognito provider.

In the
[InventoryRestClient](./src/main/java/com/betfanatics/exchange/order/client/InventoryRestClient.java)
class, we inject the Spring RestClient and make a simple API call to an endpoint.  The Spring
RestClient knows how to connect to Cognito based on the configuration settings in the
`spring.security.oauth2` section of the application.yml.


To demonstrate this working, the project has an unsecured
[RestClientController](./src/main/java/com/betfanatics/exchange/order/controllers/RestClientController.java)
class, which simply turns around and makes an authenticated REST call to an endpoint in the secure
[InventoryController](./src/main/java/com/betfanatics/exchange/order/controllers/InventoryController.java)
class.




### gRPC Client Components
The gRPC client will connect with the Cognito provider to obtain a JWT token.  Spring
Security does not have a way to do this automatically, so this is done manually.  Most of this 
is done for you through the 
[grpc-client-auth-lib](https://github.com/fanatics-gaming/grpc-client-auth-lib)
library, which is included as a dependency in this project.  Please refer to the README in that project, 
which will describe the necessary configuration.

In the 
[InventoryGrpcClient](./src/main/java/com/betfanatics/exchange/order/client/InventoryGrpcClient.java)
class, we call out to the GrpcClientHelper to obtain a client JWT token and append it to the outgoing gRPC call.


To demonstrate this working, the project has an unsecured
[GrpcClientController](./src/main/java/com/betfanatics/exchange/order/controllers/GrpcClientController.java)
class, which simply turns around and makes an authenticated gRPC call to the
[InventoryGrpcClient](./src/main/java/com/betfanatics/exchange/order/client/InventoryGrpcClient.java)
class.



__NOTE ON LOAD BALANCING AND KUBERNETES:__
The sample gRPC client is fairly simple.  It does not take into consideration more complex
concerns, such as kubernetes round-robin configuration.  The
[application-local.yml](./src/main/resources/application-local.yml)
file does contain some client-side load-balancing config, but to fully implement this in our kubernetes environment,
please refer to this doc, which goes into much greater detail:
[GRPC Round Robin Kubernetes Headless Service Routing](https://betfanatics.atlassian.net/wiki/spaces/Transforme/pages/1113915479/GRPC+Round+Robin+Kubernetes+Headless+Service+Routing)





### API Service Components
API Spring Security is configured in the
[SecurityConfig](./src/main/java/com/betfanatics/exchange/order/config/SecurityConfig.java)
class, where we specify valid OAuth2 scopes, path filters, and exception handlers.  The only other
configuration needed to enable OAuth2 in your API endpoints is under the `spring.security.oauth2` section of the
[application.yml](./src/main/resources/application.yml) 
file. 




### gRPC Service Components
The 
[grpc-service-auth-lib](https://github.com/fanatics-gaming/grpc-service-auth-lib)
library is included as a dependency in this project, which will handle most of the plumbing for 
securing your gRPC endpoints.  Please refer to the README in that project, which will describe
the necessary configuration.

Once you have done that, all that is needed to secure your gRPC service is to 
annotate your secure method with `@PreAuthorize`.

An example of this can be found in the
[InventoryGrpcService](./src/main/java/com/betfanatics/exchange/order/service/InventoryGrpcService.java)
class.







## Postgres Tasks

This project is targeted to Postgres.
Some example classes have been created for you under the `model`, `entity` and `repository` packages and
the `resources/db/migration` folder.
Please be sure to remove these files or modify them to suit your needs.

### Local configuration

 To run the example that was created with the template, you will need to have an instance of postgres
 running locally on port 5432, with the following settings:

* `database`: postgres
  * `user name`: postgres
  * `password`: postgres

When you start the app, it will connect to the db and will run the flyway migrations.



### Deploy configuration

* [base helmrelease.yaml](./deploy/base/helmrelease.yaml) - `bootstrap.spring.cloud.kubernetes.secrets.sources` - You will need to add a postgres secret that contains the following properties:
  * `spring.datasource.url` - the entire url to the postgres db
  * `spring.datasource.username` - the username credential for the db
  * `spring.datasource.password` - the password credential for the db






## Kafka Tasks

This project integrates with Kafka.

### Local configuration

* Make sure you have kafka running locally, listening on port 9092.  You will also need
kafka-ui.  Both can be obtained from the [local-dev](https://github.com/fanatics-gaming/local-dev) repo.
* Any kafka topics will be created automatically when you start the project locally.



### Deploy configuration

1. When running locally, we relied on the `KafkaTopicConfigLocal` class to auto-create the topics for us.
   For our deployed environments, we rely on a special helm file to configure those.  The file is called
   `topics.yaml`.  Edit that file to confirm that the settings are correct.
2. The `kafka-sasl` secret has been added to your `helmrelease.yaml` file, so the kafka SASL
   secrets should automatically bind to your spring properties and connect to the correct kafka
   broker.







## General Deployment Tasks

In order to deploy this project, be sure to finish the following:

### 1. Complete the Datadog service definition

Edit the [service.datadog.yaml](./service.datadog.yaml) file in the project root and search for the
`TODO` placeholders, replacing this with actual values.

### 2. Register your github repo in the github-terraform repository

Refer to [This doc](https://betfanatics.atlassian.net/wiki/spaces/Platform/pages/153518928/Tutorials+Getting+Started+Building+and+Deploying+new+Spring+Boot+Application#1.1-Register-Your-repo)
to register your new github repo.  The specific terraform file that you
will need to modify is [here](https://github.com/fanatics-gaming/github-terraform/blob/main/deploy-repos.yaml)

This will also allow you to configure branch protection, which is strongly encouraged.

### 3. Create the project in SonarCloud

This project uses SonarCloud. When you create a new project, it will need to be added to SonarCloud.

1. From Okta, choose the `SonarQube Cloud` tile.
2. Click the `+` button on the top right to create a new Sonar project.
3. Select `Analyze new project`.
4. Search for your new repo, select the checkbox next to it, and click `Set Up`.
5. Do not change any of the default settings.
6. Click `Done`.


### 4. Complete the helmrelease.yaml file

1. Open the [base helmrelease.yaml file](./deploy/base/helmrelease.yaml)
2. Search for the `TODO` placeholders, replacing this with actual values.
3. Confirm all other settings are correct. Pay special attention to the following:
    1. Settings for `minReplicas` and `maxReplicas`
    2. Injection of secrets.  Some default secrets are added for you under `bootstrap.spring.cloud.kubernetes.secrets.sources`.  If you need more secrets, add them here.




### 5. Create NGINX routing to your service, if needed

Decide if your service has any specific routing needs. If your service will only be called by other services within the same Kubernetes cluster, then Kubernetes Service Discovery is sufficient and no additional configuration is needed here. If additional configuration is needed (ex: your service runs in the parent environment but will be called from child environments, is public facing, etc), then follow these steps:

1. Reference the [How Http Traffic Routing Works](https://betfanatics.atlassian.net/wiki/spaces/Platform/pages/263198454/Reference+How+HTTP+Traffic+Routing+Works) document and follow one of the links for adding a route to a new or existing subdomain.
2. Update `server.servlet.contextPath` in your `application.yml` to match whatever path prefix NGINX is using to route traffic to your service.






## Recommended Tasks

### Configure only allowing PR "Squash and Merge" for the new GitHub repo

This will allow for a cleaner commit history by not showing the intermediate commits with less descriptive commit messages.

1. From [github](https://github.com/fanatics-gaming), navigate to the main page of the new repository.
2. Under the repository name, click "Settings".
3. Under the "Pull Requests" section, please only select "Allow squash merging".

Reference: [Configuring commit squashing for pull requests](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/configuring-pull-request-merges/configuring-commit-squashing-for-pull-requests)
