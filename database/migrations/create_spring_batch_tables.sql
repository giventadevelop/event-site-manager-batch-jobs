-- Spring Batch Metadata Tables
-- These tables are required for Spring Batch to track job executions
-- Set spring.batch.jdbc.initialize-schema: never in application.yml since we're creating tables manually.
--
-- IMPORTANT: Spring Batch expects sequences with specific names for ID generation:
--   - batch_job_seq (for BATCH_JOB_INSTANCE)
--   - batch_job_execution_seq (for BATCH_JOB_EXECUTION)
--   - batch_step_execution_seq (for BATCH_STEP_EXECUTION)
--
-- Note: sequence_generator already exists in the database (from main schema) and is used by other application tables.
-- We create separate sequences for Spring Batch tables with the exact names Spring Batch expects.
-- These sequences are isolated from the main sequence_generator to avoid conflicts in a shared database.

-- Drop existing Spring Batch sequences if they exist (for clean recreation)
DROP SEQUENCE IF EXISTS public.batch_job_seq CASCADE;
DROP SEQUENCE IF EXISTS public.batch_job_execution_seq CASCADE;
DROP SEQUENCE IF EXISTS public.batch_step_execution_seq CASCADE;

-- Create Spring Batch-specific sequences with the exact names Spring Batch expects
-- These are separate from the main sequence_generator used by other application tables
CREATE SEQUENCE IF NOT EXISTS public.batch_job_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE IF NOT EXISTS public.batch_job_execution_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE IF NOT EXISTS public.batch_step_execution_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

-- Drop existing tables if they exist (in reverse dependency order)
-- Spring Batch metadata tables (drop child tables first, then parent tables)
-- Note: Sequences will be dropped automatically due to OWNED BY relationship
DROP TABLE IF EXISTS public.BATCH_STEP_EXECUTION_CONTEXT CASCADE;
DROP TABLE IF EXISTS public.BATCH_JOB_EXECUTION_CONTEXT CASCADE;
DROP TABLE IF EXISTS public.BATCH_STEP_EXECUTION CASCADE;
DROP TABLE IF EXISTS public.BATCH_JOB_EXECUTION_PARAMS CASCADE;
DROP TABLE IF EXISTS public.BATCH_JOB_EXECUTION CASCADE;
DROP TABLE IF EXISTS public.BATCH_JOB_INSTANCE CASCADE;
-- Custom application table (independent, no dependencies)
DROP TABLE IF EXISTS public.batch_job_execution_log CASCADE;

-- BATCH_JOB_INSTANCE
-- Using BIGINT with explicit sequence (batch_job_seq) instead of BIGSERIAL
-- This ensures Spring Batch can find the sequence with the expected name
CREATE TABLE IF NOT EXISTS public.BATCH_JOB_INSTANCE (
    JOB_INSTANCE_ID BIGINT DEFAULT nextval('public.batch_job_seq') PRIMARY KEY,
    VERSION BIGINT,
    JOB_NAME VARCHAR(100) NOT NULL,
    JOB_KEY VARCHAR(32) NOT NULL,
    CONSTRAINT JOB_INST_UN UNIQUE (JOB_NAME, JOB_KEY)
);

-- BATCH_JOB_EXECUTION
-- Using BIGINT with explicit sequence (batch_job_execution_seq) instead of BIGSERIAL
CREATE TABLE IF NOT EXISTS public.BATCH_JOB_EXECUTION (
    JOB_EXECUTION_ID BIGINT DEFAULT nextval('public.batch_job_execution_seq') PRIMARY KEY,
    VERSION BIGINT,
    JOB_INSTANCE_ID BIGINT NOT NULL,
    CREATE_TIME TIMESTAMP NOT NULL,
    START_TIME TIMESTAMP,
    END_TIME TIMESTAMP,
    STATUS VARCHAR(10),
    EXIT_CODE VARCHAR(2500),
    EXIT_MESSAGE TEXT,
    LAST_UPDATED TIMESTAMP,
    CONSTRAINT JOB_INST_EXEC_FK FOREIGN KEY (job_instance_id)
        REFERENCES public.BATCH_JOB_INSTANCE(job_instance_id)
);

-- BATCH_JOB_EXECUTION_PARAMS
CREATE TABLE IF NOT EXISTS public.BATCH_JOB_EXECUTION_PARAMS (
    JOB_EXECUTION_ID BIGINT NOT NULL,
    PARAMETER_NAME VARCHAR(100) NOT NULL,
    PARAMETER_TYPE VARCHAR(100) NOT NULL,
    PARAMETER_VALUE VARCHAR(2500),
    IDENTIFYING CHAR(1) NOT NULL,
    CONSTRAINT JOB_EXEC_PARAMS_FK FOREIGN KEY (job_execution_id)
        REFERENCES public.BATCH_JOB_EXECUTION(job_execution_id)
);

-- BATCH_STEP_EXECUTION
-- Using BIGINT with explicit sequence (batch_step_execution_seq) instead of BIGSERIAL
CREATE TABLE IF NOT EXISTS public.BATCH_STEP_EXECUTION (
    STEP_EXECUTION_ID BIGINT DEFAULT nextval('public.batch_step_execution_seq') PRIMARY KEY,
    VERSION BIGINT NOT NULL,
    STEP_NAME VARCHAR(100) NOT NULL,
    JOB_EXECUTION_ID BIGINT NOT NULL,
    CREATE_TIME TIMESTAMP NOT NULL,
    START_TIME TIMESTAMP,
    END_TIME TIMESTAMP,
    STATUS VARCHAR(10),
    COMMIT_COUNT BIGINT,
    READ_COUNT BIGINT,
    FILTER_COUNT BIGINT,
    WRITE_COUNT BIGINT,
    READ_SKIP_COUNT BIGINT,
    WRITE_SKIP_COUNT BIGINT,
    PROCESS_SKIP_COUNT BIGINT,
    ROLLBACK_COUNT BIGINT,
    EXIT_CODE VARCHAR(2500),
    EXIT_MESSAGE TEXT,
    LAST_UPDATED TIMESTAMP,
    CONSTRAINT JOB_EXEC_STEP_FK FOREIGN KEY (job_execution_id)
        REFERENCES public.BATCH_JOB_EXECUTION(job_execution_id)
);

-- BATCH_STEP_EXECUTION_CONTEXT
CREATE TABLE IF NOT EXISTS public.BATCH_STEP_EXECUTION_CONTEXT (
    STEP_EXECUTION_ID BIGINT NOT NULL PRIMARY KEY,
    SHORT_CONTEXT VARCHAR(2500) NOT NULL,
    SERIALIZED_CONTEXT TEXT,
    CONSTRAINT STEP_EXEC_CTX_FK FOREIGN KEY (step_execution_id)
        REFERENCES public.BATCH_STEP_EXECUTION(step_execution_id)
);

-- BATCH_JOB_EXECUTION_CONTEXT
CREATE TABLE IF NOT EXISTS public.BATCH_JOB_EXECUTION_CONTEXT (
    JOB_EXECUTION_ID BIGINT NOT NULL PRIMARY KEY,
    SHORT_CONTEXT VARCHAR(2500) NOT NULL,
    SERIALIZED_CONTEXT TEXT,
    CONSTRAINT JOB_EXEC_CTX_FK FOREIGN KEY (job_execution_id)
        REFERENCES public.BATCH_JOB_EXECUTION(job_execution_id)
);

-- Custom application table: batch_job_execution_log
-- This table is separate from Spring Batch's internal tables (BATCH_*)
-- Used for custom tracking and auditing of batch job executions with additional metadata
-- Note: This is NOT the same as BATCH_JOB_EXECUTION (Spring Batch framework table)
-- Using BIGSERIAL for auto-increment (JPA @GeneratedValue with sequenceGenerator will work with this)
-- This table uses the shared sequence_generator sequence (already exists in database)
CREATE TABLE IF NOT EXISTS public.batch_job_execution_log (
    id BIGSERIAL PRIMARY KEY,
    job_name VARCHAR(100) NOT NULL,
    job_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    tenant_id VARCHAR(255),
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    duration_ms BIGINT,
    processed_count BIGINT DEFAULT 0,
    success_count BIGINT DEFAULT 0,
    failed_count BIGINT DEFAULT 0,
    error_message TEXT,
    triggered_by VARCHAR(100),
    parameters_json TEXT
);

-- Create indexes for better performance
-- Indexes for Spring Batch tables
CREATE INDEX IF NOT EXISTS JOB_INST_UN ON public.BATCH_JOB_INSTANCE(job_name, job_key);
CREATE INDEX IF NOT EXISTS JOB_EXEC_INST_FK ON public.BATCH_JOB_EXECUTION(job_instance_id);
CREATE INDEX IF NOT EXISTS JOB_EXEC_PARAMS_FK ON public.BATCH_JOB_EXECUTION_PARAMS(job_execution_id);
CREATE INDEX IF NOT EXISTS STEP_EXEC_JOB_FK ON public.BATCH_STEP_EXECUTION(job_execution_id);
CREATE INDEX IF NOT EXISTS STEP_EXEC_CTX_FK ON public.BATCH_STEP_EXECUTION_CONTEXT(step_execution_id);
CREATE INDEX IF NOT EXISTS JOB_EXEC_CTX_FK ON public.BATCH_JOB_EXECUTION_CONTEXT(job_execution_id);

-- Indexes for custom batch_job_execution_log table
CREATE INDEX IF NOT EXISTS idx_batch_job_execution_log_job_name
    ON public.batch_job_execution_log(job_name, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_batch_job_execution_log_status
    ON public.batch_job_execution_log(status, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_batch_job_execution_log_tenant
    ON public.batch_job_execution_log(tenant_id, started_at DESC);

-- Set sequence ownership to the columns (must be done after tables are created)
-- This ensures sequences are dropped if columns/tables are dropped
ALTER SEQUENCE public.batch_job_seq OWNED BY public.BATCH_JOB_INSTANCE.JOB_INSTANCE_ID;
ALTER SEQUENCE public.batch_job_execution_seq OWNED BY public.BATCH_JOB_EXECUTION.JOB_EXECUTION_ID;
ALTER SEQUENCE public.batch_step_execution_seq OWNED BY public.BATCH_STEP_EXECUTION.STEP_EXECUTION_ID;

