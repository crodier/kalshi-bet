#!/bin/bash
# This script uses aws-vault and awscli to fetch multiple AWS SSM parameters and writes them to a .env file.
# Update the AWS_PROFILE and AWS_DEFAULT_REGION to your desired values if you don't want the defaults
# The PARAM_PAIRS array should contain comma-separated pairs:
#   <SSM Parameter Path>,<Environment Variable Name>

# Default to sportsbook dev account and admin access
AWS_PROFILE="sportsbook-dev.AdministratorAccess"
# Default to us-west-2 region
export AWS_DEFAULT_REGION=us-west-2

# Define your list of SSM parameter paths and desired environment variable names.
declare -a PARAM_PAIRS=(
  ### Put your param_path,env_variable_name pairings here ###
  "/fbg/microservice-kickstarter/aws.cognito.client.id,AWS_FBG_COGNITO_CLIENT_ID"
  "/fbg/microservice-kickstarter/aws.cognito.client.secret,AWS_FBG_COGNITO_CLIENT_SECRET"
)

# File to output environment variables
ENV_FILE="env-local-dev"
echo "### Generated $ENV_FILE by generate-env.sh on $(date)" > "$ENV_FILE"

# Loop over each pair, fetch the parameter value, and append to the .env file
for pair in "${PARAM_PAIRS[@]}"; do
    # Split the string at the comma into two variables
    IFS=',' read -r ssm_path env_var <<< "$pair"

    echo "Fetching SSM parameter: $ssm_path"

    # Fetch the parameter value using aws-vault (with decryption)
    value=$(aws-vault exec "$AWS_PROFILE" -- aws ssm get-parameter --name "$ssm_path" --with-decryption --query "Parameter.Value" --output text)

    if [ $? -ne 0 ]; then
      echo "Error: Failed to retrieve parameter $ssm_path" >&2
      continue
    fi

    # Append the variable to the env file
    echo "${env_var}=${value}" >> "$ENV_FILE"
done

echo ".env file generated successfully"
