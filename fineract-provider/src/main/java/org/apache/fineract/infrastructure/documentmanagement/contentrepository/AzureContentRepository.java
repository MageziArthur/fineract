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
package org.apache.fineract.infrastructure.documentmanagement.contentrepository;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobErrorCode;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.specialized.BlockBlobClient;
import com.azure.storage.common.StorageSharedKeyCredential;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.io.ByteSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Locale;
import org.apache.commons.io.FilenameUtils;
import org.apache.fineract.infrastructure.core.domain.Base64EncodedImage;
import org.apache.fineract.infrastructure.documentmanagement.command.DocumentCommand;
import org.apache.fineract.infrastructure.documentmanagement.data.DocumentData;
import org.apache.fineract.infrastructure.documentmanagement.data.FileData;
import org.apache.fineract.infrastructure.documentmanagement.data.ImageData;
import org.apache.fineract.infrastructure.documentmanagement.domain.StorageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AzureContentRepository implements ContentRepository {

    private static final Logger LOG = LoggerFactory.getLogger(AzureContentRepository.class);

    private StorageSharedKeyCredential credential;
    private String endpoint;
    private BlobServiceClient storageClient;
    private BlobContainerClient blobContainerClient;
    private String containerName;

    public AzureContentRepository(final String accountKey, final String accountName, final String endpointSuffix, String containerName) {
        this.containerName = containerName;
        credential = new StorageSharedKeyCredential(accountName, accountKey);
        endpoint = String.format(Locale.ROOT, "https://%s.blob.%s", accountName, endpointSuffix);
        storageClient = new BlobServiceClientBuilder().endpoint(endpoint).credential(credential).buildClient();
        blobContainerClient = storageClient.getBlobContainerClient(containerName);
        try {
            blobContainerClient.create();
            LOG.info("Create completed%n");
        } catch (BlobStorageException error) {
            if (error.getErrorCode().equals(BlobErrorCode.CONTAINER_ALREADY_EXISTS)) {
                LOG.warn("Can't create container. It already exists %n");
            }
        }
    }

    @Override
    public String saveFile(InputStream uploadedInputStream, DocumentCommand documentCommand) {

        final String uploadDocFolder = generateFileParentDirectory(documentCommand.getParentEntityType(),
                documentCommand.getParentEntityId());
        final String uploadDocFullPath = uploadDocFolder + File.separator + documentCommand.getFileName();

        BlobClient blobClient = blobContainerClient.getBlobClient(uploadDocFullPath);
        try {
            byte[] data = uploadedInputStream.readAllBytes();
            blobClient.upload(new ByteArrayInputStream(data), data.length);
            uploadedInputStream.close();
            return blobClient.getBlobUrl();
        } catch (IOException e) {
            LOG.error(e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void deleteFile(String documentPath) {
        BlockBlobClient blockBlobClient = blobContainerClient.getBlobClient(getFileDirectoryFromLocation(documentPath))
                .getBlockBlobClient();
        blockBlobClient.delete();
    }

    @Override
    public FileData fetchFile(DocumentData documentData) {
        String filePath = getFileDirectoryFromLocation(documentData.fileLocation());
        BlockBlobClient blobClient = blobContainerClient.getBlobClient(filePath).getBlockBlobClient();
        int dataSize = (int) blobClient.getProperties().getBlobSize();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(dataSize);
        blobClient.download(outputStream);

        return new FileData(new ByteSource() {

            @Override
            public InputStream openStream() throws IOException {
                return new ByteArrayInputStream(outputStream.toByteArray());
            }
        }, documentData.fileName(), documentData.contentType());
    }

    @Override
    public String saveImage(InputStream uploadedInputStream, Long resourceId, String imageName, Long fileSize) {
        final String uploadImageLocation = generateClientImageParentDirectory(resourceId);
        final String fileLocation = uploadImageLocation + File.separator + imageName;

        BlobClient blobClient = blobContainerClient.getBlobClient(fileLocation);
        try {
            byte[] data = uploadedInputStream.readAllBytes();
            blobClient.upload(new ByteArrayInputStream(data), data.length);
            uploadedInputStream.close();
            return blobClient.getBlobUrl();
        } catch (Exception e) {
            LOG.error(e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public String saveImage(Base64EncodedImage base64EncodedImage, Long resourceId, String imageName) {

        final String uploadImageLocation = generateClientImageParentDirectory(resourceId);
        final String fileLocation = uploadImageLocation + File.separator + imageName + base64EncodedImage.getFileExtension();

        BlobClient blobClient = blobContainerClient.getBlobClient(fileLocation);
        String base64Data = Iterables.get(Splitter.on(',').split(base64EncodedImage.getBase64EncodedString()), 1);
        byte[] base64DataBytes = Base64.getDecoder().decode(base64Data);

        ByteArrayInputStream dataStream = new ByteArrayInputStream(base64DataBytes);
        blobClient.upload(dataStream, base64DataBytes.length);
        return blobClient.getBlobUrl();
    }

    @Override
    public void deleteImage(String location) {
        BlockBlobClient blockBlobClient = blobContainerClient.getBlobClient(FilenameUtils.getName(location)).getBlockBlobClient();
        blockBlobClient.delete();
    }

    @Override
    public FileData fetchImage(ImageData imageData) {

        BlockBlobClient blobClient = blobContainerClient.getBlobClient(getFileDirectoryFromLocation(imageData.location()))
                .getBlockBlobClient();
        int dataSize = (int) blobClient.getProperties().getBlobSize();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(dataSize);
        blobClient.download(outputStream);

        return new FileData(new ByteSource() {

            @Override
            public InputStream openStream() throws IOException {
                return new ByteArrayInputStream(outputStream.toByteArray());
            }
        }, imageData.getEntityDisplayName(), imageData.contentType().getValue());
    }

    @Override
    public StorageType getStorageType() {
        return StorageType.AZURE;
    }

    private String generateFileParentDirectory(final String entityType, final Long entityId) {
        return "documents" + File.separator + entityType + File.separator + entityId + File.separator
                + ContentRepositoryUtils.generateRandomString();
    }

    private String generateClientImageParentDirectory(final Long resourceId) {
        return "images" + File.separator + "clients" + File.separator + resourceId + File.separator
                + ContentRepositoryUtils.generateRandomString();
    }

    private String getFileDirectoryFromLocation(String location) {
        return Iterables.get(Splitter.onPattern(containerName + "/").split(location), 1);
    }

}
