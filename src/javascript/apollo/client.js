import {ApolloClient, InMemoryCache, HttpLink} from '@apollo/client';

/**
 * Apollo Client configuration for Jahia GraphQL endpoint.
 *
 * Connects to Jahia's GraphQL API at /modules/graphql
 * Uses credentials mode to include authentication cookies.
 */

const httpLink = new HttpLink({
    uri: '/modules/graphql',
    credentials: 'include', // Include cookies for Jahia authentication
    headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json'
    }
});

const client = new ApolloClient({
    link: httpLink,
    cache: new InMemoryCache({
        typePolicies: {
            Query: {
                fields: {
                    // Configure caching policies if needed
                }
            }
        }
    }),
    defaultOptions: {
        watchQuery: {
            fetchPolicy: 'cache-and-network',
            errorPolicy: 'all'
        },
        query: {
            fetchPolicy: 'network-only',
            errorPolicy: 'all'
        },
        mutate: {
            errorPolicy: 'all'
        }
    }
});

export default client;
