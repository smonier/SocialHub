package org.example.socialhub.service;

import javax.jcr.RepositoryException;

/**
 * Service for managing social media metrics.
 * Handles fetching and storing analytics data from external platforms.
 */
public interface SocialMetricsService {
    
    /**
     * Refreshes metrics for all published posts.
     * Queries external APIs for latest analytics data and stores as socialnt:metrics child nodes.
     * 
     * @throws RepositoryException if JCR operations fail
     */
    void refreshMetricsForPublishedPosts() throws RepositoryException;
    
    /**
     * Refreshes metrics for a specific post.
     * 
     * @param postUuid UUID of the socialnt:post node
     * @throws RepositoryException if JCR operations fail
     */
    void refreshMetricsForPost(String postUuid) throws RepositoryException;
}
