package org.example.socialhub.servlet;

import org.apache.commons.io.IOUtils;
import org.example.socialhub.service.SocialAccountService;
import org.jahia.bin.filters.AbstractServletFilter;
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

/**
 * Social Insights Servlet - Fetches insights/analytics from social media platforms.
 * 
 * Registered at: /modules/api/social/insights/{platform}/{postId}
 * 
 * Platforms supported: facebook, instagram, linkedin
 * 
 * Example: GET /modules/api/social/insights/facebook/1216046853914613
 * Returns: JSON with insights data (impressions, reach, clicks, likes, comments, shares)
 */
@Component(
    service = AbstractServletFilter.class,
    immediate = true,
    configurationPid = "org.example.socialhub.servlet.SocialProxyServlet"
)
public class SocialInsightsServlet extends AbstractServletFilter {
    
    private static final Logger logger = LoggerFactory.getLogger(SocialInsightsServlet.class);
    
    @Reference
    private SocialAccountService socialAccountService;
    
    private String facebookApiVersion = "v21.0";
    
    @Activate
    public void activate(java.util.Map<String, Object> properties) {
        logger.info("[SocialInsightsServlet] Activating with /modules/api/social/insights/*");
        setUrlPatterns(new String[]{"/modules/api/social/insights/*"});
        
        // Load Facebook API version from OSGi configuration
        if (properties != null && properties.get("facebookApiVersion") != null) {
            facebookApiVersion = properties.get("facebookApiVersion").toString();
            logger.info("[SocialInsightsServlet] Configured Facebook API version: {}", facebookApiVersion);
        } else {
            logger.warn("[SocialInsightsServlet] No configuration found, using default API version: {}", facebookApiVersion);
        }
    }
    
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        logger.info("[SocialInsightsServlet] Initialized - Registered at /modules/api/social/insights/{platform}/{postId}");
    }
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        String requestURI = httpRequest.getRequestURI();
        logger.info("[SocialInsightsServlet] Request URI: {}", requestURI);
        
        // Check if this is an insights request: /modules/api/social/insights/{platform}/{postId}
        if (requestURI != null && requestURI.contains("/modules/api/social/insights/")) {
            
            // Extract platform and postId from URI
            String[] parts = requestURI.split("/");
            if (parts.length < 7) {
                logger.error("[SocialInsightsServlet] Invalid URI format: {}", requestURI);
                httpResponse.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid URI format");
                return;
            }
            
            String platform = parts[5]; // /modules/api/social/insights/{platform}/{postId}
            String postId = parts[6];
            
            // Extract siteKey from request - try multiple sources
            String siteKey = httpRequest.getParameter("site");
            if (siteKey == null || siteKey.isEmpty()) {
                // Try to extract from referer or session
                siteKey = extractSiteKeyFromRequest(httpRequest);
            }
            
            logger.info("[SocialInsightsServlet] Platform: {}, PostId: {}, SiteKey: {}", platform, postId, siteKey);
            
            // Handle GET requests only
            if ("GET".equalsIgnoreCase(httpRequest.getMethod())) {
                try {
                    String insightsJson = fetchInsights(platform, postId, siteKey);
                    
                    httpResponse.setContentType("application/json");
                    httpResponse.setCharacterEncoding("UTF-8");
                    httpResponse.getWriter().write(insightsJson);
                    httpResponse.setStatus(HttpServletResponse.SC_OK);
                    
                    logger.info("[SocialInsightsServlet] Successfully returned insights for {} post {}", platform, postId);
                    
                } catch (Exception e) {
                    logger.error("[SocialInsightsServlet] Error fetching insights: ", e);
                    httpResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                        "Failed to fetch insights: " + e.getMessage());
                }
            } else {
                httpResponse.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, 
                    "Only GET requests are supported");
            }
            
        } else {
            // Not an insights request, continue the filter chain
            chain.doFilter(request, response);
        }
    }
    
    /**
     * Extract siteKey from HTTP request
     * Example referer: https://wonderland-jahiasales.internal.cloud.jahia.com/jahia/jcontent/jsmod/en/contentToolsAccordion/SocialHub
     * siteKey is: jsmod
     */
    private String extractSiteKeyFromRequest(HttpServletRequest request) {
        // Try to extract from referer URL
        String referer = request.getHeader("referer");
        logger.info("[SocialInsightsServlet] Referer: {}", referer);
        
        if (referer != null && referer.contains("/jcontent/")) {
            // Extract from URL pattern: /jahia/jcontent/{siteKey}/...
            String[] parts = referer.split("/jcontent/");
            if (parts.length > 1) {
                String afterJContent = parts[1];
                String[] siteParts = afterJContent.split("/");
                if (siteParts.length > 0) {
                    String siteKey = siteParts[0];
                    logger.info("[SocialInsightsServlet] Extracted siteKey from referer: {}", siteKey);
                    return siteKey;
                }
            }
        }
        
        // Fallback: try to extract from /sites/ path in JCR
        if (referer != null && referer.contains("/sites/")) {
            String[] parts = referer.split("/sites/");
            if (parts.length > 1) {
                String afterSites = parts[1];
                String[] siteParts = afterSites.split("/");
                if (siteParts.length > 0) {
                    String siteKey = siteParts[0];
                    logger.info("[SocialInsightsServlet] Extracted siteKey from /sites/ path: {}", siteKey);
                    return siteKey;
                }
            }
        }
        
        // Default fallback
        logger.warn("[SocialInsightsServlet] Could not extract siteKey from referer, using default: digitall");
        return "digitall";
    }
    
    /**
     * Fetch insights from the appropriate social platform API
     */
    private String fetchInsights(String platform, String postId, String siteKey) throws Exception {
        logger.info("[SocialInsightsServlet] Fetching insights for platform: {}, postId: {}, siteKey: {}", 
            platform, postId, siteKey);
        
        switch (platform.toLowerCase()) {
            case "facebook":
                return fetchFacebookInsights(postId, siteKey);
            case "instagram":
                return fetchInstagramInsights(postId, siteKey);
            case "linkedin":
                return fetchLinkedInInsights(postId, siteKey);
            default:
                throw new IllegalArgumentException("Unsupported platform: " + platform);
        }
    }
    
    /**
     * Fetch insights from Facebook Graph API for Page Posts
     * Endpoint: https://graph.facebook.com/{api-version}/{page-post-id}/insights
     * 
     * Important: For Page Post insights, the post ID must be in format: {page-id}_{post-id}
     * Example: 101281515074354_1216046853914613
     * 
     * Available metrics for Page Posts (period=lifetime):
     * Reaction metrics:
     * - post_reactions_like_total: Total Like reactions
     * - post_reactions_love_total: Total Love reactions
     * - post_reactions_wow_total: Total Wow reactions
     * - post_reactions_haha_total: Total Haha reactions
     * - post_reactions_sorry_total: Total Sorry reactions
     * - post_reactions_anger_total: Total Anger reactions
     * 
     * Note: Impressions/reach metrics (post_impressions, post_engaged_users) are NOT available
     * via the insights endpoint - they must be fetched via the post's engagement fields
     */
    private String fetchFacebookInsights(String postId, String siteKey) throws Exception {
        logger.info("[SocialInsightsServlet] Fetching Facebook insights for post: {} on site: {}", postId, siteKey);
        
        // Get Facebook accounts to find page ID
        java.util.Map<String, java.util.Map<String, String>> accounts = socialAccountService.getFacebookAccounts(siteKey);
        
        if (accounts.isEmpty()) {
            logger.warn("[SocialInsightsServlet] No Facebook accounts found for siteKey: {}", siteKey);
            throw new IllegalStateException("No Facebook account found for site: " + siteKey);
        }
        
        // Get first account's page ID and token
        java.util.Map.Entry<String, java.util.Map<String, String>> firstAccount = accounts.entrySet().iterator().next();
        String pageId = firstAccount.getKey();
        String pageToken = firstAccount.getValue().get("pageToken");
        
        if (pageToken == null || pageToken.isEmpty()) {
            logger.warn("[SocialInsightsServlet] No Facebook page token found for siteKey: {}", siteKey);
            throw new IllegalStateException("No Facebook page token found for site: " + siteKey);
        }
        
        // Build the full page-post ID format: {page-id}_{post-id}
        String pagePostId = postId.contains("_") ? postId : pageId + "_" + postId;
        logger.info("[SocialInsightsServlet] Using page-post ID: {}", pagePostId);
        
        // Request only reaction metrics (these are the valid Page Post Insights metrics)
        String metrics = URLEncoder.encode(
            "post_reactions_like_total,post_reactions_love_total,post_reactions_wow_total,post_reactions_haha_total,post_reactions_sorry_total,post_reactions_anger_total", 
            StandardCharsets.UTF_8.toString()
        );
        
        String apiUrl = String.format(
            "https://graph.facebook.com/%s/%s/insights?metric=%s&access_token=%s",
            facebookApiVersion, pagePostId, metrics, pageToken
        );
        
        logger.info("[SocialInsightsServlet] Calling Facebook Insights API: {}", 
            apiUrl.replace(pageToken, "***TOKEN***"));
        
        // First, get reaction insights
        URL insightsUrl = new URL(apiUrl);
        HttpURLConnection insightsConnection = (HttpURLConnection) insightsUrl.openConnection();
        insightsConnection.setRequestMethod("GET");
        insightsConnection.setRequestProperty("Accept", "application/json");
        
        String insightsResponse = null;
        int insightsResponseCode = insightsConnection.getResponseCode();
        logger.info("[SocialInsightsServlet] Facebook Insights API response code: {}", insightsResponseCode);
        
        if (insightsResponseCode == HttpURLConnection.HTTP_OK) {
            try (InputStream inputStream = insightsConnection.getInputStream()) {
                insightsResponse = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
                logger.info("[SocialInsightsServlet] Facebook Insights API response: {}", insightsResponse);
            }
        } else {
            try (InputStream errorStream = insightsConnection.getErrorStream()) {
                String errorBody = errorStream != null ? 
                    IOUtils.toString(errorStream, StandardCharsets.UTF_8) : "No error details";
                logger.error("[SocialInsightsServlet] Facebook Insights API error: {}", errorBody);
            }
        }
        
        // Second, get post engagement data (likes, comments, shares) from the post object
        String postDataUrl = String.format(
            "https://graph.facebook.com/%s/%s?fields=likes.summary(true),comments.summary(true),shares&access_token=%s",
            facebookApiVersion, pagePostId, pageToken
        );
        
        logger.info("[SocialInsightsServlet] Calling Facebook Post Data API: {}", 
            postDataUrl.replace(pageToken, "***TOKEN***"));
        
        URL postUrl = new URL(postDataUrl);
        HttpURLConnection postConnection = (HttpURLConnection) postUrl.openConnection();
        postConnection.setRequestMethod("GET");
        postConnection.setRequestProperty("Accept", "application/json");
        
        String postDataResponse = null;
        int postResponseCode = postConnection.getResponseCode();
        logger.info("[SocialInsightsServlet] Facebook Post Data API response code: {}", postResponseCode);
        
        if (postResponseCode == HttpURLConnection.HTTP_OK) {
            try (InputStream inputStream = postConnection.getInputStream()) {
                postDataResponse = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
                logger.info("[SocialInsightsServlet] Facebook Post Data API response: {}", postDataResponse);
            }
        } else {
            try (InputStream errorStream = postConnection.getErrorStream()) {
                String errorBody = errorStream != null ? 
                    IOUtils.toString(errorStream, StandardCharsets.UTF_8) : "No error details";
                logger.error("[SocialInsightsServlet] Facebook Post Data API error: {}", errorBody);
            }
        }
        
        // Parse and combine both responses
        return parseFacebookInsights(insightsResponse, postDataResponse, postId);
    }
    
    /**
     * Parse Facebook insights response and convert to standard format
     * Combines insights data (reactions) with post engagement data (likes, comments, shares)
     */
    private String parseFacebookInsights(String insightsResponse, String postDataResponse, String postId) {
        logger.info("[SocialInsightsServlet] Parsing Facebook insights and post data");
        
        int totalLikes = 0;
        int totalComments = 0;
        int totalShares = 0;
        int impressions = 0;
        int reach = 0;
        int clicks = 0;
        
        // Parse post engagement data (likes, comments, shares)
        if (postDataResponse != null) {
            try {
                org.json.JSONObject postData = new org.json.JSONObject(postDataResponse);
                
                if (postData.has("likes") && postData.getJSONObject("likes").has("summary")) {
                    totalLikes = postData.getJSONObject("likes").getJSONObject("summary").optInt("total_count", 0);
                }
                
                if (postData.has("comments") && postData.getJSONObject("comments").has("summary")) {
                    totalComments = postData.getJSONObject("comments").getJSONObject("summary").optInt("total_count", 0);
                }
                
                if (postData.has("shares")) {
                    totalShares = postData.getJSONObject("shares").optInt("count", 0);
                }
                
                logger.info("[SocialInsightsServlet] Parsed engagement: likes={}, comments={}, shares={}", 
                    totalLikes, totalComments, totalShares);
            } catch (Exception e) {
                logger.error("[SocialInsightsServlet] Error parsing post data", e);
            }
        }
        
        // Calculate engagement rate
        double engagementRate = 0.0;
        int totalEngagements = totalLikes + totalComments + totalShares;
        if (reach > 0) {
            engagementRate = (double) totalEngagements / reach * 100;
        }
        
        // Return combined insights data
        String json = String.format(
            "{\"postId\":\"%s\",\"platform\":\"facebook\"," +
            "\"impressions\":%d,\"reach\":%d,\"clicks\":%d," +
            "\"likes\":%d,\"comments\":%d,\"shares\":%d," +
            "\"engagement\":{\"rate\":%.2f}}",
            postId, impressions, reach, clicks,
            totalLikes, totalComments, totalShares, engagementRate
        );
        
        logger.info("[SocialInsightsServlet] Returning insights: {}", json);
        return json;
    }
    
    /**
     * Fetch insights from Instagram Graph API (placeholder)
     */
    private String fetchInstagramInsights(String postId, String siteKey) throws Exception {
        logger.info("[SocialInsightsServlet] Instagram insights not yet implemented for post: {} on site: {}", 
            postId, siteKey);
        
        return String.format(
            "{\"postId\":\"%s\",\"platform\":\"instagram\"," +
            "\"impressions\":0,\"reach\":0,\"clicks\":0," +
            "\"likes\":0,\"comments\":0,\"shares\":0," +
            "\"engagement\":{\"rate\":0.0}}",
            postId
        );
    }
    
    /**
     * Fetch insights from LinkedIn API (placeholder)
     */
    private String fetchLinkedInInsights(String postId, String siteKey) throws Exception {
        logger.info("[SocialInsightsServlet] LinkedIn insights not yet implemented for post: {} on site: {}", 
            postId, siteKey);
        
        return String.format(
            "{\"postId\":\"%s\",\"platform\":\"linkedin\"," +
            "\"impressions\":0,\"reach\":0,\"clicks\":0," +
            "\"likes\":0,\"comments\":0,\"shares\":0," +
            "\"engagement\":{\"rate\":0.0}}",
            postId
        );
    }
    
    @Override
    public void destroy() {
        logger.info("[SocialInsightsServlet] Destroyed");
    }
}
