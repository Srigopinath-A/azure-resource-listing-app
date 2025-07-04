package com.example.azureresourcelisting.service;


import com.azure.core.http.rest.PagedIterable;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.AzureResourceManager;
import com.azure.resourcemanager.appservice.models.WebApp;
import com.azure.resourcemanager.compute.models.VirtualMachine;
import com.azure.resourcemanager.resources.models.GenericResource;
import com.azure.resourcemanager.resources.models.ResourceGroup;
import com.example.azureresourcelisting.model.ResourceInfo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class AzureResourceService {

    
    private final AzureResourceManager azure;

    @Autowired
public AzureResourceService(@Qualifier("azureResourceManager") AzureResourceManager azure) {
    this.azure = azure;
}
    public List<String> listAllResources() {
        List<String> resources = new ArrayList<>();
        
        // List virtual machines
        azure.virtualMachines().list().forEach(vm -> 
            resources.add("VM: " + vm.name() + " | " + vm.resourceGroupName())
        );
        
        // List web apps
        azure.webApps().list().forEach(webApp -> 
            resources.add("Web App: " + webApp.name() + " | " + webApp.resourceGroupName())
        );
        
        // List other resources (excluding VMs and Web Apps)
        azure.genericResources().list().forEach(resource -> {
            String type = resource.resourceType();
            if (!type.equals("Microsoft.Compute/virtualMachines") && 
                !type.equals("Microsoft.Web/sites")) {
                resources.add("Resource: " + resource.name() + 
                            " | Type: " + type + 
                            " | " + resource.resourceGroupName());
            }
        });
        
        return resources;
    }


    public List<String> getTagsForResource(String resourceType, String resourceName) {
    List<String> tags = new ArrayList<>();
    switch (resourceType.toLowerCase()) {
        case "vm":
            azure.virtualMachines().list().forEach(vm -> {
                if (vm.name().equalsIgnoreCase(resourceName)) {
                    vm.tags().forEach((k, v) -> tags.add(k + "=" + v));
                }
            });
            break;
        case "webapp":
            azure.webApps().list().forEach(webApp -> {
                if (webApp.name().equalsIgnoreCase(resourceName)) {
                    webApp.tags().forEach((k, v) -> tags.add(k + "=" + v));
                }
            });
            break;
        case "other":
            azure.genericResources().list().forEach(resource -> {
                if (resource.name().equalsIgnoreCase(resourceName)) {
                    resource.tags().forEach((k, v) -> tags.add(k + "=" + v));
                }
            });
            break;
        default:
            // Optionally handle unknown type
            break;
    }
    return tags;
}

 public Map<String, String> findTagsByNameAndType(String resourceType, String resourceName) {
        
        Optional<Map<String, String>> tagsOptional;

        switch (resourceType.toLowerCase()) {
            case "vm":
                // This works because .list() for VMs returns the full VirtualMachine object
                tagsOptional = azure.virtualMachines().list().stream()
                        .filter(vm -> vm.name().equalsIgnoreCase(resourceName))
                        .findFirst()
                        .map(VirtualMachine::tags);
                break;

            case "webapp":
                // *** THIS IS THE CORRECTED SECTION ***
                tagsOptional = azure.webApps().list().stream()
                        .filter(webAppBasic -> webAppBasic.name().equalsIgnoreCase(resourceName))
                        .findFirst() // This gives an Optional<WebAppBasic>
                        .map(webAppBasic -> 
                            // If found, fetch the full WebApp object to get tags
                            azure.webApps().getByResourceGroup(webAppBasic.resourceGroupName(), webAppBasic.name())
                        ) // Now we have an Optional<WebApp>
                        .map(WebApp::tags); // Now we can safely get the tags
                break;

            default:
                // Fallback for any other resource type.
                tagsOptional = azure.genericResources().list().stream()
                        .filter(resource -> resource.name().equalsIgnoreCase(resourceName) &&
                                             resource.resourceType().toLowerCase().contains(resourceType.toLowerCase()))
                        .findFirst()
                        .map(GenericResource::tags);
                break;
        }

        return tagsOptional.orElse(null);
    }

    public Map<String, String> updateTagsByName(String resourceName, Map<String, String> tagsToUpdate) {
    
    System.out.println("--- Starting search for resource: '" + resourceName + "' ---");

    PagedIterable<ResourceGroup> groups = azure.resourceGroups().list();

    // === THE FIX ===
    // 1. Collect the items from the one-time iterable into a permanent List.
    List<ResourceGroup> groupList = groups.stream().collect(Collectors.toList());
    
    System.out.println("Found " + groupList.size() + " resource groups. Now searching within each...");

    // 2. Now, iterate over the List. This can be done safely.
    for (ResourceGroup group : groupList) {
        
        System.out.println("...Searching in resource group: '" + group.name() + "'");
        
        Optional<GenericResource> resourceOptional = azure.genericResources()
                .listByResourceGroup(group.name())
                .stream()
                .filter(r -> r.name().equalsIgnoreCase(resourceName))
                .findFirst();

        if (resourceOptional.isPresent()) {
            GenericResource resourceToUpdate = resourceOptional.get();
            System.out.println("SUCCESS: Found resource '" + resourceName + "' in group '" + group.name() + "'.");

            Map<String, String> newTags = new HashMap<>();
            if (resourceToUpdate.tags() != null) {
                newTags.putAll(resourceToUpdate.tags());
            }
            newTags.putAll(tagsToUpdate);

            System.out.println("Attempting to apply new tags: " + newTags);
            
            // Apply the update
            GenericResource updatedResource = resourceToUpdate.update()
                    .withTags(newTags)
                    .apply();

            // Best Practice: To be 100% sure, return the tags from the object that was returned by .apply()
            System.out.println("SUCCESS: Tags updated for '" + resourceName + "'.\n");
            return updatedResource.tags(); // Task complete, exit.
        }
    }

    System.err.println("ERROR: Resource '" + resourceName + "' was not found in any resource group.\n");
    return null;
}

 public Map<String, String> getTagsByName(String resourceName) {
        
        System.out.println("--- Starting subscription-wide search for resource: '" + resourceName + "' ---");

        // 1. Search ALL generic resources in the subscription. THIS IS THE SLOW PART.
        Optional<GenericResource> genericResourceOptional = azure.genericResources().list().stream()
                .filter(resource -> resource.name().equalsIgnoreCase(resourceName))
                .findFirst(); // Find the first resource that matches the name.

        // 2. Use modern Java 'map' to get the tags if the resource was found.
        // If the resource was found, this returns its tags (which could be an empty map).
        // If the resource was NOT found, this returns null.
        return genericResourceOptional.map(GenericResource::tags).orElse(null);
    }

}
