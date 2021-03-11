package com.bird.gateway.adapter.cstore.backup;

import com.bird.gateway.adapter.cstore.multipledest.sender.CStoreSender;
import com.bird.gateway.adapter.monitoring.Event;
import com.google.common.collect.ImmutableList;
import lombok.extern.slf4j.Slf4j;
import org.bird.gateway.IGatewayClient;
import org.bird.gateway.IGatewayClient.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import com.bird.gateway.adapter.cstore.backup.IBackupUploader.BackupException;
import org.bird.gateway.imaging.adapter.AetDictionary.*;
import org.bird.gateway.imaging.adapter.monitoring.MonitoringService;

/**
 * @date 13:16 2021-3-2
 * @description TODO
 */
@Slf4j
public class BackupUploadService  implements  IBackupUploadService{

    private final DelayCalculator delayCalculator;
    private final IBackupUploader backupUploader;
    private final ImmutableList<Integer> httpErrorCodesToRetry;
    private final int attemptsAmount;


    /**
     * Create BackupUploadService instance.
     * @param backupUploader DAO with simple write/read/remove operations.
     * @param delayCalculator util class for reSend tasks schedule delay calculation.
     * @since 2021-3-2 13:50
     */
    public BackupUploadService(IBackupUploader backupUploader, Integer attemptsAmount,
                               ImmutableList<Integer> httpErrorCodesToRetry, DelayCalculator delayCalculator) {
        this.backupUploader = backupUploader;
        this.attemptsAmount = attemptsAmount;
        this.httpErrorCodesToRetry = httpErrorCodesToRetry;
        this.delayCalculator = delayCalculator;
    }


    @Override
    public void createBackup(InputStream inputStream, String uniqueFileName) throws BackupException {
        backupUploader.doWriteBackup(inputStream, uniqueFileName);
        log.debug("sopInstanceUID={}, backup saved.", uniqueFileName);
    }


    @Override
    public CompletableFuture startUploading(IGatewayClient client, BackupState backupState) throws BackupException {
        return scheduleUploadWithDelay(backupState, new HealthcareDestinationUploadAsyncJob(client, backupState), 0);
    }


    @Override
    public CompletableFuture startUploading(CStoreSender cStoreSender, Aet target, String sopInstanceUid, String sopClassUid,
                                            BackupState backupState) throws BackupException {
        return scheduleUploadWithDelay(backupState, new DicomDestinationUploadAsyncJob(cStoreSender, backupState, target, sopInstanceUid, sopClassUid), 0);
    }


    @Override
    public void removeBackup(String fileName) {
        try {
            backupUploader.doRemoveBackup(fileName);
            log.debug("sopInstanceUID={}, removeBackup successful.", fileName);
        } catch (IOException ex) {
            MonitoringService.addEvent(Event.CSTORE_BACKUP_ERROR);
            log.error("sopInstanceUID={}, removeBackup failed.", fileName, ex);
        }
    }







    public abstract class UploadAsyncJob implements Runnable {
        protected BackupState backupState;
        protected String uniqueFileName;
        protected int attemptNumber;

        public UploadAsyncJob(BackupState backupState) {
            this.backupState = backupState;
            this.uniqueFileName = backupState.getUniqueFileName();
            this.attemptNumber = attemptsAmount + 2 - backupState.getAttemptsCountdown();
        }

        protected void logUploadFailed(Exception e) {
            log.error("sopInstanceUID={}, upload attempt {} - failed.", uniqueFileName, attemptNumber, e);
        }

        protected void logSuccessUpload() {
            log.debug("sopInstanceUID={}, upload attempt {}, - successful.", uniqueFileName, attemptNumber);
        }

        protected InputStream readBackupExceptionally() throws CompletionException {
            try {
                return backupUploader.doReadBackup(uniqueFileName);
            } catch (BackupException ex) {
                MonitoringService.addEvent(Event.CSTORE_BACKUP_ERROR);
                log.error("sopInstanceUID={}, read backup failed.", uniqueFileName, ex.getCause());
                throw new CompletionException(ex);
            }
        }
    }


    public class DicomDestinationUploadAsyncJob extends UploadAsyncJob {

        private CStoreSender cStoreSender;
        private Aet target;
        private String sopInstanceUid;
        private String sopClassUid;

        public DicomDestinationUploadAsyncJob(CStoreSender cStoreSender, BackupState backupState, Aet target,
                                              String sopInstanceUid, String sopClassUid) {
            super(backupState);
            this.cStoreSender = cStoreSender;
            this.target = target;
            this.sopInstanceUid = sopInstanceUid;
            this.sopClassUid = sopClassUid;
        }

        @Override
        public void run() {
            try {
                InputStream inputStream = readBackupExceptionally();
                cStoreSender.cstore(target, sopInstanceUid, sopClassUid, inputStream);
                logSuccessUpload();
            }catch (IOException io) {
                logUploadFailed(io);
                if (backupState.getAttemptsCountdown() > 0) {
                    try {
                        scheduleUploadWithDelay(backupState,
                                new DicomDestinationUploadAsyncJob(cStoreSender, backupState, target, sopInstanceUid, sopClassUid),
                                delayCalculator.getExponentialDelayMillis(backupState.getAttemptsCountdown(), attemptsAmount)).get();
                    }catch (BackupException | ExecutionException | InterruptedException ex) {
                        throw new CompletionException(ex);
                    }
                }else {
                    MonitoringService.addEvent(Event.CSTORE_ERROR);
                    throwOnNoResendAttemptsLeft(null, uniqueFileName);
                }
            }catch (InterruptedException ie) {
                log.error("cStoreSender.cstore interrupted. Runnable task canceled.", ie);
                Thread.currentThread().interrupt();
                throw new CompletionException(ie);
            }
        }
    }


    public class HealthcareDestinationUploadAsyncJob extends UploadAsyncJob {

        private IGatewayClient gatwayClient;

        public HealthcareDestinationUploadAsyncJob(IGatewayClient gatwayClient, BackupState backupState) {
            super(backupState);
            this.gatwayClient = gatwayClient;
        }

        @Override
        public void run() {
            try {
                InputStream inputStream = readBackupExceptionally();
                gatwayClient.stowRs(inputStream);
                logSuccessUpload();
            } catch (DicomGatewayException dwe) {
                logUploadFailed(dwe);

                if (filterHttpCode(dwe.getHttpStatus())) {
                    if (backupState.getAttemptsCountdown() > 0) {
                        try {
                            scheduleUploadWithDelay(
                                    backupState,
                                    new HealthcareDestinationUploadAsyncJob(gatwayClient, backupState),
                                    delayCalculator.getExponentialDelayMillis(backupState.getAttemptsCountdown(), attemptsAmount))
                                    .get();

                        } catch (BackupException | ExecutionException | InterruptedException ex) {
                            throw new CompletionException(ex);
                        }
                    } else {
                        MonitoringService.addEvent(Event.CSTORE_ERROR);
                        throwOnNoResendAttemptsLeft(dwe, uniqueFileName);
                    }
                } else {
                    MonitoringService.addEvent(Event.CSTORE_ERROR);
                    throwOnHttpFilterFail(dwe, dwe.getHttpStatus());
                }
            }
        }
    }


    private CompletableFuture scheduleUploadWithDelay(BackupState backupState, Runnable uploadJob, long delayMillis) throws BackupException {
        String uniqueFileName = backupState.getUniqueFileName();
        log.info("Trying to send data, sopInstanceUID={}, attempt {}. ",
                uniqueFileName,
                2 + attemptsAmount - backupState.getAttemptsCountdown());
        if (backupState.decrement()) {
            return CompletableFuture.runAsync(uploadJob, CompletableFuture.delayedExecutor(delayMillis, TimeUnit.MILLISECONDS));
        } else {
            MonitoringService.addEvent(Event.CSTORE_ERROR);
            throw getNoResendAttemptLeftException(null, uniqueFileName);
        }
    }

    private boolean filterHttpCode(Integer actualHttpStatus) {
        return actualHttpStatus >= 500 || httpErrorCodesToRetry.contains(actualHttpStatus);
    }


    private void throwOnHttpFilterFail(DicomGatewayException dwe, int httpCode) throws CompletionException {
        String errorMessage = "Not retried due to HTTP code=" + httpCode;
        log.debug(errorMessage);
        throw new CompletionException(new BackupException(dwe.getStatus(), dwe, errorMessage));
    }


    private void throwOnNoResendAttemptsLeft(DicomGatewayException dwe, String uniqueFileName) throws CompletionException {
        throw new CompletionException(getNoResendAttemptLeftException(dwe, uniqueFileName));
    }


    private BackupException getNoResendAttemptLeftException(DicomGatewayException dwe, String uniqueFileName) {
        String errorMessage = "sopInstanceUID=" + uniqueFileName + ". No upload attempt left.";
        log.debug(errorMessage);
        if (dwe != null) {
            return new BackupException(dwe.getStatus(), dwe, errorMessage);
        } else {
            return new BackupException(errorMessage);
        }
    }








}





