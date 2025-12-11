package org.example.socialhub.service;

import javax.jcr.RepositoryException;

/**
 * Service for logging social hub activities.
 * Records all publishing attempts, successes, failures, and status changes.
 */
public interface ActivityLogService {
    
    /**
     * Log a publishing attempt.
     */
    void logPublishAttempt(String postId, String postTitle, String[] platforms) throws RepositoryException;
    
    /**
     * Log a successful publish.
     */
    void logPublishSuccess(String postId, String postTitle, String platform, String externalId) throws RepositoryException;
    
    /**
     * Log a failed publish.
     */
    void logPublishFailure(String postId, String postTitle, String platform, String errorMessage) throws RepositoryException;
    
    /**
     * Log a status change.
     */
    void logStatusChange(String postId, String postTitle, String oldStatus, String newStatus, String reason) throws RepositoryException;
    
    /**
     * Log a rule execution.
     */
    void logRuleFired(String ruleName, String postId, String postTitle, String action) throws RepositoryException;
}
