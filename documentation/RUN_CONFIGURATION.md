# IntelliJ IDEA Run Configuration

## Setting Up Port 8081

To ensure the batch jobs application runs on port 8081 (avoiding conflict with backend on 8080), configure your IntelliJ run configuration:

### Option 1: Use Environment Variable (Recommended)

1. Go to **Run** → **Edit Configurations...**
2. Select your `BatchJobsApplication` configuration (or create a new one)
3. Under **Environment variables**, add:
   ```
   SERVER_PORT=8081
   ```
4. Click **OK** and run

### Option 2: Use VM Options

1. Go to **Run** → **Edit Configurations...**
2. Select your `BatchJobsApplication` configuration
3. Under **VM options**, add:
   ```
   -DSERVER_PORT=8081
   ```
4. Click **OK** and run

### Option 3: Use Application Arguments

1. Go to **Run** → **Edit Configurations...**
2. Select your `BatchJobsApplication` configuration
3. Under **Program arguments**, add:
   ```
   --server.port=8081
   ```
4. Click **OK** and run

### Option 4: Create application-local.yml

Create `src/main/resources/application-local.yml`:

```yaml
server:
  port: 8081
```

Then set the active profile to `local` in your run configuration:
- **Active profiles**: `local`

## Quick Fix

The easiest way is to add this to your run configuration's **Environment variables**:
```
SERVER_PORT=8081
```

## Verify Port

After starting, check the logs for:
```
Tomcat initialized with port(s): 8081 (http)
```

If you see `8080`, the configuration isn't being applied correctly.



