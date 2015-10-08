/*
 * Copyright (c) 2011-2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service;

import com.emc.storageos.api.ldap.exceptions.DirectoryOrFileNotFoundException;
import com.emc.storageos.api.ldap.exceptions.FileOperationFailedException;
import com.emc.storageos.api.ldap.ldapserver.LDAPServer;
import com.emc.storageos.model.auth.AuthnCreateParam;
import com.emc.storageos.model.auth.AuthnProviderRestRep;
import com.emc.storageos.model.auth.AuthnUpdateParam;
import com.emc.storageos.services.util.EnvConfig;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldif.LDIFException;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.BindException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 
 * ApiTestAuthnProviderUtils an utility class to create the
 * default authnprovider config that can be used by all the
 * other tests.
 */
public class ApiTestAuthnProviderUtils {
    private final Logger _log = LoggerFactory.getLogger(this.getClass());

    private static final String AUTHN_PROVIDER_BASE_URL = "/vdc/admin/authnproviders";
    private static final String AUTHN_PROVIDER_EDIT_URL = AUTHN_PROVIDER_BASE_URL + "/%s";

    private static final String DEFAULT_TEST_LDAP_AUTHN_PROVIDER_LABEL = "LDAPAuthnProvider";
    private static final String DEFAULT_TEST_LDAP_AUTHN_PROVIDER_DESCRIPTION = "Authn Provider implemented by LDAP";
    private static final String DEFAULT_TEST_LDAP_AUTHN_PROVIDER_MODE = "ldap";

    private static final String DEFAULT_TEST_LDAP_SERVER_URL = "ldap://" + EnvConfig.get("sanity", "ldap2.ip");
    private static final String DEFAULT_TEST_LDAP_SERVER_DOMIN = "apitest.com";
    private static final String DEFAULT_TEST_LDAP_SERVER_MANAGER_DN = "cn=Manager,dc=apitest,dc=com";
    private static final String DEFAULT_TEST_LDAP_SERVER_MANAGER_DN_PWD = "secret";
    private static final String DEFAULT_TEST_LDAP_SERVER_SEARCH_BASE = "dc=apitest,dc=com";
    private static final String DEFAULT_TEST_LDAP_SERVER_SEARCH_SCOPE = "SUBTREE";
    private static final String DEFAULT_TEST_LDAP_SERVER_SEARCH_FILTER = "uid=%U";
    private static final String DEFAULT_TEST_LDAP_SERVER_GROUP_ATTRIBUTE = "CN";
    private static final String DEFAULT_TEST_SECOND_DOMAIN = "sanity.local";
    private static final String DEFAULT_TEST_ONE_LETTER_DOMAIN = "d";

    private static final String[] DEFAULT_TEST_LDAP_SERVER_GROUP_OBJECT_CLASSES = { "groupofnames", "groupofuniquenames", "posixgroup",
            "organizationalrole" };
    private static final String[] DEFAULT_TEST_LDAP_SERVER_GROUP_MEMBER_ATTRIBUTES = { "member", "uniquemember", "memberuid",
            "roleoccupant" };

    // ldapViPRUserGroup - groupOfNames object class.
    // ldapViPRUserGroupNew - groupOfNames object class.
    // ldapViPRUserGroupOrgRole - organizationalRole object class.
    // ldapViPRUniqueNameGroup - groupOfUniqueNames object class.
    // ldapViPRPosixGroup - posixGroup object class.
    // ldapViPRUserGroupNewOuter - groupOfNames object class.
    // Marketing - groupOfUniqueNames object class.
    // MarketingNew - groupOfUniqueNames object class.
    // MarketingOuter - groupOfUniqueNames object class.
    private static final String[] DEFAULT_TEST_LDAP_GROUPS = { "ldapViPRUserGroup", "ldapViPRUserGroupNew", "ldapViPRUserGroupOrgRole",
            "ldapViPRUniqueNameGroup", "ldapViPRPosixGroup", "ldapViPRUserGroupNewOuter", "Marketing", "MarketingNew", "MarketingOuter",
            "ldapViPRUserGroupTwo"};

    // ldapViPRUser1 - is a member of ldapViPRUserGroup and Marketing.
    // ldapViPRUser2, ldapViPRUser4, ldapViPRUserGroup - is a member of ldapViPRUserGroupNew.
    // ldapViPRUserGroupNew - is a member of ldapViPRUserGroupNewOuter.
    // ldapViPRUserGroupNewOuter - is a member of ldapViPRUniqueNameGroup.
    // Marketing - is a member of MarketingNew.
    // MarketingNew - is a member of MarketingOuter.
    // ldapViPRUserGroupNewOuter, MarketingOuter - is a member of ldapViPRUserGroupOrgRole
    // ldapViPRUser5 - has attributes departmentNumber = [ENG, DEV] and localityName = [Boston].
    // ldapViPRUser6 - has attributes departmentNumber = [ENG, QE] and localityName = [New York].
    // ldapViPRUser7 - has attributes departmentNumber = [ENG, QE, MANAGE] and localityName = [Boston].
    private static final String[] DEFAULT_TEST_LDAP_USERS_UID = { "ldapViPRUser1", "ldapViPRUser2", "ldapViPRUser3", "ldapViPRUser4",
            "ldapViPRUser5", "ldapViPRUser6", "ldapViPRUser7", "ldapViPRUser8", "ldapViPRUser9" };

    private static final String DEFAULT_TEST_LDAP_SERVER_NON_MANAGER_BIND_DN = "uid=ldapViPRUser1,ou=Users,ou=ViPR,dc=apitest,dc=com";

    private static final String DEFAULT_TEST_TENANT_USERS_PASS_WORD = "secret";

    private static final String[] TEST_DEFAULT_ATTRIBUTE_KEYS = { "departmentNumber", "l" }; // l means localityName
    private static final String[] TEST_DEFAULT_ATTRIBUTE_DEPARTMENT_VALUES = { "ENG", "QE", "DEV", "MANAGE" };
    private static final String[] TEST_DEFAULT_ATTRIBUTE_LOCALITY_VALUES = { "Boston", "New York", "West Coast" };

    private static final String[] TEST_DEFAULT_CHILD1_DOMAIN_USERS = {"Child1LdapViPRUser1@child1.apitest.com", "Child1LdapViPRUser2@child1.apitest.com"};
    private static final String[] TEST_DEFAULT_CHILD2_DOMAIN_GROUPS = {"Child2ViPRUserGroup1", "Child2ViPRUserGroup2"};
    private static final String[] TEST_DEFAULT_CHILD1_DOMAIN_GROUPS = {"Child1ViPRUserGroup1", "Child1ViPRUserGroup2"};
    private static final String TEST_DEFAULT_CHILD1_DOMAIN = "child1.apitest.com";
    private static final String TEST_DEFAULT_CHILD2_DOMAIN = "child2.apitest.com";

    private static final int RETRY_START_COUNT = 0;
    private static final int MAX_START_RETRIES = 4;
    private static final int RETRY_WAIT_TIME = 30;
    private static final int MILLI_SECOND_MULTIPLIER = 1000;

    private LDAPServer ldapServer;

    public Set<String> getDefaultGroupObjectClasses() {
        return new HashSet<String>(Arrays.asList(DEFAULT_TEST_LDAP_SERVER_GROUP_OBJECT_CLASSES));
    }

    public Set<String> getDefaultGroupMemberAttributes() {
        return new HashSet<String>(Arrays.asList(DEFAULT_TEST_LDAP_SERVER_GROUP_MEMBER_ATTRIBUTES));
    }

    public String getGroupObjectClass(int index) {
        return DEFAULT_TEST_LDAP_SERVER_GROUP_OBJECT_CLASSES[index];
    }

    public String getGroupMemberAttribute(int index) {
        return DEFAULT_TEST_LDAP_SERVER_GROUP_MEMBER_ATTRIBUTES[index];
    }

    public String getDefaultGroupAttribute() {
        return DEFAULT_TEST_LDAP_SERVER_GROUP_ATTRIBUTE;
    }

    public AuthnCreateParam getDefaultAuthnCreateParam(String description) {
        AuthnCreateParam param = new AuthnCreateParam();
        param.setLabel(DEFAULT_TEST_LDAP_AUTHN_PROVIDER_LABEL);
        if (StringUtils.isNotBlank(description)) {
            param.setDescription(description);
        } else {
            param.setDescription(DEFAULT_TEST_LDAP_AUTHN_PROVIDER_DESCRIPTION);
        }
        param.setDisable(false);
        param.getDomains().add(DEFAULT_TEST_LDAP_SERVER_DOMIN);
        param.setManagerDn(DEFAULT_TEST_LDAP_SERVER_MANAGER_DN);
        param.setManagerPassword(DEFAULT_TEST_LDAP_SERVER_MANAGER_DN_PWD);
        param.setSearchBase(DEFAULT_TEST_LDAP_SERVER_SEARCH_BASE);
        param.setSearchFilter(DEFAULT_TEST_LDAP_SERVER_SEARCH_FILTER);
        param.setServerUrls(new HashSet<String>());
        param.getServerUrls().add(DEFAULT_TEST_LDAP_SERVER_URL);
        param.setMode(DEFAULT_TEST_LDAP_AUTHN_PROVIDER_MODE);
        param.setGroupAttribute(DEFAULT_TEST_LDAP_SERVER_GROUP_ATTRIBUTE);
        param.setSearchScope(DEFAULT_TEST_LDAP_SERVER_SEARCH_SCOPE);
        param.setGroupObjectClasses(getDefaultGroupObjectClasses());
        param.setGroupMemberAttributes(getDefaultGroupMemberAttributes());

        return param;
    }

    public AuthnUpdateParam getAuthnUpdateParamFromAuthnProviderRestResp(AuthnProviderRestRep createResponse) {
        AuthnUpdateParam param = new AuthnUpdateParam();
        param.setLabel(createResponse.getName());
        param.setDescription(createResponse.getDescription());
        param.setDisable(createResponse.getDisable());
        param.getDomainChanges().getAdd().addAll(createResponse.getDomains());
        param.getDomainChanges().getRemove().addAll(new HashSet<String>());
        param.setManagerDn(createResponse.getManagerDN());
        param.setManagerPassword(DEFAULT_TEST_LDAP_SERVER_MANAGER_DN_PWD);
        param.setSearchBase(createResponse.getSearchBase());
        param.setSearchFilter(createResponse.getSearchFilter());
        param.getServerUrlChanges().getAdd().addAll(createResponse.getServerUrls());
        param.getServerUrlChanges().getRemove().addAll(new HashSet<String>());
        param.setMode(createResponse.getMode());
        param.setGroupAttribute(createResponse.getGroupAttribute());
        param.setSearchScope(createResponse.getSearchScope());
        param.getGroupObjectClassChanges().getAdd().addAll(createResponse.getGroupObjectClasses());
        param.getGroupMemberAttributeChanges().getAdd().addAll(createResponse.getGroupMemberAttributes());

        return param;
    }

    public Set<String> getDefaultLDAPGroups() {
        return new HashSet<String>(Arrays.asList(DEFAULT_TEST_LDAP_GROUPS));
    }

    public String getLDAPGroup(int index) {
        return DEFAULT_TEST_LDAP_GROUPS[index];
    }

    public Set<String> getDefaultLDAPUsers() {
        return new HashSet<String>(Arrays.asList(DEFAULT_TEST_LDAP_USERS_UID));
    }

    public String getLDAPUser(int index) {
        return DEFAULT_TEST_LDAP_USERS_UID[index];
    }

    public String getLDAPUserPassword() {
        return DEFAULT_TEST_TENANT_USERS_PASS_WORD;
    }

    public String getAuthnProviderDomain() {
        return DEFAULT_TEST_LDAP_SERVER_DOMIN;
    }

    public String getSecondDomain() {
        return DEFAULT_TEST_SECOND_DOMAIN;
    }

    public String getOneLetterDomain() {
        return DEFAULT_TEST_ONE_LETTER_DOMAIN;
    }

    public String getUserWithDomain(int index) {
        return DEFAULT_TEST_LDAP_USERS_UID[index] + "@" + getAuthnProviderDomain();
    }

    public String getAuthnProviderBaseURL() {
        return AUTHN_PROVIDER_BASE_URL;
    }

    public String getAuthnProviderEditURL(URI id) {
        return String.format(AUTHN_PROVIDER_EDIT_URL, id);
    }

    public String getNonManagerDN() {
        return DEFAULT_TEST_LDAP_SERVER_NON_MANAGER_BIND_DN;
    }

    public Set<String> getDefaultAttributeKeys() {
        return new HashSet<String>(Arrays.asList(TEST_DEFAULT_ATTRIBUTE_KEYS));
    }

    public Set<String> getDefaultAttributeDepartmentValues() {
        return new HashSet<String>(Arrays.asList(TEST_DEFAULT_ATTRIBUTE_DEPARTMENT_VALUES));
    }

    public Set<String> getDefaultAttributeLocalityValues() {
        return new HashSet<String>(Arrays.asList(TEST_DEFAULT_ATTRIBUTE_LOCALITY_VALUES));
    }

    public String getAttributeKey(int index) {
        return TEST_DEFAULT_ATTRIBUTE_KEYS[index];
    }

    public String getAttributeDepartmentValue(int index) {
        return TEST_DEFAULT_ATTRIBUTE_DEPARTMENT_VALUES[index];
    }

    public String getAttributeLocalityValue(int index) {
        return TEST_DEFAULT_ATTRIBUTE_LOCALITY_VALUES[index];
    }

    public String getChild1User(int index) {
        return TEST_DEFAULT_CHILD1_DOMAIN_USERS[index];
    }

    public String getChild1Group(int index) {
        return TEST_DEFAULT_CHILD1_DOMAIN_GROUPS[index];
    }

    public String getChild2Group(int index) {
        return TEST_DEFAULT_CHILD2_DOMAIN_GROUPS[index];
    }

    public String getChild1Domain() {
        return TEST_DEFAULT_CHILD1_DOMAIN;
    }

    public String getChild2Domain() {
        return TEST_DEFAULT_CHILD2_DOMAIN;
    }

    public void startLdapServer (final String listenerName) throws LDIFException,
            LDAPException, IOException, FileOperationFailedException,
            GeneralSecurityException, DirectoryOrFileNotFoundException,
            InterruptedException {
        if (ldapServer == null) {
            ldapServer = new LDAPServer();
        }

        if (ldapServer.isRunning()) {
            ldapServer.stop();
        }

        ldapServer.setListenerName(listenerName);
        boolean started = false;
        int iteration = RETRY_START_COUNT;

        while (started != true && iteration < MAX_START_RETRIES) {
            try {
                ldapServer.start();
                started = true;
            } catch (LDAPException ex) {
                _log.error("Caught bind exception {}", ex.getCause());
                _log.info("Retry count {} and waiting for {}secs before next retry.", iteration, RETRY_WAIT_TIME);

                iteration++;
                Thread.sleep(iteration * RETRY_WAIT_TIME * MILLI_SECOND_MULTIPLIER);
            }
        }
    }

    public void stopLdapServer () {
        if (ldapServer == null ||
                !ldapServer.isRunning()) {
            return;
        }

        ldapServer.stop();
    }
}
