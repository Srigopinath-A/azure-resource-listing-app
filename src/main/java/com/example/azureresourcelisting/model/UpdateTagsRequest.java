package com.example.azureresourcelisting.model;

import java.util.Map;


import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UpdateTagsRequest {
    private String resourceName;
    private Map<String, String> tags;
    public void setResourceName(String resourceName) {
        this.resourceName = resourceName;
    }
    public void setTags(Map<String, String> tags) {
        this.tags = tags;
    }
    public String getResourceName() {
        return resourceName;
    }
    public Map<String, String> getTags() {
        return tags;
    }
    public UpdateTagsRequest(String resourceName, Map<String, String> tags) {
        this.resourceName = resourceName;
        this.tags = tags;
    }
}
