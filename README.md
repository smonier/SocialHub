# Social Hub - Jahia Module

A comprehensive Jahia module for managing social media posts with OAuth authentication, multi-platform publishing, scheduling, and analytics capabilities.

## Features

### OAuth Authentication
- **LinkedIn OAuth 2.0** - Fully implemented with profile fetching and token storage
- **Facebook OAuth** - In progress (configuration ready)
- **Instagram OAuth** - Planned (uses Facebook Login)
- Credentials stored securely in JCR at `/sites/{siteKey}/contents/social-accounts/`
- OAuth callback handler at `/modules/SocialHub/oauth/{platform}/callback`

### Content Types (CND)
- **socialnt:account** - OAuth-authenticated social media accounts with tokens
- **socialnt:post** - Social media posts with multi-platform support
- **socialnt:metrics** - Analytics snapshots for published posts (impressions, clicks, likes, shares, comments)
- **socialnt:postWithMetrics** - Convenience type combining posts with metrics
- **socialnt:activityLog** - Activity log entries for tracking actions

### Backend Services (OSGi)

#### SocialAccountService
- `connectLinkedInAccount()` - Stores LinkedIn OAuth credentials in JCR
- `getLinkedInAccounts()` - Retrieves stored LinkedIn accounts
- `disconnectLinkedInAccount()` - Removes LinkedIn account
- Similar methods for Facebook (planned)

#### SocialPostService
- `publishToPlatform()` - Publishes a post to a specific platform (Facebook, Instagram, LinkedIn)
- `publishNow(uuid)` - Publishes a post immediately to configured platforms
- `publishDueScheduledPosts()` - Scans for and publishes scheduled posts that are due
- `getScheduledPosts(startDate, endDate)` - Retrieves posts scheduled in a date range
- Uses stored OAuth tokens from JCR accounts

#### SocialMetricsService
- `refreshMetricsForPublishedPosts()` - Updates analytics for all published posts
- `refreshMetricsForPost(uuid)` - Updates analytics for a specific post
- Stores metrics as `socialnt:metrics` child nodes

#### Quartz Scheduler Jobs
- **SocialPublishJob** - Runs every 5 minutes to publish due posts
- **SocialMetricsJob** - Runs hourly to refresh metrics from external APIs
- Only runs on processing servers (cluster-aware)

#### Servlets

**SocialOAuthCallbackServlet**
- Handles OAuth callbacks at `/modules/SocialHub/oauth/{platform}/callback`
- Exchanges authorization codes for access tokens
- Fetches user profiles from provider APIs
- Stores credentials in JCR via SocialAccountService
- Supports LinkedIn, Facebook (in progress)

**SocialProxyServlet**
- Mounted at `/modules/social-proxy/*`
- Forwards requests to external social media APIs
- Handles authentication and security
- Supports GET and POST methods
- Platform-specific configurations (API versions, base URLs)

### React UI Extension

Modern React 18 application with 6 main panels:

#### 1. Accounts Panel
- Manage OAuth-authenticated social media accounts
- Initiate OAuth flows for LinkedIn, Facebook
- Display connected accounts with platform, name, email
- Disconnect accounts
- Show token expiry status

#### 2. Posts Panel
- Lists all social posts from JCR via GraphQL
- Displays status, platforms, scheduled/published dates
- Real-time updates via polling
- Moonstone table components
- Platform icons (Facebook, Instagram, LinkedIn)

#### 3. Calendar Panel
- Monthly calendar view of scheduled posts
- Color-coded by status (draft, scheduled, published, failed)
- Click days to see post details
- Month navigation

#### 4. Insights Panel
- View post performance metrics
- Displays impressions, clicks, likes, comments, shares
- Platform-specific icons and styling
- Refresh metrics on demand
- Historical snapshots

#### 5. Proxy Test Panel
- Interactive testing interface for the proxy servlet
- GET/POST method selection
- JSON request body editor
- Response viewer with syntax highlighting
- Loading and error states

#### 6. Activity Log Panel
- Records all proxy requests in the current session
- Shows method, path, status, timestamp
- Keeps last 50 requests
- Real-time updates

## Project Structure

```
SocialHub/
├── src/
│   ├── main/
│   │   ├── java/org/example/socialhub/
│   │   │   ├── service/
│   │   │   │   ├── SocialAccountService.java
│   │   │   │   ├── SocialPostService.java
│   │   │   │   ├── SocialMetricsService.java
│   │   │   │   └── impl/
│   │   │   │       ├── SocialAccountServiceImpl.java
│   │   │   │       ├── SocialPostServiceImpl.java
│   │   │   │       └── SocialMetricsServiceImpl.java
│   │   │   ├── jobs/
│   │   │   │   ├── SocialPublishJob.java
│   │   │   │   └── SocialMetricsJob.java
│   │   │   └── servlet/
│   │   │       ├── SocialOAuthCallbackServlet.java
│   │   │       └── SocialProxyServlet.java
│   │   └── resources/
│   │       ├── META-INF/
│   │       │   ├── definitions.cnd
│   │       │   └── configurations/
│   │       │       ├── org.example.socialhub.servlet.SocialOAuthCallbackServlet.cfg
│   │       │       └── org.example.socialhub.servlet.SocialProxyServlet.cfg
│   │       └── resources/
│   │           ├── SocialHub_en.properties
│   │           └── SocialHub_fr.properties
│   └── javascript/
│       ├── apollo/
│       │   └── client.js
│       ├── apps/
│       │   └── socialHub/
│       │       ├── SocialHub.jsx
│       │       ├── AccountsPanel.jsx
│       │       ├── PostsPanel.jsx
│       │       ├── CalendarPanel.jsx
│       │       ├── InsightsPanel.jsx
│       │       ├── ProxyTestPanel.jsx
│       │       ├── ActivityLogPanel.jsx
│       │       └── queries.js
│       ├── register.js
│       └── index.js
├── package.json
├── webpack.config.js
├── babel.config.js
└── pom.xml
```

## Installation

### Prerequisites
- Jahia 8.x or later
- Node.js 14+ and Yarn
- Maven 3.6+

### Build Steps

1. **Install frontend dependencies:**
   ```bash
   yarn install
   ```

2. **Build frontend assets:**
   ```bash
   yarn build
   ```

3. **Build Maven module:**
   ```bash
   mvn clean install
   ```

4. **Deploy to Jahia:**
   - Copy the generated JAR to `digital-factory-data/modules/`
   - Or deploy via Jahia module manager

## Configuration

### OAuth Configuration

Edit `org.example.socialhub.servlet.SocialOAuthCallbackServlet.cfg`:

```properties
# LinkedIn OAuth
linkedin.clientId=YOUR_CLIENT_ID
linkedin.clientSecret=YOUR_CLIENT_SECRET
linkedin.redirectUri=/modules/SocialHub/oauth/linkedin/callback
linkedin.scopes=openid,profile,email,w_member_social

# Facebook OAuth
facebook.appId=YOUR_APP_ID
facebook.appSecret=YOUR_APP_SECRET
facebook.redirectUri=https://yourserver.com/modules/SocialHub/oauth/facebook/callback
facebook.scopes=pages_show_list,pages_manage_posts,pages_read_engagement
```

**Getting LinkedIn Credentials:**
1. Register app at https://www.linkedin.com/developers/apps
2. Copy Client ID and Client Secret
3. Add redirect URL: `https://yourserver.com/modules/SocialHub/oauth/linkedin/callback`
4. Request permissions: openid, profile, email, w_member_social

**Getting Facebook Credentials:**
1. Register app at https://developers.facebook.com/apps
2. Copy App ID and App Secret
3. Add OAuth redirect URI in app settings
4. Request permissions: pages_show_list, pages_manage_posts, pages_read_engagement

### API Configuration

Edit `org.example.socialhub.servlet.SocialProxyServlet.cfg`:

```properties
# API Base URLs
facebookBaseUrl=https://graph.facebook.com
instagramBaseUrl=https://graph.facebook.com
linkedinBaseUrl=https://api.linkedin.com

# API Versions
facebookApiVersion=v24.0
linkedinApiVersion=v2

# Platform IDs (for posting)
facebookPageId=YOUR_PAGE_ID
instagramAccountId=YOUR_INSTAGRAM_ACCOUNT_ID
linkedinOrganizationId=YOUR_LINKEDIN_ORG_ID

# Fallback tokens (prefer OAuth flow)
facebookPageAccessToken=YOUR_TOKEN
```

### JCR Storage Paths

**OAuth Accounts:** `/sites/{siteKey}/contents/social-accounts/`
- LinkedIn: `linkedin_{personId}`
- Facebook: `facebook_{userId}` (planned)

**Social Posts:** `/sites/{siteKey}/contents/social-posts/`
- Can be customized in UI panel queries

**Metrics:** Stored as child nodes of posts
- Node type: `socialnt:metrics`
- Multiple snapshots per post

### Scheduler Configuration

Adjust cron expressions in job files:
- **SocialPublishJob.java** - `job.cronExpression=0 0/5 * * * ?` (every 5 minutes)
- **SocialMetricsJob.java** - `job.cronExpression=0 0 * * * ?` (every hour)

Jobs only run on processing servers (cluster-safe).

## Usage

### OAuth Authentication Flow

1. Navigate to **Accounts Panel** in Social Hub
2. Click "Connect LinkedIn" (or other platform)
3. Redirected to LinkedIn OAuth consent screen
4. Approve permissions
5. Redirected back to Jahia
6. Account stored in JCR with access token

**OAuth URLs:**
- **Start:** `/modules/SocialHub/oauth/{platform}/start?siteKey={siteKey}`
- **Callback:** `/modules/SocialHub/oauth/{platform}/callback`

### Creating Social Posts

1. Create content nodes of type `socialnt:post` in JCR
2. Set required properties:
   - `social:title` - Post title
   - `social:message` - Message content
   - `social:platform` - Platform (instagram, facebook, linkedin)
   - `social:status` - draft/scheduled/published
   - `social:scheduledAt` - Schedule date/time

3. Optional properties:
   - `social:linkUrl` - URL to attach
   - `social:imageRefs` - Image references
   - `social:tags` - Tags/keywords
   - `social:externalId` - Platform post ID (set after publishing)

### Accessing the UI

1. Log into Jahia
2. Click "Social Hub" in the admin header
3. Navigate between panels:
   - **Accounts** - Manage OAuth accounts
   - **Posts** - Browse all posts
   - **Calendar** - View scheduled posts by date
   - **Insights** - View post analytics
   - **Proxy Test** - Test API connections
   - **Activity Log** - View request history

### Publishing Posts

**Via UI (planned):**
- Click "Publish Now" in Posts Panel

**Programmatically:**
```java
socialPostService.publishNow(postUuid);
// Or specific platform:
socialPostService.publishToPlatform(postNode, "linkedin", siteKey);
```

**Scheduled Publishing:**
- Set `social:status = "scheduled"`
- Set `social:scheduledAt` to future date
- SocialPublishJob will automatically publish when due

**Publishing Flow:**
1. Job/Service retrieves post node
2. Fetches OAuth credentials from JCR account
3. Builds platform-specific payload
4. Posts to API (Graph API, LinkedIn API)
5. Updates post with `social:externalId`
6. Sets `social:status = "published"`

## GraphQL Queries

The UI uses GraphQL queries defined in `queries.js`:

**Get Social Posts:**
```graphql
query GetSocialPosts($path: String!) {
  jcr {
    nodeByPath(path: $path) {
      children(typesFilter: { types: ["socialnt:post"] }) {
        nodes {
          uuid
          name
          properties(names: [
            "social:title",
            "social:message",
            "social:status",
            "social:platform",
            "social:scheduledAt",
            "social:publishedAt",
            "social:externalId",
            "social:linkUrl"
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
```

**Get Social Accounts:**
```graphql
query GetSocialAccounts($path: String!) {
  jcr {
    nodeByPath(path: $path) {
      children(typesFilter: { types: ["socialnt:account"] }) {
        nodes {
          uuid
          name
          properties(names: [
            "social:platform",
            "social:label",
            "social:accountId",
            "social:email",
            "social:tokenExpiry",
            "social:isActive"
          ]) {
            name
            value
          }
        }
      }
    }
  }
}
```

**Get Post Metrics:**
```graphql
query GetPostMetrics($postUuid: String!) {
  jcr {
    nodeById(uuid: $postUuid) {
      children(typesFilter: { types: ["socialnt:metrics"] }) {
        nodes {
          properties(names: [
            "social:platform",
            "social:capturedAt",
            "social:impressions",
            "social:clicks",
            "social:likes",
            "social:comments",
            "social:shares"
          ]) {
            name
            value
          }
        }
      }
    }
  }
}
```

## Development

### Frontend Development

```bash
# Watch mode for development
yarn dev

# Production build
yarn build:production

# Lint code
yarn lint
yarn lint:fix
```

### Testing the Proxy

1. Go to "Proxy Test" panel
2. Select method (GET/POST)
3. Enter relative path (e.g., `/posts`)
4. For POST, add JSON body
5. Click "Send Request"
6. View response and check Activity Log

## Resource Bundle Keys

All UI labels follow Jahia conventions. Content types use the `socialnt_` prefix:

```properties
# Content type
socialnt_account=Social account
socialnt_account.ui.tooltip=Configure a social media account

# Properties
socialnt_account.social_platform=Platform
socialnt_account.social_platform.ui.tooltip=Social media platform (e.g. Instagram, LinkedIn, Facebook)
socialnt_account.social_accountId=Account ID
socialnt_account.social_accountId.ui.tooltip=Platform-specific account identifier (e.g. LinkedIn person ID)
```

**Available Translations:**
- English: `SocialHub_en.properties`
- French: `SocialHub_fr.properties`

**Content Types:**
- `socialnt_account` - OAuth accounts
- `socialnt_post` - Social posts
- `socialnt_metrics` - Analytics snapshots
- `socialnt_postWithMetrics` - Posts with embedded metrics
- `socialnt_activityLog` - Activity log entries

## Security

### Authentication
- Servlets require authenticated Jahia users
- Checks for valid JCR session via `JCRSessionFactory`
- Returns 401 for unauthenticated requests

### OAuth Security
- OAuth state parameter prevents CSRF attacks
- Access tokens stored in JCR at `/sites/{siteKey}/contents/social-accounts/`
- Tokens stored as plain strings (consider encryption for production)
- Token expiry tracked via `social:tokenExpiry` property

### API Security
- Platform-specific security mechanisms:
  - **Facebook:** App secret + appsecret_proof for API calls
  - **LinkedIn:** OAuth 2.0 bearer tokens
- Tokens retrieved from stored accounts, not config files

### Best Practices
- Rotate OAuth credentials regularly
- Use site-specific accounts (multi-tenancy support)
- Monitor token expiry and refresh proactively
- Limit OAuth scopes to minimum required permissions

## Technology Stack

### Backend
- **OSGi Declarative Services** - Component lifecycle management
- **Jahia JCR API** - Content repository access
- **Quartz Scheduler** - Background job scheduling (cluster-aware)
- **Java Servlets** - OAuth callbacks and API proxy
- **SLF4J** - Logging

### Frontend
- **React 18** - UI framework
- **@apollo/client 3** - GraphQL client with caching
- **@jahia/moonstone** - Design system (tables, buttons, cards)
- **@jahia/ui-extender** - Jahia admin UI integration
- **react-icons** - Platform icons (FaFacebook, FaInstagram, FaLinkedin)
- **Webpack 5** - Module bundler with Module Federation

### External APIs
- **LinkedIn API v2** - OAuth 2.0, userinfo, ugcPosts
- **Facebook Graph API v24.0** - OAuth, pages, posts, insights
- **Instagram Graph API** - Content publishing (via Facebook)

## Platform Support

### LinkedIn ✅
- OAuth 2.0 authentication
- Account storage in JCR
- Post publishing (text + links)
- Person URN format: `urn:li:person:{personId}`
- Required header: `X-Restli-Protocol-Version: 2.0.0`

### Facebook 🚧
- OAuth configuration ready
- Post publishing implemented
- Account storage (planned)
- Page token management

### Instagram 📋
- Planned (uses Facebook OAuth)
- Requires Instagram Business Account
- Linked to Facebook Page

## Roadmap

- [ ] Complete Facebook OAuth account storage
- [ ] Implement Instagram OAuth flow
- [ ] Add LinkedIn insights API integration
- [ ] Support image/video uploads for LinkedIn
- [ ] Token refresh mechanism before expiry
- [ ] UI for publishing posts directly from panel
- [ ] Scheduled post editing in UI
- [ ] Support LinkedIn organization posts

## License

MIT

## Support

For issues and questions, contact the developer
