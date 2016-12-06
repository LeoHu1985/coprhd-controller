/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.placement;


import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationGroup;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationSet;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.AttributeMatcher;
import com.emc.storageos.volumecontroller.Recommendation;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

public class RemoteReplicationScheduler implements Scheduler {

    public static final Logger _log = LoggerFactory.getLogger(RemoteReplicationScheduler.class);

    @Autowired
    protected PermissionsHelper _permissionsHelper = null;

    private DbClient _dbClient;
    private StorageScheduler _blockScheduler;
    private CoordinatorClient _coordinator;

    public void setBlockScheduler(final StorageScheduler blockScheduler) {
        _blockScheduler = blockScheduler;
    }

    public void setDbClient(final DbClient dbClient) {
        _dbClient = dbClient;
    }

    public void setCoordinator(CoordinatorClient coordinator) {
        _coordinator = coordinator;
    }

    public CoordinatorClient getCoordinator() {
        return _coordinator;
    }

    @Override
    public List getRecommendationsForResources(VirtualArray vArray, Project project, VirtualPool vPool, VirtualPoolCapabilityValuesWrapper capabilities) {
        /**
         * 1. We call basic storage scheduler to get matching storage pools for source volumes (based on source storage systems and
         * based on source virtual array and source virtual pool)
         *
         * 2. We call basic storage scheduler to get matching storage pools for target volumes (based on target storage system and
         * based on target varray and optionally target virtual pool)
         *
         * 3. Build recommendations for source and target storage volumes based on sets of matching source and target storage pools.
         */
        _log.info("Schedule storage for {} resource(s) of size {}.",
                capabilities.getResourceCount(), capabilities.getSize());
        Map<String, Object> attributeMap = new HashMap<>();

        Set<String> sourceStorageSystems = new HashSet<>();
        Set<String> targetStorageSystems = new HashSet<>();
        // Get all storage pools which can be used for source volumes

        // Get source and target storage systems from remote replication configuration
        if (capabilities.getRemoteReplicationGroup() != null) {
            RemoteReplicationGroup rrGroup = _dbClient.queryObject(RemoteReplicationGroup.class, capabilities.getRemoteReplicationGroup());
            sourceStorageSystems.add(rrGroup.getSourceSystem().toString());
            targetStorageSystems.add(rrGroup.getTargetSystem().toString());
        } else if (capabilities.getRemoteReplicationSet() != null) {
            RemoteReplicationSet rrSet = _dbClient.queryObject(RemoteReplicationSet.class, capabilities.getRemoteReplicationSet());
            sourceStorageSystems = rrSet.getSourceSystems();
            targetStorageSystems = rrSet.getTargetSystems();
        } else {
            // todo: this is error

        }

        // Get all storage pools for source volumes that match set of source storage systems and also match
        // passed vpool params and protocols. In addition, the pool must have enough capacity
        // to hold at least one resource of the requested size.
        attributeMap.put(AttributeMatcher.Attributes.storage_system.name(), sourceStorageSystems);
        List<StoragePool> sourcePools = _blockScheduler.getMatchingPools(vArray, vPool, capabilities, attributeMap);

        if (sourcePools == null || sourcePools.isEmpty()) {
            _log.error(
                    "No matching storage pools found for the source side of remotely replicated volumes. /n" +
                            "Source storage systems: {0} . /n" +
                            "There are no storage pools that "
                            + "match the passed vpool parameters and protocols and/or there are no pools that have enough capacity to "
                            + "hold at least one resource of the requested size.",
                    sourceStorageSystems);
            StringBuffer errorMessage = new StringBuffer();
            if (attributeMap.get(AttributeMatcher.ERROR_MESSAGE) != null) {
                errorMessage = (StringBuffer) attributeMap.get(AttributeMatcher.ERROR_MESSAGE);
            }
            throw APIException.badRequests.noStoragePools(vArray.getLabel(), vPool.getLabel(),
                    errorMessage.toString());
        }

        // Get all storage pools for target volumes that match set of target storage systems
        // and also match passed target vpool params and
        // protocols. In addition, the pool must have enough capacity
        // to hold at least one resource of the requested size.
        VirtualPoolCapabilityValuesWrapper targetCapabilities;
        StringMap remoteReplicationSettings = vPool.getRemoteReplicationProtectionSettings();
        VirtualArray targetVirtualArray = null;
        VirtualPool targetVirtualPool = vPool;
        List<StoragePool> targetPools = null;
        // todo: we support only single target virtual array for remote replication for now.
        for (Map.Entry<String, String> entry : remoteReplicationSettings.entrySet()) {
            String targetVirtualArrayId = entry.getKey();
            targetVirtualArray = _dbClient.queryObject(VirtualArray.class, URIUtil.uri(targetVirtualArrayId));
            String targetVirtualPoolId = entry.getValue();
            if (targetVirtualPoolId == null) {
                targetVirtualPool = vPool;
            } else {
                targetVirtualPool = _dbClient.queryObject(VirtualPool.class, URIUtil.uri(targetVirtualPoolId));
            }

            // if target virtual pool is the same as the source virtual pool use original capabilities
            if (targetVirtualPool.getId().equals(vPool.getId())) {
                targetCapabilities = capabilities;
            } else {
                // build capabilities based on target vpool
                // todo:
                // temporary use original capabilities
                targetCapabilities = capabilities;
            }
            attributeMap.put(AttributeMatcher.Attributes.storage_system.name(), targetStorageSystems);
            targetPools = _blockScheduler.getMatchingPools(targetVirtualArray, targetVirtualPool, targetCapabilities, attributeMap);

            if (targetPools == null || targetPools.isEmpty()) {
                _log.error(
                        "No matching storage pools found for the target side of remotely replicated volumes. /n" +
                                "Target storage systems: {0} . /n" +
                                "There are no storage pools that "
                                + "match the passed vpool parameters and protocols and/or there are no pools that have enough capacity to "
                                + "hold at least one resource of the requested size.",
                        targetStorageSystems);
                StringBuffer errorMessage = new StringBuffer();
                if (attributeMap.get(AttributeMatcher.ERROR_MESSAGE) != null) {
                    errorMessage = (StringBuffer) attributeMap.get(AttributeMatcher.ERROR_MESSAGE);
                }
                throw APIException.badRequests.noStoragePools(targetVirtualArray.getLabel(), targetVirtualPool.getLabel(),
                        errorMessage.toString());
            }
            break;
        }

        // list of recommendations for all volumes
        List<Recommendation> volumeRecommendations = new ArrayList<>();
        // We have candidate source and target storage pools now. Get recommendations for the source and target volumes.
        List<Recommendation> sourceRecommendationsForPools = _blockScheduler.getRecommendationsForPools(vArray.getId().toString(), sourcePools, capabilities);
        if (sourceRecommendationsForPools.isEmpty()) {
            String msg = String.format(
                    "Could not find matching source pools for VArray %s & VPool %s",
                    vArray.getId(), vPool.getId());
            _log.error(msg);
            throw APIException.badRequests.noStoragePools(vArray.getLabel(), vPool.getLabel(), msg);
        }

        List<Recommendation> targetRecommendationsForPools = _blockScheduler.getRecommendationsForPools(targetVirtualArray.getId().toString(),
                targetPools, capabilities);
        if (targetRecommendationsForPools.isEmpty()) {
            String msg = String.format(
                    "Could not build recommendations for target pools for VArray %s & VPool %s",
                    targetVirtualArray.getId(), targetVirtualPool.getId());
            _log.error(msg);
            throw APIException.badRequests.noStoragePools(targetVirtualArray.getLabel(), targetVirtualPool.getLabel(), msg);
        }

        // Build recommendation for each volume
        // Create recommendation for each source volume
        List<VolumeRecommendation> sourceVolumeRecommendations = new ArrayList<>();
        for (Recommendation recommendation : sourceRecommendationsForPools) {
            int count = recommendation.getResourceCount();
            while (count > 0) {
                VolumeRecommendation volumeRecommendation = new VolumeRecommendation(VolumeRecommendation.VolumeType.BLOCK_VOLUME,
                        capabilities.getSize(), vPool, vArray.getId());
                volumeRecommendation.setSourceStoragePool(recommendation.getSourceStoragePool());
                volumeRecommendation.setSourceStorageSystem(recommendation.getSourceStorageSystem());
                volumeRecommendation.setVirtualArray(vArray.getId());
                volumeRecommendation.setVirtualPool(vPool);
                volumeRecommendation.setResourceCount(1);
                volumeRecommendation.addStoragePool(recommendation.getSourceStoragePool());
                volumeRecommendation.addStorageSystem(recommendation.getSourceStorageSystem());
                sourceVolumeRecommendations.add(volumeRecommendation);
                if (capabilities.getBlockConsistencyGroup() != null) {
                    volumeRecommendation.setParameter(VolumeRecommendation.ARRAY_CG, capabilities.getBlockConsistencyGroup());
                }
                count--;
            }
        }

//        List<Recommendation> sourceVolumeRecommendations = new ArrayList<>();
//        for (Recommendation recommendation : sourceRecommendationsForPools) {
//            int count = recommendation.getResourceCount();
//            while (count > 0) {
//                Recommendation volumeRecommendation = new Recommendation();
//                volumeRecommendation.setSourceStoragePool(recommendation.getSourceStoragePool());
//                volumeRecommendation.setSourceStorageSystem(recommendation.getSourceStorageSystem());
//                volumeRecommendation.setVirtualArray(vArray.getId());
//                volumeRecommendation.setVirtualPool(vPool);
//                volumeRecommendation.setResourceCount(1);
//                sourceVolumeRecommendations.add(volumeRecommendation);
//            //    volumeRecommendations.add(volumeRecommendation);
//                count--;
//            }
//        }

        // Create recommendation for each target volume. Use source volume recommendations as underlying recommendations.
        int sourceVolumeRecommendationIndex = 0;
        for (Recommendation targetRecommendation : targetRecommendationsForPools) {
            int count = targetRecommendation.getResourceCount();
            while (count > 0) {
                VolumeRecommendation volumeRecommendation = new VolumeRecommendation(VolumeRecommendation.VolumeType.BLOCK_VOLUME,
                        capabilities.getSize(), targetVirtualPool, targetVirtualArray.getId());
                volumeRecommendation.setSourceStoragePool(targetRecommendation.getSourceStoragePool());
                volumeRecommendation.setSourceStorageSystem(targetRecommendation.getSourceStorageSystem());
                volumeRecommendation.setVirtualArray(targetVirtualArray.getId());
                volumeRecommendation.setVirtualPool(targetVirtualPool);
                volumeRecommendation.setResourceCount(1);
                volumeRecommendation.addStoragePool(targetRecommendation.getSourceStoragePool());
                volumeRecommendation.addStorageSystem(targetRecommendation.getSourceStorageSystem());
                if (capabilities.getBlockConsistencyGroup() != null) {
                    volumeRecommendation.setParameter(VolumeRecommendation.ARRAY_CG, capabilities.getBlockConsistencyGroup());
                }
                // set source volume recommendation as underlying recommendation
                volumeRecommendation.setRecommendation(sourceVolumeRecommendations.get(sourceVolumeRecommendationIndex++));
                volumeRecommendations.add(volumeRecommendation);
                count--;
            }
        }

        return volumeRecommendations;
    }

    @Override
    public String getSchedulerName() {
        return null;
    }

    @Override
    public boolean handlesVpool(VirtualPool vPool, VpoolUse vPoolUse) {
        return (VirtualPool.vPoolSpecifiesRemoteReplication(vPool));
    }


    @Override
    public List<Recommendation> getRecommendationsForVpool(VirtualArray vArray, Project project, VirtualPool vPool, VpoolUse vPoolUse,
                                                           VirtualPoolCapabilityValuesWrapper capabilities, Map<VpoolUse, List<Recommendation>> currentRecommendations) {

        return getRecommendationsForResources(vArray, project, vPool, capabilities);
    }
}
