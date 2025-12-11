package org.example.socialhub.service;

import javax.jcr.RepositoryException;
import java.util.List;

/**
 * Service for managing social media posts.
 * Handles publishing posts immediately or on schedule.
 */
public interface SocialPostService {
    
    /**
     * Publishes a social post immediately.
     * 
     * @param postUuid UUID of the socialnt:post node to publish
     * @throws RepositoryException if JCR operations fail
     */
    void publishNow(String postUuid) throws RepositoryException;
    
    /**
     * Scans for scheduled posts that are due and publishes them.
     * Should be called periodically by a scheduler.
     * 
     * @throws RepositoryException if JCR operations fail
     */
    void publishDueScheduledPosts() throws RepositoryException;
    
    /**
     * Gets all posts scheduled for a specific date range.
     * 
     * @param startDate ISO date string
     * @param endDate ISO date string
     * @return List of post UUIDs
     * @throws RepositoryException if JCR operations fail
     */
    List<String> getScheduledPosts(String startDate, String endDate) throws RepositoryException;
}
