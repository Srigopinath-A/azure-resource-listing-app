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

       // MODIFIED: REMOVED the injected AzureResourceManager field and constructor
    // private final AzureResourceManager azure;
    // @Autowired
    // public AzureResourceService(...) { ... }

    // MODIFIED: Added 'AzureResourceManager azure' parameter
    public List<String> listAllResources(AzureResourceManager azure) {
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

    // MODIFIED: Added 'AzureResourceManager azure' parameter
    public List<String> getTagsForResource(AzureResourceManager azure, String resourceType, String resourceName) {
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
                        // This might still be inefficient, needs full object fetch. See findTagsByNameAndType.
                        // For simplicity, leaving as-is, but a full fetch is better.
                        azure.webApps().getByResourceGroup(webApp.resourceGroupName(), webApp.name())
                            .tags().forEach((k, v) -> tags.add(k + "=" + v));
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
                break;
        }
        return tags;
    }

    // MODIFIED: Added 'AzureResourceManager azure' parameter
    public Map<String, String> findTagsByNameAndType(AzureResourceManager azure, String resourceType, String resourceName) {
        Optional<Map<String, String>> tagsOptional;

        switch (resourceType.toLowerCase()) {
            case "vm":
                tagsOptional = azure.virtualMachines().list().stream()
                    .filter(vm -> vm.name().equalsIgnoreCase(resourceName))
                    .findFirst()
                    .map(VirtualMachine::tags);
                break;

            case "webapp":
                tagsOptional = azure.webApps().list().stream()
                    .filter(webAppBasic -> webAppBasic.name().equalsIgnoreCase(resourceName))
                    .findFirst()
                    .map(webAppBasic -> 
                        azure.webApps().getByResourceGroup(webAppBasic.resourceGroupName(), webAppBasic.name())
                    )
                    .map(WebApp::tags);
                break;

            default:
                tagsOptional = azure.genericResources().list().stream()
                    .filter(resource -> resource.name().equalsIgnoreCase(resourceName) &&
                                     resource.resourceType().toLowerCase().contains(resourceType.toLowerCase()))
                    .findFirst()
                    .map(GenericResource::tags);
                break;
        }

        return tagsOptional.orElse(null);
    }

    // MODIFIED: Added 'AzureResourceManager azure' parameter
    public Map<String, String> updateTagsByName(AzureResourceManager azure, String resourceName, Map<String, String> tagsToUpdate) {
        System.out.println("--- Starting search for resource: '" + resourceName + "' ---");
        PagedIterable<ResourceGroup> groups = azure.resourceGroups().list();
        List<ResourceGroup> groupList = groups.stream().collect(Collectors.toList());
        System.out.println("Found " + groupList.size() + " resource groups. Now searching within each...");

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
                
                GenericResource updatedResource = resourceToUpdate.update()
                    .withTags(newTags)
                    .apply();

                System.out.println("SUCCESS: Tags updated for '" + resourceName + "'.\n");
                return updatedResource.tags();
            }
        }

        System.err.println("ERROR: Resource '" + resourceName + "' was not found in any resource group.\n");
        return null;
    }

    // MODIFIED: Added 'AzureResourceManager azure' parameter
    public Map<String, String> getTagsByName(AzureResourceManager azure, String resourceName) {
        System.out.println("--- Starting subscription-wide search for resource: '" + resourceName + "' ---");
        Optional<GenericResource> genericResourceOptional = azure.genericResources().list().stream()
            .filter(resource -> resource.name().equalsIgnoreCase(resourceName))
            .findFirst();
        return genericResourceOptional.map(GenericResource::tags).orElse(null);
    }

}
