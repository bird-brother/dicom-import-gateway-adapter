package com.bird.gateway.adapter;

import com.bird.gateway.adapter.cstore.DicomStreamUtil;
import com.bird.gateway.adapter.cstore.multipledest.IMultipleDestinationUploadService;
import com.bird.gateway.adapter.cstore.destination.DestinationHolder;
import com.bird.gateway.adapter.cstore.destination.IDestinationClientFactory;
import com.bird.gateway.adapter.monitoring.Event;
import com.google.cloud.healthcare.deid.redactor.DicomRedactor;
import com.google.common.io.CountingInputStream;
import lombok.extern.slf4j.Slf4j;
import org.bird.gateway.IGatewayClient;
import org.bird.gateway.IGatewayClient.*;
import org.bird.gateway.imaging.adapter.monitoring.MonitoringService;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.imageio.codec.Transcoder;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.PDVInputStream;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.BasicCStoreSCP;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.util.StreamUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorCompletionService;
import com.bird.gateway.adapter.cstore.multipledest.IMultipleDestinationUploadService.*;

/**
 * @date 15:38 2021-3-2
 * @description Class to handle server-side C-STORE DICOM requests.
 */
@Slf4j
public class CStoreService extends BasicCStoreSCP {


    private final IDestinationClientFactory destinationClientFactory;
    private final IMultipleDestinationUploadService multipleSendService;
    private final DicomRedactor redactor;
    private final String transcodeToSyntax;

    CStoreService(IDestinationClientFactory destinationClientFactory,
                  DicomRedactor redactor,
                  String transcodeToSyntax,
                  IMultipleDestinationUploadService multipleSendService) {
        this.destinationClientFactory = destinationClientFactory;
        this.redactor = redactor;
        this.transcodeToSyntax = transcodeToSyntax != null && transcodeToSyntax.length() > 0 ? transcodeToSyntax : null;
        this.multipleSendService = multipleSendService;

        if(this.transcodeToSyntax != null) {
            log.info("Transcoding to: " + transcodeToSyntax);
        }
    }


    @Override
    protected void store(Association association, PresentationContext presentationContext, Attributes request,
                         PDVInputStream inPdvStream, Attributes response) throws IOException {

        try {
            OutputStream x = null;
            MonitoringService.addEvent(Event.CSTORE_REQUEST);

            String sopClassUID = request.getString(Tag.AffectedSOPClassUID);
            String sopInstanceUID = request.getString(Tag.AffectedSOPInstanceUID);
            String transferSyntax = presentationContext.getTransferSyntax();


            validateParam(sopClassUID, "AffectedSOPClassUID");
            validateParam(sopInstanceUID, "AffectedSOPInstanceUID");

            DestinationHolder destinationHolder =
                    destinationClientFactory.create(association.getAAssociateAC().getCallingAET(), inPdvStream);



            final CountingInputStream countingStream = destinationHolder.getCountingInputStream();

            List<StreamProcessor> processorList = new ArrayList<>();
            if (redactor != null) {
                processorList.add(redactor::redact);
            }

            if(transcodeToSyntax != null && !transcodeToSyntax.equals(transferSyntax)) {
                processorList.add((inputStream, outputStream) -> {
                    try (Transcoder transcoder = new Transcoder(inputStream)) {
                        transcoder.setIncludeFileMetaInformation(true);
                        transcoder.setDestinationTransferSyntax(transcodeToSyntax);
                        transcoder.transcode((transcoder1, dataset) -> outputStream);
                    }
                });
            }

            if (multipleSendService != null) {
                processorList.add((inputStream, outputStream) -> {
                    multipleSendService.start(
                            destinationHolder.getHealthcareDestinations(),
                            destinationHolder.getDicomDestinations(),
                            inputStream,
                            sopClassUID,
                            sopInstanceUID
                    );
                });
            }else {
                processorList.add((inputStream, outputStream) -> { destinationHolder.getSingleDestination().stowRs(inputStream); });
            }


            try (InputStream inWithHeader = DicomStreamUtil.dicomStreamWithFileMetaHeader(sopInstanceUID, sopClassUID, transferSyntax, countingStream)){
                processStream(association.getApplicationEntity().getDevice().getExecutor(), inWithHeader, processorList);
            }catch (IOException e) {
                throw new DicomServiceException(Status.ProcessingFailure, e);
            }

            response.setInt(Tag.Status, VR.US, Status.Success);
            MonitoringService.addEvent(Event.CSTORE_BYTES, countingStream.getCount());
        }catch (DicomGatewayException e) {
            reportError(e, Event.CSTORE_ERROR);
            throw new DicomServiceException(e.getStatus(), e);
        } catch (DicomServiceException e) {
            reportError(e, Event.CSTORE_ERROR);
            throw e;
        } catch (MultipleDestinationUploadServiceException me) {
            reportError(me, null);
            throw new DicomServiceException(me.getDicomStatus() != null ? me.getDicomStatus() : Status.ProcessingFailure, me);
        } catch (Throwable e) {
            reportError(e, Event.CSTORE_ERROR);
            throw new DicomServiceException(Status.ProcessingFailure, e);
        }
    }



    private void reportError(Throwable e, Event event) {
        if (event != null) {
            MonitoringService.addEvent(event);
        }
        log.error("C-STORE request failed: ", e);
    }



    private void validateParam(String value, String name) throws DicomServiceException {
        if (value == null || value.trim().length() == 0) {
            throw new DicomServiceException(Status.CannotUnderstand, "Mandatory tag empty: " + name);
        }
    }




    private void processStream(Executor underlyingExecutor, InputStream inputStream,
                               List<StreamProcessor> processorList) throws Throwable {

        if (processorList.size() == 1) {
            StreamProcessor singleProcessor = processorList.get(0);
            singleProcessor.process(inputStream, null);
        } else if (processorList.size() > 1) {
            List<StreamCallable> callableList = new ArrayList<>();

            PipedOutputStream pdvPipeOut = new PipedOutputStream();
            InputStream nextInputStream = new PipedInputStream(pdvPipeOut);
            for(int i=0; i < processorList.size(); i++) {
                StreamProcessor processor = processorList.get(i);
                InputStream processorInput = nextInputStream;
                OutputStream processorOutput = null;

                if (i < processorList.size() - 1) {
                    PipedOutputStream pipeOut = new PipedOutputStream();
                    processorOutput = pipeOut;
                    nextInputStream = new PipedInputStream(pipeOut);
                }

                callableList.add(new StreamCallable(processorInput, processorOutput, processor));
            }

            ExecutorCompletionService<Void> ecs = new ExecutorCompletionService<>(underlyingExecutor);
            for(StreamCallable callable : callableList){
                ecs.submit(callable);
            }


            try (pdvPipeOut) {
                // PDVInputStream is thread-locked
                StreamUtils.copy(inputStream, pdvPipeOut);
            } catch (IOException e) {
                // causes or is caused by exception in callables, no need to throw this up
                log.trace("Error copying inputStream to pdvPipeOut", e);
            }

            try {
                for (int i = 0; i < callableList.size(); i++) {
                    ecs.take().get();
                }
            } catch (ExecutionException e) {
                throw e.getCause();
            }
        }
    }



    @FunctionalInterface
    private interface StreamProcessor {
        void process(InputStream inputStream, OutputStream outputStream) throws Exception;
    }


    private static class StreamCallable implements Callable<Void> {
        private final InputStream inputStream;
        private final OutputStream outputStream;
        private final StreamProcessor processor;

        public StreamCallable(InputStream inputStream, OutputStream outputStream,
                              StreamProcessor processor) {
            this.inputStream = inputStream;
            this.outputStream = outputStream;
            this.processor = processor;
        }

        @Override
        public Void call() throws Exception {
            try (inputStream) {
                try (outputStream) {
                    processor.process(inputStream, outputStream);
                }
            }
            return null;
        }
    }















}
