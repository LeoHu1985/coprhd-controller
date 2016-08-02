/*
 * Copyright 2016 Dell Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.emc.storageos.driver.dellsc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.driver.dellsc.scapi.SizeUtil;
import com.emc.storageos.driver.dellsc.scapi.StorageCenterAPI;
import com.emc.storageos.driver.dellsc.scapi.StorageCenterAPIException;
import com.emc.storageos.driver.dellsc.scapi.objects.ScReplay;
import com.emc.storageos.driver.dellsc.scapi.objects.ScReplayProfile;
import com.emc.storageos.driver.dellsc.scapi.objects.ScVolume;
import com.emc.storageos.driver.dellsc.scapi.objects.ScVolumeStorageUsage;
import com.emc.storageos.storagedriver.model.StorageVolume;
import com.emc.storageos.storagedriver.model.VolumeSnapshot;

/**
 * Utility methods for the driver.
 */
public class DellSCUtil {
    private static final Logger LOG = LoggerFactory.getLogger(DellSCUtil.class);

    /**
     * Locates a consistency group.
     *
     * @param api The API connection.
     * @param ssn The Storage Center serial number.
     * @param cgName The consistency group to look for.
     * @param consistencyGroups The cache of CG info.
     * @return The consistency group instance ID.
     */
    public String findCG(StorageCenterAPI api, String ssn, String cgName, Map<String, List<ScReplayProfile>> consistencyGroups) {
        if (cgName != null && !cgName.isEmpty()) {
            // Find CG and add volume to it
            if (!consistencyGroups.containsKey(ssn)) {
                consistencyGroups.put(ssn, new ArrayList<>());
                ScReplayProfile[] cgs = api.getConsistencyGroups(ssn);
                for (ScReplayProfile cg : cgs) {
                    consistencyGroups.get(ssn).add(cg);
                }
            }

            for (ScReplayProfile cg : consistencyGroups.get(ssn)) {
                if (cgName.equals(cg.name)) {
                    // Found our requested consistency group
                    return cg.instanceId;
                }
            }
        }

        return null;
    }

    /**
     * Gets a VolumeSnapshot object for a given replay.
     *
     * @param replay The Storage Center snapshot.
     * @param snapshot The VolumeSnapshot object to populate or null.
     * @return The VolumeSnapshot object.
     */
    public VolumeSnapshot getVolumeSnapshotFromReplay(ScReplay replay, VolumeSnapshot snapshot) {
        VolumeSnapshot snap = snapshot;
        if (snap == null) {
            snap = new VolumeSnapshot();
        }
        snap.setNativeId(replay.instanceId);
        snap.setDeviceLabel(replay.instanceName);
        snap.setDisplayName(replay.instanceName);
        snap.setStorageSystemId(String.valueOf(replay.scSerialNumber));
        snap.setWwn(replay.globalIndex);
        snap.setParentId(replay.createVolume.instanceId);
        snap.setAllocatedCapacity(SizeUtil.sizeStrToBytes(replay.size));
        snap.setProvisionedCapacity(SizeUtil.sizeStrToBytes(replay.size));
        return snap;
    }

    /**
     * Populates a StorageVolume instance with Storage Center volume data.
     *
     * @param api The API connection.
     * @param volume The Storage Center volume.
     * @param cgInfo Consistency group information or null.
     * @return The StorageVolume.
     * @throws StorageCenterAPIException
     */
    public StorageVolume getStorageVolumeFromScVolume(StorageCenterAPI api, ScVolume volume, Map<ScReplayProfile, List<String>> cgInfo)
            throws StorageCenterAPIException {
        ScVolumeStorageUsage storageUsage = api.getVolumeStorageUsage(volume.instanceId);

        StorageVolume driverVol = new StorageVolume();
        driverVol.setStorageSystemId(volume.scSerialNumber);
        driverVol.setStoragePoolId(volume.storageType.instanceId);
        driverVol.setNativeId(volume.instanceId);
        driverVol.setThinlyProvisioned(true);
        driverVol.setProvisionedCapacity(SizeUtil.sizeStrToBytes(volume.configuredSize));
        driverVol.setAllocatedCapacity(SizeUtil.sizeStrToBytes(storageUsage.totalDiskSpace));
        driverVol.setWwn(volume.deviceId);
        driverVol.setDeviceLabel(volume.name);

        // Check consistency group membership
        if (cgInfo != null) {
            for (ScReplayProfile cg : cgInfo.keySet()) {
                if (cgInfo.get(cg).contains(volume.instanceId)) {
                    // Found our volume in a consistency group
                    driverVol.setConsistencyGroup(cg.instanceId);
                    break;
                }
            }
        }

        return driverVol;
    }

    /**
     * Gets consistency groups and volumes from a given Storage Center.
     *
     * @param api The API connection.
     * @param ssn The Storage Center serial number.
     * @return The consistency groups and their volumes.
     */
    public Map<ScReplayProfile, List<String>> getGCInfo(StorageCenterAPI api, String ssn) {
        Map<ScReplayProfile, List<String>> result = new HashMap<>();
        ScReplayProfile[] cgs = api.getConsistencyGroups(ssn);
        for (ScReplayProfile cg : cgs) {
            result.put(cg, new ArrayList<>());
            try {
                ScVolume[] vols = api.getReplayProfileVolumes(cg.instanceId);
                for (ScVolume vol : vols) {
                    result.get(cg).add(vol.instanceId);
                }
            } catch (StorageCenterAPIException e) {
                LOG.warn(String.format("Error getting volumes for consistency group %s: %s", cg.instanceId, e));
            }
        }

        return result;
    }
}
