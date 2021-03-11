package com.bird.gateway.adapter.cstore.destination;

import com.bird.gateway.adapter.ImportAdapter.*;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import org.bird.gateway.IGatewayClient;
import org.bird.gateway.imaging.adapter.DestinationFilter;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.io.DicomInputStream;

import java.io.IOException;
import java.io.InputStream;

/**
 * @date 15:00 2021-3-3
 * @description TODO
 */
public abstract class DestinationClientFactory implements IDestinationClientFactory {

    protected final ImmutableList<Pair<DestinationFilter, IGatewayClient>> healthcareDestinations;
    private final IGatewayClient defaultDicomWebClient;
    private boolean dicomDestinationsNotEmpty;


    public DestinationClientFactory(ImmutableList<Pair<DestinationFilter, IGatewayClient>> healthcareDestinations,
                                    IGatewayClient defaultDicomWebClient) {
        this.healthcareDestinations = healthcareDestinations;
        this.defaultDicomWebClient = defaultDicomWebClient;
    }

    public DestinationClientFactory(ImmutableList<Pair<DestinationFilter, IGatewayClient>> healthcareDestinations,
                                    IGatewayClient defaultDicomWebClient, boolean dicomDestinationsNotEmpty) {
        this(healthcareDestinations, defaultDicomWebClient);
        this.dicomDestinationsNotEmpty = dicomDestinationsNotEmpty;
    }


    @Override
    public DestinationHolder create(String callingAet, InputStream inputStream) throws IOException {
        DestinationHolder destinationHolder;

        if ((healthcareDestinations != null && !healthcareDestinations.isEmpty()) || dicomDestinationsNotEmpty) {
            DicomInputStream inDicomStream = createDicomInputStream(inputStream);
            Attributes attrs = getFilteringAttributes(inDicomStream);

            destinationHolder = new DestinationHolder(inDicomStream, defaultDicomWebClient);
            selectAndPutDestinationClients(destinationHolder, callingAet, attrs);
        } else {
            destinationHolder = new DestinationHolder(inputStream, defaultDicomWebClient);
        }

        return destinationHolder;
    }

    @VisibleForTesting
    DicomInputStream createDicomInputStream(InputStream inputStream) throws IOException {
        return new DicomInputStream(inputStream);
    }

    private Attributes getFilteringAttributes(DicomInputStream inDicomStream) throws IOException {
        inDicomStream.mark(Integer.MAX_VALUE);
        Attributes attrs = inDicomStream.readDataset(-1, Tag.PixelData);
        inDicomStream.reset();
        return attrs;
    }

    protected abstract void selectAndPutDestinationClients(DestinationHolder destinationHolder, String callingAet, Attributes attrs);


}
