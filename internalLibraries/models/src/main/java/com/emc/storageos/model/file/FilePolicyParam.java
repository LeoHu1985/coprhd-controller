package com.emc.storageos.model.file;

import java.io.Serializable;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "file_policy")
public class FilePolicyParam implements Serializable {

    // Type of the policy
    private String policyType;

    // Name of the policy
    private String policyName;

    // Level at which policy has to be applied..
    private String applyAt;

    // Replication
    private FileSystemReplicationSettings replicationSettingParam;

    public static enum PolicyType {
        file_snapshot, file_replication, file_quota
    }

    public static enum policyApplyLevel {
        vpool, project, file_system
    }

    public FilePolicyParam() {
    }

    @XmlElement(required = true, name = "policy_type")
    public String getPolicyType() {
        return policyType;
    }

    public void setPolicyType(String policyType) {
        this.policyType = policyType;
    }

    @XmlElement(required = true, name = "policy_name")
    public String getPolicyName() {
        return policyName;
    }

    public void setPolicyName(String policyName) {
        this.policyName = policyName;
    }

    @XmlElement(required = true, name = "apply_at")
    public String getApplyAt() {
        return applyAt;
    }

    public void setApplyAt(String applyAt) {
        this.applyAt = applyAt;
    }

    @XmlElement(name = "replication_settings")
    public FileSystemReplicationSettings getReplicationSettingParam() {
        return replicationSettingParam;
    }

    public void setReplicationSettingParam(FileSystemReplicationSettings replicationSettingParam) {
        this.replicationSettingParam = replicationSettingParam;
    }
}
