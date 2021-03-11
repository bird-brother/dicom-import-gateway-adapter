package com.bird.gateway.adapter.cstore.backup;

import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.ReadChannel;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.util.Arrays;

/**
 * @date 9:28 2021-3-4
 * @description TODO
 */
public class GcpBackupUploader extends AbstractBackupUploader {

    private String projectId;
    private String bucketName;
    private String uploadFolder;
    private Storage storage;

    private static final String GCP_PATH_PREFIX = "gs://";


    public GcpBackupUploader(String uploadFilePath, String gcpProjectId) throws IOException {
        super(uploadFilePath);
        this.projectId = gcpProjectId;
        parseUploadFilePath(getUploadFilePath());
    }

    public GcpBackupUploader(String uploadFilePath, String gcpProjectId, GoogleCredentials googleCredentials) throws IOException {
        super(uploadFilePath);
        this.projectId = gcpProjectId;
        parseUploadFilePath(getUploadFilePath());
        storage = getStorage(googleCredentials);
    }


    @Override
    public void doWriteBackup(InputStream backupData, String uniqueFileName) throws BackupException {
        try {
            if (backupData == null) {
                throw new BackupException("Backup data is null");
            }
            validatePathParameter(uniqueFileName, "unique file name");
            BlobId blobId = BlobId.of(bucketName, getFullUploadObject(uniqueFileName));
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();
            storage.create(blobInfo, backupData);
        } catch (Exception e) {
            throw new BackupException("Error with writing backup file: " + e.getMessage(), e);
        }
    }

    @Override
    public InputStream doReadBackup(String uniqueFileName) throws BackupException {
        try {
            validatePathParameter(uniqueFileName, "unique file name");
            ReadChannel channel = storage.reader(BlobId.of(bucketName, getFullUploadObject(uniqueFileName)));
            return Channels.newInputStream(channel);
        } catch (Exception e) {
            throw new BackupException("Error with reading backup file: " + e.getMessage(), e);
        }
    }

    @Override
    public void doRemoveBackup(String uniqueFileName) throws BackupException {
        try {
            validatePathParameter(uniqueFileName, "unique file name");
            storage.delete(bucketName, getFullUploadObject(uniqueFileName));
        } catch (Exception e) {
            throw new BackupException("Error with removing backup file: " + e.getMessage(), e);
        }
    }


    private void parseUploadFilePath(String uploadFilePath) throws GcpUriParseException {
        try {
            if (!uploadFilePath.startsWith(GCP_PATH_PREFIX)) {
                throw new GcpUriParseException("Not gcs link");
            }
            validatePathParameter(uploadFilePath, "upload file path");
            String route = uploadFilePath.replaceAll(GCP_PATH_PREFIX, "");
            String[] ar = route.split("/");
            bucketName = ar[0];
            uploadFolder = String.join("/", Arrays.copyOfRange(ar, 1, ar.length));
            validatePathParameter(projectId, "project name");
            validatePathParameter(bucketName, "bucket name");
            validatePathParameter(uploadFolder, "upload object");
        } catch (Exception e) {
            throw new GcpUriParseException("Invalid upload path: " + e.getMessage(), e);
        }
    }

    public String getProjectId() {
        return projectId;
    }

    public String getBucketName() {
        return bucketName;
    }

    public String getUploadFolder() {
        return uploadFolder;
    }

    public Credentials getCredential(String oauthScopes) throws IOException {
        validatePathParameter(oauthScopes, "oauthScopes");
        GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
        if (!oauthScopes.isBlank()) {
            credentials = credentials.createScoped(Arrays.asList(oauthScopes.split(",")));
        }
        return credentials;
    }

    private String getFullUploadObject(String uniqueFileName) {
        return uploadFolder.concat("/").concat(uniqueFileName);
    }

    private Storage getStorage(GoogleCredentials googleCredentials) throws IOException {
        return StorageOptions.newBuilder()
                .setCredentials(googleCredentials)
                .setProjectId(projectId)
                .build()
                .getService();
    }

    public static class GcpUriParseException extends IOException {
        public GcpUriParseException(String message, Throwable cause) {
            super(message, cause);
        }

        public GcpUriParseException(String message) {
            super(message);
        }
    }



}
