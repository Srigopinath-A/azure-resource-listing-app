package com.example.azureresourcelisting.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpHeaders;

import com.azure.core.management.AzureEnvironment;
import com.azure.identity.DeviceCodeInfo;
import com.azure.core.management.profile.AzureProfile;
import com.azure.identity.DeviceCodeCredential;
import com.azure.identity.DeviceCodeCredentialBuilder;
import com.azure.identity.DeviceCodeInfo;
import com.azure.identity.UsernamePasswordCredential;
import com.azure.identity.UsernamePasswordCredentialBuilder;
import com.azure.resourcemanager.AzureResourceManager;
import com.example.azureresourcelisting.model.DeviceCodeResponse;
import com.example.azureresourcelisting.model.Loginrequest; // Corrected class name assuming it's LoginRequest
import com.example.azureresourcelisting.model.UpdateTagsRequest;
import com.example.azureresourcelisting.service.AzureResourceService;
import com.azure.identity.DeviceCodeCredential;
import com.azure.identity.DeviceCodeCredentialBuilder;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api")
public class ResourceController {

    private final AzureResourceService azureResourceService;
    
    // In-memory "database" to track pending login attempts.
    // Maps a unique loginId to the DeviceCodeCredential waiting for user action.
    private static final Map<String, DeviceCodeCredential> PENDING_LOGINS = new ConcurrentHashMap<>();
    
    // Public Client ID for Azure CLI. This is safe to use and identifies the application.
    private static final String AZURE_CLI_CLIENT_ID = "04b07795-8ddb-461a-bbee-02f9e1bf7b46";

    // Constructor Injection
    public ResourceController(AzureResourceService azureResourceService) {
        this.azureResourceService = azureResourceService;
    }
    
    // Helper to get the authenticated client from the session after login
    private AzureResourceManager getAzureFromSession(HttpServletRequest request) {
        return (AzureResourceManager) request.getSession().getAttribute("AZURE_SESSION");
    }



    private static final Map<String, PendingLogin> PENDING_LOGINS = new ConcurrentHashMap<>();

class PendingLogin {
    DeviceCodeCredential credential;
    long createdAt;
    
    PendingLogin(DeviceCodeCredential credential) {
        this.credential = credential;
        this.createdAt = System.currentTimeMillis();
    }
}

// Add session validation endpoint
@GetMapping("/check-session")
public ResponseEntity<?> checkSession(HttpServletRequest request) {
    AzureResourceManager azure = getAzureFromSession(request);
    if (azure != null) {
        return ResponseEntity.ok().build();
    }
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
}

// Add cleanup scheduler
@Scheduled(fixedRate = 300000) // 5 minutes
public void cleanPendingLogins() {
    long now = System.currentTimeMillis();
    PENDING_LOGINS.entrySet().removeIf(entry -> 
        (now - entry.getValue().createdAt) > 600000); // 10 minute expiration
}

    // --- ENDPOINT 1: /api/login/start - Initiates the Device Code Flow ---
    @PostMapping("/login/start")
    public ResponseEntity<?> startLogin(@RequestBody Loginrequest loginRequest) {
        
        String loginId = UUID.randomUUID().toString(); // Unique ID for this login attempt

        try {
            // Basic validation
            if (loginRequest.getTenantId() == null || loginRequest.getTenantId().trim().isEmpty()) {
                throw new IllegalArgumentException("Tenant ID cannot be null or empty.");
            }
            if (loginRequest.getSubscriptionId() == null || loginRequest.getSubscriptionId().trim().isEmpty()) {
                 throw new IllegalArgumentException("Subscription ID cannot be null or empty.");
            }
DeviceCodeCredential credential = new DeviceCodeCredentialBuilder()
            .tenantId(loginRequest.getTenantId())
            .clientId(AZURE_CLI_CLIENT_ID)
            .challengeConsumer(challenge -> {
                // Store challenge immediately
                DeviceCodeResponse response = new DeviceCodeResponse(
                    challenge.getUserCode(),
                    challenge.getVerificationUrl(),
                    challenge.getMessage()
                );
                PENDING_LOGINS.put(loginId, new PendingLogin(credential, response));
            })
            .build();

        // Non-blocking authentication
        credential.authenticate().subscribe(
            token -> {}, 
            error -> PENDING_LOGINS.remove(loginId)
        );

        // Return immediately stored challenge
        DeviceCodeResponse response = PENDING_LOGINS.get(loginId).getChallenge();
        return ResponseEntity.ok(Map.of(
            "loginId", loginId,
            "deviceCodeInfo", response
        ));

    } catch (Exception e) {
        return ResponseEntity.internalServerError()
            .body(Map.of("error", "Authentication failed: " + e.getMessage()));
    }
}

    // --- ENDPOINT 2: /api/login/check/{loginId} - Checks if user has completed browser login ---
    @PostMapping("/login/check/{loginId}")
    public ResponseEntity<?> checkLogin(
            @PathVariable String loginId,
            @RequestBody Loginrequest loginRequest, // Need tenantId & subscriptionId for profile
            HttpServletRequest servletRequest) {
            
        // Retrieve the DeviceCodeCredential associated with this login attempt.
        DeviceCodeCredential credential = PENDING_LOGINS.get(loginId);
        if (credential == null) {
            // If not found, perhaps it expired or was already successfully used/removed.
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Login session expired or not found."));
        }
        
        try {
            // Attempt to finalize authentication. This call will succeed ONLY IF
            // the user has completed the login in their browser.
            AzureProfile profile = new AzureProfile(loginRequest.getTenantId(), loginRequest.getSubscriptionId(), AzureEnvironment.AZURE);
            AzureResourceManager azure = AzureResourceManager.authenticate(credential, profile).withDefaultSubscription();
            
            // Success! User is now fully authenticated.
            servletRequest.getSession().setAttribute("AZURE_SESSION", azure); // Store in session
            PENDING_LOGINS.remove(loginId); // Clean up the pending login attempt
            
            return ResponseEntity.ok(Map.of("message", "Authentication successful!"));

        } catch(Exception e) {
            // This is the expected catch when the user has NOT yet completed the login.
            // It signifies that the credential is still "pending".
            // No need to log e.printStackTrace() here, as it's not a server error.
            return ResponseEntity.status(HttpStatus.ACCEPTED) // 202 Accepted status
                .body(Map.of("status", "PENDING")); // Inform frontend to keep polling
        }
    }


    @GetMapping("/resources")
    // MODIFIED: Added HttpServletRequest parameter
    public ResponseEntity<?> listResources(HttpServletRequest servletRequest) {
        // MODIFIED: Added session check
        AzureResourceManager azure = getAzureFromSession(servletRequest);
        if (azure == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "You are not logged in."));
        }

        try {
            // MODIFIED: Pass the session 'azure' object to the service
            return ResponseEntity.ok(azureResourceService.listAllResources(azure));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to list resources: " + e.getMessage()));
        }
    }

    @GetMapping("/resources/csv")
    // MODIFIED: Added HttpServletRequest parameter
    public ResponseEntity<?> exportResourcesToCsv(HttpServletRequest servletRequest) {
        // MODIFIED: Added session check
        AzureResourceManager azure = getAzureFromSession(servletRequest);
        if (azure == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "You are not logged in."));
        }
        
        try {
            // MODIFIED: Pass the session 'azure' object to the service
            List<String> resources = azureResourceService.listAllResources(azure);
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
                    .body(null); // Keep as-is or return error map
        }
    }

    @GetMapping("/tags/{resourceName}")
    // MODIFIED: Added HttpServletRequest parameter
    public ResponseEntity<?> getTagsByResourceName(@PathVariable String resourceName, HttpServletRequest servletRequest) {
        // MODIFIED: Added session check
        AzureResourceManager azure = getAzureFromSession(servletRequest);
        if (azure == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "You are not logged in."));
        }

        try {
            // MODIFIED: Pass the session 'azure' object to the service
            Map<String, String> tags = azureResourceService.getTagsByName(azure, resourceName);

            if (tags == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Collections.singletonMap("error", "Resource named '" + resourceName + "' not found anywhere in the subscription."));
            }

            return ResponseEntity.ok(tags);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "An internal Azure error occurred: " + e.getMessage()));
        }
    }

    @PostMapping("/resource/update-tags")
    // MODIFIED: Changed return type and added HttpServletRequest parameter
    public ResponseEntity<?> updateTags(@RequestBody UpdateTagsRequest request, HttpServletRequest servletRequest) {
        // MODIFIED: Added session check
        AzureResourceManager azure = getAzureFromSession(servletRequest);
        if (azure == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "You are not logged in."));
        }
        
        // --- Optional: Add validation ---
        String resourceNameToUpdate = request.getResourceName();
        Map<String, String> tagsToApply = request.getTags();
        if (resourceNameToUpdate == null || resourceNameToUpdate.isBlank() || tagsToApply == null || tagsToApply.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "resourceName and tags fields are required."));
        }
        // -----------------------------

        System.out.println("--- RECEIVED API REQUEST ---");
        System.out.println("Attempting to update resource: '" + resourceNameToUpdate + "'");
        System.out.println("With tags: " + tagsToApply);

        try {
            // MODIFIED: Pass the session 'azure' object to the service
            Map<String, String> updatedTags = azureResourceService.updateTagsByName(azure, resourceNameToUpdate, tagsToApply);

            if (updatedTags == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Resource named '" + resourceNameToUpdate + "' not found."));
            }
            return ResponseEntity.ok(updatedTags);

        } catch (Exception e) {
            e.printStackTrace(); 
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "An internal Azure error occurred: " + e.getMessage()));
        }
    }
}