package com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.resource;

public interface ResourceFactory<R extends Resource, S> {
   R acquire(S var1);
}
