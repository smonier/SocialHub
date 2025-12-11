import React, {useState} from 'react';
import {useTranslation} from 'react-i18next';
import {useQuery} from '@apollo/client';
import {
    Typography,
    Button,
    Chip,
    Loader
} from '@jahia/moonstone';
import {ChevronLeft, ChevronRight, Warning} from '@jahia/moonstone/dist/icons';
import {FaInstagram, FaFacebook, FaLinkedin} from 'react-icons/fa';
import {GET_SCHEDULED_POSTS} from './queries';

const CalendarPanel = () => {
    const {t} = useTranslation('SocialHub');
    // Get current site from Jahia context
    const siteKey = window.contextJsParameters?.siteKey || 'digitall';
    const POSTS_PATH = `/sites/${siteKey}/contents/social-posts`;

    const [currentDate, setCurrentDate] = useState(new Date());
    const [selectedDay, setSelectedDay] = useState(null);

    const {loading, error, data} = useQuery(GET_SCHEDULED_POSTS, {
        variables: {path: POSTS_PATH},
        pollInterval: 60000 // Refresh every minute
    });

    // Parse posts from GraphQL response
    const parsePost = node => {
        const post = {
            uuid: node.uuid,
            name: node.name,
            title: '',
            socialStatus: 'draft',
            scheduledAt: null,
            lastModified: null,
            lastPublished: null,
            published: null,
            platform: null
        };

        node.properties.forEach(prop => {
            switch (prop.name) {
                case 'social:title':
                    post.title = prop.value;
                    break;
                case 'social:status':
                    post.socialStatus = prop.value || 'draft';
                    break;
                case 'social:scheduledAt':
                    post.scheduledAt = prop.value ? new Date(prop.value) : null;
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
                case 'social:platform':
                    post.platform = prop.value || null;
                    break;
                default:
                    break;
            }
        });

        return post;
    };

    const posts = data?.jcr?.nodeByPath?.children?.nodes?.map(parsePost).filter(p => p.scheduledAt) || [];

    // Calendar logic
    const year = currentDate.getFullYear();
    const month = currentDate.getMonth();

    const firstDayOfMonth = new Date(year, month, 1);
    const lastDayOfMonth = new Date(year, month + 1, 0);
    const daysInMonth = lastDayOfMonth.getDate();
    const startingDayOfWeek = firstDayOfMonth.getDay();

    const monthNames = [
        t('calendar.months.january'),
        t('calendar.months.february'),
        t('calendar.months.march'),
        t('calendar.months.april'),
        t('calendar.months.may'),
        t('calendar.months.june'),
        t('calendar.months.july'),
        t('calendar.months.august'),
        t('calendar.months.september'),
        t('calendar.months.october'),
        t('calendar.months.november'),
        t('calendar.months.december')
    ];

    const dayNames = [
        t('calendar.days.sunday'),
        t('calendar.days.monday'),
        t('calendar.days.tuesday'),
        t('calendar.days.wednesday'),
        t('calendar.days.thursday'),
        t('calendar.days.friday'),
        t('calendar.days.saturday')
    ];

    const previousMonth = () => {
        setCurrentDate(new Date(year, month - 1, 1));
        setSelectedDay(null);
    };

    const nextMonth = () => {
        setCurrentDate(new Date(year, month + 1, 1));
        setSelectedDay(null);
    };

    // Get posts for a specific day
    const getPostsForDay = day => {
        return posts.filter(post => {
            if (!post.scheduledAt) {
                return false;
            }

            const postDate = post.scheduledAt;
            return (
                postDate.getFullYear() === year &&
                postDate.getMonth() === month &&
                postDate.getDate() === day
            );
        });
    };

    // Generate calendar grid
    const calendarDays = [];
    for (let i = 0; i < startingDayOfWeek; i++) {
        calendarDays.push(null);
    }

    for (let day = 1; day <= daysInMonth; day++) {
        calendarDays.push(day);
    }

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

    const getStatusColor = status => {
        switch (status) {
            case 'published':
                return '#2e7d32';
            case 'published-modified':
                return '#f57c00';
            case 'never-published':
                return '#9e9e9e';
            case 'scheduled':
                return '#1976d2';
            case 'failed':
                return '#c62828';
            case 'draft':
            default:
                return '#9e9e9e';
        }
    };

    const getStatusColorChip = status => {
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

    const getSocialStatusColorChip = status => {
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

    if (loading) {
        return (
            <div style={{padding: '48px', textAlign: 'center'}}>
                <Loader size="big"/>
                <Typography variant="body" style={{marginTop: '16px', color: '#676767'}}>
                    {t('calendar.loading')}
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
                            {t('calendar.error')}
                        </Typography>
                        <Typography variant="body" style={{color: '#676767', marginTop: '8px'}}>
                            {error.message}
                        </Typography>
                    </div>
                </div>
            </div>
        );
    }

    const selectedDayPosts = selectedDay ? getPostsForDay(selectedDay) : [];

    return (
        <div style={{padding: '24px'}}>
            <Typography variant="subheading" weight="bold" style={{marginBottom: '16px'}}>
                Calendar View
            </Typography>
            <Typography variant="body" style={{marginBottom: '24px', color: '#676767'}}>
                View scheduled posts by date
            </Typography>

            {/* Month Navigation */}
            <div style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                marginBottom: '24px',
                padding: '16px',
                backgroundColor: '#f5f5f5',
                borderRadius: '4px'
            }}
            >
                <Button
                    icon={<ChevronLeft/>}
                    variant="ghost"
                    size="big"
                    onClick={previousMonth}
                />
                <Typography variant="heading" component="h2">
                    {monthNames[month]} {year}
                </Typography>
                <Button
                    icon={<ChevronRight/>}
                    variant="ghost"
                    size="big"
                    onClick={nextMonth}
                />
            </div>

            {/* Calendar Grid */}
            <div style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(7, 1fr)',
                gap: '8px',
                marginBottom: '24px'
            }}
            >
                {/* Day headers */}
                {dayNames.map(day => (
                    <div key={day}
                         style={{
                        padding: '8px',
                        textAlign: 'center',
                        fontWeight: 'bold',
                        fontSize: '12px',
                        color: '#676767'
                    }}
                    >
                        {day}
                    </div>
                ))}

                {/* Calendar days */}
                {calendarDays.map(day => {
                    if (day === null) {
                        return <div key={`empty-${Math.random()}`}/>;
                    }

                    const dayPosts = getPostsForDay(day);
                    const isToday =
                        day === new Date().getDate() &&
                        month === new Date().getMonth() &&
                        year === new Date().getFullYear();
                    const isSelected = day === selectedDay;

                    return (
                        <div
                            key={`day-${year}-${month}-${day}`}
                            style={{
                                padding: '8px',
                                minHeight: '80px',
                                border: `2px solid ${isSelected ? '#007cb0' : isToday ? '#1976d2' : '#d0d0d0'}`,
                                borderRadius: '4px',
                                cursor: dayPosts.length > 0 ? 'pointer' : 'default',
                                backgroundColor: isSelected ? '#e3f2fd' : '#fff',
                                transition: 'all 0.2s',
                                ':hover': {
                                    backgroundColor: '#f5f5f5'
                                }
                            }}
                            onClick={() => setSelectedDay(day)}
                        >
                            <div style={{
                                fontSize: '14px',
                                fontWeight: isToday ? 'bold' : 'normal',
                                marginBottom: '4px',
                                color: isToday ? '#1976d2' : '#000'
                            }}
                            >
                                {day}
                            </div>
                            {dayPosts.length > 0 && (
                                <div style={{display: 'flex', flexDirection: 'column', gap: '2px'}}>
                                    {dayPosts.slice(0, 3).map(post => {
                                        const pubStatus = getPublicationStatus(post);
                                        return (
                                            <div
                                                key={`post-${post.uuid}`}
                                                style={{
                                                    fontSize: '10px',
                                                    padding: '2px 4px',
                                                    backgroundColor: getStatusColor(pubStatus),
                                                    color: '#fff',
                                                    borderRadius: '2px',
                                                    overflow: 'hidden',
                                                    textOverflow: 'ellipsis',
                                                    whiteSpace: 'nowrap',
                                                    display: 'flex',
                                                    alignItems: 'center',
                                                    gap: '2px'
                                                }}
                                                title={post.title}
                                            >
                                                {post.platform && (() => {
                                                const platformLower = post.platform.toLowerCase();
                                                if (platformLower === 'instagram') {
return <FaInstagram key={post.platform} size={8}/>;
}

                                                if (platformLower === 'facebook') {
return <FaFacebook key={post.platform} size={8}/>;
}

                                                if (platformLower === 'linkedin') {
return <FaLinkedin key={post.platform} size={8}/>;
}

                                                return null;
                                            })()}
                                                <span>{post.title || post.name}</span>
                                            </div>
                                        );
                                    })}
                                    {dayPosts.length > 3 && (
                                        <div style={{fontSize: '10px', color: '#676767', padding: '2px 4px'}}>
                                            +{dayPosts.length - 3} more
                                        </div>
                                    )}
                                </div>
                            )}
                        </div>
                    );
                })}
            </div>
            {selectedDay !== null && selectedDayPosts.length > 0 && (
                <div style={{
                    padding: '16px',
                    border: '1px solid #d0d0d0',
                    borderRadius: '4px',
                    backgroundColor: '#fafafa'
                }}
                >
                    <Typography variant="subheading" weight="bold" style={{marginBottom: '12px'}}>
                        Posts on {monthNames[month]} {selectedDay}, {year} ({selectedDayPosts.length})
                    </Typography>
                    <div style={{display: 'flex', flexDirection: 'column', gap: '12px'}}>
                        {selectedDayPosts.map(post => {
                            const pubStatus = getPublicationStatus(post);
                            const socialStatus = post.socialStatus || 'draft';
                            return (
                                <div
                                    key={post.uuid}
                                    style={{
                                        padding: '12px',
                                        backgroundColor: '#fff',
                                        border: '1px solid #d0d0d0',
                                        borderRadius: '4px'
                                    }}
                                >
                                    <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px', gap: '8px'}}>
                                        <Typography variant="body" weight="bold" style={{flex: 1}}>
                                            {post.title || post.name}
                                        </Typography>
                                        <div style={{display: 'flex', gap: '6px', flexWrap: 'wrap', justifyContent: 'flex-end'}}>
                                            <Chip
                                                label={getStatusLabel(pubStatus)}
                                                size="small"
                                                color={getStatusColorChip(pubStatus)}
                                            />
                                            <Chip
                                                label={getSocialStatusLabel(socialStatus)}
                                                size="small"
                                                color={getSocialStatusColorChip(socialStatus)}
                                            />
                                        </div>
                                    </div>
                                    <div style={{display: 'flex', gap: '4px', flexWrap: 'wrap'}}>
                                        {post.platform && (() => {
                                        const platformLower = post.platform.toLowerCase();
                                        let icon = null;
                                        let badgeColor = 'default';

                                        if (platformLower === 'instagram') {
                                            icon = <FaInstagram style={{marginRight: '4px'}}/>;
                                            badgeColor = '#E1306C';
                                        } else if (platformLower === 'facebook') {
                                            icon = <FaFacebook style={{marginRight: '4px'}}/>;
                                            badgeColor = '#1877F2';
                                        } else if (platformLower === 'linkedin') {
                                            icon = <FaLinkedin style={{marginRight: '4px'}}/>;
                                            badgeColor = '#0A66C2';
                                        }

                                        return (
                                            <div
                                                key={`${post.uuid}-${post.platform}`}
                                                style={{
                                                    display: 'inline-flex',
                                                    alignItems: 'center',
                                                    padding: '4px 8px',
                                                    backgroundColor: badgeColor !== 'default' ? badgeColor : '#e0e0e0',
                                                    color: badgeColor !== 'default' ? '#fff' : '#000',
                                                    borderRadius: '12px',
                                                    fontSize: '12px',
                                                    fontWeight: '500'
                                                }}
                                            >
                                                {icon}
                                                {post.platform}
                                            </div>
                                        );
                                    })()}
                                    </div>
                                    <Typography variant="caption" style={{marginTop: '8px', color: '#676767'}}>
                                        {post.scheduledAt.toLocaleString()}
                                    </Typography>
                                </div>
                            );
                        })}
                    </div>
                </div>
            )}
        </div>
    );
};

export default CalendarPanel;
