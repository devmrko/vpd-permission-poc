# 04 · Source DB Setup

원격 Postgres 와 MySQL 에 무엇이 만들어지는지, 또 ADB 가 어떻게 접속하는지.

---

## Postgres (`sql/source/postgres_setup.sql`)

```
DB        : 사용자가 .env 에서 지정 (PG_DB, 기본값 vpdpoc)
schema    : public
table     : public.customers
PK        : customer_id (INTEGER)
컬럼      : full_name TEXT, email TEXT, signup_date DATE,
            region VARCHAR(8)   -- CHECK IN ('APAC','EMEA','AMER')
seed rows : 12 (APAC 4 / EMEA 4 / AMER 4)
멱등성    : ON CONFLICT (customer_id) DO NOTHING
```

수동 실행:

```bash
PGPASSWORD=$PG_PASSWORD psql \
  -h $PG_HOST -p $PG_PORT -U $PG_USER -d $PG_DB \
  -f sql/source/postgres_setup.sql
```

---

## MySQL (`sql/source/mysql_setup.sql`)

```
DB        : MY_DB (기본 ecommerce_poc)
table     : customers
PK        : customer_id (INT)
컬럼      : 동일 스키마. region CHECK 제약은 chk_region 로 표현
seed rows : 12 (PK 101~112, 역시 APAC/EMEA/AMER 4/4/4)
멱등성    : INSERT IGNORE
```

PK 범위를 PG (1~12) 와 다르게 가져간 이유:

* 두 소스는 서로 독립적인 별개의 데이터 라는 것을 데모에서 시각적으로 보여주기 위함
* `vpduser_b` 가 양쪽 뷰를 합칠 때 PK 가 겹치지 않아 UNION 데모하기 쉬움

수동 실행:

```bash
mysql -h $MY_HOST -P $MY_PORT -u $MY_USER -p"$MY_PASSWORD" $MY_DB \
  < sql/source/mysql_setup.sql
```

---

## ADB → 원격 DB Link (`sql/adb/01_dblinks.sql`)

`DBMS_CLOUD_ADMIN.CREATE_DATABASE_LINK` 가 ADB 안에서 직접 heterogeneous 연결을
처리합니다. 별도 Database Gateway 설치 불필요.

```sql
DBMS_CLOUD_ADMIN.CREATE_DATABASE_LINK(
  db_link_name    => 'RDS_POSTGRES_LINK',
  hostname        => '<PG_HOST>',
  port            => 5432,
  service_name    => '<PG_DB>',
  credential_name => 'RDS_POSTGRES_LINK_CRED',     -- DBMS_CLOUD.CREATE_CREDENTIAL 로 등록
  gateway_params  => JSON_OBJECT('db_type' VALUE 'postgres')
);
```

MySQL 도 동일 패턴, `db_type` 만 `'mysql'`.

링크 검증:

```sql
SELECT COUNT(*) FROM "public"."customers"@RDS_POSTGRES_LINK;
SELECT COUNT(*) FROM "ecommerce_poc"."customers"@RDS_LINK;
```

---

## 원격 식별자 인용 (quoting) 주의

| 원본 DB | 식별자 quoting | ADB 에서 호출 시 |
|---|---|---|
| Postgres | 소문자 보존하려면 `"public"."customers"` (대소문자 구분) | `"public"."customers"@RDS_POSTGRES_LINK` |
| MySQL | 일반적으로 무관 (`\`customers\``) | `"ecommerce_poc"."customers"@RDS_LINK` (ADB 게이트웨이가 자동 매핑) |

→ 잘못 쓰면 `ORA-00942: table or view does not exist` 가 뜨는데, 원인이 권한이
아니라 **이름 케이스** 인 경우가 많습니다.

---

## 네트워크 체크리스트

* RDS Public Access: ON (또는 ADB ↔ RDS 같은 VCN/peer)
* Security Group: ADB egress IP (또는 0.0.0.0/0 임시) 가 5432/3306 으로 도달 가능
* Local 테스트: `nc -zv $PG_HOST 5432` / `nc -zv $MY_HOST 3306` 가 먼저 통해야
  `run.sh source` 도 통합니다.
