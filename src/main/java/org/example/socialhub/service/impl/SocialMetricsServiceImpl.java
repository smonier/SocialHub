package org.example.socialhub.service.impl;

import org.example.socialhub.service.SocialMetricsService;
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
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;

/**
 * Implementation of SocialMetricsService using OSGi Declarative Services.
 * Fetches analytics data from external platforms and stores as JCR nodes.
 */
@Component(service = SocialMetricsService.class, immediate = true)
public class SocialMetricsServiceImpl implements SocialMetricsService {
    
    private static final Logger logger = LoggerFactory.getLogger(SocialMetricsServiceImpl.class);
    
    private static final String SOCIAL_POST_TYPE = "socialnt:post";
    private static final String SOCIAL_METRICS_TYPE = "socialnt:metrics";
    private static final String STATUS_PUBLISHED = "published";
    
    // External API configuration
    private static final String EXTERNAL_API_BASE = "https://api.example.com/social";
    private static final String API_TOKEN = "your-api-token-here";
    
    @Reference
    private JCRTemplate jcrTemplate;
    
    @Override
    public void refreshMetricsForPublishedPosts() throws RepositoryException {
        logger.info("Refreshing metrics for all published posts...");
        
        jcrTemplate.doExecuteWithSystemSession(session -> {
            String sql2Query = String.format(
                "SELECT * FROM [%s] WHERE [social:status] = '%s' AND [social:externalId] IS NOT NULL",
                SOCIAL_POST_TYPE,
                STATUS_PUBLISHED
            );
            
            logger.debug("Executing query: {}", sql2Query);
            
            QueryManager queryManager = session.getWorkspace().getQueryManager();
            Query query = queryManager.createQuery(sql2Query, Query.JCR_SQL2);
            QueryResult result = query.execute();
            NodeIterator nodes = result.getNodes();
            
            int count = 0;
            while (nodes.hasNext()) {
                Node postNode = nodes.nextNode();
                String uuid = postNode.getIdentifier();
                
                try {
                    logger.debug("Refreshing metrics for post: {}", uuid);
                    refreshMetricsForPost(uuid);
                    count++;
                } catch (Exception e) {
                    logger.error("Failed to refresh metrics for post " + uuid, e);
                }
            }
            
            logger.info("Refreshed metrics for {} post(s)", count);
            return null;
        });
    }
    
    @Override
    public void refreshMetricsForPost(String postUuid) throws RepositoryException {
        logger.info("Refreshing metrics for post: {}", postUuid);
        
        jcrTemplate.doExecuteWithSystemSession(session -> {
            Node postNode = session.getNodeByIdentifier(postUuid);
            
            if (!postNode.isNodeType(SOCIAL_POST_TYPE)) {
                throw new RepositoryException("Node is not a socialnt:post: " + postUuid);
            }
            
            // Get external IDs (format: "platform:externalId")
            if (!postNode.hasProperty("social:externalId")) {
                logger.debug("Post {} has no external IDs, skipping metrics", postUuid);
                return null;
            }
            
            javax.jcr.Value[] externalIdValues = postNode.getProperty("social:externalId").getValues();
            
            for (javax.jcr.Value value : externalIdValues) {
                String externalIdString = value.getString();
                String[] parts = externalIdString.split(":", 2);
                
                if (parts.length != 2) {
                    logger.warn("Invalid externalId format: {}", externalIdString);
                    continue;
                }
                
                String platform = parts[0];
                String externalId = parts[1];
                
                try {
                    // Fetch metrics from external API
                    MetricsData metrics = fetchMetricsFromPlatform(platform, externalId);
                    
                    if (metrics != null) {
                        // Store metrics as child node
                        storeMetrics(postNode, platform, externalId, metrics);
                        logger.info("Stored metrics for post {} on platform {}", postUuid, platform);
                    }
                    
                } catch (Exception e) {
                    logger.error("Error fetching metrics for " + platform + ":" + externalId, e);
                }
            }
            
            session.save();
            return null;
        });
    }
    
    /**
     * Fetch metrics from external platform API.
     */
    private MetricsData fetchMetricsFromPlatform(String platform, String externalId) {
        try {
            String endpoint = EXTERNAL_API_BASE + "/metrics/" + platform.toLowerCase() + "/" + externalId;
            URL url = new URL(endpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + API_TOKEN);
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);
            
            int responseCode = conn.getResponseCode();
            
            if (responseCode >= 200 && responseCode < 300) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                    
                    String responseBody = response.toString();
                    logger.debug("Metrics response from {}: {}", platform, responseBody);
                    
                    // Parse JSON response (simplified - real implementation would use JSON parser)
                    return parseMetricsResponse(responseBody);
                }
            } else {
                logger.error("Failed to fetch metrics from {}: HTTP {}", platform, responseCode);
                return null;
            }
            
        } catch (Exception e) {
            logger.error("Error fetching metrics from platform " + platform, e);
            return null;
        }
    }
    
    /**
     * Parse metrics from JSON response.
     * Simplified implementation - real code would use JSON parser like Jackson or Gson.
     */
    private MetricsData parseMetricsResponse(String jsonResponse) {
        // Mock implementation - returns random metrics
        // Real implementation would parse actual JSON
        MetricsData data = new MetricsData();
        data.impressions = (long) (Math.random() * 10000);
        data.clicks = (long) (Math.random() * 500);
        data.likes = (long) (Math.random() * 200);
        data.comments = (long) (Math.random() * 50);
        data.shares = (long) (Math.random() * 100);
        return data;
    }
    
    /**
     * Store metrics as a child node under the post.
     */
    private void storeMetrics(Node postNode, String platform, String externalId, MetricsData metrics) throws RepositoryException {
        // Create unique node name based on platform and timestamp
        String nodeName = "metrics-" + platform.toLowerCase() + "-" + System.currentTimeMillis();
        
        Node metricsNode;
        if (postNode.hasNode(nodeName)) {
            metricsNode = postNode.getNode(nodeName);
        } else {
            metricsNode = postNode.addNode(nodeName, SOCIAL_METRICS_TYPE);
        }
        
        metricsNode.setProperty("social:platform", platform);
        metricsNode.setProperty("social:externalId", externalId);
        metricsNode.setProperty("social:capturedAt", Calendar.getInstance());
        metricsNode.setProperty("social:impressions", metrics.impressions);
        metricsNode.setProperty("social:clicks", metrics.clicks);
        metricsNode.setProperty("social:likes", metrics.likes);
        metricsNode.setProperty("social:comments", metrics.comments);
        metricsNode.setProperty("social:shares", metrics.shares);
        
        logger.debug("Created metrics node: {}", metricsNode.getPath());
    }
    
    /**
     * Simple data class for metrics.
     */
    private static class MetricsData {
        long impressions;
        long clicks;
        long likes;
        long comments;
        long shares;
    }
}
