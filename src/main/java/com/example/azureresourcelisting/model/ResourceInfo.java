package com.example.azureresourcelisting.model;

import java.util.Map;

public class ResourceInfo {
    private String resourceId;
    private String resourceName;
    private String resourceType;
    private String resourceLocation;
     private Map<String, String> tags;

    public ResourceInfo(String resourceId, String resourceName, String resourceType, String resourceLocation) {
        this.resourceId = resourceId;
        this.resourceName = resourceName;
        this.resourceType = resourceType;
        this.resourceLocation = resourceLocation;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getResourceName() {
        return resourceName;
    }

    public void setResourceName(String resourceName) {
        this.resourceName = resourceName;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getResourceLocation() {
        return resourceLocation;
    }

    public void setResourceLocation(String resourceLocation) {
        this.resourceLocation = resourceLocation;
    }
}