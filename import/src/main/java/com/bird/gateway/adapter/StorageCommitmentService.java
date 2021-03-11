package com.bird.gateway.adapter;

import com.bird.gateway.adapter.monitoring.Event;
import lombok.extern.slf4j.Slf4j;
import org.bird.gateway.IGatewayClient;
import org.bird.gateway.IGatewayClient.DicomGatewayException;
import org.bird.gateway.imaging.adapter.AetDictionary;
import org.bird.gateway.imaging.adapter.AetDictionary.*;
import org.bird.gateway.imaging.adapter.AttributesUtil;
import org.bird.gateway.imaging.adapter.DicomClient;
import org.bird.gateway.imaging.adapter.monitoring.MonitoringService;
import org.dcm4che3.data.*;
import org.dcm4che3.net.*;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.pdu.RoleSelection;
import org.dcm4che3.net.service.AbstractDicomService;
import org.dcm4che3.net.service.DicomServiceException;
import org.json.JSONArray;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @date 15:19 2021-3-3
 * @description TODO
 */
@Slf4j
public class StorageCommitmentService extends AbstractDicomService {

    private static final int EVENT_ID_ALL_SUCCESS = 1;
    private static final int EVENT_ID_FAILURES_PRESENT = 2;

    private final IGatewayClient gatewayClient;
    private final AetDictionary aets;

    StorageCommitmentService(IGatewayClient gatewayClient, AetDictionary aets) {
        super(UID.StorageCommitmentPushModelSOPClass);
        this.gatewayClient = gatewayClient;
        this.aets = aets;
    }



    @Override
    protected void onDimseRQ(Association as, PresentationContext pc, Dimse dimse, Attributes cmd, Attributes data) throws IOException {
        try {
            if (dimse != Dimse.N_ACTION_RQ) {
                throw new DicomServiceException(Status.UnrecognizedOperation);
            }
            if (!cmd.getString(Tag.RequestedSOPClassUID).equals(UID.StorageCommitmentPushModelSOPClass)) {
                throw new DicomServiceException(Status.NoSuchSOPclass);
            }
            if (!cmd.getString(Tag.RequestedSOPInstanceUID)
                    .equals(UID.StorageCommitmentPushModelSOPInstance)) {
                throw new DicomServiceException(Status.NoSuchObjectInstance);
            }
            int actionTypeID = cmd.getInt(Tag.ActionTypeID, 0);
            if (actionTypeID != 1) {
                throw new DicomServiceException(Status.NoSuchActionType).setActionTypeID(actionTypeID);
            }

            MonitoringService.addEvent(Event.COMMITMENT_REQUEST);

            Aet remoteAet = aets.getAet(as.getRemoteAET());
            if (remoteAet == null) {
                MonitoringService.addEvent(Event.COMMITMENT_ERROR);

                throw new DicomServiceException(Status.ProcessingFailure,
                        "Unknown AET: " + as.getRemoteAET());
            }

            CommitmentReportTask task = new CommitmentReportTask(as.getApplicationEntity(),
                    data, remoteAet);
            as.getApplicationEntity().getDevice().execute(task);

            as.writeDimseRSP(pc, Commands.mkNActionRSP(cmd, Status.Success));
        } catch (RuntimeException e) {
            throw new DicomServiceException(Status.ProcessingFailure, e);
        }
    }


    private static class CommitmentItem {
        private String instanceUid;
        private String classUid;
        private Integer failureReason;

        public CommitmentItem(String instanceUid, String classUid) {
            this.instanceUid = instanceUid;
            this.classUid = classUid;
        }

        public String getInstanceUid() {
            return instanceUid;
        }

        public String getClassUid() {
            return classUid;
        }

        public Integer getFailureReason() {
            return failureReason;
        }

        public void setFailureReason(Integer failureReason) {
            this.failureReason = failureReason;
        }

    }


    private class CommitmentReportTask implements Runnable {
        private final Attributes data;
        private final ApplicationEntity applicationEntity;
        private final Aet remoteAet;

        CommitmentReportTask(ApplicationEntity applicationEntity, Attributes data, Aet remoteAet) {
            this.applicationEntity = applicationEntity;
            this.data = data;
            this.remoteAet = remoteAet;
        }


        @Override
        public void run() {
            List<CommitmentItem> presentInstances = new ArrayList<>();
            List<CommitmentItem> absentInstances = new ArrayList<>();

            Sequence sopSequence = data.getSequence(Tag.ReferencedSOPSequence);
            for (Attributes attrsItem : sopSequence) {
                Attributes queryAttributes = new Attributes();
                CommitmentItem cmtItem = new CommitmentItem(
                        attrsItem.getString(Tag.ReferencedSOPInstanceUID),
                        attrsItem.getString(Tag.ReferencedSOPClassUID));
                queryAttributes
                        .setString(Tag.SOPInstanceUID, VR.UI, cmtItem.getInstanceUid());
                queryAttributes.setString(Tag.QueryRetrieveLevel, VR.CS, "IMAGE");
                try {
                    String qidoPath = AttributesUtil.attributesToQidoPath(queryAttributes);
                    JSONArray qidoResult = gatewayClient.qidoRs(qidoPath);
                    if (qidoResult == null || qidoResult.length() == 0) {
                        cmtItem.setFailureReason(Status.NoSuchObjectInstance);
                        absentInstances.add(cmtItem);
                    } else {
                        presentInstances.add(cmtItem);
                    }
                } catch (DicomServiceException | DicomGatewayException e) {
                    MonitoringService.addEvent(Event.COMMITMENT_QIDORS_ERROR);
                    log.error("Commitment QidoPath/QidoRs error: ", e);

                    cmtItem.setFailureReason(toFailureReason(e));
                    absentInstances.add(cmtItem);
                }
            }

            DicomClient dicomClient;
            try {
                dicomClient = DicomClient.associatePeer(applicationEntity,
                        remoteAet.getHost(), remoteAet.getPort(), makeAAssociateRQ());
            } catch (Exception e) {
                MonitoringService.addEvent(Event.COMMITMENT_ERROR);
                log.error("associatePeer exception: ", e);
                return;
            }

            Association association = dicomClient.getAssociation();
            try {
                FutureDimseRSP handler = new FutureDimseRSP(association.nextMessageID());

                int eventTypeId =
                        absentInstances.size() > 0 ? EVENT_ID_FAILURES_PRESENT : EVENT_ID_ALL_SUCCESS;
                association.neventReport(UID.StorageCommitmentPushModelSOPClass,
                        UID.StorageCommitmentPushModelSOPInstance,
                        eventTypeId,
                        makeDataset(presentInstances, absentInstances),
                        UID.ExplicitVRLittleEndian,
                        handler);

                handler.next();
                int dimseStatus = handler.getCommand().getInt(Tag.Status, /* default status */ -1);
                if (dimseStatus != Status.Success) {
                    throw new IOException("Commitment Report failed with status code: " + dimseStatus);
                }
            } catch (IOException | InterruptedException e) {
                MonitoringService.addEvent(Event.COMMITMENT_ERROR);
                log.error("neventReport error: ", e);
            } finally {
                try {
                    association.release();
                    association.waitForSocketClose();
                } catch (Exception e) {
                    log.warn("Send Commitment Report successfully, but failed to close association: ", e);
                }
            }
        }


        private AAssociateRQ makeAAssociateRQ() {
            AAssociateRQ aarq = new AAssociateRQ();
            aarq.setCallingAET(applicationEntity.getAETitle());
            aarq.setCalledAET(remoteAet.getName());
            aarq.addPresentationContext(
                    new PresentationContext(
                            1,
                            UID.StorageCommitmentPushModelSOPClass,
                            UID.ExplicitVRLittleEndian));
            aarq.addRoleSelection(
                    new RoleSelection(UID.StorageCommitmentPushModelSOPClass, false, true));
            return aarq;
        }


        private Attributes makeDataset(List<CommitmentItem> presentInstances,
                                       List<CommitmentItem> absentInstances) {
            Attributes result = new Attributes();
            result.setString(Tag.TransactionUID, VR.UI, data.getString(Tag.TransactionUID));
            result.setString(Tag.RetrieveAETitle, VR.AE, applicationEntity.getAETitle());
            addCommitmentItemSequence(result, Tag.FailedSOPSequence, absentInstances);
            addCommitmentItemSequence(result, Tag.ReferencedSOPSequence, presentInstances);
            return result;
        }

        private int toFailureReason(Exception e) {
            if (e instanceof DicomGatewayException) {
                DicomGatewayException webException = (DicomGatewayException) e;
                switch (webException.getStatus()) {
                    case Status.OutOfResources:
                        return Status.ResourceLimitation;
                    default:
                        return Status.ProcessingFailure;
                }
            }

            return Status.ProcessingFailure;
        }

        private void addCommitmentItemSequence(Attributes attrs, int tag, List<CommitmentItem> items) {
            if (items.size() > 0) {
                Sequence sequence = attrs.newSequence(tag, items.size());
                for (CommitmentItem item : items) {
                    Attributes seqElementAttributes = new Attributes();
                    seqElementAttributes
                            .setString(Tag.ReferencedSOPInstanceUID, VR.UI, item.getInstanceUid());
                    seqElementAttributes.setString(Tag.ReferencedSOPClassUID, VR.UI, item.getClassUid());
                    if (item.getFailureReason() != null) {
                        seqElementAttributes.setInt(Tag.FailureReason, VR.US, item.getFailureReason());
                    }
                    sequence.add(seqElementAttributes);
                }
            }
        }

    }






















}
