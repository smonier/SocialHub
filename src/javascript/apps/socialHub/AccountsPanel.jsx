import React, {useMemo} from 'react';
import {useTranslation} from 'react-i18next';
import {useQuery} from '@apollo/client';
import {Typography, Button, Chip, Loader} from '@jahia/moonstone';
import {Warning} from '@jahia/moonstone/dist/icons';
import {FaFacebook, FaInstagram, FaLinkedin} from 'react-icons/fa';
import {GET_SOCIAL_ACCOUNTS} from './queries';

const platformMeta = {
    facebook: {color: '#1877F2', icon: <FaFacebook size={18}/>, label: 'Facebook'},
    instagram: {color: '#E1306C', icon: <FaInstagram size={18}/>, label: 'Instagram'},
    linkedin: {color: '#0A66C2', icon: <FaLinkedin size={18}/>, label: 'LinkedIn'}
};

const AccountsPanel = () => {
    const {t} = useTranslation('SocialHub');
    const siteKey = window.contextJsParameters?.siteKey || 'digitall';

    const {loading, error, data, refetch} = useQuery(GET_SOCIAL_ACCOUNTS, {
        fetchPolicy: 'network-only'
    });

    const accounts = useMemo(() => {
        const nodes = data?.jcr?.nodesByCriteria?.nodes || [];
        return nodes.map(node => {
            const account = {
                uuid: node.uuid,
                path: node.path,
                name: node.name,
                platform: '',
                label: '',
                handle: '',
                pageId: '',
                accessToken: '',
                refreshToken: '',
                tokenExpiry: '',
                isActive: true
            };

            if (node.properties) {
                node.properties.forEach(prop => {
                    switch (prop.name) {
                        case 'social:platform':
                            account.platform = prop.value;
                            break;
                        case 'social:label':
                            account.label = prop.value;
                            break;
                        case 'social:handle':
                            account.handle = prop.value;
                            break;
                        case 'social:pageId':
                            account.pageId = prop.value;
                            break;
                        case 'social:accessToken':
                            account.accessToken = prop.value;
                            break;
                        case 'social:refreshToken':
                            account.refreshToken = prop.value;
                            break;
                        case 'social:tokenExpiry':
                            account.tokenExpiry = prop.value;
                            break;
                        case 'social:isActive':
                            account.isActive = prop.value === 'true' || prop.value === true;
                            break;
                        default:
                            break;
                    }
                });
            }

            return account;
        });
    }, [data]);

    const maskToken = token => {
        if (!token) {
            return '—';
        }

        if (token.length <= 10) {
            return token;
        }

        return `${token.substring(0, 6)}...${token.substring(token.length - 4)}`;
    };

    const openOAuthPopup = platform => {
        const popupUrl = `/modules/SocialHub/oauth/${platform}/start?site=${siteKey}`;
        const popup = window.open(popupUrl, `${platform}-oauth`, 'width=900,height=900');
        if (popup) {
            popup.focus();
        } else {
            // Popup blockers may prevent the window; keep UX simple with console warning
            // eslint-disable-next-line no-console
            console.warn('Popup blocked. Please allow popups for this site to complete OAuth.');
        }
    };

    const renderProviderCard = (platform, titleKey, bodyKey, scopesKey) => {
        const meta = platformMeta[platform];
        return (
            <div
                key={platform}
                style={{
                    border: '1px solid #d0d0d0',
                    borderRadius: '6px',
                    padding: '16px',
                    backgroundColor: '#fff',
                    display: 'flex',
                    flexDirection: 'column',
                    gap: '8px',
                    boxShadow: '0 1px 3px rgba(0,0,0,0.04)'
                }}
            >
                <div style={{display: 'flex', alignItems: 'center', gap: '10px'}}>
                    <div style={{
                        width: 32,
                        height: 32,
                        borderRadius: '50%',
                        backgroundColor: meta.color,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        color: '#fff'
                    }}
                    >
                        {meta.icon}
                    </div>
                    <div>
                        <Typography variant="body" weight="bold">{t(titleKey)}</Typography>
                        <Typography variant="caption" style={{color: '#676767'}}>
                            {t(scopesKey)}
                        </Typography>
                    </div>
                </div>
                <Typography variant="body" style={{color: '#444'}}>
                    {t(bodyKey)}
                </Typography>
                <Button
                    color="accent"
                    label={t('accounts.buttons.connect')}
                    onClick={() => openOAuthPopup(platform)}
                />
            </div>
        );
    };

    return (
        <div style={{padding: '24px'}}>
            <Typography variant="subheading" weight="bold" style={{marginBottom: '8px'}}>
                {t('accounts.title')}
            </Typography>
            <Typography variant="body" style={{marginBottom: '16px', color: '#676767'}}>
                {t('accounts.description')}
            </Typography>
            <div style={{
                padding: '12px 16px',
                backgroundColor: '#f5f5f5',
                border: '1px solid #e0e0e0',
                borderRadius: '4px',
                marginBottom: '16px'
            }}
            >
                <Typography variant="caption" style={{color: '#444'}}>
                    {t('accounts.helper')}
                </Typography>
            </div>

            <div style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))',
                gap: '12px',
                marginBottom: '24px'
            }}
            >
                {renderProviderCard('facebook', 'accounts.connect.facebook.title', 'accounts.connect.facebook.body', 'accounts.connect.facebook.scopes')}
                {renderProviderCard('instagram', 'accounts.connect.instagram.title', 'accounts.connect.instagram.body', 'accounts.connect.instagram.scopes')}
                {renderProviderCard('linkedin', 'accounts.connect.linkedin.title', 'accounts.connect.linkedin.body', 'accounts.connect.linkedin.scopes')}
            </div>

            <div style={{display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '12px'}}>
                <Typography variant="subheading" weight="bold">
                    {t('accounts.list.title')}
                </Typography>
                <Button
                    size="small"
                    variant="ghost"
                    label={t('accounts.buttons.refresh')}
                    onClick={() => refetch()}
                />
            </div>

            {loading && (
                <div style={{padding: '24px', display: 'flex', alignItems: 'center', gap: '12px'}}>
                    <Loader size="small"/>
                    <Typography variant="body">{t('accounts.list.loading')}</Typography>
                </div>
            )}

            {error && (
                <div style={{
                    padding: '16px',
                    backgroundColor: '#ffebee',
                    borderRadius: '4px',
                    border: '1px solid #ffcdd2',
                    marginBottom: '16px',
                    display: 'flex',
                    gap: '8px',
                    alignItems: 'flex-start'
                }}
                >
                    <Warning size="small" style={{color: '#c62828', marginTop: '2px'}}/>
                    <div>
                        <Typography variant="subheading" style={{color: '#c62828'}}>
                            {t('accounts.list.error')}
                        </Typography>
                        <Typography variant="body" style={{color: '#444'}}>
                            {error.message}
                        </Typography>
                    </div>
                </div>
            )}

            {!loading && accounts.length === 0 && !error && (
                <div style={{padding: '24px', backgroundColor: '#f5f5f5', borderRadius: '4px'}}>
                    <Typography variant="body" style={{color: '#666'}}>
                        {t('accounts.list.empty')}
                    </Typography>
                </div>
            )}

            {!loading && accounts.length > 0 && (
                <div style={{
                    border: '1px solid #d0d0d0',
                    borderRadius: '6px',
                    overflow: 'hidden'
                }}
                >
                    <table style={{
                        width: '100%',
                        borderCollapse: 'collapse',
                        fontSize: '13px'
                    }}
                    >
                        <thead>
                            <tr style={{backgroundColor: '#f5f5f5'}}>
                                <th style={{padding: '12px', textAlign: 'left', borderBottom: '1px solid #d0d0d0'}}>
                                    {t('accounts.list.platform')}
                                </th>
                                <th style={{padding: '12px', textAlign: 'left', borderBottom: '1px solid #d0d0d0'}}>
                                    {t('accounts.list.label')}
                                </th>
                                <th style={{padding: '12px', textAlign: 'left', borderBottom: '1px solid #d0d0d0'}}>
                                    {t('accounts.list.handle')}
                                </th>
                                <th style={{padding: '12px', textAlign: 'left', borderBottom: '1px solid #d0d0d0'}}>
                                    {t('accounts.list.pageId')}
                                </th>
                                <th style={{padding: '12px', textAlign: 'left', borderBottom: '1px solid #d0d0d0'}}>
                                    {t('accounts.list.token')}
                                </th>
                                <th style={{padding: '12px', textAlign: 'left', borderBottom: '1px solid #d0d0d0'}}>
                                    {t('accounts.list.refreshToken')}
                                </th>
                                <th style={{padding: '12px', textAlign: 'left', borderBottom: '1px solid #d0d0d0'}}>
                                    {t('accounts.list.expiry')}
                                </th>
                                <th style={{padding: '12px', textAlign: 'left', borderBottom: '1px solid #d0d0d0'}}>
                                    {t('accounts.list.status')}
                                </th>
                                <th style={{padding: '12px', textAlign: 'left', borderBottom: '1px solid #d0d0d0'}}>
                                    {t('accounts.list.path')}
                                </th>
                            </tr>
                        </thead>
                        <tbody>
                            {accounts.map(account => {
                                const meta = platformMeta[account.platform?.toLowerCase()] || {};
                                return (
                                    <tr key={account.uuid} style={{borderBottom: '1px solid #e0e0e0'}}>
                                        <td style={{padding: '12px'}}>
                                            <div style={{display: 'flex', alignItems: 'center', gap: '8px'}}>
                                                {meta.icon && (
                                                    <span style={{
                                                        width: 28,
                                                        height: 28,
                                                        borderRadius: '50%',
                                                        backgroundColor: meta.color || '#e0e0e0',
                                                        color: '#fff',
                                                        display: 'flex',
                                                        alignItems: 'center',
                                                        justifyContent: 'center'
                                                    }}
                                                    >
                                                        {meta.icon}
                                                    </span>
                                                )}
                                                <span style={{fontWeight: 600, color: '#293136'}}>
                                                    {meta.label || account.platform || '-'}
                                                </span>
                                            </div>
                                        </td>
                                        <td style={{padding: '12px'}}>{account.label || '—'}</td>
                                        <td style={{padding: '12px', fontFamily: 'monospace'}}>{account.handle || '—'}</td>
                                        <td style={{padding: '12px', fontFamily: 'monospace', maxWidth: 180, wordBreak: 'break-word'}}>
                                            {account.pageId || '—'}
                                        </td>
                                        <td style={{padding: '12px', fontFamily: 'monospace'}}>{maskToken(account.accessToken)}</td>
                                        <td style={{padding: '12px', fontFamily: 'monospace'}}>{maskToken(account.refreshToken)}</td>
                                        <td style={{padding: '12px'}}>
                                            {account.tokenExpiry ? new Date(account.tokenExpiry).toLocaleString() : '—'}
                                        </td>
                                        <td style={{padding: '12px'}}>
                                            <Chip
                                                label={account.isActive ? t('accounts.list.active') : t('accounts.list.inactive')}
                                                color={account.isActive ? 'success' : 'default'}
                                                size="small"
                                            />
                                        </td>
                                        <td style={{padding: '12px', fontFamily: 'monospace', fontSize: '12px', color: '#555'}}>
                                            {account.path || '—'}
                                        </td>
                                    </tr>
                                );
                            })}
                        </tbody>
                    </table>
                </div>
            )}
        </div>
    );
};

export default AccountsPanel;
