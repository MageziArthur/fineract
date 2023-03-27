/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.infrastructure.configuration.data;

public class AzureConfigurationData {

    private final String accountKey;
    private final String accountName;
    private final String endpointSuffix;
    private final String containerName;

    public AzureConfigurationData(String accountKey, String accountName, String endpointSuffix, String containerName) {
        this.accountKey = accountKey;
        this.accountName = accountName;
        this.endpointSuffix = endpointSuffix;
        this.containerName = containerName;
    }

    public String getAccountKey() {
        return accountKey;
    }

    public String getAccountName() {
        return accountName;
    }

    public String getEndpointSuffix() {
        return endpointSuffix;
    }

    public String getContainerName() {
        return containerName;
    }
}
