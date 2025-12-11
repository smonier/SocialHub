import {gql} from '@apollo/client';

// GraphQL query to fetch social posts
export const GET_SOCIAL_POSTS = gql`
    query GetSocialPosts($path: String!) {
        jcr {
            nodeByPath(path: $path) {
                children(typesFilter: { types: ["socialnt:post"] }) {
                    nodes {
                        uuid
                        name
                        primaryNodeType {
                            name
                        }
                        properties(names: [
                            "social:title",
                            "social:status",
                            "social:scheduledAt",
                            "social:platform",
                            "social:externalId",
                            "jcr:lastModified",
                            "j:lastPublished",
                            "j:published",
                            "j:lastPublishedBy"
                        ]) {
                            name
                            value
                            values
                        }
                    }
                }
            }
        }
    }
`;

// GraphQL query to fetch scheduled social posts for calendar
export const GET_SCHEDULED_POSTS = gql`
    query GetScheduledPosts($path: String!) {
        jcr {
            nodeByPath(path: $path) {
                children(typesFilter: { types: ["socialnt:post"] }) {
                    nodes {
                        uuid
                        name
                        properties(names: [
                            "social:title",
                            "social:status",
                            "social:scheduledAt",
                            "social:platform",
                            "jcr:lastModified",
                            "j:lastPublished",
                            "j:published"
                        ]) {
                            name
                            value
                            values
                        }
                    }
                }
            }
        }
    }
`;

// GraphQL query to fetch connected social accounts
export const GET_SOCIAL_ACCOUNTS = gql`
    query GetSocialAccounts {
        jcr {
            nodesByCriteria(criteria: { nodeType: "socialnt:account" }) {
                nodes {
                    uuid
                    path
                    name
                    properties(names: [
                        "social:platform",
                        "social:label",
                        "social:handle",
                        "social:pageId",
                        "social:accessToken",
                        "social:refreshToken",
                        "social:tokenExpiry",
                        "social:isActive"
                    ]) {
                        name
                        value
                        values
                    }
                }
            }
        }
    }
`;

// GraphQL query to fetch activity logs
export const GET_ACTIVITY_LOGS = gql`
    query GetActivityLogs($path: String!) {
        jcr {
            nodeByPath(path: $path) {
                children(typesFilter: { types: ["socialnt:activityLog"] }) {
                    nodes {
                        uuid
                        path
                        properties(names: [
                            "social:timestamp",
                            "social:action",
                            "social:postId",
                            "social:postTitle",
                            "social:platform",
                            "social:status",
                            "social:message",
                            "social:errorMessage",
                            "social:userId"
                        ]) {
                            name
                            value
                        }
                    }
                }
            }
        }
    }
`;

// GraphQL mutation to delete all activity logs and recreate the folder
export const DELETE_ACTIVITY_LOGS = gql`
    mutation DeleteActivityLogs($parentPath: String!) {
        jcr {
            mutateNode(pathOrId: $parentPath) {
                delete(name: "activity-logs")
                addChild(name: "activity-logs", primaryNodeType: "jnt:contentList") {
                    node {
                        uuid
                    }
                }
            }
        }
    }
`;
