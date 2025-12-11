package org.example.socialhub.rules;

import org.jahia.services.content.rules.ModuleGlobalObject;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Global object that exposes services to Drools rules.
 * This makes SocialHubRulesService available in rules as 'socialHubService'.
 */
@Component(service = ModuleGlobalObject.class)
public class SocialHubGlobalObject extends ModuleGlobalObject {
    
    private static final Logger logger = LoggerFactory.getLogger(SocialHubGlobalObject.class);
    
    @Reference
    public void setSocialHubRulesService(SocialHubRulesService service) {
        logger.info("[RULES] Registering SocialHubRulesService in global rules object");
        getGlobalRulesObject().put("socialHubService", service);
        logger.info("[RULES] SocialHubRulesService registered successfully");
    }
    
    public void unsetSocialHubRulesService(SocialHubRulesService service) {
        logger.info("[RULES] Unregistering SocialHubRulesService from global rules object");
        getGlobalRulesObject().remove("socialHubService");
    }
}
