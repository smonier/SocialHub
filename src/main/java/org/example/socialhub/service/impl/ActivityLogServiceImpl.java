package org.example.socialhub.service.impl;

import org.example.socialhub.service.ActivityLogService;
import org.jahia.services.content.JCRTemplate;
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
import java.util.Calendar;

/**
 * Implementation of ActivityLogService.
 * Creates JCR nodes under /sites/<site>/activity-logs to track all social hub activities.
 */
@Component(service = ActivityLogService.class, immediate = true)
public class ActivityLogServiceImpl implements ActivityLogService {
    
    private static final Logger logger = LoggerFactory.getLogger(ActivityLogServiceImpl.class);
    private static final String ACTIVITY_LOG_PATH = "/sites/%s/activity-logs";
    
    @Reference
    private JCRTemplate jcrTemplate;
    
    @Override
    public void logPublishAttempt(String postId, String postTitle, String[] platforms) throws RepositoryException {
        logger.info("[ACTIVITY] >>> logPublishAttempt called: postId={}, title={}, platforms={}", 
            postId, postTitle, platforms != null ? String.join(",", platforms) : "null");
        String message = String.format("Attempting to publish to %d platform(s): %s", 
            platforms.length, String.join(", ", platforms));
        logger.info("[ACTIVITY] >>> Calling createLogEntry...");
        createLogEntry("publish_attempt", postId, postTitle, null, "scheduled", message, null);
        logger.info("[ACTIVITY] ✓✓✓ Successfully logged publish attempt: {} - {}", postTitle, message);
    }
    
    @Override
    public void logPublishSuccess(String postId, String postTitle, String platform, String externalId) throws RepositoryException {
        String message = String.format("Successfully published to %s (ID: %s)", platform, externalId);
        createLogEntry("publish_success", postId, postTitle, platform, "published", message, null);
        logger.info("[ACTIVITY] Logged publish success: {} - {}", postTitle, message);
    }
    
    @Override
    public void logPublishFailure(String postId, String postTitle, String platform, String errorMessage) throws RepositoryException {
        String message = String.format("Failed to publish to %s", platform);
        createLogEntry("publish_failure", postId, postTitle, platform, "scheduled", message, errorMessage);
        logger.info("[ACTIVITY] Logged publish failure: {} - {}", postTitle, message);
    }
    
    @Override
    public void logStatusChange(String postId, String postTitle, String oldStatus, String newStatus, String reason) throws RepositoryException {
        String message = String.format("Status changed: %s → %s. Reason: %s", oldStatus, newStatus, reason);
        createLogEntry("schedule", postId, postTitle, null, newStatus, message, null);
        logger.info("[ACTIVITY] Logged status change: {} - {}", postTitle, message);
    }
    
    @Override
    public void logRuleFired(String ruleName, String postId, String postTitle, String action) throws RepositoryException {
        String message = String.format("Rule '%s' executed: %s", ruleName, action);
        createLogEntry("rule_fired", postId, postTitle, null, null, message, null);
        logger.info("[ACTIVITY] Logged rule execution: {} - {}", postTitle, message);
    }
    
    /**
     * Create a log entry node in the JCR.
     */
    private void createLogEntry(String action, String postId, String postTitle, String platform, 
                                String status, String message, String errorMessage) throws RepositoryException {
        logger.info("[ACTIVITY] >>> createLogEntry: action={}, postId={}, title={}", action, postId, postTitle);
        jcrTemplate.doExecuteWithSystemSession(session -> {
            try {
                logger.info("[ACTIVITY] >>> Inside doExecuteWithSystemSession");
                // Find the site from the post
                String siteName = findSiteNameFromPost(session, postId);
                logger.info("[ACTIVITY] >>> Found site name: {}", siteName);
                String logsPath = String.format(ACTIVITY_LOG_PATH, siteName != null ? siteName : "systemsite");
                logger.info("[ACTIVITY] >>> Logs path: {}", logsPath);
                
                // Create logs folder if it doesn't exist
                logger.info("[ACTIVITY] >>> Ensuring logs folder exists...");
                Node logsFolder = ensureNodeExists(session, logsPath, "jnt:contentList");
                logger.info("[ACTIVITY] >>> Logs folder ready: {}", logsFolder.getPath());
                
                // Create unique log entry name with timestamp
                String nodeName = String.format("log_%s_%d", action, System.currentTimeMillis());
                logger.info("[ACTIVITY] >>> Creating log node: {}", nodeName);
                Node logNode = logsFolder.addNode(nodeName, "socialnt:activityLog");
                logger.info("[ACTIVITY] >>> Log node created: {}", logNode.getPath());
                
                // Set properties
                logNode.setProperty("social:timestamp", Calendar.getInstance());
                logNode.setProperty("social:action", action);
                logNode.setProperty("social:message", message);
                
                if (postId != null) {
                    logNode.setProperty("social:postId", postId);
                }
                if (postTitle != null) {
                    logNode.setProperty("social:postTitle", postTitle);
                }
                if (platform != null) {
                    logNode.setProperty("social:platform", platform);
                }
                if (status != null) {
                    logNode.setProperty("social:status", status);
                }
                if (errorMessage != null) {
                    logNode.setProperty("social:errorMessage", errorMessage);
                }
                
                // Set user ID from session
                if (session.getUserID() != null) {
                    logNode.setProperty("social:userId", session.getUserID());
                }
                
                logger.info("[ACTIVITY] >>> Saving session...");
                session.save();
                logger.info("[ACTIVITY] ✓✓✓ Activity log saved successfully: {}", logNode.getPath());
                
            } catch (Exception e) {
                logger.error("[ACTIVITY] ✗✗✗ FAILED to create activity log entry", e);
                logger.error("[ACTIVITY] ✗✗✗ Error details: type={}, message={}", 
                    e.getClass().getSimpleName(), e.getMessage());
                // Don't throw - logging failures shouldn't break the main flow
            }
            
            return null;
        });
    }
    
    /**
     * Find the site name from a post UUID.
     */
    private String findSiteNameFromPost(javax.jcr.Session session, String postId) {
        if (postId == null) {
            return null;
        }
        
        try {
            Node postNode = session.getNodeByIdentifier(postId);
            String path = postNode.getPath();
            
            // Extract site name from path like /sites/jsmod/contents/...
            if (path.startsWith("/sites/")) {
                String[] parts = path.split("/");
                if (parts.length > 2) {
                    return parts[2];
                }
            }
        } catch (Exception e) {
            logger.debug("[ACTIVITY] Could not find site name for post {}", postId);
        }
        
        return null;
    }
    
    /**
     * Ensure a node path exists, creating parent nodes as needed.
     */
    private Node ensureNodeExists(javax.jcr.Session session, String path, String nodeType) throws RepositoryException {
        if (session.nodeExists(path)) {
            return session.getNode(path);
        }
        
        // Create parent path first
        String parentPath = path.substring(0, path.lastIndexOf('/'));
        if (parentPath.isEmpty()) {
            parentPath = "/";
        }
        
        Node parent;
        if (session.nodeExists(parentPath)) {
            parent = session.getNode(parentPath);
        } else {
            parent = ensureNodeExists(session, parentPath, "jnt:contentList");
        }
        
        String nodeName = path.substring(path.lastIndexOf('/') + 1);
        return parent.addNode(nodeName, nodeType);
    }
}
