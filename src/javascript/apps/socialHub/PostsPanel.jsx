import React, {useCallback} from 'react';
import {useTranslation} from 'react-i18next';
import {useQuery} from '@apollo/client';
import {
    Typography,
    Table,
    TableBody,
    TableBodyCell,
    TableHead,
    TableHeadCell,
    TableRow,
    Chip,
    Loader,
    Button
} from '@jahia/moonstone';
import {Check, Warning, Clock, Edit} from '@jahia/moonstone/dist/icons';
import {FaInstagram, FaFacebook, FaLinkedin} from 'react-icons/fa';
import {GET_SOCIAL_POSTS} from './queries';

const PostsPanel = () => {
    const {t} = useTranslation('SocialHub');
    // Get current site from Jahia context
    const siteKey = window.contextJsParameters?.siteKey || 'digitall';
    const POSTS_PATH = `/sites/${siteKey}/contents/social-posts`;

    const {loading, error, data} = useQuery(GET_SOCIAL_POSTS, {
        variables: {path: POSTS_PATH},
        pollInterval: 30000 // Refresh every 30 seconds
    });

    const handleEditContent = useCallback(post => {
        if (!post?.uuid) {
            console.error('Error: Post ID is missing.');
            return;
        }

        // Open Jahia Content Editor
        parent.top.window.CE_API.edit(
            {uuid: post.uuid},
            () => {}, // No need for a first callback
            () => {
                console.log('Content editor closed, reloading page...');
                window.location.reload();
            }
        );
    }, []);

    const formatDate = dateString => {
        if (!dateString) {
            return '-';
        }

        const date = new Date(dateString);
        return date.toLocaleString();
    };

    // Determine publication status based on JCR properties
    const getPublicationStatus = post => {
        // If j:published doesn't exist, never published
        if (post.published === null || post.published === undefined) {
            return 'never-published';
        }

        // If j:published = true
        if (post.published) {
            // Check if modified after last published
            if (post.lastModified && post.lastPublished) {
                const lastModified = new Date(post.lastModified);
                const lastPublished = new Date(post.lastPublished);
                if (lastModified > lastPublished) {
                    return 'published-modified';
                }
            }

            return 'published';
        }

        return 'never-published';
    };

    const getStatusIcon = status => {
        switch (status) {
            case 'published':
                return <Check size="small" style={{color: '#2e7d32'}}/>;
            case 'published-modified':
                return <Edit size="small" style={{color: '#f57c00'}}/>;
            case 'never-published':
                return <Warning size="small" style={{color: '#9e9e9e'}}/>;
            case 'scheduled':
                return <Clock size="small" style={{color: '#1976d2'}}/>;
            case 'failed':
                return <Warning size="small" style={{color: '#c62828'}}/>;
            case 'draft':
            default:
                return <Edit size="small" style={{color: '#9e9e9e'}}/>;
        }
    };

    const getStatusColor = status => {
        switch (status) {
            case 'published':
                return 'success';
            case 'published-modified':
                return 'warning';
            case 'never-published':
                return 'default';
            case 'scheduled':
                return 'accent';
            case 'failed':
                return 'danger';
            case 'draft':
            default:
                return 'default';
        }
    };

    const getSocialStatusColor = status => {
        switch (status) {
            case 'published':
                return 'success';
            case 'scheduled':
                return 'accent';
            case 'failed':
                return 'danger';
            case 'draft':
            default:
                return 'default';
        }
    };

    const getSocialStatusLabel = status => {
        switch (status) {
            case 'published':
                return 'Social Published';
            case 'scheduled':
                return 'Scheduled';
            case 'failed':
                return 'Failed';
            case 'draft':
            default:
                return 'Draft';
        }
    };

    const getStatusLabel = status => {
        switch (status) {
            case 'published':
                return 'Published';
            case 'published-modified':
                return 'Published & Modified';
            case 'never-published':
                return 'Never Published';
            case 'scheduled':
                return 'Scheduled';
            case 'failed':
                return 'Failed';
            case 'draft':
            default:
                return 'Draft';
        }
    };

    // Parse properties from GraphQL response
    const parsePost = node => {
        const post = {
            uuid: node.uuid,
            name: node.name,
            title: '',
            status: 'draft',
            scheduledAt: null,
            lastModified: null,
            lastPublished: null,
            published: null,
            lastPublishedBy: null,
            platform: null
        };

        node.properties.forEach(prop => {
            switch (prop.name) {
                case 'social:title':
                    post.title = prop.value;
                    break;
                case 'social:status':
                    post.status = prop.value;
                    break;
                case 'social:scheduledAt':
                    post.scheduledAt = prop.value;
                    break;
                case 'jcr:lastModified':
                    post.lastModified = prop.value;
                    break;
                case 'j:lastPublished':
                    post.lastPublished = prop.value;
                    break;
                case 'j:published':
                    post.published = prop.value === 'true' || prop.value === true;
                    break;
                case 'j:lastPublishedBy':
                    post.lastPublishedBy = prop.value;
                    break;
                case 'social:platform':
                    post.platform = prop.value || null;
                    break;
                default:
                    break;
            }
        });

        return post;
    };

    if (loading) {
        return (
            <div style={{padding: '48px', textAlign: 'center'}}>
                <Loader size="big"/>
                <Typography variant="body" style={{marginTop: '16px', color: '#676767'}}>
                    {t('posts.loading')}
                </Typography>
            </div>
        );
    }

    if (error) {
        return (
            <div style={{padding: '24px'}}>
                <div style={{
                    padding: '16px',
                    backgroundColor: '#ffebee',
                    borderRadius: '4px',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '12px'
                }}
                >
                    <Warning style={{color: '#c62828'}}/>
                    <div>
                        <Typography variant="subheading" style={{color: '#c62828'}}>
                            {t('posts.error')}
                        </Typography>
                        <Typography variant="body" style={{color: '#676767', marginTop: '8px'}}>
                            {error.message}
                        </Typography>
                    </div>
                </div>
            </div>
        );
    }

    const posts = data?.jcr?.nodeByPath?.children?.nodes?.map(parsePost) || [];

    if (posts.length === 0) {
        return (
            <div style={{padding: '24px'}}>
                <div style={{
                    padding: '48px 24px',
                    textAlign: 'center',
                    backgroundColor: '#f5f5f5',
                    borderRadius: '4px'
                }}
                >
                    <Typography variant="body" style={{color: '#9e9e9e', marginBottom: '8px'}}>
                        {t('posts.empty')}
                    </Typography>
                    <Typography variant="caption" style={{color: '#9e9e9e'}}>
                        {t('posts.emptyDescription')}
                    </Typography>
                </div>
            </div>
        );
    }

    return (
        <div style={{padding: '24px'}}>
            <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px'}}>
                <Typography variant="subheading" weight="bold">
                    Social Posts ({posts.length})
                </Typography>
            </div>
            <Typography variant="body" style={{marginBottom: '24px', color: '#676767'}}>
                Browse and manage social media posts from the JCR repository
            </Typography>

            <div style={{
                border: '1px solid #d0d0d0',
                borderRadius: '4px',
                overflow: 'hidden'
            }}
            >
                <Table>
                    <TableHead>
                        <TableRow>
                            <TableHeadCell width="60px">Edit</TableHeadCell>
                            <TableHeadCell>{t('posts.columns.contentStatus')}</TableHeadCell>
                            <TableHeadCell>{t('posts.columns.socialStatus')}</TableHeadCell>
                            <TableHeadCell>{t('posts.columns.title')}</TableHeadCell>
                            <TableHeadCell>{t('posts.columns.platforms')}</TableHeadCell>
                            <TableHeadCell>{t('posts.columns.scheduledAt')}</TableHeadCell>
                            <TableHeadCell>Last Modified</TableHeadCell>
                            <TableHeadCell>Last Published</TableHeadCell>
                            <TableHeadCell>Published By</TableHeadCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {posts.map(post => {
                            const pubStatus = getPublicationStatus(post);
                            const socialStatus = post.status || 'draft';
                            return (
                                <TableRow key={post.uuid}>
                                    <TableBodyCell width="60px">
                                        <Button
                                            icon={<Edit size="small"/>}
                                            variant="ghost"
                                            size="small"
                                            aria-label="Edit post"
                                            onClick={() => handleEditContent(post)}
                                        />
                                    </TableBodyCell>
                                    <TableBodyCell>
                                        <div style={{display: 'flex', alignItems: 'center', gap: '8px'}}>
                                            {getStatusIcon(pubStatus)}
                                            <Chip
                                                label={getStatusLabel(pubStatus)}
                                                color={getStatusColor(pubStatus)}
                                                size="small"
                                            />
                                        </div>
                                    </TableBodyCell>
                                    <TableBodyCell>
                                        <Chip
                                            label={getSocialStatusLabel(socialStatus)}
                                            color={getSocialStatusColor(socialStatus)}
                                            size="small"
                                        />
                                    </TableBodyCell>
                                    <TableBodyCell>
                                        <Typography variant="body" weight="bold" style={{whiteSpace: 'normal', wordBreak: 'break-word'}}>
                                            {post.title || post.name}
                                        </Typography>
                                    </TableBodyCell>
                                    <TableBodyCell>
                                        <div style={{display: 'flex', gap: '6px', flexWrap: 'wrap'}}>
                                            {post.platform && (() => {
                                            const platformLower = post.platform.toLowerCase();
                                            let icon = null;
                                            let badgeColor = 'default';

                                            if (platformLower === 'instagram') {
                                                icon = <FaInstagram size={16}/>;
                                                badgeColor = '#E1306C';
                                            } else if (platformLower === 'facebook') {
                                                icon = <FaFacebook size={16}/>;
                                                badgeColor = '#1877F2';
                                            } else if (platformLower === 'linkedin') {
                                                icon = <FaLinkedin size={16}/>;
                                                badgeColor = '#0A66C2';
                                            }

                                            return (
                                                <div
                                                    key={`${post.uuid}-${post.platform}`}
                                                    style={{
                                                        display: 'inline-flex',
                                                        alignItems: 'center',
                                                        justifyContent: 'center',
                                                        width: '28px',
                                                        height: '28px',
                                                        backgroundColor: badgeColor !== 'default' ? badgeColor : '#e0e0e0',
                                                        color: badgeColor !== 'default' ? '#fff' : '#000',
                                                        borderRadius: '50%',
                                                        fontSize: '16px'
                                                    }}
                                                    title={post.platform}
                                                >
                                                    {icon}
                                                </div>
                                            );
                                        })()}
                                        </div>
                                    </TableBodyCell>
                                    <TableBodyCell>
                                        <Typography variant="caption" style={{color: '#676767'}}>
                                            {formatDate(post.scheduledAt)}
                                        </Typography>
                                    </TableBodyCell>
                                    <TableBodyCell>
                                        <Typography variant="caption" style={{color: '#676767'}}>
                                            {formatDate(post.lastModified)}
                                        </Typography>
                                    </TableBodyCell>
                                    <TableBodyCell>
                                        <Typography variant="caption" style={{color: '#676767'}}>
                                            {formatDate(post.lastPublished)}
                                        </Typography>
                                    </TableBodyCell>
                                    <TableBodyCell>
                                        <Typography variant="caption" style={{color: '#676767'}}>
                                            {post.lastPublishedBy || '-'}
                                        </Typography>
                                    </TableBodyCell>
                                </TableRow>
                            );
                        })}
                    </TableBody>
                </Table>
            </div>
        </div>
    );
};

export default PostsPanel;
