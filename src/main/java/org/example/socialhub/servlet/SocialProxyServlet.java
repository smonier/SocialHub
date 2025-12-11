package org.example.socialhub.servlet;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.IOUtils;
import org.jahia.bin.filters.AbstractServletFilter;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.usermanager.JahiaUser;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Social Proxy Servlet - Forwards requests from Jahia UI to external social media APIs.
 * 
 * This servlet acts as a secure proxy between the Jahia frontend and external APIs,
 * handling authentication and request forwarding.
 * 
 * Registered at: /modules/social-proxy/*
 * 
 * Security: Only authenticated Jahia users can access this servlet.
 * 
 * Configuration: Reads targetBaseUrl and authToken from OSGi configuration file
 * at src/main/resources/META-INF/configurations/org.example.socialhub.servlet.SocialProxyServlet.cfg
 */
@Component(service = AbstractServletFilter.class)
public class SocialProxyServlet extends AbstractServletFilter {
    
    private static final Logger logger = LoggerFactory.getLogger(SocialProxyServlet.class);
    
    // Configuration properties - loaded from OSGi config file
    private String targetBaseUrl;
    private String facebookBaseUrl;
    private String instagramBaseUrl;
    private String linkedinBaseUrl;
    private String authToken;
    private String facebookAppSecret;
    
    // Connection timeout in milliseconds
    private static final int CONNECTION_TIMEOUT = 10000;
    private static final int READ_TIMEOUT = 30000;

    @Activate
    public void activate(Map<String, String> config) {
        logger.info("Activating SocialProxyServlet with /modules/social-proxy/*");
        setUrlPatterns(new String[]{"/modules/social-proxy/*"});
        
        // Read configuration properties from OSGi config
        targetBaseUrl = config.getOrDefault("targetBaseUrl", "https://api.example.com/social");
        facebookBaseUrl = config.get("facebookBaseUrl");
        instagramBaseUrl = config.get("instagramBaseUrl");
        linkedinBaseUrl = config.get("linkedinBaseUrl");
        authToken = config.getOrDefault("authToken", "your-api-token-here");
        facebookAppSecret = config.getOrDefault("facebookAppSecret", "");
        
        logger.info("Configured targetBaseUrl: {}", targetBaseUrl);
        logger.info("Configured authToken: {}", authToken != null && !authToken.isEmpty() ? "****" : "NOT SET");
        if (facebookAppSecret != null && !facebookAppSecret.isEmpty()) {
            logger.info("Facebook app secret configured for appsecret_proof.");
        }
    }

    @Override
    public void init(FilterConfig filterConfig) {
        logger.debug("Initializing SocialProxyServlet");
    }

    @Override
    public void destroy() {
        logger.debug("Destroying SocialProxyServlet");
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) 
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String method = request.getMethod();
        logger.info("Received {} request to URL: {}", method, request.getRequestURL());

        // Security check: Ensure user is authenticated
        if (!isUserAuthenticated(request)) {
            sendJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, 
                "Authentication required", "User must be logged in to access the proxy");
            return;
        }

        if ("GET".equalsIgnoreCase(method)) {
            handleProxyRequest(request, response, "GET");
        } else if ("POST".equalsIgnoreCase(method)) {
            handleProxyRequest(request, response, "POST");
        } else {
            logger.warn("Unsupported HTTP method: {}", method);
            sendJsonError(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED, 
                "Method not supported", "Only GET and POST methods are allowed");
        }
    }

    /**
     * Main proxy logic - handles both GET and POST requests.
     */
    private void handleProxyRequest(HttpServletRequest req, HttpServletResponse resp, String method) 
            throws IOException {
        
        // Extract the relative path after /modules/social-proxy
        String relativePath = extractRelativePath(req);
        if (relativePath == null || relativePath.isEmpty()) {
            sendJsonError(resp, HttpServletResponse.SC_BAD_REQUEST, 
                "Invalid path", "Relative path is required");
            return;
        }

        // Build target URL
        String targetUrl = buildTargetUrl(relativePath, req.getQueryString(), req);
        
        logger.info("Proxying {} request to: {}", method, targetUrl);

        HttpURLConnection connection = null;
        try {
            // Create connection to external API
            URL url = new URL(targetUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(CONNECTION_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            
            // Set headers
            setProxyHeaders(connection, req);
            
            // Handle POST body if present
            if ("POST".equals(method)) {
                connection.setDoOutput(true);
                String requestBody = readRequestBody(req);
                if (requestBody != null && !requestBody.isEmpty()) {
                    try (OutputStream os = connection.getOutputStream()) {
                        byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
                        os.write(input, 0, input.length);
                    }
                }
            }

            // Get response from external API
            int responseCode = connection.getResponseCode();
            String contentType = connection.getContentType();
            
            // Set response headers
            resp.setStatus(responseCode);
            if (contentType != null) {
                resp.setContentType(contentType);
            } else {
                resp.setContentType("application/json");
            }
            
            // Stream response back to client
            try (InputStream inputStream = (responseCode >= 200 && responseCode < 400) 
                    ? connection.getInputStream() 
                    : connection.getErrorStream()) {
                
                if (inputStream != null) {
                    IOUtils.copy(inputStream, resp.getOutputStream());
                }
            }
            
            logger.info("Successfully proxied response with status: {}", responseCode);
            
        } catch (IOException e) {
            logger.error("Error proxying request to external API", e);
            sendJsonError(resp, HttpServletResponse.SC_BAD_GATEWAY, 
                "Proxy error", "Failed to connect to external API: " + e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Check if user is authenticated with Jahia.
     */
    private boolean isUserAuthenticated(HttpServletRequest req) {
        try {
            // Check if there's a valid JCR session
            JahiaUser user = JCRSessionFactory.getInstance().getCurrentUser();
            
            if (user != null && !user.getUsername().equals("guest")) {
                logger.debug("Authenticated user: {}", user.getUsername());
                return true;
            }
            
            logger.warn("Unauthorized access attempt - user is guest or null");
            return false;
            
        } catch (Exception e) {
            logger.error("Error checking user authentication", e);
            return false;
        }
    }

    /**
     * Extract the relative path after /modules/social-proxy.
     * Example: /modules/social-proxy/posts/123 -> /posts/123
     */
    private String extractRelativePath(HttpServletRequest req) {
        String requestUri = req.getRequestURI();
        String contextPath = req.getContextPath() != null ? req.getContextPath() : "";
        String prefix = contextPath + "/modules/social-proxy";

        if (requestUri != null && requestUri.startsWith(prefix)) {
            String withoutPrefix = requestUri.substring(prefix.length());
            if (withoutPrefix.isEmpty()) {
                return "/";
            }
            return withoutPrefix;
        }

        String pathInfo = req.getPathInfo();
        if (pathInfo != null && !pathInfo.isEmpty()) {
            return pathInfo;
        }
        return "/";
    }

    /**
     * Build the complete target URL with query parameters.
     */
    private String buildTargetUrl(String relativePath, String queryString, HttpServletRequest request) {
        String base = resolveBaseUrl(request);
        StringBuilder url = new StringBuilder(base);
        url.append(relativePath);

        String finalQuery = queryString;

        // Add appsecret_proof automatically for Facebook Graph API calls if possible
        if (isFacebookGraphTarget() && facebookAppSecret != null && !facebookAppSecret.isEmpty()) {
            String accessToken = extractQueryParam(queryString, "access_token");
            if (accessToken != null) {
                finalQuery = stripQueryParam(finalQuery, "appsecret_proof");
                String proof = generateAppSecretProof(accessToken, facebookAppSecret);
                if (logger.isDebugEnabled()) {
                    logger.debug("Generated appsecret_proof: {}", proof);
                }
                if (finalQuery == null || finalQuery.isEmpty()) {
                    finalQuery = "appsecret_proof=" + proof;
                } else {
                    finalQuery = finalQuery + "&appsecret_proof=" + proof;
                }
            }
        }

        if (finalQuery != null && !finalQuery.isEmpty()) {
            url.append("?").append(finalQuery);
        }

        return url.toString();
    }

    /**
     * Determine which base URL to use based on the provider hint.
     * Order of resolution: query param "provider" -> header "X-Proxy-Provider" -> default targetBaseUrl.
     */
    private String resolveBaseUrl(HttpServletRequest request) {
        String provider = request.getParameter("provider");
        if (provider == null || provider.isEmpty()) {
            provider = request.getHeader("X-Proxy-Provider");
        }
        if (provider != null) {
            switch (provider.toLowerCase()) {
                case "facebook":
                    if (facebookBaseUrl != null && !facebookBaseUrl.isEmpty()) {
                        return facebookBaseUrl;
                    }
                    break;
                case "instagram":
                    if (instagramBaseUrl != null && !instagramBaseUrl.isEmpty()) {
                        return instagramBaseUrl;
                    }
                    break;
                case "linkedin":
                    if (linkedinBaseUrl != null && !linkedinBaseUrl.isEmpty()) {
                        return linkedinBaseUrl;
                    }
                    break;
                default:
                    break;
            }
        }
        return targetBaseUrl;
    }

    private boolean isFacebookGraphTarget() {
        return targetBaseUrl != null && targetBaseUrl.toLowerCase().contains("graph.facebook.com")
            || facebookBaseUrl != null && facebookBaseUrl.toLowerCase().contains("graph.facebook.com");
    }

    private String extractQueryParam(String query, String key) {
        if (query == null || query.isEmpty() || key == null) {
            return null;
        }
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                String paramKey = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                if (paramKey.equals(key)) {
                    return URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                }
            }
        }
        return null;
    }

    /**
     * Remove all instances of a query parameter from the raw query string.
     */
    private String stripQueryParam(String query, String key) {
        if (query == null || query.isEmpty() || key == null) {
            return query;
        }
        StringBuilder cleaned = new StringBuilder();
        String[] pairs = query.split("&");
        boolean first = true;
        for (String pair : pairs) {
            int idx = pair.indexOf('=');
            String paramKey = idx > 0 ? URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8) : pair;
            if (!key.equals(paramKey)) {
                if (!first) {
                    cleaned.append("&");
                }
                cleaned.append(pair);
                first = false;
            }
        }
        return cleaned.toString();
    }

    /**
     * Set required headers for the proxied request.
     */
    private void setProxyHeaders(HttpURLConnection connection, HttpServletRequest req) {
        // Set authentication header
        connection.setRequestProperty("Authorization", "Bearer " + authToken);
        
        // Forward Content-Type if present
        String contentType = req.getContentType();
        if (contentType != null) {
            connection.setRequestProperty("Content-Type", contentType);
        } else {
            connection.setRequestProperty("Content-Type", "application/json");
        }
        
        // Set Accept header
        String accept = req.getHeader("Accept");
        if (accept != null) {
            connection.setRequestProperty("Accept", accept);
        } else {
            connection.setRequestProperty("Accept", "application/json");
        }
        
        // Set User-Agent
        connection.setRequestProperty("User-Agent", "Jahia-SocialHub-Proxy/1.0");
    }

    /**
     * Read the request body from the incoming request.
     */
    private String readRequestBody(HttpServletRequest req) throws IOException {
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
        }
        return body.toString();
    }

    /**
     * Send a JSON error response.
     */
    private void sendJsonError(HttpServletResponse resp, int status, String error, String details) 
            throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        
        String json = String.format(
            "{\"error\": \"%s\", \"details\": \"%s\", \"status\": %d}",
            escapeJson(error),
            escapeJson(details),
            status
        );
        
        resp.getWriter().write(json);
        resp.getWriter().flush();
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
            logger.error("Failed to generate appsecret_proof", e);
            return "";
        }
    }

    /**
     * Simple JSON string escaping.
     */
    private String escapeJson(String input) {
        if (input == null) {
            return "";
        }
        return input
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
}
