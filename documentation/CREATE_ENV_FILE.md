# Creating .env File

Since `.env` files contain sensitive information, they cannot be created automatically. Please create the `.env` file manually in the project root directory.

## Steps to Create .env File

1. **Create a new file** named `.env` in the project root directory (`E:\project_workspace\event-site-manager-batch-jobs\.env`)

2. **Copy the following content** into the `.env` file:

```env
# Batch Jobs Application Environment Variables
SERVER_PORT=8081

# Database Configuration
RDS_ENDPOINT=localhost
DB_NAME=event_site_manager_db
DB_USERNAME=event_site_app
DB_PASSWORD=your-database-password
DB_MAX_POOL_SIZE=10
DB_MIN_IDLE=2

# AWS Configuration (for SES email sending)
AWS_ACCESS_KEY_ID=your-aws-access-key-id
AWS_SECRET_ACCESS_KEY=your-aws-secret-access-key
AWS_REGION=us-east-1
AWS_SES_FROM_EMAIL=noreply@example.com
AWS_SES_RATE_LIMIT_PER_SECOND=200
```

## Important Notes

1. **SERVER_PORT=8081** - Changed from 8080 to avoid conflict with backend
2. **Database variables** - Update `RDS_ENDPOINT`, `DB_NAME`, `DB_USERNAME`, and `DB_PASSWORD` with your actual database values
3. **AWS credentials** - Replace `your-aws-access-key-id` and `your-aws-secret-access-key` with your actual AWS credentials
4. **Variables NOT included** - CLERK_*, JWT_*, TWILIO_*, etc. are not needed for batch jobs

## Using .env File in IntelliJ

If you're using the EnvFile plugin in IntelliJ:

1. Install the **EnvFile** plugin if not already installed
2. Go to **Run** → **Edit Configurations...**
3. Select your `BatchJobsApplication` configuration
4. Under **EnvFile**, enable **Enable EnvFile**
5. Add the `.env` file path
6. The environment variables will be loaded automatically

## Alternative: Set Environment Variables Directly

If you prefer not to use a .env file, you can set environment variables directly in IntelliJ:

1. Go to **Run** → **Edit Configurations...**
2. Select your `BatchJobsApplication` configuration
3. Under **Environment variables**, add each variable:
   - `SERVER_PORT=8081`
   - `RDS_ENDPOINT=localhost`
   - `DB_NAME=event_site_manager_db`
   - etc.

## Verify Configuration

After creating the `.env` file and running the application, verify in the logs:
- Port should be 8081: `Tomcat initialized with port(s): 8081 (http)`
- Database connection should succeed
- No port conflict errors


