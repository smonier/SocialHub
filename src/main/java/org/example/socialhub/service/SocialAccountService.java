package org.example.socialhub.service;

import javax.jcr.RepositoryException;
import java.util.List;
import java.util.Map;

/**
 * Service for managing connected social media accounts.
 */
public interface SocialAccountService {
    
    /**
     * Connect a Facebook account by exchanging user access token for page tokens.
     * Calls Facebook /me/accounts endpoint and stores page credentials.
     * 
     * @param userAccessToken Facebook user access token
     * @param siteKey The site key where accounts will be stored
     * @return List of connected page names
     * @throws RepositoryException if JCR operation fails
     */
    List<String> connectFacebookAccount(String userAccessToken, String siteKey) throws RepositoryException;
    
    /**
     * Get stored Facebook Page credentials for a site.
     * 
     * @param siteKey The site key
     * @return Map of page ID to credentials (pageId, pageName, pageAccessToken)
     * @throws RepositoryException if JCR operation fails
     */
    Map<String, Map<String, String>> getFacebookAccounts(String siteKey) throws RepositoryException;
    
    /**
     * Get Facebook Page access token for publishing.
     * 
     * @param siteKey The site key
     * @param pageId Optional specific page ID, if null returns first available
     * @return Page access token or null if not found
     * @throws RepositoryException if JCR operation fails
     */
    String getFacebookPageAccessToken(String siteKey, String pageId) throws RepositoryException;
    
    /**
     * Disconnect a Facebook page.
     * 
     * @param siteKey The site key
     * @param pageId The page ID to disconnect
     * @throws RepositoryException if JCR operation fails
     */
    void disconnectFacebookAccount(String siteKey, String pageId) throws RepositoryException;
    
    /**
     * Connect a LinkedIn account with OAuth credentials.
     * 
     * @param siteKey The site key where account will be stored
     * @param personId The LinkedIn person ID (from 'sub' field)
     * @param name Display name
     * @param email Email address
     * @param accessToken LinkedIn access token
     * @param expiresIn Token expiration time in seconds
     * @throws RepositoryException if JCR operation fails
     */
    void connectLinkedInAccount(String siteKey, String personId, String name, String email, 
                                String accessToken, int expiresIn) throws RepositoryException;
    
    /**
     * Get stored LinkedIn credentials for a site.
     * 
     * @param siteKey The site key
     * @return Map of person ID to credentials (personId, name, email, accessToken)
     * @throws RepositoryException if JCR operation fails
     */
    Map<String, Map<String, String>> getLinkedInAccounts(String siteKey) throws RepositoryException;
    
    /**
     * Disconnect a LinkedIn account.
     * 
     * @param siteKey The site key
     * @param personId The LinkedIn person ID to disconnect
     * @throws RepositoryException if JCR operation fails
     */
    void disconnectLinkedInAccount(String siteKey, String personId) throws RepositoryException;
}
