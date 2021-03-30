package com.bird.gateway.adapter;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * @author wangpeng
 * @date 14:53 2021-3-1
 * @description TODO
 */
@Data
@Slf4j
public class Flags {
    private static final String PROPERTIES_NAME = "\\config\\application.properties";

    public void setFlag(){
        FileInputStream in = null;
        try {
            String confPath = System.getProperty("user.dir");
            log.info("confPath£º"+confPath);
            Properties properties = new Properties();
            in = new FileInputStream(confPath+PROPERTIES_NAME);
            properties.load(in);

            clientUID = properties.getProperty("config.client.uid");
            dimseAET = properties.getProperty("config.dimse.aet");
            dimsePort = Integer.valueOf(properties.getProperty("config.dimse.port"));
            gatewayAddress = properties.getProperty("config.dicom.address");
            persistentFileStorageLocation = properties.getProperty("config.file.storage.location");
            persistentFileUploadRetryAmount = Integer.valueOf(properties.getProperty("config.file.upload.amount"));
            transcodeToSyntax = properties.getProperty("config.dicom.transcode");


        }catch (IOException e){
            log.error(e.getMessage());
        }finally {
            if(in != null){
                try {
                    in.close();
                }catch (IOException e){
                    log.error(e.getMessage());
                }
            }
        }
    }






    String clientUID = "";

    /**
     * Title of DIMSE Application Entity.
     */

    String dimseAET = "";
    /**
     * Port the server is listening to for incoming DIMSE requests.
     */
    Integer dimsePort = 0;

    String dicomwebAddr = "";
    String dicomwebStowPath = "";
    boolean verbose = true;
    String aetDictionaryPath = "";
    String aetDictionaryInline = "";
    String destinationConfigPath = "";
    String destinationConfigInline = "";
    Boolean fuzzyMatching = false;
    String gcsBackupProjectId = "";
    Integer minUploadDelay = 10;
    Integer maxWaitingTimeBetweenUploads = 10;
    List<Integer> httpErrorCodesToRetry = new ArrayList<>();
    Boolean sendToAllMatchingDestinations = false;

    /**
     * Address for Dicom Gateway service.
     */
     String gatewayAddress = "";

    /**
     * temporary location for storing files before send
     */
    String persistentFileStorageLocation = "";

    /**
     * upload retry amount
     */
    Integer persistentFileUploadRetryAmount = 0;

    /**
     * Transfer Syntax to convert instances to during C-STORE upload. See Readme for list of supported syntaxes.
     */
    String transcodeToSyntax = "";

    /**
     * (Optional) Separate AET used for C-STORE calls within context of C-MOVE.
     */
    String dimseCmoveAET = "";

    /**
     * Whether to use HTTP 2.0 for StowRS (i.e. StoreInstances) requests. True by default.
     */
    Boolean useHttp2ForStow = false;


    Boolean tagsToReplace = false;
    /**
     * Tags to remove during C-STORE upload, comma separated. Only one of 'redact' flags may be present
     */
    String tagsToRemove = "00100020";

    /**
     * Tags to keep during C-STORE upload, comma separated. Only one of 'redact' flags may be present
     */
    String tagsToKeep = "";

    /**
     * Filter tags by predefined profile during C-STORE upload. Only one of 'redact' flags may be present. Values: CHC_BASIC"
     */
    String tagsProfile = "";




}


