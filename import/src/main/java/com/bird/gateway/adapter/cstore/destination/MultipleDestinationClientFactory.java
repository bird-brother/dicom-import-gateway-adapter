package com.bird.gateway.adapter.cstore.destination;

import com.bird.gateway.adapter.ImportAdapter.*;
import com.google.common.collect.ImmutableList;

import org.bird.gateway.IGatewayClient;
import org.bird.gateway.imaging.adapter.AetDictionary.*;
import org.bird.gateway.imaging.adapter.DestinationFilter;
import org.dcm4che3.data.Attributes;

/**
 * @date 15:08 2021-3-3
 * @description TODO
 */
public class MultipleDestinationClientFactory extends DestinationClientFactory {

    private ImmutableList<Pair<DestinationFilter, Aet>> dicomDestinations;

    public MultipleDestinationClientFactory(ImmutableList<Pair<DestinationFilter, IGatewayClient>> healthcareDestinations,
                                            ImmutableList<Pair<DestinationFilter, Aet>> dicomDestinations,
                                            IGatewayClient defaultDicomWebClient) {
        super(healthcareDestinations, defaultDicomWebClient, dicomDestinations != null && !dicomDestinations.isEmpty());
        this.dicomDestinations = dicomDestinations;
    }


    @Override
    protected void selectAndPutDestinationClients(DestinationHolder destinationHolder, String callingAet, Attributes attrs) {
        ImmutableList.Builder<IGatewayClient> filteredHealthcareWebClientsBuilder = ImmutableList.builder();
        if (healthcareDestinations != null) {
            for (Pair<DestinationFilter, IGatewayClient> filterToDestination : healthcareDestinations) {
                if (filterToDestination.getLeft().matches(callingAet, attrs)) {
                    filteredHealthcareWebClientsBuilder.add(filterToDestination.getRight());
                }
            }
            destinationHolder.setHealthcareDestinations(filteredHealthcareWebClientsBuilder.build());
        }

        if (dicomDestinations != null) {
            ImmutableList.Builder<Aet> filteredDicomDestinationsBuilder = ImmutableList.builder();
            for (Pair<DestinationFilter, Aet> filterToDestination : dicomDestinations) {
                if (filterToDestination.getLeft().matches(callingAet, attrs)) {
                    filteredDicomDestinationsBuilder.add(filterToDestination.getRight());
                }
            }
            destinationHolder.setDicomDestinations(filteredDicomDestinationsBuilder.build());
        }
    }

}
