package com.bird.gateway.adapter;

import com.bird.gateway.adapter.monitoring.Event;
import lombok.extern.slf4j.Slf4j;
import org.bird.gateway.IGatewayClient;
import org.bird.gateway.IGatewayClient.DicomGatewayException;
import org.bird.gateway.imaging.adapter.AttributesUtil;
import org.bird.gateway.imaging.adapter.DimseTask;
import org.bird.gateway.imaging.adapter.monitoring.MonitoringService;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Commands;
import org.dcm4che3.net.Dimse;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.BasicCFindSCP;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.util.TagUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CancellationException;

/**
 * @date 15:19 2021-3-3
 * @description TODO
 */
@Slf4j
public class CFindService extends BasicCFindSCP  {

    private final IGatewayClient gatewayClient;
    private final Flags cFINDFlags;

    CFindService(IGatewayClient gatewayClient, Flags flags) {
        super(UID.StudyRootQueryRetrieveInformationModelFIND);
        this.gatewayClient = gatewayClient;
        this.cFINDFlags = flags;
    }

    private static HashMap<String, JSONObject> uniqueResults(List<JSONArray> responses) {
        HashMap<String, JSONObject> uniqueResults = new HashMap<>();
        for (JSONArray response : responses) {
            for (Object result : response) {
                JSONObject resultJson = (JSONObject) result;
                String key = getResultKey(resultJson);
                if (!uniqueResults.containsKey(key)) {
                    uniqueResults.put(key, resultJson);
                }
            }
        }
        return uniqueResults;
    }


    private static String getResultKey(JSONObject jsonObject) {
        return AttributesUtil.getTagValueOrNull(jsonObject,
                TagUtils.toHexString(Tag.StudyInstanceUID)) + "_" +
                AttributesUtil.getTagValueOrNull(jsonObject,
                        TagUtils.toHexString(Tag.SeriesInstanceUID)) + "_" +
                AttributesUtil.getTagValueOrNull(jsonObject,
                        TagUtils.toHexString(Tag.SOPInstanceUID));
    }


    @Override
    public void onDimseRQ(Association association,
                          PresentationContext presentationContext,
                          Dimse dimse,
                          Attributes request,
                          Attributes keys) throws IOException {
        if (dimse != Dimse.C_FIND_RQ) {
            throw new DicomServiceException(Status.UnrecognizedOperation);
        }

        MonitoringService.addEvent(Event.CFIND_REQUEST);

        CFindTask task = new CFindTask(association, presentationContext, request, keys);
        association.getApplicationEntity().getDevice().execute(task);
    }


    private class CFindTask extends DimseTask {

        private final Attributes keys;

        private CFindTask(Association as, PresentationContext pc, Attributes cmd, Attributes keys) {
            super(as, pc, cmd);

            this.keys = keys;
        }

        @Override
        public void run() {
            try {
                if (canceled) {
                    throw new CancellationException();
                }
                runThread = Thread.currentThread();

                String[] qidoPaths = AttributesUtil.attributesToQidoPathArray(keys);
                List<JSONArray> qidoResults = new ArrayList<>();
                for (String qidoPath : qidoPaths) {
                    if (canceled) {
                        throw new CancellationException();
                    }
                    if (cFINDFlags != null && cFINDFlags.fuzzyMatching)   {
                        qidoPath += "fuzzymatching=true" + "&";
                    }
                    log.info("CFind QidoPath: " + qidoPath);
                    MonitoringService.addEvent(Event.CFIND_QIDORS_REQUEST);
                    JSONArray qidoResult = gatewayClient.qidoRs(qidoPath);
                    qidoResults.add(qidoResult);
                }
                HashMap<String, JSONObject> uniqueResults = uniqueResults(qidoResults);

                for (JSONObject obj : uniqueResults.values()) {
                    if (canceled) {
                        throw new CancellationException();
                    }
                    Attributes attrs = AttributesUtil.jsonToAttributes(obj);
                    as.writeDimseRSP(pc, Commands.mkCFindRSP(cmd, Status.Pending), attrs);
                }
                as.writeDimseRSP(pc, Commands.mkCFindRSP(cmd, Status.Success));
            } catch (CancellationException e) {
                log.info("Canceled CFind", e);
                MonitoringService.addEvent(Event.CFIND_CANCEL);
                as.tryWriteDimseRSP(pc, Commands.mkCFindRSP(cmd, Status.Cancel));
            } catch (DicomGatewayException e) {
                log.error("CFind Qido-rs error", e);
                MonitoringService.addEvent(Event.CFIND_QIDORS_ERROR);
                sendErrorResponse(e.getStatus(), e.getMessage());
            } catch (Throwable e) {
                log.error("Failure processing CFind", e);
                MonitoringService.addEvent(Event.CFIND_ERROR);
                sendErrorResponse(Status.ProcessingFailure, e.getMessage());
            } finally {
                synchronized (this) {
                    runThread = null;
                }
                int msgId = cmd.getInt(Tag.MessageID, -1);
                as.removeCancelRQHandler(msgId);
            }
        }

        private void sendErrorResponse(int status, String message) {
            Attributes cmdAttr = Commands.mkCFindRSP(cmd, status);
            cmdAttr.setString(Tag.ErrorComment, VR.LO, message);
            as.tryWriteDimseRSP(pc, cmdAttr);
        }

    }



}
