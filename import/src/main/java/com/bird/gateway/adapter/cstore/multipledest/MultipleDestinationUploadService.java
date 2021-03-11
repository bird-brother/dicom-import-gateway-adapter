package com.bird.gateway.adapter.cstore.multipledest;

import com.bird.gateway.adapter.cstore.backup.BackupState;
import com.bird.gateway.adapter.cstore.backup.IBackupUploadService;
import com.bird.gateway.adapter.cstore.backup.IBackupUploader.BackupException;
import com.bird.gateway.adapter.cstore.multipledest.sender.CStoreSender;
import com.bird.gateway.adapter.cstore.multipledest.sender.CStoreSenderFactory;
import com.bird.gateway.adapter.monitoring.Event;
import com.google.common.collect.ImmutableList;
import lombok.extern.slf4j.Slf4j;
import org.bird.gateway.IGatewayClient;
import org.bird.gateway.imaging.adapter.AetDictionary.*;
import org.bird.gateway.imaging.adapter.monitoring.MonitoringService;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * @date 14:59 2021-3-2
 * @description TODO
 */
@Slf4j
public class MultipleDestinationUploadService  implements  IMultipleDestinationUploadService{

    private CStoreSenderFactory cStoreSenderFactory;
    private IBackupUploadService backupUploadService;
    private int attemptsAmount;

    public MultipleDestinationUploadService(CStoreSenderFactory cStoreSenderFactory, IBackupUploadService backupUploadService, int attemptsAmount) {
        this.cStoreSenderFactory = cStoreSenderFactory;
        this.backupUploadService = backupUploadService;
        this.attemptsAmount = attemptsAmount;
    }


    @Override
    public void start(ImmutableList<IGatewayClient> healthcareDestinations, ImmutableList<Aet> dicomDestinations, InputStream inputStream,
                      String sopClassUID, String sopInstanceUID) throws MultipleDestinationUploadServiceException{

        if (backupUploadService == null) {
            throw new IllegalArgumentException("backupUploadService is null. Some flags not set.");
        }

        List<Throwable> asyncUploadProcessingExceptions = new ArrayList<>();

        try {
            backupUploadService.createBackup(inputStream, sopInstanceUID);
        } catch (BackupException be) {
            MonitoringService.addEvent(Event.CSTORE_BACKUP_ERROR);
            log.error("{} processing failed.", this.getClass().getSimpleName(), be);
            throw new MultipleDestinationUploadServiceException(be);
        }

        List<CompletableFuture> uploadFutures = new ArrayList<>();

        CompletableFuture healthcareUploadFuture;
        for (IGatewayClient healthcareDest: healthcareDestinations) {
            try {
                healthcareUploadFuture = backupUploadService.startUploading(
                        healthcareDest,
                        new BackupState(sopInstanceUID, attemptsAmount));
                uploadFutures.add(healthcareUploadFuture);
            } catch (BackupException be) {
                log.error("Async upload to healthcareDest task not started.", be);
                asyncUploadProcessingExceptions.add(be);
            }
        }

        if (dicomDestinations.isEmpty() == false) {
            CStoreSender cStoreSender = cStoreSenderFactory.create();

            CompletableFuture dicomUploadFuture;
            for (Aet dicomDest : dicomDestinations) {
                try {
                    dicomUploadFuture = backupUploadService.startUploading(
                            cStoreSender,
                            dicomDest,
                            sopInstanceUID,
                            sopClassUID,
                            new BackupState(sopInstanceUID, attemptsAmount));
                    uploadFutures.add(dicomUploadFuture);
                } catch (BackupException be) {
                    log.error("Async upload to dicomDest task not started.", be);
                    asyncUploadProcessingExceptions.add(be);
                }
            }
        }

        for (CompletableFuture uploadFuture: uploadFutures) {
            try {
                uploadFuture.get();
            } catch (ExecutionException eex) {
                log.error("Exception on asyncUpload Job processing.", eex);
                asyncUploadProcessingExceptions.add(eex.getCause());
            } catch (InterruptedException ie) {
                log.error("CStoreSender task interrupted. Upload tasks canceled.", ie);
                Thread.currentThread().interrupt();
                throw new MultipleDestinationUploadServiceException(ie);
            }
        }

        if (asyncUploadProcessingExceptions.isEmpty()) {
            backupUploadService.removeBackup(sopInstanceUID);
        } else {
            log.error("Exception messages of the upload async jobs:\n{}",
                    asyncUploadProcessingExceptions.stream().map(t -> t.getMessage()).collect(Collectors.joining("\n")));;

            throw new MultipleDestinationUploadServiceException(asyncUploadProcessingExceptions.get(0));
        }
    }





}
