import React from 'react';
import {LayoutContent} from '@jahia/moonstone';
import {useTranslation} from 'react-i18next';
export const AdminPanel = () => {
    const {t} = useTranslation('SocialHub');

    return (
        <LayoutContent content={(
            <>
                {t('SocialHub.hello')} {window.contextJsParameters.currentUser} !
            </>
    )}/>
    );
};
