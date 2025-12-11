import {registry} from '@jahia/ui-extender';
import constants from './AdminPanel.constants';
import SocialHub from '../apps/socialHub/SocialHub';
import React, {Suspense} from 'react';
import {Apps} from '@jahia/moonstone/dist/icons';

export const registerRoutes = () => {
    window.jahia.i18n.loadNamespaces('SocialHub');

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

    registry.add('adminRoute', 'SocialHub', {
        targets: ['contentToolsAccordionApps'],
        icon: <Apps/>,
        label: 'SocialHub:app.title',
        path: `${constants.DEFAULT_ROUTE}*`,
        defaultPath: constants.DEFAULT_ROUTE,
        isSelectable: true,
        render: () => <Suspense fallback="loading..."><SocialHub/></Suspense>
    });

    console.debug('%c SocialHub is activated', 'color: #3c8cba');
};
