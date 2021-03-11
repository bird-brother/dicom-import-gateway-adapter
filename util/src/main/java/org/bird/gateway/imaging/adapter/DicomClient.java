package org.bird.gateway.imaging.adapter;

import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.net.*;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4che3.net.pdu.PresentationContext;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;

/**
 * @date 10:10 2021-3-1
 * @description DicomClient is used to handle client-side legacy DICOM requests on given association.
 */
public class DicomClient {

    private Association association;
    public DicomClient(Association association) {
        this.association = association;
    }


    /**
     * Creates a new DicomClient by creating an association to the given peer.
     * @params
     * @return DicomClient
     * @since 2021-3-1 10:30
     */
    public static DicomClient associatePeer(ApplicationEntity clientAE, String peerAET, String peerHostname, int peerPort,
                                            PresentationContext pc) throws IOException, InterruptedException, IncompatibleConnectionException, GeneralSecurityException {
        AAssociateRQ rq = new AAssociateRQ();
        rq.addPresentationContext(pc);
        rq.setCalledAET(peerAET);
        return associatePeer(clientAE, peerHostname, peerPort, rq);
    }



    public static DicomClient associatePeer(ApplicationEntity clientAE, String peerHostname, int peerPort, AAssociateRQ rq)
            throws IOException, InterruptedException, IncompatibleConnectionException, GeneralSecurityException {
        Connection remoteConn = new Connection();
        remoteConn.setHostname(peerHostname);
        remoteConn.setPort(peerPort);
        Association association = clientAE.connect(remoteConn, rq);
        return new DicomClient(association);
    }



    /**
     *
     * @params
     * @since 2021-3-1 10:36
     */
    public static void connectAndCstore(String sopClassUid, String sopInstanceUid, InputStream in,
                                        ApplicationEntity applicationEntity, String dimsePeerAet, String dimsePeerHost,
                                        int dimsePeerPort) throws IOException, InterruptedException {

        DicomInputStream din = new DicomInputStream(in);
        din.readFileMetaInformation();
        PresentationContext pc = new PresentationContext(1, sopClassUid, din.getTransferSyntax());
        DicomClient dicomClient;

        try {
            dicomClient = DicomClient.associatePeer(applicationEntity, dimsePeerAet, dimsePeerHost, dimsePeerPort, pc);
        }catch (IOException | IncompatibleConnectionException | GeneralSecurityException e) {
            // calling code doesn't need to distinguish these
            throw new IOException(e);
        }
    }


    /**
     *
     * @params
     * @since 2021-3-1 10:36
     */
    public void cstore(String sopClassUid, String sopInstanceUid, String transferSyntaxUid,
                       DicomInputStream din, DimseRSPHandler responseHandler) throws IOException, InterruptedException{
        InputStreamDataWriter data = new InputStreamDataWriter(din);
        /* priority */
        association.cstore(sopClassUid, sopInstanceUid, 1, data, transferSyntaxUid, responseHandler);
    }

    public Association getAssociation() {
        return association;
    }




}
