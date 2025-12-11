package org.example.socialhub.service.impl;

import org.example.socialhub.service.SocialAccountService;
import org.jahia.services.content.JCRTemplate;
import org.json.JSONArray;
import org.json.JSONObject;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.http.HttpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Implementation of SocialAccountService.
 * Manages Facebook account connections and stores credentials in existing socialnt:account nodes.
 * All API calls go through SocialProxyServlet.
 */
@Component(
    service = SocialAccountService.class,
    immediate = true,
    configurationPid = "org.example.socialhub.servlet.SocialProxyServlet"
)
public class SocialAccountServiceImpl implements SocialAccountService {
    
    private static final Logger logger = LoggerFactory.getLogger(SocialAccountServiceImpl.class);
    private static final String ACCOUNTS_PATH = "/sites/%s/contents/social-accounts";
    
    private String facebookBaseUrl = "https://graph.facebook.com";
    private String facebookApiVersion = "v21.0";
    
    @Reference
    private JCRTemplate jcrTemplate;
    
    @Activate
    protected void activate(Map<String, Object> properties) {
        if (properties.get("facebookBaseUrl") != null) {
            facebookBaseUrl = (String) properties.get("facebookBaseUrl");
        }
        if (properties.get("facebookApiVersion") != null) {
            facebookApiVersion = (String) properties.get("facebookApiVersion");
        }
        logger.info("[ACCOUNT] SocialAccountServiceImpl activated - Facebook API: {}/{}", 
            facebookBaseUrl, facebookApiVersion);
    }
    
    @Override
    public List<String> connectFacebookAccount(String userAccessToken, String siteKey) throws RepositoryException {
        logger.info("[ACCOUNT] >>> Starting Facebook account connection for site: {}", siteKey);
        logger.info("[ACCOUNT] >>> Step 1: Storing user access token and fetching Page tokens from /me/accounts");
        List<String> connectedPages = new ArrayList<>();
        
        try {
            // Step 1: Call Facebook /me/accounts API with the User Access Token
            String apiUrl = String.format("%s/%s/me/accounts?access_token=%s", 
                facebookBaseUrl, facebookApiVersion, userAccessToken);
            
            logger.info("[ACCOUNT] >>> Calling Facebook API: {}/{}/me/accounts", facebookBaseUrl, facebookApiVersion);
            
            URL url = new URL(apiUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            
            int responseCode = connection.getResponseCode();
            logger.info("[ACCOUNT] >>> Facebook API response code: {}", responseCode);
            
            if (responseCode == 200) {
                // Read response
                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }
                
                String jsonResponse = response.toString();
                logger.info("[ACCOUNT] >>> Facebook API response: {}", jsonResponse);
                
                // Parse JSON response
                JSONObject jsonObject = new JSONObject(jsonResponse);
                JSONArray dataArray = jsonObject.getJSONArray("data");
                
                logger.info("[ACCOUNT] >>> Found {} page(s) in response", dataArray.length());
                
                // Step 2: Store each Page with BOTH tokens (user token + page token)
                for (int i = 0; i < dataArray.length(); i++) {
                    JSONObject pageData = dataArray.getJSONObject(i);
                    
                    String pageId = pageData.getString("id");
                    String pageName = pageData.getString("name");
                    String pageToken = pageData.getString("access_token"); // This is the Page Access Token from /me/accounts
                    
                    logger.info("[ACCOUNT] >>> Page '{}': User token: {}..., Page token: {}...", 
                        pageName,
                        userAccessToken.substring(0, Math.min(10, userAccessToken.length())),
                        pageToken.substring(0, Math.min(10, pageToken.length())));
                    
                    // Check tasks to ensure user has required permissions
                    JSONArray tasks = pageData.optJSONArray("tasks");
                    boolean hasRequiredPermissions = false;
                    if (tasks != null) {
                        for (int j = 0; j < tasks.length(); j++) {
                            String task = tasks.getString(j);
                            if ("CREATE_CONTENT".equals(task) || "MANAGE".equals(task)) {
                                hasRequiredPermissions = true;
                                break;
                            }
                        }
                    }
                    
                    if (!hasRequiredPermissions) {
                        logger.warn("[ACCOUNT] >>> Page '{}' (ID: {}) - User doesn't have required permissions (CREATE_CONTENT or MANAGE)", 
                            pageName, pageId);
                        continue;
                    }
                    
                    // Store BOTH tokens: userAccessToken (for /me/accounts) and pageToken (for publishing)
                    storeAccountInJCR(siteKey, pageId, pageName, userAccessToken, pageToken);
                    connectedPages.add(pageName);
                    
                    logger.info("[ACCOUNT] >>> ✓ Connected Facebook Page: '{}' (ID: {})", pageName, pageId);
                }
                
            } else {
                // Read error response
                StringBuilder errorResponse = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        errorResponse.append(line);
                    }
                }
                logger.error("[ACCOUNT] >>> Facebook API error ({}): {}", responseCode, errorResponse.toString());
            }
            
            connection.disconnect();
            
        } catch (Exception e) {
            logger.error("[ACCOUNT] >>> Error connecting Facebook account", e);
            throw new RepositoryException("Failed to connect Facebook account: " + e.getMessage(), e);
        }
        
        logger.info("[ACCOUNT] >>> Successfully connected {} Facebook Page(s)", connectedPages.size());
        return connectedPages;
    }
    
    /**
     * Store Facebook Page credentials in JCR using existing socialnt:account node type.
     * @param userAccessToken The user's access token (for refreshing Page tokens via /me/accounts)
     * @param pageToken The Page-specific access token from /me/accounts response (used for publishing)
```
     */
    private void storeAccountInJCR(String siteKey, String pageId, String pageName, String userAccessToken, String pageToken) 
            throws RepositoryException {
        
        jcrTemplate.doExecuteWithSystemSession(session -> {
            try {
                // Ensure /sites/{site}/contents/social-accounts folder exists
                String accountsPath = String.format(ACCOUNTS_PATH, siteKey);
                Node accountsFolder;
                
                if (session.nodeExists(accountsPath)) {
                    accountsFolder = session.getNode(accountsPath);
                    logger.info("[ACCOUNT] >>> Found existing social-accounts folder at: {}", accountsPath);
                } else {
                    // Create the social-accounts folder under /sites/{site}/contents/
                    String contentsPath = "/sites/" + siteKey + "/contents";
                    if (!session.nodeExists(contentsPath)) {
                        logger.error("[ACCOUNT] >>> Contents folder not found at: {}", contentsPath);
                        throw new RepositoryException("Contents folder not found: " + contentsPath);
                    }
                    Node contentsNode = session.getNode(contentsPath);
                    accountsFolder = contentsNode.addNode("social-accounts", "jnt:contentFolder");
                    logger.info("[ACCOUNT] >>> Created social-accounts folder at: {}", accountsPath);
                }
                
                // Create or update socialnt:account node for this Facebook Page
                String accountNodeName = "facebook_" + pageId;
                Node accountNode;
                
                if (accountsFolder.hasNode(accountNodeName)) {
                    accountNode = accountsFolder.getNode(accountNodeName);
                    logger.info("[ACCOUNT] >>> Updating existing account node: {}", accountNodeName);
                } else {
                    accountNode = accountsFolder.addNode(accountNodeName, "socialnt:account");
                    logger.info("[ACCOUNT] >>> Created new account node: {}", accountNodeName);
                }
                
                // Set properties using existing socialnt:account fields
                accountNode.setProperty("social:platform", "facebook");
                accountNode.setProperty("social:label", pageName);
                accountNode.setProperty("social:handle", pageName); // Using handle for page name
                accountNode.setProperty("social:pageId", pageId);
                accountNode.setProperty("social:accessToken", userAccessToken); // User token (for /me/accounts refresh)
                accountNode.setProperty("social:pageToken", pageToken); // Page token (for publishing)
                accountNode.setProperty("social:isActive", true);
                
                // Set token expiry (Facebook long-lived tokens expire after 60 days)
                java.util.Calendar expiry = java.util.Calendar.getInstance();
                expiry.add(java.util.Calendar.DAY_OF_MONTH, 60);
                accountNode.setProperty("social:tokenExpiry", expiry);
                
                logger.info("[ACCOUNT] >>> Stored tokens - User token: {}... (len: {}), Page token: {}... (len: {})",
                    userAccessToken.substring(0, Math.min(10, userAccessToken.length())), userAccessToken.length(),
                    pageToken.substring(0, Math.min(10, pageToken.length())), pageToken.length());
                
                // Save the session
                session.save();
                logger.info("[ACCOUNT] >>> Saved Facebook account to JCR: {}/{}", accountsPath, accountNodeName);
                
            } catch (RepositoryException e) {
                logger.error("[ACCOUNT] >>> Error storing account in JCR", e);
                throw e;
            }
            return null;
        });
    }
    
    @Override
    public Map<String, Map<String, String>> getFacebookAccounts(String siteKey) throws RepositoryException {
        Map<String, Map<String, String>> accounts = new HashMap<>();
        
        logger.info("[ACCOUNT] >>> getFacebookAccounts called for siteKey: '{}'", siteKey);
        
        try {
            jcrTemplate.doExecuteWithSystemSession(session -> {
                String accountsPath = String.format(ACCOUNTS_PATH, siteKey);
                logger.info("[ACCOUNT] >>> Checking path: '{}'", accountsPath);
                
                if (!session.nodeExists(accountsPath)) {
                    logger.warn("[ACCOUNT] >>> No social-accounts folder found at: {}", accountsPath);
                    return null;
                }
                
                Node accountsFolder = session.getNode(accountsPath);
                NodeIterator nodes = accountsFolder.getNodes();
                
                long nodeCount = nodes.getSize();
                logger.info("[ACCOUNT] >>> Found {} nodes in social-accounts folder", nodeCount);
                
                while (nodes.hasNext()) {
                    Node accountNode = nodes.nextNode();
                    String nodeName = accountNode.getName();
                    String nodeType = accountNode.getPrimaryNodeType().getName();
                    logger.info("[ACCOUNT] >>> Checking node: {} (type: {})", nodeName, nodeType);
                    
                    if (accountNode.isNodeType("socialnt:account")) {
                        String platform = accountNode.getProperty("social:platform").getString();
                        logger.info("[ACCOUNT] >>> Node {} has platform: {}", nodeName, platform);
                        
                        if ("facebook".equals(platform) && accountNode.hasProperty("social:isActive") 
                                && accountNode.getProperty("social:isActive").getBoolean()) {
                            
                            Map<String, String> accountData = new HashMap<>();
                            accountData.put("pageId", accountNode.getProperty("social:pageId").getString());
                            accountData.put("pageName", accountNode.getProperty("social:handle").getString());
                            
                            logger.info("[ACCOUNT] >>> Facebook account {} has pageId: {}, pageName: {}", 
                                nodeName, 
                                accountData.get("pageId"), 
                                accountData.get("pageName"));
                            
                            // Get the Page token (used for publishing)
                            String pageToken = accountNode.hasProperty("social:pageToken") 
                                ? accountNode.getProperty("social:pageToken").getString()
                                : null;
                            
                            logger.info("[ACCOUNT] >>> Node {} has social:pageToken property: {}", 
                                nodeName, accountNode.hasProperty("social:pageToken"));
                            
                            // Fallback to accessToken if pageToken doesn't exist (backwards compatibility)
                            if (pageToken == null && accountNode.hasProperty("social:accessToken")) {
                                pageToken = accountNode.getProperty("social:accessToken").getString();
                                logger.warn("[ACCOUNT] >>> Account {} missing social:pageToken, using social:accessToken as fallback", nodeName);
                            }
                            
                            accountData.put("pageToken", pageToken);
                            
                            String pageId = accountData.get("pageId");
                            accounts.put(pageId, accountData);
                            
                            logger.info("[ACCOUNT] >>> ✓ Added Facebook account: {} (ID: {}) - Page Token: {}...{} (len: {})", 
                                accountData.get("pageName"), pageId,
                                pageToken != null && pageToken.length() > 10 ? pageToken.substring(0, 10) : "***",
                                pageToken != null && pageToken.length() > 10 ? pageToken.substring(pageToken.length() - 10) : "***",
                                pageToken != null ? pageToken.length() : 0);
                        }
                    }
                }
                
                logger.info("[ACCOUNT] >>> Total Facebook accounts found: {}", accounts.size());
                return null;
            });
        } catch (RepositoryException e) {
            logger.error("[ACCOUNT] >>> Error retrieving Facebook accounts for siteKey: {}", siteKey, e);
            throw e;
        }
        
        logger.info("[ACCOUNT] >>> Returning {} Facebook accounts for siteKey: {}", accounts.size(), siteKey);
        return accounts;
    }
    
    @Override
    public String getFacebookPageAccessToken(String siteKey, String pageId) throws RepositoryException {
        Map<String, Map<String, String>> accounts = getFacebookAccounts(siteKey);
        
        if (accounts.isEmpty()) {
            logger.info("[ACCOUNT] >>> No Facebook accounts found for site: {}", siteKey);
            return null;
        }
        
        // If pageId is null, return page token from first account
        if (pageId == null || pageId.isEmpty()) {
            Map.Entry<String, Map<String, String>> firstAccount = accounts.entrySet().iterator().next();
            String token = firstAccount.getValue().get("pageToken"); // Use pageToken instead of accessToken
            logger.info("[ACCOUNT] >>> Using page token from first Facebook account: {} (len: {})", 
                firstAccount.getValue().get("pageName"),
                token != null ? token.length() : 0);
            return token;
        }
        
        // Return page token for specific pageId
        Map<String, String> accountData = accounts.get(pageId);
        if (accountData != null) {
            String token = accountData.get("pageToken"); // Use pageToken instead of accessToken
            logger.info("[ACCOUNT] >>> Using page token for Page ID: {} (len: {})", pageId, 
                token != null ? token.length() : 0);
            return token;
        }
        
        logger.warn("[ACCOUNT] >>> No Facebook account found for Page ID: {}", pageId);
        return null;
    }
    
    @Override
    public void disconnectFacebookAccount(String siteKey, String pageId) throws RepositoryException {
        jcrTemplate.doExecuteWithSystemSession(session -> {
            try {
                String accountsPath = String.format(ACCOUNTS_PATH, siteKey);
                String accountNodePath = accountsPath + "/facebook_" + pageId;
                
                if (session.nodeExists(accountNodePath)) {
                    Node accountNode = session.getNode(accountNodePath);
                    String pageName = accountNode.getProperty("social:handle").getString();
                    
                    accountNode.remove();
                    session.save();
                    
                    logger.info("[ACCOUNT] >>> Disconnected Facebook Page: '{}' (ID: {})", pageName, pageId);
                } else {
                    logger.warn("[ACCOUNT] >>> Facebook account not found for Page ID: {}", pageId);
                }
            } catch (RepositoryException e) {
                logger.error("[ACCOUNT] >>> Error disconnecting Facebook account", e);
                throw e;
            }
            return null;
        });
    }
    
    @Override
    public void connectLinkedInAccount(String siteKey, String personId, String name, String email,
                                       String accessToken, int expiresIn) throws RepositoryException {
        jcrTemplate.doExecuteWithSystemSession(session -> {
            try {
                // Ensure social-accounts folder exists
                String accountsPath = String.format(ACCOUNTS_PATH, siteKey);
                Node accountsFolder;
                if (session.nodeExists(accountsPath)) {
                    accountsFolder = session.getNode(accountsPath);
                } else {
                    // Create folder structure
                    String sitePath = "/sites/" + siteKey;
                    if (!session.nodeExists(sitePath)) {
                        logger.error("[ACCOUNT] >>> Site path does not exist: {}", sitePath);
                        throw new RepositoryException("Site not found: " + siteKey);
                    }
                    
                    Node siteNode = session.getNode(sitePath);
                    Node contentsNode = siteNode.hasNode("contents") 
                        ? siteNode.getNode("contents")
                        : siteNode.addNode("contents", "jnt:contentFolder");
                    accountsFolder = contentsNode.addNode("social-accounts", "jnt:contentFolder");
                }
                
                // Create or update LinkedIn account node
                String accountNodeName = "linkedin_" + personId;
                Node accountNode;
                
                if (accountsFolder.hasNode(accountNodeName)) {
                    accountNode = accountsFolder.getNode(accountNodeName);
                    logger.info("[ACCOUNT] >>> Updating existing LinkedIn account: {}", personId);
                } else {
                    accountNode = accountsFolder.addNode(accountNodeName, "socialnt:account");
                    logger.info("[ACCOUNT] >>> Creating new LinkedIn account: {}", personId);
                }
                
                // Set account properties
                accountNode.setProperty("social:platform", "linkedin");
                accountNode.setProperty("social:accountId", personId);
                accountNode.setProperty("social:label", name);
                accountNode.setProperty("social:handle", name);
                accountNode.setProperty("social:accessToken", accessToken);
                
                if (email != null && !email.isEmpty()) {
                    accountNode.setProperty("social:email", email);
                }
                
                // Calculate token expiry
                Calendar expiry = Calendar.getInstance();
                expiry.add(Calendar.SECOND, expiresIn);
                accountNode.setProperty("social:tokenExpiry", expiry);
                
                session.save();
                
                logger.info("[ACCOUNT] >>> LinkedIn account stored: name='{}', personId='{}', expires={}", 
                    name, personId, expiry.getTime());
                
            } catch (RepositoryException e) {
                logger.error("[ACCOUNT] >>> Error storing LinkedIn account", e);
                throw e;
            }
            return null;
        });
    }
    
    @Override
    public Map<String, Map<String, String>> getLinkedInAccounts(String siteKey) throws RepositoryException {
        return jcrTemplate.doExecuteWithSystemSession(session -> {
            Map<String, Map<String, String>> accounts = new java.util.HashMap<>();
            
            try {
                String accountsPath = String.format(ACCOUNTS_PATH, siteKey);
                
                if (!session.nodeExists(accountsPath)) {
                    logger.debug("[ACCOUNT] >>> No accounts folder found for site: {}", siteKey);
                    return accounts;
                }
                
                Node accountsFolder = session.getNode(accountsPath);
                javax.jcr.NodeIterator nodes = accountsFolder.getNodes("linkedin_*");
                
                while (nodes.hasNext()) {
                    Node accountNode = nodes.nextNode();
                    
                    if (accountNode.hasProperty("social:platform") && 
                        "linkedin".equals(accountNode.getProperty("social:platform").getString())) {
                        
                        String personId = accountNode.getProperty("social:accountId").getString();
                        String name = accountNode.getProperty("social:label").getString();
                        String accessToken = accountNode.getProperty("social:accessToken").getString();
                        String email = accountNode.hasProperty("social:email") 
                            ? accountNode.getProperty("social:email").getString() : "";
                        
                        Map<String, String> accountData = new java.util.HashMap<>();
                        accountData.put("personId", personId);
                        accountData.put("name", name);
                        accountData.put("email", email);
                        accountData.put("accessToken", accessToken);
                        
                        accounts.put(personId, accountData);
                        
                        logger.debug("[ACCOUNT] >>> Found LinkedIn account: name='{}', personId='{}'", 
                            name, personId);
                    }
                }
                
            } catch (RepositoryException e) {
                logger.error("[ACCOUNT] >>> Error retrieving LinkedIn accounts", e);
                throw e;
            }
            
            return accounts;
        });
    }
    
    @Override
    public void disconnectLinkedInAccount(String siteKey, String personId) throws RepositoryException {
        jcrTemplate.doExecuteWithSystemSession(session -> {
            try {
                String accountsPath = String.format(ACCOUNTS_PATH, siteKey);
                String accountNodePath = accountsPath + "/linkedin_" + personId;
                
                if (session.nodeExists(accountNodePath)) {
                    Node accountNode = session.getNode(accountNodePath);
                    String name = accountNode.getProperty("social:label").getString();
                    
                    accountNode.remove();
                    session.save();
                    
                    logger.info("[ACCOUNT] >>> Disconnected LinkedIn account: '{}' (ID: {})", name, personId);
                } else {
                    logger.warn("[ACCOUNT] >>> LinkedIn account not found for person ID: {}", personId);
                }
            } catch (RepositoryException e) {
                logger.error("[ACCOUNT] >>> Error disconnecting LinkedIn account", e);
                throw e;
            }
            return null;
        });
    }
}
