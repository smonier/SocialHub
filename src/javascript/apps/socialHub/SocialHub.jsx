import React, {useState} from 'react';
import {useTranslation} from 'react-i18next';
import {ApolloProvider} from '@apollo/client';
import {Apps} from '@jahia/moonstone/dist/icons';
import apolloClient from '../../apollo/client';
import PostsPanel from './PostsPanel';
import CalendarPanel from './CalendarPanel';
import InsightsPanel from './InsightsPanel';
import ProxyTestPanel from './ProxyTestPanel';
import ActivityLogPanel from './ActivityLogPanel';
import AccountsPanel from './AccountsPanel';
import {Button as MoonstoneButton} from '@jahia/moonstone';

/* eslint-disable complexity */
const SocialHub = () => {
    const {t} = useTranslation('SocialHub');
    const [activeTab, setActiveTab] = useState('posts');
    const [activities, setActivities] = useState([]);
    const [refreshCounter, setRefreshCounter] = useState(0);

    // Keep only last 50 activities
    const MAX_ACTIVITIES = 50;

    const handleRequestComplete = activity => {
        setActivities(prev => {
            const updated = [activity, ...prev];
            return updated.slice(0, MAX_ACTIVITIES);
        });
    };

    const handleRefreshAll = () => {
        setRefreshCounter(prev => prev + 1);
    };

    return (
        <ApolloProvider client={apolloClient}>
            <div style={{
                display: 'flex',
                flexDirection: 'column',
                height: '100vh',
                backgroundColor: '#fff'
            }}
            >
                {/* Header */}
                <div
                    style={{
                        padding: '24px 32px',
                        borderBottom: '1px solid #d0d0d0',
                        backgroundColor: '#fafafa'
                    }}
                >
                    <div style={{display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '12px'}}>
                        <div style={{display: 'flex', alignItems: 'center', gap: '12px'}}>
                            <Apps size="big" style={{color: '#007cb0'}}/>
                            <h1 style={{margin: 0, fontSize: '24px', fontWeight: '600', color: '#293136'}}>
                                {t('app.title')}
                            </h1>
                        </div>
                        <MoonstoneButton
                            label={t('app.refresh')}
                            size="big"
                            color="accent"
                            variant="outlined"
                            onClick={handleRefreshAll}
                        />
                    </div>
                    <p style={{margin: 0, fontSize: '14px', color: '#676767'}}>
                        {t('app.description')}
                    </p>
                </div>

                {/* Tab Navigation */}
                <div style={{
                    display: 'flex',
                    gap: '8px',
                    padding: '16px 32px',
                    borderBottom: '1px solid #d0d0d0',
                    backgroundColor: '#fff'
                }}
                >
                    <button
                        type="button"
                        style={{
                            padding: '8px 16px',
                            border: 'none',
                            borderBottom: activeTab === 'posts' ? '2px solid #007cb0' : 'none',
                            backgroundColor: activeTab === 'posts' ? '#f0f0f0' : 'transparent',
                            color: activeTab === 'posts' ? '#007cb0' : '#676767',
                            cursor: 'pointer',
                            fontWeight: activeTab === 'posts' ? '600' : 'normal',
                            transition: 'all 0.2s'
                        }}
                        onClick={() => setActiveTab('posts')}
                    >
                        {t('tabs.posts')}
                    </button>
                    <button
                        type="button"
                        style={{
                            padding: '8px 16px',
                            border: 'none',
                            borderBottom: activeTab === 'calendar' ? '2px solid #007cb0' : 'none',
                            backgroundColor: activeTab === 'calendar' ? '#f0f0f0' : 'transparent',
                            color: activeTab === 'calendar' ? '#007cb0' : '#676767',
                            cursor: 'pointer',
                            fontWeight: activeTab === 'calendar' ? '600' : 'normal',
                            transition: 'all 0.2s'
                        }}
                        onClick={() => setActiveTab('calendar')}
                    >
                        {t('tabs.calendar')}
                    </button>
                    <button
                        type="button"
                        style={{
                            padding: '8px 16px',
                            border: 'none',
                            borderBottom: activeTab === 'insights' ? '2px solid #007cb0' : 'none',
                            backgroundColor: activeTab === 'insights' ? '#f0f0f0' : 'transparent',
                            color: activeTab === 'insights' ? '#007cb0' : '#676767',
                            cursor: 'pointer',
                            fontWeight: activeTab === 'insights' ? '600' : 'normal',
                            transition: 'all 0.2s'
                        }}
                        onClick={() => setActiveTab('insights')}
                    >
                        {t('tabs.insights')}
                    </button>
                    <button
                        type="button"
                        style={{
                            padding: '8px 16px',
                            border: 'none',
                            borderBottom: activeTab === 'accounts' ? '2px solid #007cb0' : 'none',
                            backgroundColor: activeTab === 'accounts' ? '#f0f0f0' : 'transparent',
                            color: activeTab === 'accounts' ? '#007cb0' : '#676767',
                            cursor: 'pointer',
                            fontWeight: activeTab === 'accounts' ? '600' : 'normal',
                            transition: 'all 0.2s'
                        }}
                        onClick={() => setActiveTab('accounts')}
                    >
                        {t('tabs.accounts')}
                    </button>
                    <button
                        type="button"
                        style={{
                            padding: '8px 16px',
                            border: 'none',
                            borderBottom: activeTab === 'proxy-test' ? '2px solid #007cb0' : 'none',
                            backgroundColor: activeTab === 'proxy-test' ? '#f0f0f0' : 'transparent',
                            color: activeTab === 'proxy-test' ? '#007cb0' : '#676767',
                            cursor: 'pointer',
                            fontWeight: activeTab === 'proxy-test' ? '600' : 'normal',
                            transition: 'all 0.2s'
                        }}
                        onClick={() => setActiveTab('proxy-test')}
                    >
                        {t('tabs.proxyTest')}
                    </button>
                    <button
                        type="button"
                        style={{
                            padding: '8px 16px',
                            border: 'none',
                            borderBottom: activeTab === 'activity-log' ? '2px solid #007cb0' : 'none',
                            backgroundColor: activeTab === 'activity-log' ? '#f0f0f0' : 'transparent',
                            color: activeTab === 'activity-log' ? '#007cb0' : '#676767',
                            cursor: 'pointer',
                            fontWeight: activeTab === 'activity-log' ? '600' : 'normal',
                            transition: 'all 0.2s',
                            position: 'relative'
                        }}
                        onClick={() => setActiveTab('activity-log')}
                    >
                        {t('tabs.activityLog')}
                        {activities.length > 0 && (
                            <span style={{
                                position: 'absolute',
                                top: '4px',
                                right: '4px',
                                backgroundColor: '#007cb0',
                                color: '#fff',
                                borderRadius: '10px',
                                padding: '2px 6px',
                                fontSize: '10px',
                                fontWeight: 'bold'
                            }}
                            >
                                {activities.length}
                            </span>
                        )}
                    </button>
                </div>

                {/* Content Area */}
                <div style={{
                    flex: 1,
                    overflow: 'auto',
                    backgroundColor: '#fff'
                }}
                >
                    {activeTab === 'posts' && (
                        <PostsPanel key={`posts-${refreshCounter}`}/>
                    )}
                    {activeTab === 'calendar' && (
                        <CalendarPanel key={`calendar-${refreshCounter}`}/>
                    )}
                    {activeTab === 'insights' && (
                        <InsightsPanel key={`insights-${refreshCounter}`} refreshCounter={refreshCounter}/>
                    )}
                    {activeTab === 'accounts' && (
                        <AccountsPanel key={`accounts-${refreshCounter}`}/>
                    )}
                    {activeTab === 'proxy-test' && (
                        <ProxyTestPanel key={`proxy-${refreshCounter}`} onRequestComplete={handleRequestComplete}/>
                    )}
                    {activeTab === 'activity-log' && (
                        <ActivityLogPanel key={`activity-${refreshCounter}`}/>
                    )}
                </div>
            </div>
        </ApolloProvider>
    );
};

export default SocialHub;
