/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.block;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Native continuous copy creation parameters. The
 * name and count values are used to generate volume
 * labels for the mirrors. If count is 1, name is used
 * as the label. If count is greater than 1, name and
 * count are combined to generate labels for the multiple
 * mirrors.
 */
@XmlRootElement(name = "native_continuous_copy_create")
public class NativeContinuousCopyCreate {

    private String name;
    private Integer count;
    private BlockPerformancePolicyMap performancePolicies;    

    public NativeContinuousCopyCreate() {
    }

    public NativeContinuousCopyCreate(String name, Integer count, BlockPerformancePolicyMap performancePolicies) {
        this.name = name;
        this.count = count;
        this.performancePolicies = performancePolicies;
    }

    /**
     * User provided name.
     * 
     */
    @XmlElement(required = true)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * User provided number of copies.
     *
     */ 
    @XmlElement(required = false)
    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }
    
    /**
     * The performance policies to use when the newly created mirror is 
     * provisioned.
     * 
     * @return The performance policies.
     */
    @XmlElement(name = "performance_policies")
    public BlockPerformancePolicyMap getPerformancePolicies() {
        return performancePolicies;
    }

    public void setPerformancePolicies(BlockPerformancePolicyMap performancePolicies) {
        this.performancePolicies = performancePolicies;
    }    
}
