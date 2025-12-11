import React, {useState, useEffect} from 'react';
import {useTranslation} from 'react-i18next';
import {Typography, Button} from '@jahia/moonstone';
import {Check, Warning} from '@jahia/moonstone/dist/icons';
import {useQuery, useMutation} from '@apollo/client';
import {GET_ACTIVITY_LOGS, DELETE_ACTIVITY_LOGS} from './queries';

const ActivityLogPanel = () => {
    const {t} = useTranslation('SocialHub');
    const [activities, setActivities] = useState([]);

    // Get current site from Jahia context
    const siteKey = window.contextJsParameters?.siteKey || 'digitall';
    const activityLogsPath = `/sites/${siteKey}/activity-logs`;

    const {data, loading, error, refetch} = useQuery(GET_ACTIVITY_LOGS, {
        variables: {path: activityLogsPath},
        fetchPolicy: 'network-only'
    });

    const [deleteAllLogs] = useMutation(DELETE_ACTIVITY_LOGS, {
        onCompleted: () => {
            refetch();
        },
        onError: err => {
            console.error('Failed to delete logs:', err);
        }
    });

    useEffect(() => {
        if (data?.jcr?.nodeByPath?.children?.nodes) {
            const logs = data.jcr.nodeByPath.children.nodes.map(node => ({
                id: node.uuid,
                timestamp: node.properties.find(p => p.name === 'social:timestamp')?.value || '',
                action: node.properties.find(p => p.name === 'social:action')?.value || '',
                postTitle: node.properties.find(p => p.name === 'social:postTitle')?.value || 'N/A',
                platform: node.properties.find(p => p.name === 'social:platform')?.value || 'All',
                status: node.properties.find(p => p.name === 'social:status')?.value || '',
                message: node.properties.find(p => p.name === 'social:message')?.value || '',
                errorMessage: node.properties.find(p => p.name === 'social:errorMessage')?.value || null
            }));
            setActivities(logs.reverse()); // Most recent first
        } else if (data?.jcr?.nodeByPath === null) {
            // Folder doesn't exist yet - show empty state instead of error
            setActivities([]);
        }
    }, [data]);

    const formatTimestamp = timestamp => {
        const date = new Date(timestamp);
        return date.toLocaleString();
    };

    const getActionColor = action => {
        switch (action) {
            case 'publish_success':
                return '#2e7d32';
            case 'publish_failure':
                return '#c62828';
            case 'publish_attempt':
                return '#1976d2';
            case 'schedule':
                return '#f57c00';
            case 'rule_fired':
                return '#7b1fa2';
            default:
                return '#676767';
        }
    };

    const getActionIcon = action => {
        if (action.includes('success')) {
            return <Check size="small" style={{color: '#2e7d32'}}/>;
        }

        if (action.includes('failure')) {
            return <Warning size="small" style={{color: '#c62828'}}/>;
        }

        return <Check size="small" style={{color: '#1976d2'}}/>;
    };

    const handleFlushLogs = async () => {
        // eslint-disable-next-line no-alert
        if (window.confirm(t('activityLog.confirmFlush'))) {
            try {
                await deleteAllLogs({
                    variables: {
                        parentPath: `/sites/${siteKey}`
                    }
                });
            } catch {
                // Error already logged in onError callback
            }
        }
    };

    return (
        <div style={{padding: '24px'}}>
            <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px'}}>
                <Typography variant="subheading" weight="bold">
                    {t('activityLog.title')}
                </Typography>
                <div style={{display: 'flex', gap: '8px'}}>
                    <Button
                        label={t('activityLog.refresh')}
                        variant="outlined"
                        disabled={loading}
                        onClick={() => refetch()}
                    />
                    <Button
                        label={t('activityLog.flushLogs')}
                        variant="outlined"
                        color="danger"
                        disabled={loading || activities.length === 0}
                        onClick={handleFlushLogs}
                    />
                </div>
            </div>

            <Typography variant="body" style={{marginBottom: '24px', color: '#676767'}}>
                {t('activityLog.description')}
            </Typography>

            {loading && (
                <div style={{padding: '48px 24px', textAlign: 'center'}}>
                    <Typography variant="body">Loading activity logs...</Typography>
                </div>
            )}

            {error && !error.message.includes('PathNotFoundException') && (
                <div style={{padding: '24px', backgroundColor: '#ffebee', borderRadius: '4px', marginBottom: '16px'}}>
                    <Typography variant="body" style={{color: '#c62828'}}>
                        Error loading logs: {error.message}
                    </Typography>
                </div>
            )}

            {!loading && !error && activities.length === 0 ? (
                <div style={{
                    padding: '48px 24px',
                    textAlign: 'center',
                    backgroundColor: '#f5f5f5',
                    borderRadius: '4px'
                }}
                >
                    <Typography variant="body" style={{color: '#9e9e9e'}}>
                        {t('activityLog.empty')}
                    </Typography>
                </div>
            ) : (
                !loading && activities.length > 0 && (
                    <div style={{
                        border: '1px solid #d0d0d0',
                        borderRadius: '4px',
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
                                    <th style={{
                                        padding: '12px',
                                        textAlign: 'left',
                                        fontWeight: 'bold',
                                        borderBottom: '1px solid #d0d0d0'
                                    }}
                                    >
                                        {t('activityLog.columns.action')}
                                    </th>
                                    <th style={{
                                        padding: '12px',
                                        textAlign: 'left',
                                        fontWeight: 'bold',
                                        borderBottom: '1px solid #d0d0d0'
                                    }}
                                    >
                                        {t('activityLog.columns.post')}
                                    </th>
                                    <th style={{
                                        padding: '12px',
                                        textAlign: 'left',
                                        fontWeight: 'bold',
                                        borderBottom: '1px solid #d0d0d0'
                                    }}
                                    >
                                        {t('activityLog.columns.platform')}
                                    </th>
                                    <th style={{
                                        padding: '12px',
                                        textAlign: 'left',
                                        fontWeight: 'bold',
                                        borderBottom: '1px solid #d0d0d0'
                                    }}
                                    >
                                        {t('activityLog.columns.message')}
                                    </th>
                                    <th style={{
                                        padding: '12px',
                                        textAlign: 'left',
                                        fontWeight: 'bold',
                                        borderBottom: '1px solid #d0d0d0'
                                    }}
                                    >
                                        {t('activityLog.columns.timestamp')}
                                    </th>
                                </tr>
                            </thead>
                            <tbody>
                                {activities.map((activity, activityIndex) => (
                                    <tr
                                        key={activity.id}
                                        style={{
                                            borderBottom: activityIndex < activities.length - 1 ? '1px solid #e0e0e0' : 'none',
                                            backgroundColor: activityIndex % 2 === 0 ? '#fff' : '#fafafa'
                                        }}
                                    >
                                        <td style={{padding: '12px'}}>
                                            <div style={{display: 'flex', alignItems: 'center', gap: '8px'}}>
                                                {getActionIcon(activity.action)}
                                                <span style={{
                                                    color: getActionColor(activity.action),
                                                    fontWeight: '500',
                                                    fontSize: '11px',
                                                    textTransform: 'uppercase'
                                                }}
                                                >
                                                    {activity.action.replace(/_/g, ' ')}
                                                </span>
                                            </div>
                                        </td>
                                        <td style={{
                                            padding: '12px',
                                            maxWidth: '200px',
                                            overflow: 'hidden',
                                            textOverflow: 'ellipsis',
                                            whiteSpace: 'nowrap'
                                        }}
                                        >
                                            {activity.postTitle}
                                        </td>
                                        <td style={{padding: '12px'}}>
                                            <span style={{
                                                padding: '2px 8px',
                                                backgroundColor: '#e3f2fd',
                                                borderRadius: '4px',
                                                fontSize: '11px',
                                                fontWeight: '500'
                                            }}
                                            >
                                                {activity.platform}
                                            </span>
                                        </td>
                                        <td style={{
                                            padding: '12px',
                                            maxWidth: '300px'
                                        }}
                                        >
                                            <div>
                                                {activity.message}
                                            </div>
                                            {activity.errorMessage && (
                                                <div style={{
                                                    marginTop: '4px',
                                                    padding: '4px 8px',
                                                    backgroundColor: '#ffebee',
                                                    borderRadius: '4px',
                                                    fontSize: '11px',
                                                    color: '#c62828'
                                                }}
                                                >
                                                    Error: {activity.errorMessage}
                                                </div>
                                            )}
                                        </td>
                                        <td style={{
                                            padding: '12px',
                                            color: '#676767',
                                            fontSize: '12px',
                                            whiteSpace: 'nowrap'
                                        }}
                                        >
                                            {formatTimestamp(activity.timestamp)}
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )
            )}

            {!loading && activities.length > 0 && (
                <Typography variant="caption" style={{marginTop: '16px', color: '#9e9e9e'}}>
                    Showing {activities.length} activity log entr{activities.length !== 1 ? 'ies' : 'y'}
                </Typography>
            )}
        </div>
    );
};

export default ActivityLogPanel;
