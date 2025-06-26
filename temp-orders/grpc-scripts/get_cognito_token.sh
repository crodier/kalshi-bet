#!/bin/bash

# get the AWS credentials from the env-local-dev file
if [ -f "../env-local-dev" ]; then
    source ../env-local-dev
else
    echo "Error: ../env-local-dev file does not exist.  Please run the generate_local_dev_secrets.sh script to create it."
    exit 1
fi

source ../env-local-dev

cognitoDomain="https://fbg-user-pool-fbg-dev-1.auth.us-west-2.amazoncognito.com"
tokenUrl="${cognitoDomain}/oauth2/token"

# Send POST request to get the token
response=$(curl -s -X POST \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&client_id=${AWS_FBG_COGNITO_CLIENT_ID}&client_secret=${AWS_FBG_COGNITO_CLIENT_SECRET}" \
  "${tokenUrl}")

# Extract access token from JSON response
accessToken=$(echo "${response}" | grep -o '"access_token":"[^"]*' | cut -d'"' -f4)

echo "${accessToken}"
