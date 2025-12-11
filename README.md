# Social Hub - Jahia Module

A comprehensive Jahia module for managing social media posts with scheduling, publishing, and analytics capabilities.

## Features

### Content Types (CND)
- **social:account** - Social media account configuration with OAuth tokens
- **social:post** - Social media posts with multi-platform support
- **social:metrics** - Analytics snapshots for published posts
- **social:postWithMetrics** - Convenience type combining posts with metrics

### Backend Services (OSGi)

#### SocialPostService
- `publishNow(uuid)` - Publishes a post immediately to configured platforms
- `publishDueScheduledPosts()` - Scans for and publishes scheduled posts that are due
- `getScheduledPosts(startDate, endDate)` - Retrieves posts scheduled in a date range

#### SocialMetricsService
- `refreshMetricsForPublishedPosts()` - Updates analytics for all published posts
- `refreshMetricsForPost(uuid)` - Updates analytics for a specific post

#### Quartz Scheduler Jobs
- **SocialPublishJob** - Runs every 5 minutes to publish due posts
- **SocialMetricsJob** - Runs hourly to refresh metrics from external APIs

#### SocialProxyServlet
- Mounted at `/modules/social-proxy/*`
- Forwards requests to external social media APIs
- Handles authentication and security
- Supports GET and POST methods

### React UI Extension

Modern React 18 application with 4 main panels:

#### 1. Posts Panel
- Lists all social posts from JCR via GraphQL
- Displays status, platforms, scheduled/published dates
- Real-time updates via polling
- Moonstone table components

#### 2. Calendar Panel
- Monthly calendar view of scheduled posts
- Color-coded by status (draft, scheduled, published, failed)
- Click days to see post details
- Month navigation

#### 3. Proxy Test Panel
- Interactive testing interface for the proxy servlet
- GET/POST method selection
- JSON request body editor
- Response viewer with syntax highlighting
- Loading and error states

#### 4. Activity Log
- Records all proxy requests in the current session
- Shows method, path, status, timestamp
- Keeps last 50 requests

## Project Structure

```
social-hub/
├── src/
│   ├── main/
│   │   ├── java/org/example/socialhub/
│   │   │   ├── service/
│   │   │   │   ├── SocialPostService.java
│   │   │   │   ├── SocialMetricsService.java
│   │   │   │   └── impl/
│   │   │   │       ├── SocialPostServiceImpl.java
│   │   │   │       └── SocialMetricsServiceImpl.java
│   │   │   ├── jobs/
│   │   │   │   ├── SocialPublishJob.java
│   │   │   │   └── SocialMetricsJob.java
│   │   │   └── servlet/
│   │   │       └── SocialProxyServlet.java
│   │   └── resources/
│   │       ├── META-INF/
│   │       │   └── definitions.cnd
│   │       └── resources/
│   │           ├── social_hub_en.properties
│   │           └── social_hub_fr.properties
│   └── javascript/
│       ├── apollo/
│       │   └── client.js
│       ├── apps/
│       │   └── socialHub/
│       │       ├── SocialHub.jsx
│       │       ├── PostsPanel.jsx
│       │       ├── CalendarPanel.jsx
│       │       ├── ProxyTestPanel.jsx
│       │       └── ActivityLogPanel.jsx
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

### External API Configuration

The module connects to external social media APIs. Configure these in:

**SocialPostServiceImpl.java:**
```java
private static final String EXTERNAL_API_BASE = "https://api.example.com/social";
private static final String API_TOKEN = "your-api-token-here";
```

**SocialProxyServlet.java:**
```java
private static final String TARGET_BASE_URL = "https://api.example.com/social";
private static final String AUTH_TOKEN = "your-api-token-here";
```

> **Production:** These should be externalized using OSGi Configuration Admin.

### JCR Content Path

Social posts are expected at: `/sites/digitall/contents/social-posts`

To change this, update the `POSTS_PATH` constant in:
- `PostsPanel.jsx`
- `CalendarPanel.jsx`

### Scheduler Configuration

Adjust cron expressions in:
- **SocialPublishJob.java** - `job.cronExpression=0 0/5 * * * ?` (every 5 minutes)
- **SocialMetricsJob.java** - `job.cronExpression=0 0 * * * ?` (every hour)

## Usage

### Creating Social Posts

1. Create content nodes of type `social:post` in JCR
2. Set required properties:
   - `social:title` - Post title
   - `social:message` - Default message
   - `social:platforms` - Array of platforms (Instagram, LinkedIn, Facebook)
   - `social:status` - draft/scheduled/published
   - `social:scheduledAt` - Schedule date/time

3. Platform-specific messages (optional):
   - `social:messageInstagram`
   - `social:messageLinkedIn`
   - `social:messageFacebook`

### Accessing the UI

1. Log into Jahia
2. Click "Social Hub" in the admin header
3. Navigate between panels:
   - **Posts** - Browse all posts
   - **Calendar** - View scheduled posts by date
   - **Proxy Test** - Test API connections
   - **Activity Log** - View request history

### Publishing Posts

**Manual Publishing:**
```java
socialPostService.publishNow(postUuid);
```

**Scheduled Publishing:**
- Set `social:status = "scheduled"`
- Set `social:scheduledAt` to future date
- SocialPublishJob will automatically publish when due

## GraphQL Queries

The UI uses these GraphQL queries:

**Get Social Posts:**
```graphql
query GetSocialPosts($path: String!) {
  jcr {
    nodeByPath(path: $path) {
      children(typesFilter: { types: ["social:post"] }) {
        nodes {
          uuid
          name
          properties(names: [
            "social:title",
            "social:status",
            "social:scheduledAt",
            "social:publishedAt",
            "social:platforms"
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

All UI labels follow Jahia conventions:

```properties
socialhub_socialPost=Social post
socialhub_socialPost.ui.tooltip=A post to be published to social media platforms
socialhub_socialPost.title=Title
socialhub_socialPost.title.ui.tooltip=Internal name of the social post
```

See `social_hub_en.properties` and `social_hub_fr.properties` for complete listings.

## Security

- Servlet requires authenticated Jahia users
- Checks for valid JCR session via `JCRSessionFactory`
- Returns 401 for unauthenticated requests
- OAuth tokens stored in JCR (encrypted storage recommended for production)

## Technology Stack

### Backend
- **OSGi Declarative Services** - Component lifecycle
- **Jahia JCR API** - Content repository access
- **Quartz Scheduler** - Job scheduling
- **Java Servlets** - HTTP proxy
- **SLF4J** - Logging

### Frontend
- **React 18** - UI framework
- **@apollo/client** - GraphQL client
- **@jahia/moonstone** - Design system
- **@jahia/ui-extender** - Jahia UI integration
- **Webpack 5** - Module bundler

## License

MIT

## Support

For issues and questions, contact your Jahia support team.
