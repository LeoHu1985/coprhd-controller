/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.upgrade.callbacks;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.FileShare.PersonalityTypes;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

/**
 * Migration handler to update the internalSiteName field on MetroPoint Volume objects where it has
 * been set incorrectly due to pre-3.0 code. Jira: COP-27924
 *
 */
public class MetroPointVolumeInternalSiteNameMigration extends BaseCustomMigrationCallback {
    private static final Logger log = LoggerFactory.getLogger(MetroPointVolumeInternalSiteNameMigration.class);

    @Override
    public void process() throws MigrationCallbackException {
        updateVolumeInternalSiteName();
    }

    /**
     * Update the MetroPoint Volume objects to ensure the source VPlex volume uses the
     * internalSiteName of the source side backing Volume. The correct source side backing
     * volume can be found by matching up the RP copy name on the volumes.
     */
    private void updateVolumeInternalSiteName() throws MigrationCallbackException {
        log.info("Migrating MetroPoint Volume internalSiteName fields.");
        try {
            DbClient dbClient = getDbClient();
            List<URI> volumeURIs = dbClient.queryByType(Volume.class, false);
            Iterator<Volume> volumes = dbClient.queryIterativeObjects(Volume.class, volumeURIs);

            List<Volume> metroPointSourceVolumes = new ArrayList<Volume>();

            while (volumes.hasNext()) {
                Volume volume = volumes.next();
                if (volume != null && NullColumnValueGetter.isNotNullValue(volume.getRpCopyName())
                        && PersonalityTypes.SOURCE.name().equals(volume.getPersonality())
                        && volume.getAssociatedVolumes() != null && volume.getAssociatedVolumes().size() == 2) {
                    // This is a MetroPoint VPlex source volume, add it to the list.
                    metroPointSourceVolumes.add(volume);
                }

            }

            updateVolumes(metroPointSourceVolumes);
        } catch (Exception e) {
            String errorMsg = String.format("%s encounter unexpected error %s", this.getName(), e.getMessage());
            throw new MigrationCallbackException(errorMsg, e);
        }
    }

    /**
     * Updates the MetroPoint VPlex source volume's internalSiteName to match that of
     * the corresponding active production backing volume.
     *
     * @param volumes the volumes to verify and update
     */
    private void updateVolumes(List<Volume> volumes) {
        for (Volume volume : volumes) {
            for (String volUri : volume.getAssociatedVolumes()) {
                Volume backingVolume = dbClient.queryObject(Volume.class, URI.create(volUri));

                // Get the associated volumes and determine which one is the active production volume
                // based on the virtual array. The backing volume matching the virtual array of the source
                // VPlex volume is the active production backing volume. Only update the VPlex source
                // volume's internalSiteName if it is different from the corresponding backing volume.
                if (!NullColumnValueGetter.isNullURI(volume.getVirtualArray())
                        && volume.getVirtualArray().equals(backingVolume.getVirtualArray())
                        && NullColumnValueGetter.isNotNullValue(backingVolume.getInternalSiteName())
                        && !backingVolume.getInternalSiteName().equals(volume.getInternalSiteName())) {
                    log.info(String
                            .format("MetroPoint source volume [%s] has an invalid internal site name [%s]. Updating the internal site name to [%s] based on corresponding backing volume [%s].",
                                    volume.getId(), volume.getInternalSiteName(), backingVolume.getInternalSiteName(),
                                    backingVolume.getId()));
                    volume.setInternalSiteName(backingVolume.getInternalSiteName());
                    dbClient.updateObject(volume);
                }
            }
        }
    }
}
