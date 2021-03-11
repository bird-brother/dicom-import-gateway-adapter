package com.bird.gateway.adapter.cstore.destination;

import com.bird.gateway.adapter.ImportAdapter.*;
import com.google.common.collect.ImmutableList;
import org.bird.gateway.IGatewayClient;
import org.bird.gateway.imaging.adapter.DestinationFilter;
import org.dcm4che3.data.Attributes;

/**
 * @date 15:08 2021-3-3
 * @description TODO
 */
public class SingleDestinationClientFactory extends DestinationClientFactory {


    public SingleDestinationClientFactory(ImmutableList<Pair<DestinationFilter, IGatewayClient>> healthDestinationPairList, IGatewayClient defaultDicomWebClient) {
        super(healthDestinationPairList, defaultDicomWebClient);
    }

    @Override
    protected void selectAndPutDestinationClients(DestinationHolder destinationHolder, String callingAet, Attributes attrs) {
        for (Pair<DestinationFilter, IGatewayClient> filterToDestination: healthcareDestinations) {
            if (filterToDestination.getLeft().matches(callingAet, attrs)) {
                destinationHolder.setSingleDestination(filterToDestination.getRight());
                return;
            }
        }
    }


}
