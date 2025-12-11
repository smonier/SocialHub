package org.example.socialhub.jobs;

import org.example.socialhub.service.SocialMetricsService;
import org.jahia.services.scheduler.BackgroundJob;
import org.jahia.services.scheduler.SchedulerService;
import org.jahia.settings.SettingsBean;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jahia.osgi.BundleUtils;

import javax.jcr.RepositoryException;

/**
 * Background job that refreshes social media metrics for published posts.
 * Runs periodically to fetch latest analytics data from external platforms.
 * 
 * Uses Jahia BackgroundJob with SchedulerService for proper OSGi lifecycle management.
 * Only runs on processing servers to avoid duplicate execution in clustered environments.
 */
@Component(immediate = true)
public class SocialMetricsJob extends BackgroundJob {
    
    private static final Logger logger = LoggerFactory.getLogger(SocialMetricsJob.class);
    
    private SchedulerService schedulerService;
    private JobDetail jobDetail;
    
    @Activate
    public void start() throws Exception {
        logger.info("[JOB] SocialMetricsJob @Activate starting...");
        
        jobDetail = BackgroundJob.createJahiaJob(
            "Social Hub - Refresh Metrics",
            SocialMetricsJob.class
        );
        
        logger.info("[JOB] Job detail created: {} (group: {})", jobDetail.getName(), jobDetail.getGroup());
        logger.info("[JOB] Is processing server: {}", SettingsBean.getInstance().isProcessingServer());
        logger.info("[JOB] Existing jobs in group: {}", schedulerService.getAllJobs(jobDetail.getGroup()).size());
        
        if (schedulerService.getAllJobs(jobDetail.getGroup()).isEmpty() &&
            SettingsBean.getInstance().isProcessingServer()) {
            
            // Run every hour (3600000 milliseconds)
            Trigger trigger = new SimpleTrigger(
                "socialMetricsJob_trigger",
                jobDetail.getGroup(),
                SimpleTrigger.REPEAT_INDEFINITELY,
                3600000
            );
            
            schedulerService.getScheduler().scheduleJob(jobDetail, trigger);
            logger.info("[JOB] SocialMetricsJob scheduled successfully (every hour / 3600000ms)");
        } else {
            logger.warn("[JOB] SocialMetricsJob NOT scheduled - either job exists or not a processing server");
        }
    }
    
    @Deactivate
    public void stop() throws Exception {
        if (!schedulerService.getAllJobs(jobDetail.getGroup()).isEmpty() &&
            SettingsBean.getInstance().isProcessingServer()) {
            
            schedulerService.getScheduler().deleteJob(
                jobDetail.getName(),
                jobDetail.getGroup()
            );
            logger.info("SocialMetricsJob unscheduled");
        }
    }
    
    @Override
    public void executeJahiaJob(JobExecutionContext jobExecutionContext) {
        logger.info("[JOB] ========== SocialMetricsJob executing at {} ==========", new java.util.Date());
        logger.info("[JOB] Fire time: {}", jobExecutionContext.getFireTime());
        logger.info("[JOB] Scheduled fire time: {}", jobExecutionContext.getScheduledFireTime());
        
        try {
            // Lookup service dynamically since BackgroundJob instances are created by Quartz, not OSGi
            SocialMetricsService socialMetricsService = BundleUtils.getOsgiService(SocialMetricsService.class, null);
            if (socialMetricsService == null) {
                logger.error("[JOB] SocialMetricsService not available - cannot refresh metrics");
                return;
            }
            
            logger.info("[JOB] Calling socialMetricsService.refreshMetricsForPublishedPosts()...");
            socialMetricsService.refreshMetricsForPublishedPosts();
            logger.info("[JOB] ========== SocialMetricsJob completed successfully ==========");
            
        } catch (RepositoryException e) {
            logger.error("[JOB] RepositoryException in SocialMetricsJob", e);
        } catch (Exception e) {
            logger.error("[JOB] Unexpected error in SocialMetricsJob", e);
        }
    }
    
    @Reference
    public void setSchedulerService(SchedulerService schedulerService) {
        this.schedulerService = schedulerService;
    }
}
