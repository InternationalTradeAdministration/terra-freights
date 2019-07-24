#!/usr/bin/env bash
cd docker
sudo az acr login --name itatarifftool
sudo docker build -t itatarifftool.azurecr.io/fta-tariff-tool-dataloader .
sudo docker push itatarifftool.azurecr.io/fta-tariff-tool-dataloader
sudo az container delete --resource-group ita-tariff-tool-resources --name itatarifftool --yes
sudo az container create --resource-group ita-tariff-tool-resources --name itatarifftool \
    --image itatarifftool.azurecr.io/fta-tariff-tool-dataloader:latest --dns-name-label fta-tariff-tool-dataloader --ports 8080 \
    --location westus --registry-username itatarifftool \
    --environment-variables 'TARIFFTOOL_AZURE_STORAGE_ACCOUNT'=$TARIFFTOOL_AZURE_STORAGE_ACCOUNT \
    'TARIFFTOOL_AZURE_STORAGE_ACCOUNT_KEY'=$TARIFFTOOL_AZURE_STORAGE_ACCOUNT_KEY \
    'TARIFFTOOL_AZURE_STORAGE_CONTAINER'=$TARIFFTOOL_AZURE_STORAGE_CONTAINER \
    'TARIFFTOOL_AZURE_OAUTH_CLIENT_ID'=TARIFFTOOL_AZURE_OAUTH_CLIENT_ID \
    'TARIFFTOOL_AZURE_OAUTH_CLIENT_SECRET'=TARIFFTOOL_AZURE_OAUTH_CLIENT_SECRET \
    'TARIFFTOOL_AZURE_OAUTH_TENANT_ID'=TARIFFTOOL_AZURE_OAUTH_TENANT_ID \

#FYI: Registry credentials are autogenerated and can be procured from portal.azure.com

#to view logs
#az container logs --resource-group ita-tariff-tool-resources --name itatarifftool
cd ..