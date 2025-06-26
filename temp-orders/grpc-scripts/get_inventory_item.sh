#!/bin/bash

# Call the token script and capture its output
echo "Requesting JWT token..."
accessToken=$(./get_cognito_token.sh)

if [ -z "${accessToken}" ]; then
  echo "Failed to get cognito token. Exiting." >&2
  exit 1
fi

echo "Token acquired successfully."

grpcServer="localhost:9090"
serviceMethod="inventory.v1.InventoryService/getInventoryItem"

requestData='{
  "itemId": 123
}'

# Call the gRPC endpoint with authentication
grpcurl \
  -H "Authorization: Bearer ${accessToken}" \
  -d "${requestData}" \
  -proto ../src/main/proto/v1/inventory_service.proto \
  -plaintext  \
  "${grpcServer}" \
  "${serviceMethod}"