---
layout: default
title: CAS - Web Flow Acceptable Usage Policy
---

# Acceptable Usage Policy

CAS presents the ability to allow the user to accept the usage policy before moving on to the application. 
Production-level deployments of this feature would require modifications to the flow such that the retrieval
and/or acceptance of the policy would be handled via an external storage mechanism such as LDAP or JDBC.

## Configuration

Support is enabled by including the following dependency in the WAR overlay:

```xml
<dependency>
  <groupId>org.apereo.cas</groupId>
  <artifactId>cas-server-webapp-actions-aup-webflow</artifactId>
  <version>${cas.version}</version>
</dependency>
```

### Enable Webflow

In the `login-webflow.xml` file, enable the transition to `acceptableUsagePolicyCheck`
by uncommenting the following entry:

```xml
<transition on="success" to="acceptableUsagePolicyCheck" />
```

Customize the policy by modifying `casAcceptableUsagePolicyView.html`.

## Storage Mechanism

### Default

By default the task of remembering the user's choice is kept in memory by default and will be lost upon 
container restarts and/or in clustered deployments.   

### LDAP

Alternatively, CAS can be configured to use LDAP as the storage mechanism. This option allows the deployer
to detect the current user's policy choice via a CAS single-valued `boolean` attribute. 
The attribute must be resolved using
the [CAS attribute resolution strategy](../integration/Attribute-Resolution.html). 
If the attribute contains a value of `false`, CAS will attempt to
ask for policy acceptance. Upon accepting the policy, the result will be stored back into LDAP and
remembered via the same attribute. 

Support is enabled by including the following dependency in the WAR overlay:

```xml
<dependency>
  <groupId>org.apereo.cas</groupId>
  <artifactId>cas-server-webapp-actions-aup-ldap</artifactId>
  <version>${cas.version}</version>
</dependency>
```

To see the relevant list of CAS properties, please [review this guide](Configuration-Properties.html).

