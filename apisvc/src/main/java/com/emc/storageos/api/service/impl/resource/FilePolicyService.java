/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.DbObjectMapper.toLink;
import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;
import static com.emc.storageos.api.mapper.FilePolicyMapper.map;

import java.net.URI;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.api.mapper.functions.MapFilePolicy;
import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.resource.utils.FilePolicyServiceUtils;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.FilePolicy;
import com.emc.storageos.db.client.model.FilePolicy.FilePolicyApplyLevel;
import com.emc.storageos.db.client.model.FilePolicy.FilePolicyType;
import com.emc.storageos.db.client.model.FilePolicy.SnapshotExpireType;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.auth.ACLAssignmentChanges;
import com.emc.storageos.model.auth.ACLAssignments;
import com.emc.storageos.model.file.policy.FilePolicyAssignParam;
import com.emc.storageos.model.file.policy.FilePolicyAssignResp;
import com.emc.storageos.model.file.policy.FilePolicyBulkRep;
import com.emc.storageos.model.file.policy.FilePolicyCreateResp;
import com.emc.storageos.model.file.policy.FilePolicyListRestRep;
import com.emc.storageos.model.file.policy.FilePolicyParam;
import com.emc.storageos.model.file.policy.FilePolicyRestRep;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;

/**
 * @author jainm15
 */
@Path("/file/file-policies")
@DefaultPermissions(readRoles = { Role.TENANT_ADMIN, Role.SYSTEM_MONITOR }, writeRoles = { Role.TENANT_ADMIN })
public class FilePolicyService extends TaskResourceService {

    private static final Logger _log = LoggerFactory.getLogger(FilePolicyService.class);

    protected static final String EVENT_SERVICE_SOURCE = "FilePolicyService";

    private static final String EVENT_SERVICE_TYPE = "FilePolicy";

    @Autowired
    private RecordableEventManager _evtMgr;

    @Autowired
    private NetworkService networkSvc;

    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    @Override
    protected FilePolicy queryResource(URI id) {
        ArgValidator.checkUri(id);
        FilePolicy filePolicy = _permissionsHelper.getObjectById(id, FilePolicy.class);
        ArgValidator.checkEntityNotNull(filePolicy, id, isIdEmbeddedInURL(id));
        return filePolicy;
    }

    @Override
    protected URI getTenantOwner(URI id) {
        return null;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.FILE_POLICY;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Class<FilePolicy> getResourceClass() {
        return FilePolicy.class;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public FilePolicyBulkRep queryBulkResourceReps(List<URI> ids) {
        Iterator<FilePolicy> _dbIterator = _dbClient.queryIterativeObjects(
                getResourceClass(), ids);
        BulkList.ResourceFilter filter = new BulkList.FilePolicyResourceFilter(getUserFromContext(), _permissionsHelper);
        return new FilePolicyBulkRep(BulkList.wrapping(_dbIterator,
                MapFilePolicy.getInstance(_dbClient), filter));
    }

    @Override
    public FilePolicyBulkRep queryFilteredBulkResourceReps(List<URI> ids) {
        return queryBulkResourceReps(ids);
    }

    /**
     * Retrieve resource representations based on input ids.
     * 
     * @param param POST data containing the id list.
     * @brief List of file policies of given ids.
     * @return list of representations.
     */
    @POST
    @Path("/bulk")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Override
    public FilePolicyBulkRep getBulkResources(BulkIdParam param) {
        return (FilePolicyBulkRep) super.getBulkResources(param);
    }

    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN })
    public FilePolicyCreateResp createFilePolicy(FilePolicyParam param) {

        // Make policy name as mandatory field
        ArgValidator.checkFieldNotNull(param.getPolicyName(), "policyName");

        // Check for duplicate policy name
        if (param.getPolicyName() != null && !param.getPolicyName().isEmpty()) {
            checkForDuplicateName(param.getPolicyName(), FilePolicy.class);
        }
        // check policy type is valid or not
        ArgValidator.checkFieldValueFromEnum(param.getPolicyType(), "policy_type",
                EnumSet.allOf(FilePolicyType.class));

        _log.info("file policy creation started -- ");
        if (param.getPolicyType().equals(FilePolicyType.file_replication.name())) {
            return createFileReplicationPolicy(param);

        } else if (param.getPolicyType().equals(FilePolicyType.file_snapshot.name())) {
            return createFileSnapshotPolicy(param);
        }
        return null;
    }

    /**
     * Gets the ids and self links of all file policies.
     * 
     * @brief List file policies
     * @return A list of file policy reference specifying the ids and self links.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.TENANT_ADMIN })
    public FilePolicyListRestRep getFilePolicies() {

        FilePolicyListRestRep filePolicyList = new FilePolicyListRestRep();
        List<URI> ids = _dbClient.queryByType(FilePolicy.class, true);
        for (URI id : ids) {
            FilePolicy filePolicy = _dbClient.queryObject(FilePolicy.class, id);

            if (filePolicy != null) {

                if (canAccessFilePolicy(filePolicy)) {
                    filePolicyList.add(toNamedRelatedResource(filePolicy, filePolicy.getFilePolicyName()));
                }
            }
        }

        return filePolicyList;
    }

    private boolean canAccessFilePolicy(FilePolicy filePolicy) {
        StringSet tenants = filePolicy.getTenantOrg();

        if (tenants == null || tenants.isEmpty()) {
            return true;
        }

        if (isSystemAdmin()) {
            return true;
        }

        StorageOSUser user = getUserFromContext();
        String userTenantId = user.getTenantId();

        return tenants.contains(userTenantId);
    }

    /**
     * Get details of a file policy.
     * 
     * @param id the file file policy id.
     * @return File policy information.
     */
    @GET
    @Path("/{id}")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.TENANT_ADMIN })
    public FilePolicyRestRep getFilePolicy(@PathParam("id") URI id) {

        _log.info("Request recieved to get the file policy of id: {}", id);
        FilePolicy filepolicy = queryResource(id);
        ArgValidator.checkEntity(filepolicy, id, true);

        if (!canAccessFilePolicy(filepolicy)) {
            throw APIException.forbidden.tenantCannotAccessFilePolicy(filepolicy.getFilePolicyName());
        }

        return map(filepolicy, _dbClient);
    }

    @DELETE
    @Path("/{id}")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN })
    public Response deleteFilePolicy(@PathParam("id") URI id) {

        FilePolicy filepolicy = queryResource(id);
        ArgValidator.checkEntity(filepolicy, id, true);

        ArgValidator.checkReference(FilePolicy.class, id, checkForDelete(filepolicy));

        String policyAppliedAt = filepolicy.getApplyAt();
        if (policyAppliedAt != null) {
            _log.error("Delete file pocicy failed because the policy is applied at " + policyAppliedAt);
            throw APIException.badRequests.failedToDeleteFilePolicy(filepolicy.getLabel(), "This policy is applied at: " + policyAppliedAt);
        }
        StringSet assignedResources = filepolicy.getAssignedResources();

        if (assignedResources != null && !assignedResources.isEmpty()) {
            _log.error("Delete file pocicy failed because the policy has associacted resources");
            throw APIException.badRequests.failedToDeleteFilePolicy(filepolicy.getLabel(), "This policy has assigned resources.");
        }

        _dbClient.markForDeletion(filepolicy);

        auditOp(OperationTypeEnum.DELETE_FILE_POLICY, true, null, filepolicy.getId().toString(),
                filepolicy.getLabel());
        return Response.ok().build();
    }

    @PUT
    @Path("/{id}/assign-policy")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN })
    public FilePolicyAssignResp assignFilePolicy(@PathParam("id") URI id, FilePolicyAssignParam param) {

        ArgValidator.checkFieldUriType(id, FilePolicy.class, "id");
        FilePolicy filepolicy = this._dbClient.queryObject(FilePolicy.class, id);
        ArgValidator.checkEntity(filepolicy, id, true);

        ArgValidator.checkFieldValueFromEnum(param.getApplyAt(), "apply_at",
                EnumSet.allOf(FilePolicyApplyLevel.class));

        StringBuilder errorMsg = new StringBuilder();
        if (filepolicy.getApplyAt() != null && !param.getApplyAt().equals(filepolicy.getApplyAt())) {
            errorMsg.append("File Policy" + id + " is already applied to :" + filepolicy.getApplyAt());
            _log.error(errorMsg.toString());
            throw APIException.badRequests.invalidFilePolicyAssignParam(filepolicy.getFilePolicyName(), errorMsg.toString());
        }

        if (param.getApplyAt().equals(FilePolicyApplyLevel.vpool.name())) {
            return assignFilePolicyToVpool(param, filepolicy);
        } else if (param.getApplyAt().equals(FilePolicyApplyLevel.project.name())) {
            return assignFilePolicyToProject(param, filepolicy);
        } else if (param.getApplyAt().equals(FilePolicyApplyLevel.file_system.name())) {
            return assignFilePolicyToFS(param, filepolicy);
        }
        return null;
    }

    /**
     * Add or remove individual File Policy ACL entry(s). Request body must include at least one add or remove operation.
     * 
     * @param id the URN of a ViPR File Policy
     * @param changes ACL assignment changes
     * @brief Add or remove ACL entries from file store VirtualPool
     * @return No data returned in response body
     */
    @PUT
    @Path("/{id}/acl")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.SYSTEM_ADMIN, Role.RESTRICTED_SECURITY_ADMIN }, blockProxies = true)
    public ACLAssignments updateAcls(@PathParam("id") URI id,
            ACLAssignmentChanges changes) {
        // return updateAclsOnVirtualPool(VirtualPool.Type.file, id, changes);

        FilePolicy policy = queryResource(id);
        ArgValidator.checkEntityNotNull(policy, id, isIdEmbeddedInURL(id));
        _permissionsHelper.updateACLs(policy, changes,
                new PermissionsHelper.UsageACLFilter(_permissionsHelper));
        _dbClient.updateObject(policy);
        return getAclsOnPolicy(id);
    }

    /**
     * Get File Policy ACLs
     * 
     * @param id the URN of a ViPR FilePolicy
     * @brief Show ACL entries for File policy
     * @return ACL Assignment details
     */
    @GET
    @Path("/{id}/acl")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public ACLAssignments getAcls(@PathParam("id") URI id) {
        return getAclsOnPolicy(id);
    }

    protected ACLAssignments getAclsOnPolicy(URI id) {
        FilePolicy policy = queryResource(id);
        ArgValidator.checkEntityNotNull(policy, id, isIdEmbeddedInURL(id));
        ACLAssignments response = new ACLAssignments();
        response.setAssignments(_permissionsHelper.convertToACLEntries(policy.getAcls()));
        return response;
    }

    /**
     * Replication Policy Checks and creation.
     * 
     * @param param
     * @return
     */
    private FilePolicyCreateResp createFileReplicationPolicy(FilePolicyParam param) {
        StringBuilder errorMsg = new StringBuilder();
        FilePolicy fileReplicationPolicy = new FilePolicy();

        // Validate replication policy schedule parameters
        boolean isValidSchedule = FilePolicyServiceUtils.validatePolicySchdeuleParam(
                param.getPolicySchedule(), fileReplicationPolicy, errorMsg);
        if (!isValidSchedule && errorMsg.length() > 0) {
            _log.error("Failed to create file replication policy due to {} ", errorMsg.toString());
            throw APIException.badRequests.invalidFilePolicyScheduleParam(param.getPolicyName(), errorMsg.toString());
        }
        // validate replication type and copy mode parameters
        ArgValidator.checkFieldValueFromEnum(param.getReplicationPolicyParams().getReplicationType(), "replicationType",
                EnumSet.allOf(FilePolicy.FileReplicationType.class));

        ArgValidator.checkFieldValueFromEnum(param.getReplicationPolicyParams().getReplicationCopyMode(), "replicationCopyMode",
                EnumSet.allOf(FilePolicy.FileReplicationCopyMode.class));

        fileReplicationPolicy.setId(URIUtil.createId(FilePolicy.class));
        fileReplicationPolicy.setLabel(param.getPolicyName());
        fileReplicationPolicy.setFilePolicyName(param.getPolicyName());
        fileReplicationPolicy.setFilePolicyType(param.getPolicyType());
        if (param.getPolicyDescription() != null && !param.getPolicyDescription().isEmpty()) {
            fileReplicationPolicy.setFilePolicyDescription(param.getPolicyDescription());
        }
        fileReplicationPolicy.setScheduleFrequency(param.getPolicySchedule().getScheduleFrequency());
        fileReplicationPolicy.setFileReplicationType(param.getReplicationPolicyParams().getReplicationType());
        fileReplicationPolicy.setFileReplicationCopyMode(param.getReplicationPolicyParams().getReplicationCopyMode());
        this._dbClient.createObject(fileReplicationPolicy);
        _log.info("Policy {} created successfully", fileReplicationPolicy);

        return new FilePolicyCreateResp(fileReplicationPolicy.getId(), toLink(ResourceTypeEnum.FILE_POLICY,
                fileReplicationPolicy.getId()), fileReplicationPolicy.getLabel());
    }

    /**
     * Snapshot Policy checks and creation.
     * 
     * @param param
     * @return
     */
    private FilePolicyCreateResp createFileSnapshotPolicy(FilePolicyParam param) {
        StringBuilder errorMsg = new StringBuilder();
        FilePolicy fileSnapshotPolicy = new FilePolicy();

        // Validate snapshot policy schedule parameters
        boolean isValidSchedule = FilePolicyServiceUtils.validatePolicySchdeuleParam(
                param.getPolicySchedule(), fileSnapshotPolicy, errorMsg);
        if (!isValidSchedule && errorMsg.length() > 0) {
            _log.error("Failed to create file snapshot policy due to {} ", errorMsg.toString());
            throw APIException.badRequests.invalidFilePolicyScheduleParam(param.getPolicyName(), errorMsg.toString());
        }

        // Validate snapshot policy expire parameters..
        if (param.getSnapshotPolicyPrams().getSnapshotExpireParams() != null) {
            FilePolicyServiceUtils.validateSnapshotPolicyParam(param.getSnapshotPolicyPrams());
        } else {
            errorMsg.append("Required parameter snapshot_expire was missing or empty");
            _log.error("Failed to create snapshot policy due to {} ", errorMsg.toString());
            throw APIException.badRequests.invalidFilePolicyScheduleParam(param.getPolicyName(), errorMsg.toString());
        }
        fileSnapshotPolicy.setId(URIUtil.createId(FilePolicy.class));
        fileSnapshotPolicy.setLabel(param.getPolicyName());
        fileSnapshotPolicy.setFilePolicyType(param.getPolicyType());
        fileSnapshotPolicy.setFilePolicyName(param.getPolicyName());
        if (param.getPolicyDescription() != null && !param.getPolicyDescription().isEmpty()) {
            fileSnapshotPolicy.setFilePolicyDescription(param.getPolicyDescription());
        }
        fileSnapshotPolicy.setScheduleFrequency(param.getPolicySchedule().getScheduleFrequency());
        fileSnapshotPolicy.setSnapshotExpireType(param.getSnapshotPolicyPrams().getSnapshotExpireParams().getExpireType());
        if (!param.getSnapshotPolicyPrams().getSnapshotExpireParams().getExpireType()
                .equalsIgnoreCase(SnapshotExpireType.NEVER.toString())) {
            fileSnapshotPolicy.setSnapshotExpireTime((long) param.getSnapshotPolicyPrams().getSnapshotExpireParams().getExpireValue());
        }
        this._dbClient.createObject(fileSnapshotPolicy);
        _log.info("Snapshot policy {} created successfully", fileSnapshotPolicy);

        return new FilePolicyCreateResp(fileSnapshotPolicy.getId(), toLink(ResourceTypeEnum.FILE_POLICY,
                fileSnapshotPolicy.getId()), fileSnapshotPolicy.getLabel());
    }

    /**
     * Assigning policy at vpool level
     * 
     * @param param
     * @param filepolicy
     */
    private FilePolicyAssignResp assignFilePolicyToVpool(FilePolicyAssignParam param, FilePolicy filepolicy) {
        StringBuilder errorMsg = new StringBuilder();
        ArgValidator.checkFieldNotNull(param.getVpoolAssignParams(), "vpool_assign_param");

        if (param.getVpoolAssignParams().getAssigntoAll()) {
            // policy has to be applied on all applicable file vpools
            List<URI> vpoolIDs = this._dbClient.queryByType(VirtualPool.class, true);
            List<VirtualPool> virtualPools = this._dbClient.queryObject(VirtualPool.class, vpoolIDs);
            StringSet assignedResources = new StringSet();
            for (VirtualPool virtualPool : virtualPools) {
                if (virtualPool.getType().equals(VirtualPool.Type.file.name())
                        && FilePolicyServiceUtils.validateVpoolSupportPolicyType(filepolicy, virtualPool)) {
                    assignedResources.add(virtualPool.getId().toString());
                }
            }
            filepolicy.setApplyAt(FilePolicyApplyLevel.vpool.name());
            filepolicy.setAssignedResources(assignedResources);

        } else {
            // Policy has to be applied on specified file vpools..
            ArgValidator.checkFieldNotNull(param.getVpoolAssignParams().getAssigntoVpools(), "assign_to_vpools");
            Set<URI> vpoolURIs = param.getVpoolAssignParams().getAssigntoVpools();
            StringSet assignedResources = new StringSet();

            for (URI vpoolURI : vpoolURIs) {
                ArgValidator.checkFieldUriType(vpoolURI, VirtualPool.class, "vpool");
                VirtualPool virtualPool = this._dbClient.queryObject(VirtualPool.class, vpoolURI);
                ArgValidator.checkEntity(virtualPool, vpoolURI, false);

                if (filepolicy.getAssignedResources() != null
                        && filepolicy.getAssignedResources().contains(virtualPool.getId().toString())) {
                    errorMsg.append("Provided vpool :" + virtualPool.getId().toString() + " is already assigned to policy"
                            + filepolicy.getId().toString());
                    _log.error(errorMsg.toString());
                    throw APIException.badRequests.invalidFilePolicyAssignParam(filepolicy.getFilePolicyName(), errorMsg.toString());
                }

                if (FilePolicyServiceUtils.validateVpoolSupportPolicyType(filepolicy, virtualPool)) {
                    assignedResources.add(virtualPool.getId().toString());
                } else {
                    errorMsg.append("Provided vpool :" + virtualPool.getId().toString() + " doesn't support policy type:"
                            + filepolicy.getFilePolicyType());
                    _log.error(errorMsg.toString());
                    throw APIException.badRequests.invalidFilePolicyAssignParam(filepolicy.getFilePolicyName(), errorMsg.toString());
                }
            }
            filepolicy.setApplyAt(FilePolicyApplyLevel.vpool.name());
            filepolicy.setAssignedResources(assignedResources);
        }
        this._dbClient.updateObject(filepolicy);
        return new FilePolicyAssignResp(filepolicy.getId(), toLink(ResourceTypeEnum.FILE_POLICY,
                filepolicy.getId()), filepolicy.getLabel(), filepolicy.getApplyAt(), filepolicy.getAssignedResources());
    }

    /**
     * Assign policy at project level
     * 
     * @param param
     * @param filepolicy
     */
    private FilePolicyAssignResp assignFilePolicyToProject(FilePolicyAssignParam param, FilePolicy filepolicy) {
        StringBuilder errorMsg = new StringBuilder();
        ArgValidator.checkFieldNotNull(param.getProjectAssignParams(), "project_assign_param");
        ArgValidator.checkFieldUriType(param.getProjectAssignParams().getVpool(), VirtualPool.class, "vpool");
        VirtualPool vpool = this._dbClient.queryObject(VirtualPool.class, param.getProjectAssignParams().getVpool());
        ArgValidator.checkEntity(vpool, vpool.getId(), false);

        // Check if the vpool supports provided policy type..
        if (!FilePolicyServiceUtils.validateVpoolSupportPolicyType(filepolicy, vpool)) {
            errorMsg.append("Provided vpool :" + vpool.getId().toString() + " doesn't support policy type:"
                    + filepolicy.getFilePolicyType());
            _log.error(errorMsg.toString());
            throw APIException.badRequests.invalidFilePolicyAssignParam(filepolicy.getFilePolicyName(), errorMsg.toString());
        }

        // Check if the vpool supports policy at project level..
        if (!vpool.isFilePolicyAtProjectLevel()) {
            errorMsg.append("Provided vpool :" + vpool.getId().toString() + " doesn't support policy at project level");
            _log.error(errorMsg.toString());
            throw APIException.badRequests.invalidFilePolicyAssignParam(filepolicy.getFilePolicyName(), errorMsg.toString());
        }

        if (param.getProjectAssignParams().getAssigntoAll()) {
            // policy has to be applied on all projects.
            List<URI> projectIDs = this._dbClient.queryByType(Project.class, true);
            StringSet assignedResources = new StringSet();
            for (URI projectID : projectIDs) {
                assignedResources.add(projectID.toString());
            }
            filepolicy.setApplyAt(FilePolicyApplyLevel.project.name());
            filepolicy.setAssignedResources(assignedResources);
            filepolicy.setFilePolicyVpool(param.getProjectAssignParams().getVpool());
        } else {
            // policy has to be applied on specified projects
            ArgValidator.checkFieldNotNull(param.getProjectAssignParams().getAssigntoProjects(), "assign_to_projects");
            Set<URI> projetcURIs = param.getProjectAssignParams().getAssigntoProjects();
            StringSet assignedResources = new StringSet();
            for (URI projetcURI : projetcURIs) {
                ArgValidator.checkFieldUriType(projetcURI, Project.class, "project");
                Project project = this._dbClient.queryObject(Project.class, projetcURI);
                ArgValidator.checkEntity(project, projetcURI, false);

                // Check if policy is being already assigned this project
                if (filepolicy.getAssignedResources() != null && filepolicy.getAssignedResources().contains(project.getId().toString())) {
                    errorMsg.append("Provided project :" + project.getId().toString() + " is already assigned to policy"
                            + filepolicy.getId().toString());
                    _log.error(errorMsg.toString());
                    throw APIException.badRequests.invalidFilePolicyAssignParam(filepolicy.getFilePolicyName(), errorMsg.toString());
                }

                assignedResources.add(projetcURI.toString());
            }
            filepolicy.setApplyAt(FilePolicyApplyLevel.project.name());
            filepolicy.setAssignedResources(assignedResources);
            filepolicy.setFilePolicyVpool(param.getProjectAssignParams().getVpool());
        }
        this._dbClient.updateObject(filepolicy);
        return new FilePolicyAssignResp(filepolicy.getId(), toLink(ResourceTypeEnum.FILE_POLICY,
                filepolicy.getId()), filepolicy.getLabel(), filepolicy.getApplyAt(), filepolicy.getAssignedResources());
    }

    /**
     * Assign policy at File system level
     * 
     * @param param
     * @param filepolicy
     */
    private FilePolicyAssignResp assignFilePolicyToFS(FilePolicyAssignParam param, FilePolicy filepolicy) {
        StringBuilder errorMsg = new StringBuilder();

        // check the vpool parameter
        ArgValidator.checkFieldNotNull(param.getFileSystemAssignParams(), "filesystem_assign_param");

        ArgValidator.checkFieldUriType(param.getFileSystemAssignParams().getVpool(), VirtualPool.class, "vpool");
        VirtualPool vpool = this._dbClient.queryObject(VirtualPool.class, param.getFileSystemAssignParams().getVpool());
        ArgValidator.checkEntity(vpool, vpool.getId(), false);

        // Check if policy is being already mapped to the provided vpool
        if (filepolicy.getFilePolicyVpool() != null && filepolicy.getFilePolicyVpool().equals(vpool.getId())) {
            errorMsg.append("Provided vpool :" + vpool.getId().toString() + " is already assigned to policy"
                    + filepolicy.getId().toString());
            _log.error(errorMsg.toString());
            throw APIException.badRequests.invalidFilePolicyAssignParam(filepolicy.getFilePolicyName(), errorMsg.toString());
        }

        // Check if the vpool supports provided policy type
        if (!FilePolicyServiceUtils.validateVpoolSupportPolicyType(filepolicy, vpool)) {
            errorMsg.append("Provided vpool :" + vpool.getId().toString() + " doesn't support policy type:"
                    + filepolicy.getFilePolicyType());
            _log.error(errorMsg.toString());
            throw APIException.badRequests.invalidFilePolicyAssignParam(filepolicy.getFilePolicyName(), errorMsg.toString());
        }

        // Check if the vpool supports policy at file system level..
        if (!vpool.isFilePolicyAtFSLevel()) {
            errorMsg.append("Provided vpool :" + vpool.getId().toString() + " doesn't support policy at file system level");
            _log.error(errorMsg.toString());
            throw APIException.badRequests.invalidFilePolicyAssignParam(filepolicy.getFilePolicyName(), errorMsg.toString());
        }

        filepolicy.setApplyAt(FilePolicyApplyLevel.file_system.name());
        filepolicy.setFilePolicyVpool(param.getFileSystemAssignParams().getVpool());
        
        filepolicy.setApplyToAllFS(param.getFileSystemAssignParams().getAssigntoAll());
        this._dbClient.updateObject(filepolicy);
        return new FilePolicyAssignResp(filepolicy.getId(), toLink(ResourceTypeEnum.FILE_POLICY,
                filepolicy.getId()), filepolicy.getLabel(), filepolicy.getApplyAt());
    }

}
