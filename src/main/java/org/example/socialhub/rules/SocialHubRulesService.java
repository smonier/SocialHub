package org.example.socialhub.rules;

import org.example.socialhub.service.ActivityLogService;
import org.jahia.services.content.rules.AbstractNodeFact;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;

/**
 * Service for Social Hub rules operations.
 * This service is exposed to Drools rules via ModuleGlobalObject.
 */
@Component(service = SocialHubRulesService.class)
public class SocialHubRulesService {
    
    private static final Logger logger = LoggerFactory.getLogger(SocialHubRulesService.class);
    
    @Reference
    private ActivityLogService activityLogService;
    
    /**
     * Mark a social post as scheduled when it's published.
     * 
     * @param node The node fact representing the social post
     * @throws RepositoryException if JCR operations fail
     */
    public void markAsScheduled(AbstractNodeFact node) throws RepositoryException {
        logger.info("[RULES] markAsScheduled called for node: {}", node != null ? node.getPath() : "null");
        
        if (node == null) {
            logger.warn("[RULES] Node is null, skipping");
            return;
        }
        
        logger.debug("[RULES] Node type: {}", node.getNode().getPrimaryNodeType().getName());
        
        if (!node.getNode().isNodeType("socialnt:post")) {
            logger.debug("[RULES] Node is not a socialnt:post, skipping");
            return;
        }
        
        if (node.getNode().hasProperty("social:scheduledAt")) {
            String title = node.getNode().hasProperty("social:title") ? 
                node.getNode().getProperty("social:title").getString() : node.getName();
            String postId = node.getNode().getIdentifier();
            
            logger.info("[RULES] Setting social:status to 'scheduled' for: {}", node.getName());
            node.getNode().setProperty("social:status", "scheduled");
            node.getNode().getSession().save();
            logger.info("[RULES] Social post published - marked as scheduled: {}", node.getName());
            
            // Log activity
            try {
                activityLogService.logStatusChange(postId, title, "draft", "scheduled", 
                    "Rule fired: j:published property set to true");
            } catch (Exception e) {
                logger.warn("[ACTIVITY] Failed to log status change", e);
            }
        } else {
            logger.debug("[RULES] Node does not have social:scheduledAt property");
        }
    }
    
    /**
     * Mark a social post as draft when it's unpublished.
     * 
     * @param node The node fact representing the social post
     * @throws RepositoryException if JCR operations fail
     */
    public void markAsDraft(AbstractNodeFact node) throws RepositoryException {
        logger.info("[RULES] markAsDraft called for node: {}", node != null ? node.getPath() : "null");
        
        if (node == null) {
            logger.warn("[RULES] Node is null, skipping");
            return;
        }
        
        logger.debug("[RULES] Node type: {}", node.getNode().getPrimaryNodeType().getName());
        
        if (!node.getNode().isNodeType("socialnt:post")) {
            logger.debug("[RULES] Node is not a socialnt:post, skipping");
            return;
        }
        
        if (node.getNode().hasProperty("social:status")) {
            String title = node.getNode().hasProperty("social:title") ? 
                node.getNode().getProperty("social:title").getString() : node.getName();
            String postId = node.getNode().getIdentifier();
            String oldStatus = node.getNode().getProperty("social:status").getString();
            
            logger.info("[RULES] Setting social:status to 'draft' for: {}", node.getName());
            node.getNode().setProperty("social:status", "draft");
            node.getNode().getSession().save();
            logger.info("[RULES] Social post unpublished - marked as draft: {}", node.getName());
            
            // Log activity
            try {
                activityLogService.logStatusChange(postId, title, oldStatus, "draft", 
                    "Rule fired: post unpublished");
            } catch (Exception e) {
                logger.warn("[ACTIVITY] Failed to log status change", e);
            }
        } else {
            logger.debug("[RULES] Node does not have social:status property");
        }
    }
    
    /**
     * Auto-schedule posts that don't have an explicit schedule time.
     * 
     * @param node The node fact representing the social post
     * @throws RepositoryException if JCR operations fail
     */
    public void autoSchedulePost(AbstractNodeFact node) throws RepositoryException {
        logger.info("[RULES] autoSchedulePost called for node: {}", node != null ? node.getPath() : "null");
        
        if (node == null) {
            logger.warn("[RULES] Node is null, skipping");
            return;
        }
        
        logger.debug("[RULES] Node type: {}", node.getNode().getPrimaryNodeType().getName());
        
        if (!node.getNode().isNodeType("socialnt:post")) {
            logger.debug("[RULES] Node is not a socialnt:post, skipping");
            return;
        }
        
        if (!node.getNode().hasProperty("social:scheduledAt")) {
            logger.info("[RULES] Auto-scheduling post without scheduledAt: {}", node.getName());
            node.getNode().setProperty("social:scheduledAt", new java.util.GregorianCalendar());
            node.getNode().setProperty("social:status", "scheduled");
            node.getNode().getSession().save();
            logger.info("[RULES] Social post published without schedule time - set to publish immediately: {}", node.getName());
        } else {
            logger.debug("[RULES] Node already has social:scheduledAt property");
        }
    }
}
