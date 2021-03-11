package org.bird.gateway;

import com.google.api.client.http.HttpStatusCodes;
import org.dcm4che3.net.Status;
import org.json.JSONArray;

import java.io.InputStream;

/**
 * @date 15:57 2021-2-26
 * @description TODO
 */
public interface IGatewayClient {

    /**
     * wado
     * @params path
     * @return InputStream
     * @since 2021-3-1 9:18
     */
    InputStream wadoRs (String path) throws DicomGatewayException;

    /**
     * qido
     * @params path
     * @return JSONArray
     * @since 2021-3-1 9:18
     */
    JSONArray qidoRs(String path) throws DicomGatewayException;

    /**
     * stow
     * @params path
     * @since 2021-3-1 9:18
     */
    void stowRs(InputStream in) throws DicomGatewayException;





    /**
     * An exception for errors returned by the Dicom Gateway server.
     */
    class DicomGatewayException extends Exception {

        private int status = Status.ProcessingFailure;
        private int httpStatus;


        public DicomGatewayException(String message, int status) {
            super(message);
            this.status = status;
        }

        public DicomGatewayException(Throwable cause, int status) {
            super(cause);
            this.status = status;
        }

        public DicomGatewayException(String message, Throwable cause, int status) {
            super(message, cause);
            this.status = status;
        }

        public DicomGatewayException(String message, int httpStatus, int defaultDicomStatus) {
            super(message);
            this.status = httpStatusToDicomStatus(httpStatus, defaultDicomStatus);
            this.httpStatus = httpStatus;
        }

        public DicomGatewayException(String message, Throwable cause, int httpStatus, int defaultDicomStatus) {
            super(message, cause);
            this.status = httpStatusToDicomStatus(httpStatus, defaultDicomStatus);
            this.httpStatus = httpStatus;
        }

        public DicomGatewayException(String message) {
            super(message);
        }

        public DicomGatewayException(Throwable cause) {
            super(cause);
        }


        public int getStatus() {
            return status;
        }

        public int getHttpStatus() {
            return httpStatus;
        }



        private int httpStatusToDicomStatus(int httpStatus, int defaultStatus) {
            switch (httpStatus) {
                case HttpStatusCodes.STATUS_CODE_SERVICE_UNAVAILABLE:
                    return Status.OutOfResources;
                case HttpStatusCodes.STATUS_CODE_UNAUTHORIZED:
                    return Status.NotAuthorized;
                default:
                    return defaultStatus;
            }
        }


    }








}
