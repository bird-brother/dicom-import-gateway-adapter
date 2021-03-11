package com.bird.gateway.adapter.cstore;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.io.DicomOutputStream;

import java.io.*;

/**
 * @date 10:45 2021-3-2
 * @description Provides utilities for handling DICOM streams.
 */
public class DicomStreamUtil {


    /**
     * Adds the DICOM meta header to input stream.
     * @params
     * @return InputStream
     * @since 2021-3-2 11:26
     */
    public static InputStream dicomStreamWithFileMetaHeader(String sopInstanceUID, String sopClassUID, String transferSyntax,
                                                            InputStream inDicomStream) throws IOException {

        // File meta header (group 0002 tags), always in Explicit VR Little Endian.
        // http://dicom.nema.org/dicom/2013/output/chtml/part10/chapter_7.html
        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        DicomOutputStream fmiStream = new DicomOutputStream(outBuffer, UID.ExplicitVRLittleEndian);
        Attributes fmi = Attributes.createFileMetaInformation(sopInstanceUID, sopClassUID, transferSyntax);

        fmiStream.writeFileMetaInformation(fmi);

        // Add the file meta header + DICOM dataset (other groups) as a sequence of input streams.
        return new SequenceInputStream(new ByteArrayInputStream(outBuffer.toByteArray()), inDicomStream);
    }

    private DicomStreamUtil() {}

}
