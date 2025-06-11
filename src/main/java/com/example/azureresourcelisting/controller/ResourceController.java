package com.example.azureresourcelisting.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.http.HttpHeaders;
import com.example.azureresourcelisting.model.ResourceInfo;
import com.example.azureresourcelisting.service.AzureResourceService;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
public class ResourceController {

    private final AzureResourceService azureResourceService;

public ResourceController(AzureResourceService azureResourceService) {
    this.azureResourceService = azureResourceService;
}

@GetMapping("/resources")
public ResponseEntity<List<String>> listResources() {
    try {
        return ResponseEntity.ok(azureResourceService.listAllResources());
    } catch (Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(null);
    }
}

@GetMapping("/resources/csv")
public ResponseEntity<byte[]> exportResourcesToCsv() {
    try {
        List<String> resources = azureResourceService.listAllResources();
        StringBuilder csvBuilder = new StringBuilder();
        csvBuilder.append("Resource\n");
        for (String resource : resources) {
            csvBuilder.append("\"").append(resource.replace("\"", "\"\"")).append("\"\n");
        }
        byte[] csvBytes = csvBuilder.toString().getBytes(StandardCharsets.UTF_8);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=azure-resources.csv");
        headers.setContentType(MediaType.parseMediaType("text/csv"));

        return ResponseEntity.ok()
                .headers(headers)
                .body(csvBytes);
    } catch (Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(null);
    }
}

    @GetMapping("/resources/tags")
public ResponseEntity<List<String>> getTagsForResource(String type, String name) {
    try {
        List<String> tags = azureResourceService.getTagsForResource(type, name);
        if (tags.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
        return ResponseEntity.ok(tags);
    } catch (Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
    }
}

 @GetMapping("/resources/tagsJson")
    public ResponseEntity<Map<String, String>> getTagsForResourcej(
            @RequestParam("type") String resourceType,
            @RequestParam("name") String resourceName) {
        
        try {
            Map<String, String> tags = azureResourceService.findTagsByNameAndType(resourceType, resourceName);

            // The service returns null if no matching resource was found
            if (tags == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Collections.singletonMap("error", "Resource not found with type '" + resourceType + "' and name '" + resourceName + "'"));
            }

            // If found, return the tags. Spring Boot automatically makes this a JSON object.
            // If the resource exists but has no tags, this will correctly return an empty JSON object: {}
            return ResponseEntity.ok(tags);

        } catch (Exception e) {
            // log.error("Error fetching tags for type={} name={}", resourceType, resourceName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "An internal error occurred: " + e.getMessage()));
        }
    }

     @PatchMapping("/resources/{resourceName}/tags")
    public ResponseEntity<Map<String, String>> updateTagsByResourceName(
            @PathVariable String resourceName,
            @RequestBody Map<String, String> tagsToUpdate) {
        try {
            Map<String, String> updatedTags = azureResourceService.updateTagsByName(resourceName, tagsToUpdate);

            if (updatedTags == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Resource named '" + resourceName + "' not found anywhere in the subscription."));
            }

            return ResponseEntity.ok(updatedTags);

        } catch (Exception e) {
            // This catches authentication errors or other Azure SDK issues.
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "An internal Azure error occurred: " + e.getMessage()));
        }
    }
}
