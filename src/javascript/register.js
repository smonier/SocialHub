import {registry} from '@jahia/ui-extender';
import React, {Suspense} from 'react';
import {Apps} from '@jahia/moonstone/dist/icons';
import SocialHub from './apps/socialHub/SocialHub';

// Register the Social Hub application
export default function registerSocialHub() {
    window.jahia.i18n.loadNamespaces('SocialHub');

    // Check if accordion exists, if not create it
    const accordionType = 'accordionItem';
    const accordionKey = 'contentToolsAccordion';
    const accordionExists = window.jahia.uiExtender.registry.get(accordionType, accordionKey);

    if (!accordionExists) {
        registry.add(accordionType, accordionKey, registry.get(accordionType, 'renderDefaultApps'), {
            targets: ['jcontent:75'],
            icon: <Apps/>,
            label: 'SocialHub:accordion.title',
            appsTarget: 'contentToolsAccordionApps'
        });
    }

    // Register the main Social Hub admin route
    registry.add('adminRoute', 'socialHub', {
        targets: ['contentToolsAccordionApps:10'],
        icon: <Apps/>,
        label: 'SocialHub:app.title',
        path: '/social-hub',
        defaultPath: '/social-hub',
        isSelectable: true,
        requiredPermission: 'jcr:read',
        render: () => (
            <Suspense fallback="loading...">
                <SocialHub/>
            </Suspense>
        )
    });

    console.log('Social Hub UI extension registered successfully');
}
