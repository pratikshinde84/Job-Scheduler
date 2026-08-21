-- ============================================================
-- Job Scheduler Schema for Supabase PostgreSQL
-- Paste this entire file into Supabase SQL Editor and run it.
-- ============================================================

-- ── Enum Types ───────────────────────────────────────────────
-- Using VARCHAR + CHECK constraints instead of PG enum types for
-- full compatibility with Hibernate @Enumerated(STRING).

-- worker_status values
-- (active | draining | offline)

-- job_status values
-- (pending | scheduled | claimed | running | completed | failed | dead)

-- ── Users ────────────────────────────────────────────────────
-- id mirrors the `sub` claim from Supabase JWTs.
-- No password_hash — identity is delegated to Supabase Auth (OAuth).
-- avatar_url comes from OAuth provider metadata.
CREATE TABLE users (
  id         UUID        PRIMARY KEY,           -- = Supabase auth.users.id
  email      TEXT        UNIQUE NOT NULL,
  name       TEXT        NOT NULL DEFAULT '',
  avatar_url TEXT,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- ── Organizations ────────────────────────────────────────────
CREATE TABLE organizations (
  id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  name       TEXT        NOT NULL,
  slug       TEXT        UNIQUE NOT NULL,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- ── Organization Members ─────────────────────────────────────
-- Links users to organizations with a role.
CREATE TABLE organization_members (
  user_id UUID REFERENCES users(id) ON DELETE CASCADE,
  org_id  UUID REFERENCES organizations(id) ON DELETE CASCADE,
  role    TEXT NOT NULL DEFAULT 'member',
  PRIMARY KEY (user_id, org_id)
);

-- ── Projects ─────────────────────────────────────────────────
CREATE TABLE projects (
  id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id        UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  org_id         UUID        REFERENCES organizations(id) ON DELETE SET NULL,
  name           TEXT        NOT NULL,
  api_key_prefix TEXT        NOT NULL DEFAULT '',
  created_at     TIMESTAMPTZ DEFAULT NOW(),
  updated_at     TIMESTAMPTZ DEFAULT NOW()
);

-- ── Queues ───────────────────────────────────────────────────
CREATE TABLE queues (
  id                   UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id           UUID        NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  name                 VARCHAR     NOT NULL,
  concurrency_limit    INT         DEFAULT 5,
  is_paused            BOOLEAN     DEFAULT FALSE,
  default_retry_config JSONB       DEFAULT '{"strategy":"exponential","base_delay_seconds":1,"max_attempts":3}',
  UNIQUE (project_id, name)
);

-- ── Workers ──────────────────────────────────────────────────
CREATE TABLE workers (
  id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  name              VARCHAR     UNIQUE NOT NULL,
  last_heartbeat_at TIMESTAMPTZ,
  status            VARCHAR(20) NOT NULL DEFAULT 'offline'
                    CHECK (status IN ('active', 'draining', 'offline'))
);

-- ── Jobs ─────────────────────────────────────────────────────
CREATE TABLE jobs (
  id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  queue_id      UUID        NOT NULL REFERENCES queues(id) ON DELETE RESTRICT,
  status        VARCHAR(20) NOT NULL DEFAULT 'pending'
                CHECK (status IN ('pending','scheduled','claimed','running','completed','failed','dead')),
  priority      SMALLINT    DEFAULT 0,
  scheduled_at  TIMESTAMPTZ DEFAULT NOW(),
  next_retry_at TIMESTAMPTZ,
  locked_at     TIMESTAMPTZ,
  locked_by     VARCHAR,
  attempt_count INT        DEFAULT 0,
  max_attempts  INT        DEFAULT 3,
  retry_config  JSONB,
  payload       JSONB,
  last_error    TEXT,
  created_at    TIMESTAMPTZ DEFAULT NOW(),
  updated_at    TIMESTAMPTZ DEFAULT NOW()
);

-- ── Job Attempts ─────────────────────────────────────────────
CREATE TABLE job_attempts (
  id             BIGSERIAL   PRIMARY KEY,
  job_id         UUID        NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
  attempt_number INT         NOT NULL,
  worker_name    VARCHAR     NOT NULL,
  started_at     TIMESTAMPTZ NOT NULL,
  finished_at    TIMESTAMPTZ,
  error_stack    TEXT
);

-- ── Dead Letter Queue ────────────────────────────────────────
CREATE TABLE dead_letter_entries (
  id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  job_id           UUID        REFERENCES jobs(id) ON DELETE SET NULL,
  original_payload JSONB       NOT NULL,
  error            TEXT        NOT NULL,
  died_at          TIMESTAMPTZ DEFAULT NOW()
);

-- ── Indexes ──────────────────────────────────────────────────
-- Poller: fetch next runnable jobs in priority order
CREATE INDEX idx_jobs_poller
  ON jobs (queue_id, priority DESC, scheduled_at)
  WHERE status IN ('pending', 'scheduled');

-- Reaper: find claimed jobs that have timed out
CREATE INDEX idx_jobs_reaper
  ON jobs (status, locked_at)
  WHERE status = 'claimed';

-- Unique queue name per project (also enforced by table constraint above)
CREATE UNIQUE INDEX idx_queues_project_name
  ON queues (project_id, name);

-- Dashboard joins from jobs → queue
CREATE INDEX idx_jobs_user_lookup ON jobs (queue_id);

-- Job attempt lookups
CREATE INDEX idx_job_attempts_job_id
  ON job_attempts (job_id, started_at DESC);

-- ── updated_at trigger function ──────────────────────────────
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = NOW();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_updated_at
  BEFORE UPDATE ON users
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_organizations_updated_at
  BEFORE UPDATE ON organizations
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_projects_updated_at
  BEFORE UPDATE ON projects
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_jobs_updated_at
  BEFORE UPDATE ON jobs
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();
