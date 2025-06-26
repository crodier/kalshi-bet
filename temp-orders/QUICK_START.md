# Quick Start Guide

Congratulations, your project has been created!   
Follow these steps to get started as quickly as possible...

### Prerequisites
* Complete all the "Local configuration" tasks in the
[FINISH_YOUR_SETUP](FINISH_YOUR_SETUP.md) doc.
* Download and install Java 21.
* This guide assumes you are using the IntelliJ IDE.

### Open the project
1. From IntelliJ, choose `File -> New -> Project from Existing Sources...`
2. From the window that pops up, select `Import project from external model` and choose `Maven`
3. Click `Create`
4. When prompted where you would like to open the project, click `New Window`

A new window will open with the project that was created.

### Build
1. Open the 'Project View' by choosing `View -> Tool Windows -> Project`
2. From the Project View, right-click on the project name and choose "Build module".
3. Choose the "Build" tab to see the build results.  If you do not have the Build tab, you can
add it by choosing `View -> Tool Windows -> Build`.

### Run unit tests
1. From the Project View, right-click on the project name and choose "Run All Tests".
2. The Test Runner tab should open automatically to show test results.  


### Start the service locally
1. Open the 'Services View' by choosing `View -> Tool Windows -> Services`
2. Click the `Add service` link
3. Choose `Run Configuration Type`
4. In the list that pops up, scroll down and choose `Spring Boot`
5. Your project will be automatically added as a service.  
6. Click on the project name and click the green triangle "Run" button.

You should see the log entries with no errors and the service will start.


### Generating local dev secrets automatically
This repo includes a method for generating local secrets to help with running services locally against dev. 
The repo includes a script called generate_local_dev_secrets.sh, which will use aws-vault to fetch parameters
from the sportsbook-dev parameter store. It will then save them into a local file called env_local_dev (excluded from git by default).
Finally, if you install the EnvFile plugin and enable your local run configuration to use it, it will automatically pull in the env variables
when running locally. Now you don't have to go through the manual process of fetching the secrets from the dev parameter store
or a team 1password vault. You also don't have to worry about accidentally committing the raw secrets values to source code. Here's a guide:

#### Pre setup
1. Install aws-vault
    - See this [confluence page](https://betfanatics.atlassian.net/wiki/spaces/Platform/pages/651001880) for a guide on setup
2. Install aws-cli
    - See this [confluence page](https://betfanatics.atlassian.net/wiki/spaces/Platform/pages/651001880) for a guide on setup
3. Install EnvFile Plugin
    - This is an Intellij Plugin that you can find by searching on the Intellij Plugin marketplace

#### Automated Secret Fetching
1. Run the generate_local_dev_secrets.sh script in your terminal, it will generate a file called env-local-dev in the root directory
2. Click edit on the Run Configuration you created above.
3. In the edit menu, click the checkbox for "Enable EnvFile". It should be located under the "active profiles" section.
4. After enabling, click the '+' in the box below the checkboxes and navigate to the file "env-local-dev" in the finder window.
It will be generated in the same location you ran the generate script.
5. Now when running with the local profile enabled, the secrets will be auto-generated. If you think a secret value has changed, 
just re-run the script and you're good to go.


### Perform some local testing
Each archetype project can be tested at runtime, to verify that all the plumbing is working.  
See [ARCHETYPE_TESTS](ARCHETYPE_TESTS.md) for more details.


### Prepare to deploy
* Before deploying, make sure to change the scaling behavior. Generally, this means patching the dev-1 environment to scale up to 1 or more replicas.
* When you are ready for your service to go to all environments you can uncomment the scaling behavior in `deploy/base` to enable deployment by default