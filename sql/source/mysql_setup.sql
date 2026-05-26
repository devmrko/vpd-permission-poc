-- ============================================================
-- sql/source/mysql_setup.sql
-- Run on the REMOTE MySQL instance (mysql CLI).
--
-- Creates the customers table in the `ecommerce_poc` schema with
-- sample data spanning multiple regions (APAC / EMEA / AMER).
--
-- 동일 customer_id 가 Postgres seed 와 겹치지만, 다른 DB 이므로 무관.
-- 의도적으로 일부 row 의 region 을 다르게 줘서 두 소스가 독립적임을 보임.
--
-- 멱등성: INSERT IGNORE 로 중복 PK skip.
--
-- 호출 예시 (run.sh source 가 자동 실행):
--   mysql -h $MY_HOST -P $MY_PORT -u $MY_USER -p"$MY_PASSWORD" \
--         $MY_DB < sql/source/mysql_setup.sql
-- ============================================================

SELECT '=== Creating ecommerce_poc.customers (idempotent) ===' AS status;

CREATE TABLE IF NOT EXISTS customers (
  customer_id  INT          NOT NULL PRIMARY KEY,
  full_name    VARCHAR(80)  NOT NULL,
  email        VARCHAR(120) NOT NULL,
  signup_date  DATE         NOT NULL,
  region       VARCHAR(8)   NOT NULL,
  CONSTRAINT chk_region CHECK (region IN ('APAC','EMEA','AMER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SELECT '=== Seeding sample rows (12 rows across 3 regions) ===' AS status;

INSERT IGNORE INTO customers (customer_id, full_name, email, signup_date, region) VALUES
  (101, 'Aki Tanaka',      'aki.tanaka@shop.example',     '2024-01-20', 'APAC'),
  (102, 'Bohyun Lee',      'bohyun.lee@shop.example',     '2024-02-08', 'APAC'),
  (103, 'Cheng Ho',        'cheng.ho@shop.example',       '2024-02-25', 'APAC'),
  (104, 'Devi Patel',      'devi.patel@shop.example',     '2024-03-12', 'APAC'),
  (105, 'Erik Johansson',  'erik.j@shop.example',         '2024-03-22', 'EMEA'),
  (106, 'Fatima Al-Hassan','fatima.h@shop.example',       '2024-04-05', 'EMEA'),
  (107, 'Greta Lindqvist', 'greta.l@shop.example',        '2024-04-18', 'EMEA'),
  (108, 'Hans Becker',     'hans.becker@shop.example',    '2024-05-01', 'EMEA'),
  (109, 'Ines Martinez',   'ines.martinez@shop.example',  '2024-05-13', 'AMER'),
  (110, 'Jorge Silva',     'jorge.silva@shop.example',    '2024-05-25', 'AMER'),
  (111, 'Kayla Anderson',  'kayla.a@shop.example',        '2024-06-07', 'AMER'),
  (112, 'Lucas Costa',     'lucas.costa@shop.example',    '2024-06-19', 'AMER');

SELECT '=== Verifying ===' AS status;
SELECT region, COUNT(*) AS row_count
  FROM customers
 GROUP BY region
 ORDER BY region;

SELECT '=== mysql_setup done ===' AS status;
