package com.bird.gateway.adapter;

import com.bird.gateway.adapter.cmove.CMoveSenderFactory;
import com.bird.gateway.adapter.cstore.backup.*;
import com.bird.gateway.adapter.cstore.destination.IDestinationClientFactory;
import com.bird.gateway.adapter.cstore.destination.MultipleDestinationClientFactory;
import com.bird.gateway.adapter.cstore.destination.SingleDestinationClientFactory;
import com.bird.gateway.adapter.cstore.multipledest.MultipleDestinationUploadService;
import com.bird.gateway.adapter.cstore.multipledest.sender.CStoreSenderFactory;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.javanet.NetHttpTransport;
//import com.google.cloud.healthcare.deid.redactor.DicomRedactor;
import org.bird.redactor.DicomRedactor;
import com.google.cloud.healthcare.deid.redactor.protos.DicomConfigProtos;
import com.google.cloud.healthcare.deid.redactor.protos.DicomConfigProtos.*;
import com.google.common.collect.ImmutableList;
import lombok.extern.slf4j.Slf4j;
import org.bird.gateway.*;
import org.bird.gateway.imaging.adapter.*;
import org.bird.gateway.imaging.adapter.AetDictionary.Aet;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.service.BasicCEchoSCP;
import org.dcm4che3.net.service.DicomServiceRegistry;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;

/**
 * @date 14:30 2021-3-1
 * @description TODO
 */
@Slf4j
public class ImportAdapter {

    private static final String STUDIES = "studies";
    private static final String GCP_PATH_PREFIX = "gs://";
    private static final String FILTER = "filter";


    public static void main(String[] args) throws IOException, GeneralSecurityException {
        Flags flags = new Flags();
        flags.setFlag();
        if(flags != null){
            log.info("Configuration loaded successfully");
        }

        String gatewayAddress = GatewayValidation.validatePath(flags.gatewayAddress,GatewayValidation.DICOMWEB_ROOT_VALIDATION);

        HttpRequestFactory requestFactory = new NetHttpTransport().createRequestFactory();

        // Dicom service handlers.
        DicomServiceRegistry serviceRegistry = new DicomServiceRegistry();

        // Handle C-ECHO (all nodes which accept associations must support this).
        serviceRegistry.addDicomService(new BasicCEchoSCP());

        // Handle C-STORE
        String cstoreDicomwebAddr = gatewayAddress;
        String cstoreDicomwebStowPath = STUDIES;

        String cstoreSubAet = flags.dimseCmoveAET.equals("") ? flags.dimseAET : flags.dimseCmoveAET;
        if (cstoreSubAet.isBlank()) {
            throw new IllegalArgumentException("--dimse_aet flag must be set.");
        }

        IGatewayClient defaultCstoreGatewayClient = configureDefaultGatewayClient(
                requestFactory, cstoreDicomwebAddr, cstoreDicomwebStowPath, flags);

        DicomRedactor redactor = configureRedactor(flags);

        BackupUploadService backupUploadService = configureBackupUploadService(flags);

        IDestinationClientFactory destinationClientFactory = configureDestinationClientFactory(
                defaultCstoreGatewayClient, flags, backupUploadService != null);


        MultipleDestinationUploadService multipleDestinationSendService = configureMultipleDestinationUploadService(
                flags, cstoreSubAet, backupUploadService);

        CStoreService cStoreService =
                new CStoreService(destinationClientFactory, redactor, flags.transcodeToSyntax, multipleDestinationSendService);
        serviceRegistry.addDicomService(cStoreService);


        // Handle C-FIND
        IGatewayClient dicomWebClient = new GatewayClient(requestFactory, flags.gatewayAddress, STUDIES);
        CFindService cFindService = new CFindService(dicomWebClient, flags);
        serviceRegistry.addDicomService(cFindService);

        // Handle C-MOVE
        CMoveSenderFactory cMoveSenderFactory = new CMoveSenderFactory(cstoreSubAet, dicomWebClient);
        AetDictionary aetDict = new AetDictionary(flags.aetDictionaryInline, flags.aetDictionaryPath);
        CMoveService cMoveService = new CMoveService(dicomWebClient, aetDict, cMoveSenderFactory);
        serviceRegistry.addDicomService(cMoveService);

        // Handle Storage Commitment N-ACTION
        serviceRegistry.addDicomService(new StorageCommitmentService(dicomWebClient, aetDict));

        // Start DICOM server
        Device device = DeviceUtil.createServerDevice(flags.dimseAET, flags.dimsePort, serviceRegistry);
        device.bindConnections();

    }




    private static IGatewayClient configureDefaultGatewayClient(
            HttpRequestFactory requestFactory, String cstoreDicomwebAddr, String cstoreDicomwebStowPath, Flags flags){
        IGatewayClient defaultCstoreGatewayClient;
        if (flags.useHttp2ForStow) {
            defaultCstoreGatewayClient = new GatewayClientJetty(StringUtil.joinPath(cstoreDicomwebAddr, cstoreDicomwebStowPath));
        }else {
            defaultCstoreGatewayClient = new GatewayClient(requestFactory, cstoreDicomwebAddr, cstoreDicomwebStowPath);
        }
        return defaultCstoreGatewayClient;
    }


    private static IDestinationClientFactory configureDestinationClientFactory(
            IGatewayClient defaultCstoreGatewayClient, Flags flags, boolean backupServicePresent) throws IOException {

        IDestinationClientFactory destinationClientFactory;
        if (flags.sendToAllMatchingDestinations) {
            if (backupServicePresent == false) {
                throw new IllegalArgumentException("backup is not configured properly. '--send_to_all_matching_destinations' " +
                        "flag must be used only in pair with backup, local or GCP. Please see readme to configure backup.");
            }
            Pair<ImmutableList<Pair<DestinationFilter, IGatewayClient>>,
                    ImmutableList<Pair<DestinationFilter, Aet>>> multipleDestinations = configureMultipleDestinationTypesMap(
                    flags.destinationConfigInline,
                    flags.destinationConfigPath,
                    DestinationsConfig.ENV_DESTINATION_CONFIG_JSON);

            destinationClientFactory = new MultipleDestinationClientFactory(
                    multipleDestinations.getLeft(),
                    multipleDestinations.getRight(),
                    defaultCstoreGatewayClient);
        }else {
            // with or without backup usage.
            destinationClientFactory = new SingleDestinationClientFactory(
                    configureDestinationMap(flags.destinationConfigInline, flags.destinationConfigPath), defaultCstoreGatewayClient);
        }
        return destinationClientFactory;
    }


    private static MultipleDestinationUploadService configureMultipleDestinationUploadService(
            Flags flags,
            String cstoreSubAet,
            BackupUploadService backupUploadService) {
        if (backupUploadService != null) {
            return new MultipleDestinationUploadService(
                    new CStoreSenderFactory(cstoreSubAet),
                    backupUploadService,
                    flags.persistentFileUploadRetryAmount);
        }
        return null;
    }


    private static BackupUploadService configureBackupUploadService(Flags flags) throws IOException {
        String uploadPath = flags.persistentFileStorageLocation;

        if (!uploadPath.isBlank()) {
            final IBackupUploader backupUploader;
            if (uploadPath.startsWith(GCP_PATH_PREFIX)) {
                backupUploader = new GcpBackupUploader(uploadPath, flags.gcsBackupProjectId);
            } else {
                backupUploader = new LocalBackupUploader(uploadPath);
            }
            return new BackupUploadService(
                    backupUploader,
                    flags.persistentFileUploadRetryAmount,
                    ImmutableList.copyOf(flags.httpErrorCodesToRetry),
                    new DelayCalculator(flags.minUploadDelay, flags.maxWaitingTimeBetweenUploads));
        }
        return null;
    }


    private static DicomRedactor configureRedactor(Flags flags) throws IOException{
        DicomRedactor redactor = null;
        int tagEditFlags =
                (flags.tagsToRemove.isEmpty() ? 0 : 1) +
                (flags.tagsToKeep.isEmpty() ? 0 : 1) +
                (flags.tagsProfile.isEmpty() ? 0 : 1);
        if (tagEditFlags > 1) {
            throw new IllegalArgumentException("Only one of 'redact' flags may be present");
        }
        if (tagEditFlags > 0) {
            DicomConfig.Builder configBuilder = DicomConfig.newBuilder();
            if (!flags.tagsToRemove.isEmpty()) {
                List<String> removeList = Arrays.asList(flags.tagsToRemove.split(","));
                configBuilder.setRemoveList(
                        DicomConfig.TagFilterList.newBuilder().addAllTags(removeList));
            } else if (!flags.tagsToKeep.isEmpty()) {
                List<String> keepList = Arrays.asList(flags.tagsToKeep.split(","));
                configBuilder.setKeepList(
                        DicomConfig.TagFilterList.newBuilder().addAllTags(keepList));
            } else if (!flags.tagsProfile.isEmpty()){
                configBuilder.setFilterProfile(DicomConfig.TagFilterProfile.valueOf(flags.tagsProfile));
            }

            try {
                redactor = new DicomRedactor(configBuilder.build(),flags.clientUID,flags.tagsToReplace);
            } catch (Exception e) {
                throw new IOException("Failure creating DICOM redactor", e);
            }
        }
        return redactor;
    }


    private static ImmutableList<Pair<DestinationFilter, IGatewayClient>> configureDestinationMap(
            String destinationJsonInline,
            String destinationsJsonPath) throws IOException {
        DestinationsConfig conf = new DestinationsConfig(destinationJsonInline, destinationsJsonPath);

        ImmutableList.Builder<Pair<DestinationFilter, IGatewayClient>> filterPairBuilder = ImmutableList.builder();
        for (String filterString : conf.getMap().keySet()) {
            String filterPath = StringUtil.trim(conf.getMap().get(filterString));
            filterPairBuilder.add(
                    new Pair(
                            new DestinationFilter(filterString),
                            new GatewayClientJetty(filterPath.endsWith(STUDIES)? filterPath : StringUtil.joinPath(filterPath, STUDIES))
                    ));
        }
        ImmutableList resultList = filterPairBuilder.build();
        return resultList.size() > 0 ? resultList : null;
    }



    public static Pair<ImmutableList<Pair<DestinationFilter, IGatewayClient>>,
            ImmutableList<Pair<DestinationFilter, Aet>>> configureMultipleDestinationTypesMap(
            String destinationJsonInline,
            String jsonPath,
            String jsonEnvKey) throws IOException{

        ImmutableList.Builder<Pair<DestinationFilter, Aet>> dicomDestinationFiltersBuilder = ImmutableList.builder();
        ImmutableList.Builder<Pair<DestinationFilter, IGatewayClient>> healthDestinationFiltersBuilder = ImmutableList.builder();

        JSONArray jsonArray = JsonUtil.parseConfig(destinationJsonInline, jsonPath, jsonEnvKey);

        if (jsonArray != null) {
            for (Object elem : jsonArray) {
                JSONObject elemJson = (JSONObject) elem;
                if (elemJson.has(FILTER) == false) {
                    throw new IOException("Mandatory key absent: " + FILTER);
                }
                String filter = elemJson.getString(FILTER);
                DestinationFilter destinationFilter = new DestinationFilter(StringUtil.trim(filter));

                // try to create Aet instance
                if (elemJson.has("host")) {
                    dicomDestinationFiltersBuilder.add(
                            new Pair(destinationFilter,
                                    new Aet(elemJson.getString("name"),
                                            elemJson.getString("host"), elemJson.getInt("port"))));
                } else {
                    // in this case to try create IDicomWebClient instance
                    String filterPath = elemJson.getString("dicomweb_destination");
                    healthDestinationFiltersBuilder.add(
                            new Pair(
                                    destinationFilter,
                                    new GatewayClientJetty(filterPath.endsWith(STUDIES)? filterPath : StringUtil.joinPath(filterPath, STUDIES))));
                }
            }
        }
        return new Pair(healthDestinationFiltersBuilder.build(), dicomDestinationFiltersBuilder.build());
    }




    public static class Pair<A, D>{
        private final A left;
        private final D right;

        public Pair(A left, D right) {
            this.left = left;
            this.right = right;
        }

        public A getLeft() {
            return left;
        }

        public D getRight() {
            return right;
        }
    }

}
