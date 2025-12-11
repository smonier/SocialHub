package org.example.socialhub.servlet;

import org.apache.commons.codec.binary.Hex;
import org.example.socialhub.service.SocialAccountService;
import org.jahia.bin.filters.AbstractServletFilter;
import org.jahia.services.content.JCRTemplate;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Map;
import java.util.UUID;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.jcr.Node;
import javax.jcr.RepositoryException;

/**
 * OAuth Callback Servlet for Social Media Platforms
 * 
 * Handles OAuth callbacks from:
 * - Facebook: /modules/SocialHub/oauth/facebook/callback
 * - Instagram: /modules/SocialHub/oauth/instagram/callback
 * - LinkedIn: /modules/SocialHub/oauth/linkedin/callback
 * 
 * This servlet receives the authorization code from the OAuth provider
 * and exchanges it for an access token.
 */
@Component(
    service = AbstractServletFilter.class,
    configurationPid = "org.example.socialhub.servlet.SocialOAuthCallbackServlet"
)
public class SocialOAuthCallbackServlet extends AbstractServletFilter {
    
    private static final Logger logger = LoggerFactory.getLogger(SocialOAuthCallbackServlet.class);

    // Provider configuration (loaded from OSGi config)
    private String facebookAppId;
    private String facebookRedirectUri;
    private String facebookAppSecret;
    private String facebookScopes = "pages_show_list,pages_manage_posts,pages_read_engagement";

    private String instagramAppId;
    private String instagramRedirectUri;
    private String instagramAppSecret;
    private String instagramScopes = "instagram_basic,instagram_content_publish,pages_show_list";

    private String linkedinClientId;
    private String linkedinRedirectUri;
    private String linkedinClientSecret;
    // LinkedIn OAuth scopes:
    // - openid: Required for OIDC authentication
    // - profile: Required to retrieve member's id, name, and profile picture
    // - email: Required to retrieve member's email address
    // - w_member_social: Required to create posts on behalf of the authenticated member
    // - r_organization_social: Read organization posts
    // - rw_organization_admin: Manage organization posts
    // - offline_access: Refresh tokens for long-lived access
    private String linkedinScopes = "openid,profile,email,w_member_social,r_organization_social,rw_organization_admin,offline_access";

    @Reference
    private JCRTemplate jcrTemplate;
    
    @Reference
    private SocialAccountService socialAccountService;

    @Activate
    public void activate(Map<String, String> config) {
        logger.info("Activating SocialOAuthCallbackServlet with /modules/SocialHub/oauth/*");
        setUrlPatterns(new String[]{"/modules/SocialHub/oauth/*"});

        facebookAppId = config.get("facebook.appId");
        facebookRedirectUri = config.get("facebook.redirectUri");
        facebookAppSecret = config.get("facebook.appSecret");
        facebookScopes = config.getOrDefault("facebook.scopes", facebookScopes);

        instagramAppId = config.get("instagram.appId");
        instagramRedirectUri = config.get("instagram.redirectUri");
        instagramAppSecret = config.get("instagram.appSecret");
        instagramScopes = config.getOrDefault("instagram.scopes", instagramScopes);

        // LinkedIn config properties (support both formats)
        linkedinClientId = config.get("linkedinClientId");
        if (linkedinClientId == null) linkedinClientId = config.get("linkedin.clientId");
        
        linkedinRedirectUri = config.get("linkedinRedirectUri");
        if (linkedinRedirectUri == null) linkedinRedirectUri = config.get("linkedin.redirectUri");
        
        linkedinClientSecret = config.get("linkedinClientSecret");
        if (linkedinClientSecret == null) linkedinClientSecret = config.get("linkedin.clientSecret");
        
        linkedinScopes = config.getOrDefault("linkedinScopes", 
            config.getOrDefault("linkedin.scopes", linkedinScopes));
        
        logger.info("[OAuth] LinkedIn config loaded - ClientId: {}, RedirectUri: {}", 
            linkedinClientId != null ? "***" + linkedinClientId.substring(Math.max(0, linkedinClientId.length() - 4)) : "null",
            linkedinRedirectUri);
    }

    @Override
    public void init(FilterConfig filterConfig) {
        logger.debug("Initializing SocialOAuthCallbackServlet");
    }

    @Override
    public void destroy() {
        logger.debug("Destroying SocialOAuthCallbackServlet");
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, 
                         FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        
        // Use getRequestURI() instead of getPathInfo() for filters
        String requestURI = request.getRequestURI();
        String contextPath = request.getContextPath();
        String pathInfo = requestURI.replace(contextPath, "");
        
        logger.info("OAuth callback received - URI: {}, Context: {}, Path: {}", 
                   requestURI, contextPath, pathInfo);
        
        // Extract the platform and action from the path (e.g., /modules/SocialHub/oauth/facebook/callback)
        PathInfo parsedPath = parsePath(pathInfo);
        
        if (parsedPath == null || parsedPath.platform == null) {
            // Return 200 with error page (Facebook requires 200 for validation)
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("text/html");
            response.getWriter().write(generateErrorPage("OAuth", "Invalid callback path", 
                "The OAuth callback URL is malformed. Expected format: /modules/SocialHub/oauth/{platform}/{action}"));
            return;
        }
        
        // Handle different callback types
        try {
            switch (parsedPath.action) {
                case "start":
                    handleOAuthStart(parsedPath.platform, request, response);
                    break;
                case "callback":
                    handleOAuthCallback(parsedPath.platform, request, response);
                    break;
                case "uninstall":
                    handleUninstallCallback(parsedPath.platform, request, response);
                    break;
                case "delete":
                    handleDeleteCallback(parsedPath.platform, request, response);
                    break;
                default:
                    // Return 200 with error page for unknown actions
                    response.setStatus(HttpServletResponse.SC_OK);
                    response.setContentType("text/html");
                    response.getWriter().write(generateErrorPage("OAuth", 
                        "Unsupported action: " + parsedPath.action,
                        "Valid actions are: start, callback, uninstall, delete"));
            }
        } catch (Exception e) {
            logger.error("Error processing {} callback for {}", parsedPath.action, parsedPath.platform, e);
            // Return 200 with error page (not 500)
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("text/html");
            response.getWriter().write(generateErrorPage("OAuth", 
                "Internal Error", 
                "Failed to process callback: " + e.getMessage()));
        }
    }
    
    /**
     * Initiates the OAuth flow for a given platform by redirecting to the provider's auth URL.
     */
    private void handleOAuthStart(String platform, HttpServletRequest request,
                                  HttpServletResponse response) throws IOException {
        String state = request.getParameter("state");
        String site = request.getParameter("site");
        if (state == null || state.isEmpty()) {
            // Include site in state when provided so the callback can use it later.
            state = site != null ? "site:" + site : UUID.randomUUID().toString();
        }

        switch (platform.toLowerCase()) {
            case "facebook": {
                if (facebookAppId == null || facebookAppId.isEmpty()) {
                    response.setStatus(HttpServletResponse.SC_OK);
                    response.setContentType("text/html");
                    response.getWriter().write(generateErrorPage("Facebook",
                        "Missing configuration",
                        "facebook.appId is not configured for SocialHub. Set it in the OSGi config and retry."));
                    return;
                }

                String redirectUri = resolveRedirectUri(facebookRedirectUri, request, "facebook");
                String authUrl = "https://www.facebook.com/v20.0/dialog/oauth"
                    + "?client_id=" + urlEncode(facebookAppId)
                    + "&redirect_uri=" + urlEncode(redirectUri)
                    + "&state=" + urlEncode(state)
                    + "&scope=" + urlEncode(facebookScopes);

                logger.info("Redirecting to Facebook OAuth: {}", authUrl);
                response.sendRedirect(authUrl);
                return;
            }
            case "instagram": {
                if (instagramAppId == null || instagramAppId.isEmpty()) {
                    response.setStatus(HttpServletResponse.SC_OK);
                    response.setContentType("text/html");
                    response.getWriter().write(generateErrorPage("Instagram",
                        "Missing configuration",
                        "instagram.appId is not configured for SocialHub. Set it in the OSGi config and retry."));
                    return;
                }

                String redirectUri = resolveRedirectUri(instagramRedirectUri, request, "instagram");
                String authUrl = "https://api.instagram.com/oauth/authorize"
                    + "?client_id=" + urlEncode(instagramAppId)
                    + "&redirect_uri=" + urlEncode(redirectUri)
                    + "&state=" + urlEncode(state)
                    + "&scope=" + urlEncode(instagramScopes)
                    + "&response_type=code";

                logger.info("Redirecting to Instagram OAuth: {}", authUrl);
                response.sendRedirect(authUrl);
                return;
            }
            case "linkedin": {
                if (linkedinClientId == null || linkedinClientId.isEmpty()) {
                    response.setStatus(HttpServletResponse.SC_OK);
                    response.setContentType("text/html");
                    response.getWriter().write(generateErrorPage("LinkedIn",
                        "Missing configuration",
                        "linkedin.clientId is not configured for SocialHub. Set it in the OSGi config and retry."));
                    return;
                }

                String redirectUri = resolveRedirectUri(linkedinRedirectUri, request, "linkedin");
                
                // For LinkedIn token exchange, we need to use the exact same redirect URI
                // Store it in the state parameter so callback can use it
                String stateWithRedirect = state + "|redirect:" + urlEncode(redirectUri);
                
                String authUrl = "https://www.linkedin.com/oauth/v2/authorization"
                    + "?response_type=code"
                    + "&client_id=" + urlEncode(linkedinClientId)
                    + "&redirect_uri=" + urlEncode(redirectUri)
                    + "&scope=" + urlEncode(linkedinScopes)
                    + "&state=" + urlEncode(stateWithRedirect);

                logger.info("Redirecting to LinkedIn OAuth: {}", authUrl);
                response.sendRedirect(authUrl);
                return;
            }
            default:
                response.setStatus(HttpServletResponse.SC_OK);
                response.setContentType("text/html");
                response.getWriter().write(generateErrorPage(platform,
                    "Unsupported platform: " + platform,
                    "Valid platforms are: facebook, instagram, linkedin"));
        }
    }

    /**
     * Handle OAuth authorization callback
     */
    private void handleOAuthCallback(String platform, HttpServletRequest request, 
                                     HttpServletResponse response) throws IOException {
        // Get OAuth parameters
        String code = request.getParameter("code");
        String state = request.getParameter("state");
        String error = request.getParameter("error");
        String errorDescription = request.getParameter("error_description");
        
        // Handle OAuth error responses
        if (error != null) {
            logger.error("OAuth error from {}: {} - {}", platform, error, errorDescription);
            response.setContentType("text/html");
            response.getWriter().write(generateErrorPage(platform, error, errorDescription));
            return;
        }
        
        // Validate authorization code
        if (code == null || code.isEmpty()) {
            // Return 200 with error page
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("text/html");
            response.getWriter().write(generateErrorPage(platform, 
                "Missing authorization code", 
                "The OAuth provider did not return an authorization code."));
            return;
        }
        
        logger.info("Received OAuth callback for {} with code: {}", platform, 
                   code.substring(0, Math.min(10, code.length())) + "...");
        
        // Process based on platform
        switch (platform.toLowerCase()) {
            case "facebook":
                handleFacebookCallback(code, state, request, response);
                break;
            case "instagram":
                handleInstagramCallback(code, state, request, response);
                break;
            case "linkedin":
                handleLinkedInCallback(code, state, request, response);
                break;
            default:
                // Return 200 with error page
                response.setStatus(HttpServletResponse.SC_OK);
                response.setContentType("text/html");
                response.getWriter().write(generateErrorPage(platform, 
                "Unsupported platform: " + platform,
                "Valid platforms are: facebook, instagram, linkedin"));
        }
    }

    /**
     * Persist a socialnt:account node for the given site and platform.
     */
    private void persistAccount(String siteKey, String platform, String label, String handle,
                                String pageId, String accessToken, String refreshToken, Calendar expiry) throws RepositoryException {
        String folderPath = "/sites/" + siteKey + "/contents/social-accounts";

        jcrTemplate.doExecuteWithSystemSession(session -> {
            Node folder;
            if (session.itemExists(folderPath)) {
                folder = (Node) session.getItem(folderPath);
            } else {
                // Create folder if it does not exist
                String contentsPath = "/sites/" + siteKey + "/contents";
                if (!session.itemExists(contentsPath)) {
                    throw new RepositoryException("Contents folder does not exist for site: " + siteKey);
                }
                Node contents = (Node) session.getItem(contentsPath);
                folder = contents.addNode("social-accounts", "jnt:contentFolder");
            }

            String nodeName = platform + "-" + System.currentTimeMillis();
            Node account = folder.addNode(nodeName, "socialnt:account");

            account.setProperty("social:platform", platform);
            if (label != null) {
                account.setProperty("social:label", label);
            }
            if (handle != null) {
                account.setProperty("social:handle", handle);
            }
            if (pageId != null) {
                account.setProperty("social:pageId", pageId);
            }
            if (accessToken != null) {
                account.setProperty("social:accessToken", accessToken);
            }
            if (refreshToken != null) {
                account.setProperty("social:refreshToken", refreshToken);
            }
            if (expiry != null) {
                account.setProperty("social:tokenExpiry", expiry);
            }
            account.setProperty("social:isActive", true);

            session.save();
            return null;
        });
    }

    private String extractSiteFromState(String state) {
        if (state == null) {
            return null;
        }
        if (state.startsWith("site:")) {
            return state.substring("site:".length());
        }
        return null;
    }

    /**
     * Basic HTTP GET helper returning the response body as a string, or null on failure.
     */
    private String httpGet(String url) {
        HttpURLConnection connection = null;
        try {
            URL target = new URL(url);
            connection = (HttpURLConnection) target.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(20000);

            int status = connection.getResponseCode();
            java.io.InputStream stream = status >= 200 && status < 400 ? connection.getInputStream() : connection.getErrorStream();
            if (stream == null) {
                return null;
            }
            try (java.util.Scanner scanner = new java.util.Scanner(stream, StandardCharsets.UTF_8.name())) {
                scanner.useDelimiter("\\A");
                return scanner.hasNext() ? scanner.next() : null;
            }
        } catch (IOException e) {
            logger.error("HTTP GET failed for {}", url, e);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String extractJsonString(String json, String key) {
        if (json == null || key == null) {
            return null;
        }
        String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]+)\"";
        Matcher m = Pattern.compile(pattern).matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private String extractJsonNumberAsString(String json, String key) {
        if (json == null || key == null) {
            return null;
        }
        String pattern = "\"" + key + "\"\\s*:\\s*([0-9]+)";
        Matcher m = Pattern.compile(pattern).matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    /**
     * Generates Facebook appsecret_proof for secured Graph API calls.
     */
    private String generateAppSecretProof(String accessToken, String appSecret) {
        try {
            SecretKeySpec key = new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            byte[] result = mac.doFinal(accessToken.getBytes(StandardCharsets.UTF_8));
            return Hex.encodeHexString(result);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate appsecret_proof", e);
        }
    }

    /**
     * Returns the configured redirect URI or derives it from the current request.
     */
    private String resolveRedirectUri(String configuredRedirectUri, HttpServletRequest request, String platform) {
        if (configuredRedirectUri != null && !configuredRedirectUri.isEmpty()) {
            // If it's a relative path, build full URL from request
            if (configuredRedirectUri.startsWith("/")) {
                String scheme = request.getScheme();
                String serverName = request.getServerName();
                int serverPort = request.getServerPort();
                
                StringBuilder fullUrl = new StringBuilder();
                fullUrl.append(scheme).append("://").append(serverName);
                
                // Only add port if it's not the default for the scheme
                if ((scheme.equals("http") && serverPort != 80) || 
                    (scheme.equals("https") && serverPort != 443)) {
                    fullUrl.append(":").append(serverPort);
                }
                
                fullUrl.append(configuredRedirectUri);
                
                String resolvedUri = fullUrl.toString();
                logger.info("Resolved {} redirect URI: {}", platform, resolvedUri);
                return resolvedUri;
            }
            
            // Already a full URL
            return configuredRedirectUri;
        }

        // Derive callback URL from the current request URL by replacing /start with /callback
        String current = request.getRequestURL().toString();
        String fallback = current.replace("/start", "/callback");
        logger.debug("Derived redirect URI for {}: {}", platform, fallback);
        return fallback;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
    
    /**
     * Handle app uninstall callback (Facebook sends this when user uninstalls the app)
     */
    private void handleUninstallCallback(String platform, HttpServletRequest request, 
                                        HttpServletResponse response) throws IOException {
        logger.info("Received uninstall callback for {}", platform);
        
        // TODO: Process uninstall data
        // - Parse the signed_request parameter
        // - Verify signature
        // - Extract user ID
        // - Remove user's tokens from JCR
        
        String signedRequest = request.getParameter("signed_request");
        if (signedRequest != null) {
            logger.info("Signed request received for uninstall: {}", 
                       signedRequest.substring(0, Math.min(20, signedRequest.length())) + "...");
        }
        
        // Return success response
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json");
        response.getWriter().write("{\"success\": true}");
    }
    
    /**
     * Handle data deletion callback (GDPR compliance - Facebook sends this for data deletion requests)
     */
    private void handleDeleteCallback(String platform, HttpServletRequest request, 
                                     HttpServletResponse response) throws IOException {
        logger.info("Received delete callback for {}", platform);
        
        // TODO: Process data deletion request
        // - Parse the signed_request parameter
        // - Verify signature
        // - Extract user ID
        // - Delete all user data from JCR
        // - Return confirmation URL and code
        
        String signedRequest = request.getParameter("signed_request");
        if (signedRequest != null) {
            logger.info("Signed request received for deletion: {}", 
                       signedRequest.substring(0, Math.min(20, signedRequest.length())) + "...");
        }
        
        // Return confirmation response (required by Facebook)
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json");
        
        // Generate a unique confirmation code
        String confirmationCode = "deletion-" + System.currentTimeMillis();
        String statusUrl = request.getRequestURL().toString().replace("/delete", "/status/" + confirmationCode);
        
        response.getWriter().write(String.format(
            "{\"url\": \"%s\", \"confirmation_code\": \"%s\"}", 
            statusUrl, confirmationCode
        ));
    }
    
    /**
     * Parse path info to extract platform and action
     * Path format: /modules/SocialHub/oauth/facebook/callback
     */
    private PathInfo parsePath(String pathInfo) {
        if (pathInfo == null || pathInfo.isEmpty()) {
            return null;
        }
        
        // Remove leading slash if present
        if (pathInfo.startsWith("/")) {
            pathInfo = pathInfo.substring(1);
        }
        
        String[] parts = pathInfo.split("/");
        // Expected: ["modules", "SocialHub", "oauth", "facebook", "callback"]
        // We need parts[3] (platform) and parts[4] (action)
        if (parts.length >= 5) {
            return new PathInfo(parts[3], parts[4]);
        } else if (parts.length >= 4 && "oauth".equals(parts[2])) {
            // Default to callback if only platform is specified
            return new PathInfo(parts[3], "callback");
        }
        
        return null;
    }
    
    /**
     * Helper class to hold parsed path information
     */
    private static class PathInfo {
        String platform;
        String action;
        
        PathInfo(String platform, String action) {
            this.platform = platform;
            this.action = action;
        }
    }
    
    /**
     * Extract platform name from path (kept for backward compatibility)
     */
    private String extractPlatform(String pathInfo) {
        PathInfo parsed = parsePath(pathInfo);
        return parsed != null ? parsed.platform : null;
    }
    
    /**
     * Handle Facebook OAuth callback
     */
    private void handleFacebookCallback(String code, String state, 
                                       HttpServletRequest request, 
                                       HttpServletResponse response) throws IOException {
        logger.info("Processing Facebook OAuth callback");
        
        if (facebookAppId == null || facebookAppSecret == null || facebookAppId.isEmpty() || facebookAppSecret.isEmpty()) {
            response.setContentType("text/html");
            response.getWriter().write(generateErrorPage("Facebook",
                "Missing configuration",
                "facebook.appId or facebook.appSecret is not configured."));
            return;
        }

        // 1. Exchange code for short-lived token
        String redirectUri = resolveRedirectUri(facebookRedirectUri, request, "facebook");
        String shortTokenUrl = "https://graph.facebook.com/v20.0/oauth/access_token"
            + "?client_id=" + urlEncode(facebookAppId)
            + "&redirect_uri=" + urlEncode(redirectUri)
            + "&client_secret=" + urlEncode(facebookAppSecret)
            + "&code=" + urlEncode(code);

        String shortTokenResponse = httpGet(shortTokenUrl);
        if (shortTokenResponse == null) {
            response.setContentType("text/html");
            response.getWriter().write(generateErrorPage("Facebook",
                "Token exchange failed",
                "Could not retrieve short-lived token."));
            return;
        }

        String shortToken = extractJsonString(shortTokenResponse, "access_token");
        String expiresInStr = extractJsonNumberAsString(shortTokenResponse, "expires_in");
        if (shortToken == null || shortToken.isEmpty()) {
            response.setContentType("text/html");
            response.getWriter().write(generateErrorPage("Facebook",
                "Token exchange failed",
                "Facebook did not return a short-lived access_token."));
            return;
        }

        // 2. Exchange for long-lived token
        String longTokenUrl = "https://graph.facebook.com/v20.0/oauth/access_token"
            + "?grant_type=fb_exchange_token"
            + "&client_id=" + urlEncode(facebookAppId)
            + "&client_secret=" + urlEncode(facebookAppSecret)
            + "&fb_exchange_token=" + urlEncode(shortToken);

        String longTokenResponse = httpGet(longTokenUrl);
        if (longTokenResponse == null) {
            response.setContentType("text/html");
            response.getWriter().write(generateErrorPage("Facebook",
                "Token exchange failed",
                "Could not retrieve long-lived token."));
            return;
        }

        String longToken = extractJsonString(longTokenResponse, "access_token");
        String longExpiresInStr = extractJsonNumberAsString(longTokenResponse, "expires_in");
        if (longToken == null || longToken.isEmpty()) {
            logger.warn("Long-lived token missing; will fall back to short-lived token.");
        }

        // 3. Persist account in JCR (as a user-level token; page selection can be added later)
        String siteKey = extractSiteFromState(state);
        if (siteKey == null || siteKey.isEmpty()) {
            siteKey = request.getParameter("site");
        }
        if (siteKey == null || siteKey.isEmpty()) {
            siteKey = "digitall";
        }

        Calendar expiry = null;
        try {
            if (longExpiresInStr != null) {
                long seconds = Long.parseLong(longExpiresInStr);
                expiry = Calendar.getInstance();
                expiry.add(Calendar.SECOND, (int) seconds);
            } else if (expiresInStr != null) {
                long seconds = Long.parseLong(expiresInStr);
                expiry = Calendar.getInstance();
                expiry.add(Calendar.SECOND, (int) seconds);
            }
        } catch (NumberFormatException e) {
            logger.warn("Unable to parse expires_in: {}", e.getMessage());
        }

        try {
            // Use the user token to call /me/accounts and store page tokens
            String userToken = longToken != null ? longToken : shortToken;
            logger.info("[SERVLET] >>> Calling connectFacebookAccount with user token for site: {}", siteKey);
            socialAccountService.connectFacebookAccount(userToken, siteKey);
            logger.info("[SERVLET] >>> Successfully connected Facebook account(s)");
        } catch (RepositoryException e) {
            logger.error("Failed to connect Facebook account", e);
            response.setContentType("text/html");
            response.getWriter().write(generateErrorPage("Facebook",
                "Storage error",
                "Failed to store account in JCR: " + e.getMessage()));
            return;
        }

        // 4. Redirect to success page
        response.setContentType("text/html");
        response.getWriter().write(generateSuccessPage("Facebook",
            "Facebook account(s) connected successfully! You can now close this window."));
    }
    
    /**
     * Handle Instagram OAuth callback
     */
    private void handleInstagramCallback(String code, String state, 
                                        HttpServletRequest request, 
                                        HttpServletResponse response) throws IOException {
        logger.info("Processing Instagram OAuth callback");
        
        // TODO: Implement Instagram token exchange
        
        response.setContentType("text/html");
        response.getWriter().write(generateSuccessPage("Instagram", 
            "Instagram account connected successfully!"));
    }
    
    /**
     * Handle LinkedIn OAuth callback
     * 
     * LinkedIn OAuth 2.0 flow:
     * 1. Exchange authorization code for access token
     * 2. Get user profile information
     * 3. Store account credentials in JCR
     */
    private void handleLinkedInCallback(String code, String state, 
                                       HttpServletRequest request, 
                                       HttpServletResponse response) throws IOException {
        logger.info("[OAuth] Processing LinkedIn OAuth callback");
        
        try {
            // Extract redirect URI from state (format: "state|redirect:encoded_uri")
            String redirectUriToUse = linkedinRedirectUri;
            if (state != null && state.contains("|redirect:")) {
                String[] stateParts = state.split("\\|redirect:", 2);
                if (stateParts.length == 2) {
                    redirectUriToUse = java.net.URLDecoder.decode(stateParts[1], "UTF-8");
                    state = stateParts[0]; // Update state to original value without redirect
                }
            }
            
            // If redirect URI is relative, resolve it from request
            if (redirectUriToUse != null && redirectUriToUse.startsWith("/")) {
                redirectUriToUse = resolveRedirectUri(redirectUriToUse, request, "linkedin");
            }
            
            logger.info("[OAuth] Using redirect URI for token exchange: {}", redirectUriToUse);
            
            // Step 1: Exchange authorization code for access token
            String tokenUrl = "https://www.linkedin.com/oauth/v2/accessToken";
            String postData = String.format(
                "grant_type=authorization_code&code=%s&client_id=%s&client_secret=%s&redirect_uri=%s",
                urlEncode(code),
                urlEncode(linkedinClientId),
                urlEncode(linkedinClientSecret),
                urlEncode(redirectUriToUse)
            );
            
            logger.info("[OAuth] Exchanging LinkedIn authorization code for access token");
            
            HttpURLConnection connection = (HttpURLConnection) new URL(tokenUrl).openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setDoOutput(true);
            connection.getOutputStream().write(postData.getBytes(StandardCharsets.UTF_8));
            
            int responseCode = connection.getResponseCode();
            InputStream stream = responseCode >= 200 && responseCode < 400 ? 
                connection.getInputStream() : connection.getErrorStream();
            String responseBody = null;
            if (stream != null) {
                try (Scanner scanner = new Scanner(stream, StandardCharsets.UTF_8.name())) {
                    scanner.useDelimiter("\\A");
                    responseBody = scanner.hasNext() ? scanner.next() : null;
                }
            }
            
            logger.info("[OAuth] LinkedIn token exchange response code: {}", responseCode);
            
            if (responseCode != 200) {
                logger.error("[OAuth] LinkedIn token exchange failed: {}", responseBody);
                response.setContentType("text/html");
                response.getWriter().write(generateErrorPage("LinkedIn", 
                    "Token Exchange Failed", 
                    "Failed to exchange authorization code for access token"));
                return;
            }
            
            // Parse access token from response
            org.json.JSONObject tokenData = new org.json.JSONObject(responseBody);
            String accessToken = tokenData.getString("access_token");
            int expiresIn = tokenData.optInt("expires_in", 5184000); // Default 60 days
            
            logger.info("[OAuth] LinkedIn access token obtained, expires in: {} seconds", expiresIn);
            
            // Step 2: Get user profile information
            String profileUrl = "https://api.linkedin.com/v2/userinfo";
            HttpURLConnection profileConnection = (HttpURLConnection) new URL(profileUrl).openConnection();
            profileConnection.setRequestMethod("GET");
            profileConnection.setRequestProperty("Authorization", "Bearer " + accessToken);
            
            int profileResponseCode = profileConnection.getResponseCode();
            InputStream profileStream = profileResponseCode >= 200 && profileResponseCode < 400 ? 
                profileConnection.getInputStream() : profileConnection.getErrorStream();
            String profileResponseBody = null;
            if (profileStream != null) {
                try (Scanner profileScanner = new Scanner(profileStream, StandardCharsets.UTF_8.name())) {
                    profileScanner.useDelimiter("\\A");
                    profileResponseBody = profileScanner.hasNext() ? profileScanner.next() : null;
                }
            }
            
            logger.info("[OAuth] LinkedIn profile response code: {}", profileResponseCode);
            
            if (profileResponseCode != 200) {
                logger.error("[OAuth] LinkedIn profile fetch failed: {}", profileResponseBody);
                response.setContentType("text/html");
                response.getWriter().write(generateErrorPage("LinkedIn", 
                    "Profile Fetch Failed", 
                    "Failed to fetch user profile information"));
                return;
            }
            
            org.json.JSONObject profileData = new org.json.JSONObject(profileResponseBody);
            
            // The 'sub' field contains the unique person identifier
            // This will be used as "urn:li:person:{sub}" for posting via ugcPosts API
            String personId = profileData.optString("sub", "unknown");
            String name = profileData.optString("name", "LinkedIn User");
            String givenName = profileData.optString("given_name", "");
            String familyName = profileData.optString("family_name", "");
            String email = profileData.optString("email", "");
            String picture = profileData.optString("picture", "");
            
            logger.info("[OAuth] LinkedIn profile: personId={}, name={}, email={}", personId, name, email);
            
            // Step 3: Store credentials in JCR (similar to Facebook)
            // Extract siteKey from state parameter (format: "state_{siteKey}_{random}")
            String siteKey = "digitall"; // default
            if (state != null && state.startsWith("state_")) {
                String[] parts = state.split("_");
                if (parts.length >= 2) {
                    siteKey = parts[1];
                }
            } else if (state != null && state.startsWith("site:")) {
                siteKey = state.substring("site:".length());
            }
            
            // Step 4: Store LinkedIn account in JCR
            logger.info("[OAuth] Storing LinkedIn account for site: {}", siteKey);
            
            try {
                socialAccountService.connectLinkedInAccount(
                    siteKey, personId, name, email, accessToken, expiresIn
                );
                logger.info("[OAuth] LinkedIn account stored successfully");
            } catch (RepositoryException e) {
                logger.error("[OAuth] Failed to store LinkedIn account in JCR", e);
                response.setContentType("text/html");
                response.getWriter().write(generateErrorPage("LinkedIn", 
                    "Storage Error", 
                    "Failed to save LinkedIn account: " + e.getMessage()));
                return;
            }
            
            // Return success page
            response.setContentType("text/html");
            response.getWriter().write(generateSuccessPage("LinkedIn", 
                "LinkedIn account (" + name + ") connected successfully!"));
            
        } catch (Exception e) {
            logger.error("[OAuth] Error processing LinkedIn callback", e);
            response.setContentType("text/html");
            response.getWriter().write(generateErrorPage("LinkedIn", 
                "Connection Error", 
                "An error occurred while connecting your LinkedIn account: " + e.getMessage()));
        }
    }
    
    /**
     * Generate HTML success page
     */
    private String generateSuccessPage(String platform, String message) {
        return "<!DOCTYPE html>" +
               "<html>" +
               "<head>" +
               "    <title>" + platform + " OAuth - Success</title>" +
               "    <style>" +
               "        body { font-family: Arial, sans-serif; display: flex; justify-content: center; align-items: center; height: 100vh; margin: 0; background: #f5f5f5; }" +
               "        .container { text-align: center; padding: 40px; background: white; border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }" +
               "        .success { color: #2e7d32; font-size: 24px; margin-bottom: 20px; }" +
               "        .message { color: #666; margin-bottom: 30px; }" +
               "        .close-btn { padding: 10px 20px; background: #007cb0; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 16px; }" +
               "        .close-btn:hover { background: #005f85; }" +
               "    </style>" +
               "</head>" +
               "<body>" +
               "    <div class='container'>" +
               "        <div class='success'>✓ Success!</div>" +
               "        <div class='message'>" + message + "</div>" +
               "        <button class='close-btn' onclick='window.close()'>Close Window</button>" +
               "    </div>" +
               "</body>" +
               "</html>";
    }
    
    /**
     * Generate HTML error page
     */
    private String generateErrorPage(String platform, String error, String errorDescription) {
        return "<!DOCTYPE html>" +
               "<html>" +
               "<head>" +
               "    <title>" + platform + " OAuth - Error</title>" +
               "    <style>" +
               "        body { font-family: Arial, sans-serif; display: flex; justify-content: center; align-items: center; height: 100vh; margin: 0; background: #f5f5f5; }" +
               "        .container { text-align: center; padding: 40px; background: white; border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); max-width: 500px; }" +
               "        .error { color: #c62828; font-size: 24px; margin-bottom: 20px; }" +
               "        .message { color: #666; margin-bottom: 10px; }" +
               "        .details { color: #999; font-size: 14px; margin-bottom: 30px; }" +
               "        .close-btn { padding: 10px 20px; background: #c62828; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 16px; }" +
               "        .close-btn:hover { background: #8e0000; }" +
               "    </style>" +
               "</head>" +
               "<body>" +
               "    <div class='container'>" +
               "        <div class='error'>✗ Authentication Failed</div>" +
               "        <div class='message'>Error: " + error + "</div>" +
               "        <div class='details'>" + (errorDescription != null ? errorDescription : "") + "</div>" +
               "        <button class='close-btn' onclick='window.close()'>Close Window</button>" +
               "    </div>" +
               "</body>" +
               "</html>";
    }
}
