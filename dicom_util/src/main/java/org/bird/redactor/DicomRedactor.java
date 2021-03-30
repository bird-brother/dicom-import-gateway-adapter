package org.bird.redactor;


import org.dcm4che3.data.*;
import org.dcm4che3.data.Attributes.Visitor;
import org.dcm4che3.imageio.codec.TransferSyntaxType;
import org.dcm4che3.io.BulkDataDescriptor;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomInputStream.IncludeBulkData;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.util.UIDUtils;
import com.google.protobuf.TextFormat;
import com.google.cloud.healthcare.deid.redactor.protos.DicomConfigProtos.DicomConfig;


import java.io.*;
import java.util.*;

/**
 * @date 9:23 2021-3-11
 * @description TDicomRedactor implements basic DICOM redaction.
 */
public class DicomRedactor {

    /**
     * RedactorSettings holds the settings for DICOM redaction.
     */
    private final class RedactorSettings {
        public Set<Integer> tagSet;
        public boolean isKeepList;
    }

    private final RedactorSettings settings;
    private  String clientUid;
    private  Boolean tagToReplace;


    /**
     * Replace SOPInstanceUID, StudyInstanceUID, SeriesInstanceUID, and MediaStorageSOPInstanceUID.
     */
    private static final List<Integer> replaceUIDs = new ArrayList<>(Arrays.asList(0x00080018, 0x0020000D, 0x0020000E, 0x00020003));
    private static final String CHC_BASIC_FILE = "chc_basic.textproto";


    /**
     *  Iterates over all tags in a DICOM file and redacts based on tagSet. If isKeepList is true, the
     *  tags in the tagSet are kept untouched and all others are removed. If isKeepList is false, the
     *  tags in the tagSet are removed and all others are kept untouched.
     */
    private class RedactVisitor implements Visitor {


        @Override
        public boolean visit(Attributes attrs, int tag, VR vr, Object value) {
            if (replaceUIDs.contains(tag)) {
                //取消原项目的修改UID方法
                //DicomRedactor.this.regenUID(attrs, tag);
                return true;
            }
            if ((settings.isKeepList && !settings.tagSet.contains(tag)) || (!settings.isKeepList && settings.tagSet.contains(tag))) {
                attrs.setNull(tag, vr);
            }
            return true;
        }
    }


    /**
     * Constructs a DicomRedactor for the provided config.
     * @param config controls the redaction process.
     * @throws IllegalArgumentException if the configuration structure is invalid.
     */
    public DicomRedactor(DicomConfig config) throws Exception {
        this.settings = parseConfig(config);
    }

    public DicomRedactor(DicomConfig config,String clientUid,Boolean tagToReplace) throws Exception {
        this.clientUid = clientUid;
        this.tagToReplace = tagToReplace;
        this.settings = parseConfig(config);

    }

    /**
     * Constructs a DicomRedactor for the provided config and prefix for UID replacement.
     * @param config controls the redaction process.
     * @param prefix is the prefix to apply before every new generated UID.
     * @throws IllegalArgumentException if the configuration structure is invalid.
     */
    public DicomRedactor(DicomConfig config, String prefix) throws Exception {
        this(config);
        UIDUtils.setRoot(prefix);
    }




    /**First submission
     * Parses DicomConfig.proto proto to produce a RedactorSettings object.
     * @throws IllegalArgumentException if the configuration structure or tags are invalid.
     * @throws InternalError if there is an exception trying to load a predefined profile.
     */
    private RedactorSettings parseConfig(DicomConfig config) throws IllegalArgumentException {

        RedactorSettings ret = new RedactorSettings();
        DicomConfig.TagFilterList tags;
        switch(config.getTagFilterCase()) {
            case KEEP_LIST:
                ret.isKeepList = true;
                tags = config.getKeepList();
                break;
            case REMOVE_LIST:
                ret.isKeepList = false;
                tags = config.getRemoveList();
                break;
            case FILTER_PROFILE:
                switch(config.getFilterProfile()) {
                    case TAG_FILTER_PROFILE_UNSPECIFIED:
                    case CHC_BASIC:
                        DicomConfig.Builder configBuilder = DicomConfig.newBuilder();
                        TextFormat.Parser parser = TextFormat.Parser.newBuilder().build();
                        final StringBuilder protoString = new StringBuilder();
                        try (BufferedReader protoReader = new BufferedReader(new InputStreamReader(
                                DicomRedactor.class.getClassLoader().getResourceAsStream(CHC_BASIC_FILE)))) {
                            String line;
                            while (true) {
                                line = protoReader.readLine();
                                if (line == null) {
                                    break;
                                }
                                protoString.append(line).append('\n');
                            }
                            parser.merge(protoString.toString(), configBuilder);
                            if (configBuilder.hasFilterProfile()) {
                                throw new InternalError("Profile cannot point to another profile");
                            }
                        } catch (Exception e) {
                            throw new InternalError("Failed to load selected profile.", e);
                        }
                        return parseConfig(configBuilder.build());
                    default:
                        throw new IllegalArgumentException("Config specifies an unrecognized profile.");
                }
            default:
                throw new IllegalArgumentException("Config does not specify a tag filtration method.");
        }
        ret.tagSet = new HashSet<Integer>();
        for (String tag : tags.getTagsList()) {
            int tagID = this.toTagID(tag);
            ret.tagSet.add(tagID);
        }
        return ret;
    }




    /**
     * Converts tag keywords and id strings to tag IDs.
     * @throws IllegalArgumentException if the tag cannot be converted.
     */
    private int toTagID(String tag) throws IllegalArgumentException {
        int tagNum = 0;
        // Attempt to parse as a tag keyword.
        if(tagToReplace) {
            tagNum = StandardElementDictionary.tagForKeyword(tag, null);
        }
        if (tagNum != -1) {
            return tagNum;
        }
        // Attempt to parse as a tag id in string form.
        int ret;
        try {
            ret = Integer.parseInt(tag, 16);
        } catch (Exception e) {
            throw new IllegalArgumentException(String.format("Failed to recognize DICOM tag: %s", tag));
        }
        return ret;
    }



    /** 取消 Regenerates UID for the given tag. Does not check VR of tag to ensure it is a UID. */
    private void regenUID(Attributes attrs, int tag) {
//        String newUID = UIDUtils.createNameBasedUID(attrs.getString(tag).getBytes());
//        attrs.setString(tag, VR.UI, newUID);
    }


    /**
     * Redact the given DICOM input stream, and write the result to the given output stream.
     * @param inStream the DICOM input stream to read.
     * @param outStream the output stream to write the result to.
     * @throws IOException if the input stream cannot be read or the output stream cannot be written.
     * @throws IllegalArgumentException if there is an error redacting the object.
     */
    public void redact(InputStream inStream, OutputStream outStream) throws IOException, IllegalArgumentException {

        Attributes metadata,dataset;
        try (DicomInputStream dicomInputStream = new DicomInputStream(inStream)) {
            dicomInputStream.setIncludeBulkData(IncludeBulkData.YES);
            dicomInputStream.setBulkDataDescriptor(BulkDataDescriptor.PIXELDATA);
            metadata = dicomInputStream.getFileMetaInformation();

            // len & stop tag
            dataset = dicomInputStream.readDataset(-1 , -1 );
            String patId = dataset.getString(Tag.PatientID);
            String studyId = dataset.getString(Tag.StudyInstanceUID);
            String seriesId = dataset.getString(Tag.SeriesInstanceUID);
            String sopId = dataset.getString(Tag.SOPInstanceUID);

            //update UID
            dataset.setString(Tag.PatientID,VR.LO,clientUid+"-"+patId+"-"+studyId);
            dataset.setString(Tag.StudyInstanceUID,VR.UI,studyId+"."+clientUid);
            dataset.setString(Tag.SeriesInstanceUID,VR.UI,seriesId+"."+clientUid);
            dataset.setString(Tag.SOPInstanceUID,VR.UI,sopId+"."+clientUid);

        }catch (Exception e) {
            throw new IOException("Failed to read input DICOM object", e);
        }

        try {
            RedactVisitor visitor = new RedactVisitor();
            // visitNestedDatasets
            dataset.accept(visitor, false );
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to redact one or more tags", e);
        }


        // 取消Update UID in metadata.
        //regenUID(metadata, Tag.MediaStorageSOPInstanceUID);

        // Overwrite transfer syntax if PixelData has been removed.
        String ts = metadata.getString(Tag.TransferSyntaxUID);
        if (dataset.contains(toTagID("PixelData")) && (!dataset.containsValue(toTagID("PixelData"))) && (TransferSyntaxType.forUID(ts) != TransferSyntaxType.NATIVE)) {
            metadata.setString(Tag.TransferSyntaxUID, VR.UI, UID.ExplicitVRLittleEndian);
        }

        try (DicomOutputStream dicomOutputStream = new DicomOutputStream(outStream, UID.ExplicitVRLittleEndian)) {
            dicomOutputStream.writeDataset(metadata, dataset);
        } catch (Exception e) {
            throw new IOException("Failed to write output DICOM object", e);
        }

    }


}


























