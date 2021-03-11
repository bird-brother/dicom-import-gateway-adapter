package com.bird.gateway.adapter.cstore.multipledest;

import com.bird.gateway.adapter.cstore.backup.IBackupUploader.BackupException;
import com.google.common.collect.ImmutableList;
import org.bird.gateway.IGatewayClient;
import org.bird.gateway.imaging.adapter.AetDictionary.Aet;

import java.io.InputStream;

/**
 * @date 14:59 2021-3-2
 * @description TODO
 */
public interface IMultipleDestinationUploadService {

    void start(ImmutableList<IGatewayClient> healthcareDestinations,
               ImmutableList<Aet> dicomDestinations,
               InputStream inputStream,
               String sopClassUID,
               String sopInstanceUID) throws MultipleDestinationUploadServiceException;


    class MultipleDestinationUploadServiceException extends Exception {

        private Integer dicomStatus;

        public MultipleDestinationUploadServiceException(Throwable cause) {
            super(cause);
        }

        public MultipleDestinationUploadServiceException(BackupException be) {
            super(be);
            this.dicomStatus = be.getDicomStatus();
        }

        public Integer getDicomStatus() {
            return dicomStatus;
        }

    }


}
