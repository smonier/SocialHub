package org.example.socialhub.service.impl;

import org.example.socialhub.service.ActivityLogService;
import org.example.socialhub.service.SocialAccountService;
import org.example.socialhub.service.SocialPostService;
import org.jahia.api.Constants;
import org.jahia.services.content.*;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Implementation of SocialPostService using OSGi Declarative Services.
 * Handles publishing social posts to external platforms through SocialProxyServlet.
 * All external API calls go through the proxy at /modules/social-proxy.
 * 
 * Configuration is read from: org.example.socialhub.servlet.SocialProxyServlet.cfg
 */
@Component(
    service = SocialPostService.class, 
    immediate = true,
    configurationPid = "org.example.socialhub.servlet.SocialProxyServlet"
)
public class SocialPostServiceImpl implements SocialPostService {
    
    private static final Logger logger = LoggerFactory.getLogger(SocialPostServiceImpl.class);
    
    private static final String SOCIAL_POST_TYPE = "socialnt:post";
    private static final String STATUS_SCHEDULED = "scheduled";
    private static final String STATUS_PUBLISHED = "published";
    
    // Configuration properties from OSGi Config Admin
    private String facebookBaseUrl = "https://graph.facebook.com";
    private String instagramBaseUrl = "https://graph.facebook.com";
    private String linkedinBaseUrl = "https://api.linkedin.com";
    private String facebookApiVersion = "v21.0";
    private String linkedinApiVersion = "v2";
    private String authToken = "your-api-token-here";
    private String serverBaseUrl = "https://wonderland-jahiasales.internal.cloud.jahia.com";
    
    // Platform-specific configuration
    private String facebookPageId = "";
    private String facebookAppSecret = "";
    private String instagramAccountId = "";
    private String linkedinOrganizationId = "";
    private String facebookPageAccessToken = "";
    private String instagramAccessToken = "";
    private String linkedinAccessToken = "";
    
    @Activate
    protected void activate(Map<String, Object> properties) {
        // Read configuration from .cfg file
        if (properties.get("facebookBaseUrl") != null) {
            facebookBaseUrl = (String) properties.get("facebookBaseUrl");
        }
        if (properties.get("instagramBaseUrl") != null) {
            instagramBaseUrl = (String) properties.get("instagramBaseUrl");
        }
        if (properties.get("linkedinBaseUrl") != null) {
            linkedinBaseUrl = (String) properties.get("linkedinBaseUrl");
        }
        if (properties.get("facebookApiVersion") != null) {
            facebookApiVersion = (String) properties.get("facebookApiVersion");
        }
        if (properties.get("linkedinApiVersion") != null) {
            linkedinApiVersion = (String) properties.get("linkedinApiVersion");
        }
        if (properties.get("authToken") != null) {
            authToken = (String) properties.get("authToken");
        }
        if (properties.get("facebookPageId") != null) {
            facebookPageId = (String) properties.get("facebookPageId");
        }
        if (properties.get("facebookAppSecret") != null) {
            facebookAppSecret = (String) properties.get("facebookAppSecret");
        }
        if (properties.get("instagramAccountId") != null) {
            instagramAccountId = (String) properties.get("instagramAccountId");
        }
        if (properties.get("linkedinOrganizationId") != null) {
            linkedinOrganizationId = (String) properties.get("linkedinOrganizationId");
        }
        if (properties.get("facebookPageAccessToken") != null) {
            facebookPageAccessToken = (String) properties.get("facebookPageAccessToken");
        }
        if (properties.get("instagramAccessToken") != null) {
            instagramAccessToken = (String) properties.get("instagramAccessToken");
        }
        if (properties.get("linkedinAccessToken") != null) {
            linkedinAccessToken = (String) properties.get("linkedinAccessToken");
        }
        if (properties.get("serverBaseUrl") != null) {
            serverBaseUrl = (String) properties.get("serverBaseUrl");
        }
        
        logger.info("[SERVICE] SocialPostServiceImpl activated with config:");
        logger.info("[SERVICE]   - serverBaseUrl: {}", serverBaseUrl);
        logger.info("[SERVICE]   - facebookBaseUrl: {}", facebookBaseUrl);
        logger.info("[SERVICE]   - instagramBaseUrl: {}", instagramBaseUrl);
        logger.info("[SERVICE]   - linkedinBaseUrl: {}", linkedinBaseUrl);
        logger.info("[SERVICE]   - authToken: {}...{}", 
            authToken.length() > 10 ? authToken.substring(0, 5) : "***",
            authToken.length() > 10 ? authToken.substring(authToken.length() - 5) : "***");
        logger.info("[SERVICE]   - activityLogService: {}", activityLogService != null ? "INJECTED" : "NULL!!!");
    }
    
    @Reference
    private JCRTemplate jcrTemplate;
    
    @Reference
    private ActivityLogService activityLogService;
    
    @Reference
    private SocialAccountService socialAccountService;
    
    /**
     * Get the base URL for a specific platform.
     */
    private String getPlatformBaseUrl(String platform) {
        switch (platform.toLowerCase()) {
            case "facebook":
                return facebookBaseUrl;
            case "instagram":
                return instagramBaseUrl;
            case "linkedin":
                return linkedinBaseUrl;
            default:
                logger.warn("[SERVICE] Unknown platform '{}', using Facebook URL as fallback", platform);
                return facebookBaseUrl;
        }
    }
    
    @Override
    public void publishNow(String postUuid) throws RepositoryException {
        logger.info("[SERVICE] ========== publishNow called for post: {} ==========", postUuid);
        
        jcrTemplate.doExecuteWithSystemSession(session -> {
            logger.info("[SERVICE] Retrieving node with UUID: {}", postUuid);
            Node postNode = session.getNodeByIdentifier(postUuid);
            logger.info("[SERVICE] Node found: {} (type: {})", postNode.getPath(), postNode.getPrimaryNodeType().getName());
            
            if (!postNode.isNodeType(SOCIAL_POST_TYPE)) {
                logger.error("[SERVICE] Node is not a socialnt:post: {}", postUuid);
                throw new RepositoryException("Node is not a socialnt:post: " + postUuid);
            }
            
            logger.info("[SERVICE] Node validated as socialnt:post");
            
            try {
                // Extract post data
                String postPath = postNode.getPath();
                String title = getPropertyValue(postNode, "social:title");
                String message = getPropertyValue(postNode, "social:message");
                String linkUrl = getPropertyValue(postNode, "social:linkUrl");
                String platform = getPropertyValue(postNode, "social:platform"); // Single platform
                
                if (platform == null || platform.isEmpty()) {
                    logger.error("[SERVICE] Post has no platform specified: {}", postUuid);
                    throw new RepositoryException("Post has no platform specified");
                }
                
                // Extract image URLs from imageRefs
                List<String> imageUrls = new ArrayList<>();
                if (postNode.hasProperty("social:imageRefs")) {
                    logger.info("[SERVICE] >>> Post has social:imageRefs property");
                    javax.jcr.Value[] imageRefs = postNode.getProperty("social:imageRefs").getValues();
                    logger.info("[SERVICE] >>> Found {} image reference(s)", imageRefs.length);
                    
                    for (javax.jcr.Value ref : imageRefs) {
                        try {
                            String refId = ref.getString();
                            logger.info("[SERVICE] >>> Resolving image reference: {}", refId);
                            Node imageNode = session.getNodeByIdentifier(refId);
                            
                            if (imageNode != null) {
                                String imagePath = imageNode.getPath();
                                logger.info("[SERVICE] >>> Image node found: {} (type: {})", imagePath, imageNode.getPrimaryNodeType().getName());
                                
                                // Build proper Jahia file servlet URL
                                // Pattern: {serverBaseUrl}/files/live{imagePath}
                                // Example: https://wonderland-jahiasales.internal.cloud.jahia.com/files/live/sites/jsmod/files/social-posts/photo-1512106374988-c95f566d39ef
                                // Note: Jahia file servlet handles files with or without extensions
                                String imageUrl = serverBaseUrl + "/files/live" + imagePath;
                                
                                imageUrls.add(imageUrl);
                                logger.info("[SERVICE] >>> ✓ Resolved image URL: {}", imageUrl);
                                logger.info("[SERVICE] >>> ⚠️  IMPORTANT: Verify this URL is publicly accessible without authentication!");
                            } else {
                                logger.warn("[SERVICE] >>> Image node not found for reference: {}", refId);
                            }
                        } catch (Exception e) {
                            logger.error("[SERVICE] >>> Error resolving image reference: {}", e.getMessage(), e);
                        }
                    }
                } else {
                    logger.info("[SERVICE] >>> Post has no social:imageRefs property");
                }
                
                logger.info("[SERVICE] >>> Total images resolved: {}", imageUrls.size());
                
                logger.info("[SERVICE] >>> ABOUT TO LOG ACTIVITY - activityLogService is: {}", activityLogService != null ? "AVAILABLE" : "NULL");
                
                // Log publish attempt
                if (activityLogService != null) {
                    try {
                        activityLogService.logPublishAttempt(postUuid, title, new String[]{platform});
                        logger.info("[ACTIVITY] ✓ Logged publish attempt for: {}", title);
                    } catch (Exception e) {
                        logger.error("[ACTIVITY] ✗ Failed to log publish attempt for: " + title, e);
                    }
                } else {
                    logger.error("[ACTIVITY] ✗✗✗ ActivityLogService is NULL - cannot log publish attempt!");
                }
                
                logger.debug("Publishing post '{}' to platform: {}", title, platform);
                
                // Publish to the single platform
                String externalId = null;
                try {
                    externalId = publishToPlatform(platform, postPath, title, message, linkUrl, imageUrls);
                        
                        if (externalId != null) {
                            logger.info("Successfully published to {}: {}", platform, externalId);
                            
                            // Store external ID and update status
                            postNode.setProperty("social:externalId", externalId);
                            postNode.setProperty("social:status", STATUS_PUBLISHED);
                            session.save();
                            logger.info("[SERVICE] ✓ Post {} status updated to: 'published'", postUuid);
                            
                            // Log success
                            if (activityLogService != null) {
                                try {
                                    activityLogService.logPublishSuccess(postUuid, title, platform, externalId);
                                    logger.info("[ACTIVITY] ✓ Logged publish success for: {} on {}", title, platform);
                                } catch (Exception e) {
                                    logger.error("[ACTIVITY] ✗ Failed to log publish success for: " + title, e);
                                }
                            }
                        } else {
                            logger.error("Failed to publish to {}", platform);
                            
                            // Log failure
                            if (activityLogService != null) {
                                try {
                                    activityLogService.logPublishFailure(postUuid, title, platform, "No external ID returned");
                                    logger.info("[ACTIVITY] ✓ Logged publish failure for: {} on {}", title, platform);
                                } catch (Exception e) {
                                    logger.error("[ACTIVITY] ✗ Failed to log publish failure for: " + title, e);
                                }
                            }
                        }
                    } catch (Exception e) {
                        logger.error("Error publishing to platform " + platform, e);
                        
                        // Log failure with error
                        if (activityLogService != null) {
                            try {
                                activityLogService.logPublishFailure(postUuid, title, platform, e.getMessage());
                                logger.info("[ACTIVITY] ✓ Logged publish failure for: {} on {}", title, platform);
                            } catch (Exception logEx) {
                                logger.error("[ACTIVITY] ✗ Failed to log publish failure for: " + title, logEx);
                            }
                        }
                    }
                
                // Verify final status after save
                session.refresh(false);
                Node verifyNode = session.getNodeByIdentifier(postUuid);
                String finalStatus = verifyNode.hasProperty("social:status") 
                    ? verifyNode.getProperty("social:status").getString() 
                    : "unknown";
                
                if (STATUS_PUBLISHED.equals(finalStatus)) {
                    logger.info("[SERVICE] ✓ Post successfully published to {} - Final status verified: {}", platform, finalStatus);
                } else {
                    logger.error("[SERVICE] ✗ Post status verification failed - Expected 'published' but got '{}'", finalStatus);
                }
                
            } catch (Exception e) {
                logger.error("[SERVICE] \u2717\u2717\u2717 Critical error publishing post " + postUuid, e);
                // Do NOT set status to 'failed' - it's not an allowed value
                // Leave status as 'scheduled' for retry
                throw new RepositoryException("Failed to publish post", e);
            }
            
            return null;
        });
    }
    
    @Override
    public void publishDueScheduledPosts() throws RepositoryException {
        String now = getCurrentTimestamp();
        logger.info("[SERVICE] ========== publishDueScheduledPosts invoked at {} (default workspace) ==========", now);
        
        jcrTemplate.doExecuteWithSystemSession(session -> {
            String sql2Query = String.format(
                "SELECT * FROM [%s] WHERE [social:status] = '%s' AND [social:scheduledAt] <= CAST('%s' AS DATE)",
                SOCIAL_POST_TYPE,
                STATUS_SCHEDULED,
                now
            );
            
            logger.info("[SERVICE] Executing query: {}", sql2Query);
            
            QueryManager queryManager = session.getWorkspace().getQueryManager();
            Query query = queryManager.createQuery(sql2Query, Query.JCR_SQL2);
            QueryResult result = query.execute();
            NodeIterator nodes = result.getNodes();
            
            logger.info("[SERVICE] Query executed, found {} nodes", nodes.getSize());
            
            int count = 0;
            while (nodes.hasNext()) {
                Node postNode = nodes.nextNode();
                String uuid = postNode.getIdentifier();
                String path = postNode.getPath();
                
                try {
                    String title = postNode.hasProperty("social:title") ? postNode.getProperty("social:title").getString() : "(no title)";
                    String status = postNode.hasProperty("social:status") ? postNode.getProperty("social:status").getString() : "(no status)";
                    String scheduledAt = postNode.hasProperty("social:scheduledAt") ? postNode.getProperty("social:scheduledAt").getString() : "(no date)";
                    
                    logger.info("[SERVICE] ==================== Processing Post #{} ====================", count + 1);
                    logger.info("[SERVICE] UUID: {}", uuid);
                    logger.info("[SERVICE] Path: {}", path);
                    logger.info("[SERVICE] Title: {}", title);
                    logger.info("[SERVICE] Current Status: {}", status);
                    logger.info("[SERVICE] Scheduled At: {}", scheduledAt);
                    logger.info("[SERVICE] Calling publishNow()...");
                    
                    publishNow(uuid);
                    
                    // Check if post was actually published by checking status after publishNow()
                    session.refresh(false); // Refresh to get latest data
                    Node refreshedNode = session.getNodeByIdentifier(uuid);
                    String finalStatus = refreshedNode.hasProperty("social:status") ? 
                        refreshedNode.getProperty("social:status").getString() : status;
                    
                    if (STATUS_PUBLISHED.equals(finalStatus)) {
                        count++;
                        logger.info("[SERVICE] ✓ Successfully published post: {}", title);
                    } else {
                        logger.error("[SERVICE] ✗ Post {} remains in status '{}' - publication failed", title, finalStatus);
                    }
                    logger.info("[SERVICE] =====================================================");
                } catch (Exception e) {
                    logger.error("[SERVICE] Failed to publish scheduled post " + uuid, e);
                }
            }
            
            logger.info("[SERVICE] ========== Published {} scheduled post(s) ==========", count);
            return null;
        });
    }
    
    @Override
    public List<String> getScheduledPosts(String startDate, String endDate) throws RepositoryException {
        List<String> postUuids = new ArrayList<>();
        
        jcrTemplate.doExecuteWithSystemSession(session -> {
            String sql2Query = String.format(
                "SELECT * FROM [%s] WHERE [social:scheduledAt] >= CAST('%s' AS DATE) AND [social:scheduledAt] <= CAST('%s' AS DATE)",
                SOCIAL_POST_TYPE,
                startDate,
                endDate
            );
            
            QueryManager queryManager = session.getWorkspace().getQueryManager();
            Query query = queryManager.createQuery(sql2Query, Query.JCR_SQL2);
            QueryResult result = query.execute();
            NodeIterator nodes = result.getNodes();
            
            while (nodes.hasNext()) {
                Node postNode = nodes.nextNode();
                postUuids.add(postNode.getIdentifier());
            }
            
            return null;
        });
        
        return postUuids;
    }

    /**
     * Get platform-specific message or fall back to default message.
     */
    /**
     * Publishes a post to an external platform via API.
     * Returns the external post ID on success, null on failure.
     */
    private String publishToPlatform(String platform, String postPath, String title, String message, String linkUrl, List<String> imageUrls) {
        logger.info("[SERVICE] >>> publishToPlatform({}, title='{}', messageLen={}, link={}, images={})", 
            platform, title, message.length(), linkUrl != null ? linkUrl : "null", imageUrls != null ? imageUrls.size() : 0);
        
        try {
            // Extract site key from post path (/sites/jsmod/contents/...)
            String siteKey = extractSiteFromPath(postPath);
            
            String endpoint;
            String accessToken;
            String jsonPayload;
            
            // Build platform-specific endpoint and payload
            switch (platform.toLowerCase()) {
                case "facebook":
                    // Try to get stored Facebook Page credentials
                    String pageToken = null;
                    String pageId = null;
                    boolean fromJCR = false;
                    try {
                        pageToken = socialAccountService.getFacebookPageAccessToken(siteKey, null);
                        Map<String, Map<String, String>> accounts = socialAccountService.getFacebookAccounts(siteKey);
                        if (!accounts.isEmpty()) {
                            Map<String, String> firstAccount = accounts.values().iterator().next();
                            pageId = firstAccount.get("pageId");
                            if (pageToken != null && pageId != null) {
                                fromJCR = true;
                                logger.info("[SERVICE] >>> Retrieved Facebook Page credentials from JCR for site: {}", siteKey);
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("[SERVICE] Could not retrieve stored Facebook Page credentials: {}", e.getMessage());
                    }
                    
                    // Fallback to configuration if no stored credentials
                    if (pageToken == null || pageId == null) {
                        logger.warn("[SERVICE] No stored Facebook Page credentials found for site: {}, using .cfg fallback", siteKey);
                        if (facebookPageId == null || facebookPageId.isEmpty()) {
                            logger.error("[SERVICE] Facebook Page ID not configured and no stored Page credentials found");
                            return null;
                        }
                        pageId = facebookPageId;
                        pageToken = facebookPageAccessToken != null && !facebookPageAccessToken.isEmpty() 
                            ? facebookPageAccessToken : authToken;
                    }
                    
                    logger.info("[SERVICE] Using Facebook Page ID: {} (source: {})", pageId, fromJCR ? "JCR" : ".cfg");
                    logger.info("[SERVICE] Page Token: {}...{} (length: {})", 
                        pageToken.length() > 10 ? pageToken.substring(0, 10) : "***",
                        pageToken.length() > 10 ? pageToken.substring(pageToken.length() - 10) : "***",
                        pageToken.length());
                    
                    // Handle Facebook image posting
                    if (imageUrls != null && !imageUrls.isEmpty()) {
                        if (imageUrls.size() == 1) {
                            // OPTION 1: Single image - use /photos endpoint
                            logger.info("[SERVICE] Publishing Facebook post with single image");
                            return publishFacebookSingleImage(pageId, pageToken, title, message, linkUrl, imageUrls.get(0));
                        } else {
                            // OPTION 2: Multiple images - upload unpublished, then create feed post
                            logger.info("[SERVICE] Publishing Facebook post with {} images", imageUrls.size());
                            return publishFacebookMultipleImages(pageId, pageToken, title, message, linkUrl, imageUrls);
                        }
                    }
                    
                    // No images - use /feed endpoint for text-only post
                    endpoint = String.format("%s/%s/%s/feed", getPlatformBaseUrl(platform), facebookApiVersion, pageId);
                    accessToken = pageToken;
                    jsonPayload = buildFacebookPayload(title, message, linkUrl, null);
                    break;
                    
                case "instagram":
                    if (instagramAccountId == null || instagramAccountId.isEmpty()) {
                        logger.error("[SERVICE] Instagram Account ID not configured");
                        return null;
                    }
                    // Instagram requires a 2-step process: create container, then publish
                    endpoint = String.format("%s/%s/%s/media", getPlatformBaseUrl(platform), facebookApiVersion, instagramAccountId);
                    accessToken = instagramAccessToken != null && !instagramAccessToken.isEmpty() 
                        ? instagramAccessToken : authToken;
                    jsonPayload = buildInstagramPayload(message, linkUrl);
                    break;
                    
                case "linkedin":
                    // Retrieve LinkedIn account credentials from JCR
                    String linkedinPersonId = null;
                    String linkedinToken = null;
                    
                    try {
                        Map<String, Map<String, String>> linkedinAccounts = socialAccountService.getLinkedInAccounts(siteKey);
                        if (!linkedinAccounts.isEmpty()) {
                            Map<String, String> firstAccount = linkedinAccounts.values().iterator().next();
                            linkedinPersonId = firstAccount.get("personId");
                            linkedinToken = firstAccount.get("accessToken");
                            logger.info("[SERVICE] Retrieved LinkedIn credentials from JCR for site: {} (personId: {})", 
                                siteKey, linkedinPersonId);
                        } else {
                            logger.error("[SERVICE] No LinkedIn account found in JCR for site: {}", siteKey);
                        }
                    } catch (Exception e) {
                        logger.error("[SERVICE] Failed to retrieve LinkedIn account from JCR", e);
                    }
                    
                    // Build author URN
                    String linkedinAuthor = null;
                    if (linkedinPersonId != null && !linkedinPersonId.isEmpty()) {
                        linkedinAuthor = "urn:li:person:" + linkedinPersonId;
                    } else if (linkedinOrganizationId != null && !linkedinOrganizationId.isEmpty()) {
                        // Fallback to organization if configured
                        linkedinAuthor = "urn:li:organization:" + linkedinOrganizationId;
                    }
                    
                    if (linkedinAuthor == null) {
                        logger.error("[SERVICE] LinkedIn author not available - no person ID in account or organization ID in config");
                        return null;
                    }
                    
                    // Use token from JCR account, fallback to config
                    accessToken = linkedinToken != null && !linkedinToken.isEmpty() 
                        ? linkedinToken 
                        : (linkedinAccessToken != null && !linkedinAccessToken.isEmpty() ? linkedinAccessToken : authToken);
                    
                    endpoint = String.format("%s/%s/ugcPosts", getPlatformBaseUrl(platform), linkedinApiVersion);
                    jsonPayload = buildLinkedInPayload(message, linkUrl, linkedinAuthor);
                    
                    logger.info("[SERVICE] LinkedIn posting - Author: {}, Token length: {}", 
                        linkedinAuthor, accessToken != null ? accessToken.length() : 0);
                    break;
                    
                default:
                    logger.error("[SERVICE] Unknown platform: {}", platform);
                    return null;
            }
            
            logger.info("[SERVICE] API Endpoint: {}", endpoint);
            
            URL url = new URL(endpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);
            
            // LinkedIn requires this header for all API requests
            if ("linkedin".equalsIgnoreCase(platform)) {
                conn.setRequestProperty("X-Restli-Protocol-Version", "2.0.0");
            }
            
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);
            
            // Send payload
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            int responseCode = conn.getResponseCode();
            
            if (responseCode >= 200 && responseCode < 300) {
                // Parse response to extract external ID
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                    
                    // Extract ID from response (simplified - real implementation would parse JSON)
                    String responseBody = response.toString();
                    logger.info("[SERVICE] Platform {} response: {}", platform, responseBody);
                    
                    // Mock extraction - in real implementation use JSON parser
                    return extractExternalId(responseBody);
                }
            } else {
                // Log error response
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                    StringBuilder errorResponse = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        errorResponse.append(line);
                    }
                    logger.error("[SERVICE] Failed to publish to {}: HTTP {} - {}", platform, responseCode, errorResponse.toString());
                } catch (Exception e) {
                    logger.error("[SERVICE] Failed to publish to {}: HTTP {}", platform, responseCode);
                }
                return null;
            }
            
        } catch (Exception e) {
            logger.error("[SERVICE] Error publishing to platform " + platform, e);
            return null;
        }
    }
    
    /**
     * Publish Facebook post with single image using /photos endpoint.
     * This creates a post with one image.
     * 
     * @param pageId Facebook Page ID
     * @param pageToken Page access token
     * @param title Post title
     * @param message Post message
     * @param linkUrl Optional link URL
     * @param imageUrl Image URL to post
     * @return External post ID or null on failure
     */
    private String publishFacebookSingleImage(String pageId, String pageToken, String title, String message, String linkUrl, String imageUrl) {
        try {
            String endpoint = String.format("%s/%s/%s/photos", facebookBaseUrl, facebookApiVersion, pageId);
            String fullMessage = title + "\n\n" + message;
            if (linkUrl != null && !linkUrl.isEmpty()) {
                fullMessage += "\n\n" + linkUrl;
            }
            
            // Build form data payload
            String payload = "url=" + URLEncoder.encode(imageUrl, "UTF-8") +
                           "&message=" + URLEncoder.encode(fullMessage, "UTF-8") +
                           "&access_token=" + URLEncoder.encode(pageToken, "UTF-8");
            
            // Add appsecret_proof if app secret is configured
            if (facebookAppSecret != null && !facebookAppSecret.isEmpty()) {
                String appsecretProof = generateAppSecretProof(pageToken, facebookAppSecret);
                payload += "&appsecret_proof=" + appsecretProof;
            }
            
            logger.info("[SERVICE] Facebook /photos endpoint: {}", endpoint);
            logger.info("[SERVICE] Image URL: {}", imageUrl);
            
            URL url = new URL(endpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);
            
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = payload.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            int responseCode = conn.getResponseCode();
            
            if (responseCode >= 200 && responseCode < 300) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                    
                    String responseBody = response.toString();
                    logger.info("[SERVICE] Facebook /photos response: {}", responseBody);
                    return extractExternalId(responseBody);
                }
            } else {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                    StringBuilder errorResponse = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        errorResponse.append(line);
                    }
                    logger.error("[SERVICE] Failed to publish Facebook photo: HTTP {} - {}", responseCode, errorResponse.toString());
                }
                return null;
            }
            
        } catch (Exception e) {
            logger.error("[SERVICE] Error publishing Facebook single image", e);
            return null;
        }
    }
    
    /**
     * Publish Facebook post with multiple images.
     * Step 1: Upload each image as unpublished (published=false)
     * Step 2: Create feed post with attached_media array
     * 
     * @param pageId Facebook Page ID
     * @param pageToken Page access token
     * @param title Post title
     * @param message Post message
     * @param linkUrl Optional link URL
     * @param imageUrls List of image URLs
     * @return External post ID or null on failure
     */
    private String publishFacebookMultipleImages(String pageId, String pageToken, String title, String message, String linkUrl, List<String> imageUrls) {
        try {
            List<String> mediaIds = new ArrayList<>();
            
            // Step 1: Upload each image as unpublished
            for (int i = 0; i < imageUrls.size(); i++) {
                String imageUrl = imageUrls.get(i);
                logger.info("[SERVICE] Uploading image {}/{} as unpublished: {}", i + 1, imageUrls.size(), imageUrl);
                
                String endpoint = String.format("%s/%s/%s/photos", facebookBaseUrl, facebookApiVersion, pageId);
                
                // Build form data payload with published=false
                String payload = "url=" + URLEncoder.encode(imageUrl, "UTF-8") +
                               "&published=false" +
                               "&access_token=" + URLEncoder.encode(pageToken, "UTF-8");
                
                // Add appsecret_proof if app secret is configured
                if (facebookAppSecret != null && !facebookAppSecret.isEmpty()) {
                    String appsecretProof = generateAppSecretProof(pageToken, facebookAppSecret);
                    payload += "&appsecret_proof=" + appsecretProof;
                }
                
                URL url = new URL(endpoint);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(30000);
                
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = payload.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }
                
                int responseCode = conn.getResponseCode();
                
                if (responseCode >= 200 && responseCode < 300) {
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) {
                            response.append(line);
                        }
                        
                        String responseBody = response.toString();
                        logger.info("[SERVICE] Facebook unpublished photo response: {}", responseBody);
                        
                        // Extract media ID
                        String mediaId = extractMediaId(responseBody);
                        if (mediaId != null) {
                            mediaIds.add(mediaId);
                            logger.info("[SERVICE] Uploaded image {}/{} - Media ID: {}", i + 1, imageUrls.size(), mediaId);
                        } else {
                            logger.error("[SERVICE] Failed to extract media ID from response");
                            return null;
                        }
                    }
                } else {
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                        StringBuilder errorResponse = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) {
                            errorResponse.append(line);
                        }
                        logger.error("[SERVICE] Failed to upload unpublished photo: HTTP {} - {}", responseCode, errorResponse.toString());
                    }
                    return null;
                }
            }
            
            // Step 2: Create feed post with attached media
            logger.info("[SERVICE] Creating feed post with {} attached media IDs", mediaIds.size());
            
            String feedEndpoint = String.format("%s/%s/%s/feed", facebookBaseUrl, facebookApiVersion, pageId);
            String fullMessage = title + "\n\n" + message;
            if (linkUrl != null && !linkUrl.isEmpty()) {
                fullMessage += "\n\n" + linkUrl;
            }
            
            // Build form data with attached_media array
            StringBuilder feedPayload = new StringBuilder();
            feedPayload.append("message=").append(URLEncoder.encode(fullMessage, "UTF-8"));
            
            for (int i = 0; i < mediaIds.size(); i++) {
                feedPayload.append("&attached_media[").append(i).append("]=")
                          .append(URLEncoder.encode("{\"media_fbid\":\"" + mediaIds.get(i) + "\"}", "UTF-8"));
            }
            
            feedPayload.append("&access_token=").append(URLEncoder.encode(pageToken, "UTF-8"));
            
            // Add appsecret_proof if app secret is configured
            if (facebookAppSecret != null && !facebookAppSecret.isEmpty()) {
                String appsecretProof = generateAppSecretProof(pageToken, facebookAppSecret);
                feedPayload.append("&appsecret_proof=").append(appsecretProof);
            }
            
            URL url = new URL(feedEndpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);
            
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = feedPayload.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            int responseCode = conn.getResponseCode();
            
            if (responseCode >= 200 && responseCode < 300) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                    
                    String responseBody = response.toString();
                    logger.info("[SERVICE] Facebook /feed response: {}", responseBody);
                    return extractExternalId(responseBody);
                }
            } else {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                    StringBuilder errorResponse = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        errorResponse.append(line);
                    }
                    logger.error("[SERVICE] Failed to publish Facebook feed post: HTTP {} - {}", responseCode, errorResponse.toString());
                }
                return null;
            }
            
        } catch (Exception e) {
            logger.error("[SERVICE] Error publishing Facebook multiple images", e);
            return null;
        }
    }
    
    /**
     * Extract media ID from Facebook photo upload response.
     * Response format: {"id":"mediaId"}
     */
    private String extractMediaId(String responseBody) {
        if (responseBody == null || responseBody.isEmpty()) {
            return null;
        }
        
        try {
            if (responseBody.contains("\"id\"")) {
                int idStart = responseBody.indexOf("\"id\":\"") + 6;
                int idEnd = responseBody.indexOf("\"", idStart);
                if (idStart > 5 && idEnd > idStart) {
                    return responseBody.substring(idStart, idEnd);
                }
            }
        } catch (Exception e) {
            logger.error("[SERVICE] Error parsing media ID from response: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Generate appsecret_proof for Facebook API calls.
     * HMAC-SHA256 hash of access_token using app secret as key.
     */
    private String generateAppSecretProof(String accessToken, String appSecret) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec secretKeySpec = new javax.crypto.spec.SecretKeySpec(
                appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(accessToken.getBytes(StandardCharsets.UTF_8));
            
            // Convert to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            logger.error("[SERVICE] Error generating appsecret_proof", e);
            return "";
        }
    }
    
    /**
     * Build Facebook-specific JSON payload.
     * Facebook Graph API /feed endpoint supports:
     * - message: text content (includes title + message)
     * - link: URL to share
     * - url: single image URL (Facebook will fetch and attach)
     */
    private String buildFacebookPayload(String title, String message, String linkUrl, List<String> imageUrls) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        
        // Combine title and message for Facebook post
        String fullMessage = title + "\n\n" + message;
        json.append("\"message\":\"").append(escapeJson(fullMessage)).append("\"");
        
        // Add link if provided
        if (linkUrl != null && !linkUrl.isEmpty()) {
            json.append(",\"link\":\"").append(escapeJson(linkUrl)).append("\"");
        }
        
        // Add first image URL if available (Facebook /feed endpoint supports single image via 'url' parameter)
        if (imageUrls != null && !imageUrls.isEmpty()) {
            String firstImageUrl = imageUrls.get(0);
            json.append(",\"url\":\"").append(escapeJson(firstImageUrl)).append("\"");
            logger.info("[SERVICE] >>> Including image in Facebook post: {}", firstImageUrl);
            if (imageUrls.size() > 1) {
                logger.warn("[SERVICE] >>> Facebook /feed endpoint supports only 1 image, {} additional images ignored", imageUrls.size() - 1);
            }
        }
        
        json.append("}");
        return json.toString();
    }
    
    /**
     * Build Instagram-specific JSON payload.
     */
    private String buildInstagramPayload(String message, String imageUrl) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"caption\":\"").append(escapeJson(message)).append("\"");
        if (imageUrl != null && !imageUrl.isEmpty()) {
            json.append(",\"image_url\":\"").append(escapeJson(imageUrl)).append("\"");
        }
        json.append("}");
        return json.toString();
    }
    
    /**
     * Build LinkedIn-specific JSON payload for ugcPosts API.
     * Reference: https://learn.microsoft.com/en-us/linkedin/consumer/integrations/self-serve/share-on-linkedin
     * 
     * @param message The post text content
     * @param linkUrl Optional URL to share (requires shareMediaCategory: ARTICLE)
     * @param authorUrn Full author URN - either "urn:li:person:{personId}" or "urn:li:organization:{orgId}"
     * @return JSON payload string
     */
    private String buildLinkedInPayload(String message, String linkUrl, String authorUrn) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"author\":\"").append(escapeJson(authorUrn)).append("\",");
        json.append("\"lifecycleState\":\"PUBLISHED\",");
        json.append("\"specificContent\":{");
        json.append("\"com.linkedin.ugc.ShareContent\":{");
        json.append("\"shareCommentary\":{");
        json.append("\"text\":\"").append(escapeJson(message)).append("\"");
        json.append("}");
        
        // If linkUrl is provided, add it as ARTICLE media
        if (linkUrl != null && !linkUrl.isEmpty()) {
            json.append(",\"shareMediaCategory\":\"ARTICLE\"");
            json.append(",\"media\":[{");
            json.append("\"status\":\"READY\",");
            json.append("\"originalUrl\":\"").append(escapeJson(linkUrl)).append("\"");
            json.append("}]");
        } else {
            json.append(",\"shareMediaCategory\":\"NONE\"");
        }
        
        json.append("}},");
        json.append("\"visibility\":{\"com.linkedin.ugc.MemberNetworkVisibility\":\"PUBLIC\"}");
        json.append("}");
        return json.toString();
    }
    
    /**
     * Extract external ID from API response.
     * Parses JSON to extract post ID from Facebook, Instagram, LinkedIn responses.
     */
    private String extractExternalId(String responseBody) {
        if (responseBody == null || responseBody.isEmpty()) {
            return null;
        }
        
        try {
            // Facebook returns: {"id": "pageId_postId"}
            // Instagram returns: {"id": "mediaId"}
            // LinkedIn returns: {"id": "urn:li:share:123"}
            
            if (responseBody.contains("\"id\"")) {
                // Extract id from JSON: {"id":"123_456"}
                int idStart = responseBody.indexOf("\"id\":\"") + 6;
                int idEnd = responseBody.indexOf("\"", idStart);
                if (idStart > 5 && idEnd > idStart) {
                    String fullId = responseBody.substring(idStart, idEnd);
                    logger.info("[SERVICE] >>> Full ID from API response: {}", fullId);
                    
                    // For Facebook posts, extract only the post ID (after underscore)
                    // Format: pageId_postId -> store only postId
                    if (fullId.contains("_")) {
                        String postId = fullId.substring(fullId.indexOf("_") + 1);
                        logger.info("[SERVICE] >>> Extracted post ID (after underscore): {}", postId);
                        return postId;
                    }
                    
                    return fullId;
                }
            }
            
            // Facebook photos endpoint returns: {"post_id": "pageId_postId"}
            if (responseBody.contains("\"post_id\"")) {
                int idStart = responseBody.indexOf("\"post_id\":\"") + 11;
                int idEnd = responseBody.indexOf("\"", idStart);
                if (idStart > 10 && idEnd > idStart) {
                    String fullId = responseBody.substring(idStart, idEnd);
                    logger.info("[SERVICE] >>> Full post_id from API response: {}", fullId);
                    
                    // For Facebook posts, extract only the post ID (after underscore)
                    if (fullId.contains("_")) {
                        String postId = fullId.substring(fullId.indexOf("_") + 1);
                        logger.info("[SERVICE] >>> Extracted post ID (after underscore): {}", postId);
                        return postId;
                    }
                    
                    return fullId;
                }
            }
            
            logger.warn("[SERVICE] >>> Could not extract post ID from response: {}", responseBody);
            return null;
            
        } catch (Exception e) {
            logger.error("[SERVICE] >>> Error parsing post ID from response: {}", e.getMessage());
            return null;
        }
    }
    
    private String getPropertyValue(Node node, String propertyName) throws RepositoryException {
        if (node.hasProperty(propertyName)) {
            return node.getProperty(propertyName).getString();
        }
        return "";
    }
    
    private String[] getMultiValueProperty(Node node, String propertyName) throws RepositoryException {
        if (node.hasProperty(propertyName)) {
            javax.jcr.Value[] values = node.getProperty(propertyName).getValues();
            String[] result = new String[values.length];
            for (int i = 0; i < values.length; i++) {
                result[i] = values[i].getString();
            }
            return result;
        }
        return new String[0];
    }
    
    private String getCurrentTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
        return sdf.format(new Date());
    }
    
    private String extractSiteFromPath(String path) {
        // Extract site key from path like /sites/jsmod/contents/...
        if (path != null && path.startsWith("/sites/")) {
            String[] parts = path.split("/");
            if (parts.length > 2) {
                return parts[2];
            }
        }
        return "systemsite";
    }
    
    private String escapeJson(String input) {
        if (input == null) return "";
        return input
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
}
