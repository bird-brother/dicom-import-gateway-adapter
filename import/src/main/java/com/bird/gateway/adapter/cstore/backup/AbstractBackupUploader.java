package com.bird.gateway.adapter.cstore.backup;

/**
 * @date 9:27 2021-3-4
 * @description TODO
 */
public abstract class AbstractBackupUploader implements IBackupUploader{

    private String uploadFilePath;

    public AbstractBackupUploader(String uploadFilePath) {
        this.uploadFilePath = uploadFilePath;
    }

    public String getUploadFilePath() {
        return uploadFilePath;
    }

    public void validatePathParameter(String parameterValue, String parameterName) throws BackupException {
        if (parameterValue == null || parameterValue.isBlank()) {
            throw new BackupException("Invalid upload path, parameter - " + parameterName + " is blank.");
        }
    }


}
