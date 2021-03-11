package com.bird.gateway.adapter.cstore.backup;

import java.io.InputStream;
import java.util.concurrent.CompletableFuture;

import com.bird.gateway.adapter.cstore.backup.IBackupUploader.BackupException;
import com.bird.gateway.adapter.cstore.multipledest.sender.CStoreSender;
import org.bird.gateway.IGatewayClient;
import org.bird.gateway.imaging.adapter.AetDictionary.*;

/**
 * @date 13:17 2021-3-2
 * @description TODO
 */
public interface IBackupUploadService {

    void createBackup(InputStream inputStream, String uniqueFileName) throws BackupException;

    CompletableFuture startUploading(IGatewayClient webClient, BackupState backupState) throws BackupException;

    CompletableFuture startUploading(CStoreSender cStoreSender, Aet target, String sopInstanceUid, String sopClassUid,
                                     BackupState backupState) throws BackupException;

    void removeBackup(String uniqueFileName);

}
