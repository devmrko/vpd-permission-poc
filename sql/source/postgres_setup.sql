-- ============================================================
-- sql/source/postgres_setup.sql
-- Run on the REMOTE PostgreSQL instance (psql).
--
-- Creates the customers table in the `public` schema with sample
-- data spanning multiple regions (APAC / EMEA / AMER). The VPD
-- policy on ADB will filter rows from this table per logged-in user.
--
-- 멱등성: 기존 데이터가 있으면 그대로 두고 INSERT 시 ON CONFLICT 로 skip.
--
-- 호출 예시 (run.sh source 가 자동 실행):
--   PGPASSWORD=$PG_PASSWORD psql -h $PG_HOST -U $PG_USER -d $PG_DB \
--     -f sql/source/postgres_setup.sql
-- ============================================================

\echo === Creating public.customers (idempotent) ===

CREATE TABLE IF NOT EXISTS public.customers (
  customer_id  INTEGER PRIMARY KEY,
  full_name    TEXT        NOT NULL,
  email        TEXT        NOT NULL,
  signup_date  DATE        NOT NULL,
  region       VARCHAR(8)  NOT NULL CHECK (region IN ('APAC','EMEA','AMER'))
);

\echo === Seeding sample rows (12 rows across 3 regions) ===

INSERT INTO public.customers (customer_id, full_name, email, signup_date, region) VALUES
  ( 1, 'Alex Kim',        'alex.kim@example.com',     DATE '2024-01-15', 'APAC'),
  ( 2, 'Bora Park',       'bora.park@example.com',    DATE '2024-02-03', 'APAC'),
  ( 3, 'Chen Wei',        'chen.wei@example.com',     DATE '2024-02-21', 'APAC'),
  ( 4, 'Daisuke Sato',    'daisuke.sato@example.com', DATE '2024-03-09', 'APAC'),
  ( 5, 'Emma Mueller',    'emma.mueller@example.com', DATE '2024-03-18', 'EMEA'),
  ( 6, 'Francois Dubois', 'francois.d@example.com',   DATE '2024-04-02', 'EMEA'),
  ( 7, 'Giulia Rossi',    'giulia.rossi@example.com', DATE '2024-04-14', 'EMEA'),
  ( 8, 'Henry Smith',     'henry.smith@example.com',  DATE '2024-04-27', 'EMEA'),
  ( 9, 'Isabella Garcia', 'isabella.g@example.com',   DATE '2024-05-08', 'AMER'),
  (10, 'James Brown',     'james.brown@example.com',  DATE '2024-05-19', 'AMER'),
  (11, 'Karen Lopez',     'karen.lopez@example.com',  DATE '2024-06-01', 'AMER'),
  (12, 'Liam Wilson',     'liam.wilson@example.com',  DATE '2024-06-13', 'AMER')
ON CONFLICT (customer_id) DO NOTHING;

\echo === Verifying ===
SELECT region, COUNT(*) AS row_count
  FROM public.customers
 GROUP BY region
 ORDER BY region;

\echo === postgres_setup done ===
