import React, {useState, useEffect} from 'react';
import PropTypes from 'prop-types';
import {useTranslation} from 'react-i18next';
import {useQuery} from '@apollo/client';
import {Typography} from '@jahia/moonstone';
import {FaFacebook, FaInstagram, FaLinkedin, FaEye, FaMousePointer, FaHeart, FaComment, FaShare, FaUsers} from 'react-icons/fa';
import {GET_SOCIAL_POSTS} from './queries';

const InsightsPanel = ({refreshCounter}) => {
    const {t} = useTranslation('SocialHub');
    const [selectedPost, setSelectedPost] = useState(null);
    const [insights, setInsights] = useState(null);
    const [loading, setLoading] = useState(false);

    // Get siteKey from window context like other panels
    const siteKey = window.contextJsParameters?.siteKey || 'digitall';
    const postsPath = `/sites/${siteKey}/contents/social-posts`;

    const {data: postsData, loading: postsLoading, refetch} = useQuery(GET_SOCIAL_POSTS, {
        variables: {path: postsPath},
        fetchPolicy: 'network-only'
    });

    // Debug: Log raw GraphQL response
    useEffect(() => {
        console.log('[InsightsPanel] siteKey:', siteKey);
        console.log('[InsightsPanel] GraphQL postsData:', postsData);
        console.log('[InsightsPanel] postsLoading:', postsLoading);
        console.log('[InsightsPanel] postsPath:', postsPath);
    }, [siteKey, postsData, postsLoading, postsPath]);

    useEffect(() => {
        if (refreshCounter > 0) {
            refetch();
        }
    }, [refreshCounter, refetch]);

    // Parse posts with externalId (published posts)
    const publishedPosts = React.useMemo(() => {
        console.log('[InsightsPanel] useMemo triggered, postsData:', postsData);

        if (!postsData?.jcr?.nodeByPath?.children?.nodes) {
            console.log('[InsightsPanel] No nodes found in GraphQL response');
            return [];
        }

        const allPosts = postsData.jcr.nodeByPath.children.nodes
            .map(node => {
                const post = {
                    uuid: node.uuid,
                    name: node.name,
                    title: null,
                    platform: null,
                    externalId: null,
                    status: null
                };

                node.properties.forEach(prop => {
                    switch (prop.name) {
                        case 'social:title':
                            post.title = prop.value;
                            break;
                        case 'social:platform':
                            post.platform = prop.value;
                            break;
                        case 'social:externalId':
                            post.externalId = prop.value;
                            break;
                        case 'social:status':
                            post.status = prop.value;
                            break;
                        default:
                            break;
                    }
                });

                return post;
            });

        // Debug logging
        console.log('[InsightsPanel] All posts:', allPosts);
        console.log('[InsightsPanel] Posts with externalId:', allPosts.filter(p => p.externalId));
        console.log('[InsightsPanel] Posts with status=published:', allPosts.filter(p => p.status === 'published'));
        console.log('[InsightsPanel] Posts with valid platform:', allPosts.filter(p => ['facebook', 'instagram', 'linkedin'].includes(p.platform)));

        const filtered = allPosts.filter(post => post.externalId && post.status === 'published' && ['facebook', 'instagram', 'linkedin'].includes(post.platform));
        console.log('[InsightsPanel] Filtered published posts:', filtered);

        return filtered;
    }, [postsData]);

    const fetchInsights = async post => {
        setLoading(true);
        setSelectedPost(post);

        try {
            // ExternalId now contains only the post ID (platform determines which API to call)
            const postId = post.externalId;

            // Call backend service to fetch insights with platform and postId
            const response = await fetch(`/modules/api/social/insights/${post.platform}/${postId}`, {
                method: 'GET',
                headers: {
                    'Content-Type': 'application/json'
                }
            });

            if (response.ok) {
                const data = await response.json();
                setInsights(data);
            } else {
                console.error('Failed to fetch insights:', response.statusText);
                setInsights(null);
            }
        } catch (error) {
            console.error('Error fetching insights:', error);
            setInsights(null);
        } finally {
            setLoading(false);
        }
    };

    const MetricCard = ({icon: Icon, label, value, color}) => (
        <div style={{
            flex: 1,
            minWidth: '200px',
            padding: '20px',
            backgroundColor: '#fff',
            border: '1px solid #e0e0e0',
            borderRadius: '8px',
            display: 'flex',
            flexDirection: 'column',
            gap: '12px'
        }}
        >
            <div style={{display: 'flex', alignItems: 'center', gap: '8px'}}>
                <Icon size={20} style={{color}}/>
                <Typography variant="caption" style={{color: '#676767', textTransform: 'uppercase', fontSize: '11px', fontWeight: '600'}}>
                    {label}
                </Typography>
            </div>
            <Typography variant="heading" style={{fontSize: '32px', fontWeight: '700', color: '#293136'}}>
                {value !== null && value !== undefined ? value.toLocaleString() : '—'}
            </Typography>
        </div>
    );

    MetricCard.propTypes = {
        icon: PropTypes.elementType.isRequired,
        label: PropTypes.string.isRequired,
        value: PropTypes.number,
        color: PropTypes.string.isRequired
    };

    if (postsLoading) {
        return (
            <div style={{padding: '24px', textAlign: 'center'}}>
                <Typography variant="body">{t('insights.loading')}</Typography>
            </div>
        );
    }

    return (
        <div style={{display: 'flex', height: '100%', overflow: 'hidden'}}>
            {/* Left Sidebar - Posts List */}
            <div style={{
                width: '320px',
                borderRight: '1px solid #d0d0d0',
                backgroundColor: '#fafafa',
                overflow: 'auto'
            }}
            >
                <div style={{padding: '16px', borderBottom: '1px solid #d0d0d0', backgroundColor: '#fff'}}>
                    <Typography variant="subheading" weight="bold">
                        {t('insights.publishedPosts')}
                    </Typography>
                    <Typography variant="caption" style={{color: '#676767', marginTop: '4px'}}>
                        {publishedPosts.length} {publishedPosts.length === 1 ? t('insights.post') : t('insights.posts')}
                    </Typography>
                </div>

                {publishedPosts.length === 0 ? (
                    <div style={{padding: '24px', textAlign: 'center'}}>
                        <Typography variant="body" style={{color: '#9e9e9e'}}>
                            {t('insights.noPosts')}
                        </Typography>
                    </div>
                ) : (
                    <div style={{padding: '8px'}}>
                        {publishedPosts.map(post => (
                            <div
                                key={post.uuid}
                                style={{
                                    padding: '12px',
                                    marginBottom: '8px',
                                    backgroundColor: selectedPost?.uuid === post.uuid ? '#e3f2fd' : '#fff',
                                    border: selectedPost?.uuid === post.uuid ? '2px solid #007cb0' : '1px solid #e0e0e0',
                                    borderRadius: '6px',
                                    cursor: 'pointer',
                                    transition: 'all 0.2s'
                                }}
                                onClick={() => fetchInsights(post)}
                            >
                                <div style={{display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '6px'}}>
                                    {post.platform === 'facebook' && <FaFacebook size={16} style={{color: '#1877F2'}}/>}
                                    {post.platform === 'instagram' && <FaInstagram size={16} style={{color: '#E1306C'}}/>}
                                    {post.platform === 'linkedin' && <FaLinkedin size={16} style={{color: '#0A66C2'}}/>}
                                    <Typography variant="body" weight="bold" style={{fontSize: '14px'}}>
                                        {post.title || post.name}
                                    </Typography>
                                </div>
                                <Typography variant="caption" style={{color: '#676767', fontSize: '11px'}}>
                                    ID: {post.externalId}
                                </Typography>
                            </div>
                        ))}
                    </div>
                )}
            </div>

            {/* Right Panel - Insights Display */}
            <div style={{flex: 1, overflow: 'auto', backgroundColor: '#f5f5f5'}}>
                {!selectedPost ? (
                    <div style={{
                        height: '100%',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        padding: '48px'
                    }}
                    >
                        <div style={{textAlign: 'center', maxWidth: '400px'}}>
                            <FaEye size={64} style={{color: '#d0d0d0', marginBottom: '16px'}}/>
                            <Typography variant="heading" style={{marginBottom: '8px'}}>
                                {t('insights.selectPost')}
                            </Typography>
                            <Typography variant="body" style={{color: '#676767'}}>
                                {t('insights.selectPostDescription')}
                            </Typography>
                        </div>
                    </div>
                ) : loading ? (
                    <div style={{padding: '48px', textAlign: 'center'}}>
                        <Typography variant="heading">{t('insights.fetchingData')}</Typography>
                    </div>
                ) : insights ? (
                    <div style={{padding: '24px'}}>
                        {/* Header */}
                        <div style={{
                            padding: '24px',
                            backgroundColor: '#fff',
                            borderRadius: '8px',
                            marginBottom: '24px',
                            border: '1px solid #e0e0e0'
                        }}
                        >
                            <div style={{display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '8px'}}>
                                {selectedPost.platform === 'facebook' && <FaFacebook size={24} style={{color: '#1877F2'}}/>}
                                {selectedPost.platform === 'instagram' && <FaInstagram size={24} style={{color: '#E1306C'}}/>}
                                {selectedPost.platform === 'linkedin' && <FaLinkedin size={24} style={{color: '#0A66C2'}}/>}
                                <Typography variant="heading" style={{fontSize: '24px'}}>
                                    {selectedPost.title || selectedPost.name}
                                </Typography>
                            </div>
                            <Typography variant="caption" style={{color: '#676767'}}>
                                Post ID: {selectedPost.externalId}
                            </Typography>
                        </div>

                        {/* Metrics Grid */}
                        <div>
                            <Typography variant="subheading" weight="bold" style={{marginBottom: '16px'}}>
                                {t('insights.metrics')}
                            </Typography>
                            <div style={{
                                    display: 'flex',
                                    flexWrap: 'wrap',
                                    gap: '16px',
                                    marginBottom: '24px'
                                }}
                            >
                                <MetricCard
                                        icon={FaEye}
                                        label={t('insights.impressions')}
                                        value={insights.impressions}
                                        color="#9c27b0"
                                    />
                                <MetricCard
                                        icon={FaUsers}
                                        label={t('insights.reach')}
                                        value={insights.reach}
                                        color="#2196f3"
                                    />
                                <MetricCard
                                        icon={FaMousePointer}
                                        label={t('insights.clicks')}
                                        value={insights.clicks}
                                        color="#ff9800"
                                    />
                                <MetricCard
                                        icon={FaHeart}
                                        label={t('insights.likes')}
                                        value={insights.likes}
                                        color="#e91e63"
                                    />
                                <MetricCard
                                        icon={FaComment}
                                        label={t('insights.comments')}
                                        value={insights.comments}
                                        color="#00bcd4"
                                    />
                                <MetricCard
                                        icon={FaShare}
                                        label={t('insights.shares')}
                                        value={insights.shares}
                                        color="#4caf50"
                                    />
                            </div>

                            {/* Additional Details */}
                            {insights.engagement && (
                            <div style={{
                                        padding: '20px',
                                        backgroundColor: '#fff',
                                        border: '1px solid #e0e0e0',
                                        borderRadius: '8px'
                                    }}
                            >
                                <Typography variant="subheading" weight="bold" style={{marginBottom: '12px'}}>
                                    {t('insights.engagement')}
                                </Typography>
                                <Typography variant="body">
                                    {t('insights.engagementRate')}: <strong>{insights.engagement.rate}%</strong>
                                </Typography>
                            </div>
                                )}
                        </div>
                    </div>
                ) : (
                    <div style={{
                        padding: '48px',
                        backgroundColor: '#fff',
                        border: '1px solid #e0e0e0',
                        borderRadius: '8px',
                        textAlign: 'center',
                        margin: '24px'
                    }}
                    >
                        <Typography variant="body" style={{color: '#9e9e9e'}}>
                            {t('insights.noData')}
                        </Typography>
                        <Typography variant="caption" style={{color: '#9e9e9e', marginTop: '8px'}}>
                            {t('insights.noDataDescription')}
                        </Typography>
                    </div>
                )}
            </div>
        </div>
    );
};

InsightsPanel.propTypes = {
    refreshCounter: PropTypes.number
};

InsightsPanel.defaultProps = {
    refreshCounter: 0
};

export default InsightsPanel;
