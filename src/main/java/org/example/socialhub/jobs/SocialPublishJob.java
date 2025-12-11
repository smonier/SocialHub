package org.example.socialhub.jobs;

import org.example.socialhub.service.SocialPostService;
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
 * Background job that publishes social posts that are due.
 * Runs periodically to check for scheduled posts where scheduledAt <= now.
 * 
 * Uses Jahia BackgroundJob with SchedulerService for proper OSGi lifecycle management.
 * Only runs on processing servers to avoid duplicate execution in clustered environments.
 */
@Component(immediate = true)
public class SocialPublishJob extends BackgroundJob {
    
    private static final Logger logger = LoggerFactory.getLogger(SocialPublishJob.class);
    
    private SchedulerService schedulerService;
    private JobDetail jobDetail;
    
    @Activate
    public void start() throws Exception {
        logger.info("[JOB] SocialPublishJob @Activate starting...");
        
        jobDetail = BackgroundJob.createJahiaJob(
            "Social Hub - Publish Scheduled Posts",
            SocialPublishJob.class
        );
        
        logger.info("[JOB] Job detail created: {} (group: {})", jobDetail.getName(), jobDetail.getGroup());
        logger.info("[JOB] Is processing server: {}", SettingsBean.getInstance().isProcessingServer());
        logger.info("[JOB] Existing jobs in group: {}", schedulerService.getAllJobs(jobDetail.getGroup()).size());
        
        if (schedulerService.getAllJobs(jobDetail.getGroup()).isEmpty() &&
            SettingsBean.getInstance().isProcessingServer()) {
            
            // Run every 5 minutes (300000 milliseconds)
            Trigger trigger = new SimpleTrigger(
                "socialPublishJob_trigger",
                jobDetail.getGroup(),
                SimpleTrigger.REPEAT_INDEFINITELY,
                300000
            );
            
            schedulerService.getScheduler().scheduleJob(jobDetail, trigger);
            logger.info("[JOB] SocialPublishJob scheduled successfully (every 5 minutes / 300000ms)");
        } else {
            logger.warn("[JOB] SocialPublishJob NOT scheduled - either job exists or not a processing server");
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
            logger.info("SocialPublishJob unscheduled");
        }
    }
    
    @Override
    public void executeJahiaJob(JobExecutionContext jobExecutionContext) {
        logger.info("[JOB] ========== SocialPublishJob executing at {} ==========", new java.util.Date());
        logger.info("[JOB] Fire time: {}", jobExecutionContext.getFireTime());
        logger.info("[JOB] Scheduled fire time: {}", jobExecutionContext.getScheduledFireTime());
        
        try {
            // Lookup service dynamically since BackgroundJob instances are created by Quartz, not OSGi
            SocialPostService socialPostService = BundleUtils.getOsgiService(SocialPostService.class, null);
            if (socialPostService == null) {
                logger.error("[JOB] SocialPostService not available - cannot publish posts");
                return;
            }
            
            logger.info("[JOB] Calling socialPostService.publishDueScheduledPosts()...");
            socialPostService.publishDueScheduledPosts();
            logger.info("[JOB] ========== SocialPublishJob completed successfully ==========");
            
        } catch (RepositoryException e) {
            logger.error("[JOB] RepositoryException in SocialPublishJob", e);
        } catch (Exception e) {
            logger.error("[JOB] Unexpected error in SocialPublishJob", e);
        }
    }
    
    @Reference
    public void setSchedulerService(SchedulerService schedulerService) {
        this.schedulerService = schedulerService;
    }
}
