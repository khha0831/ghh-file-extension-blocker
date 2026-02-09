# 파일 확장자 차단 시스템

---

## 요구사항

```
1-1. 고정 확장자는 차단을 자주하는 확장자를 리스트이며, default는 unCheck되어져 있습니다.
1-2. 고정 확장자를 check or uncheck를 할 경우 db에 저장됩니다. - 새로고침시 유지되어야합니다.
(아래쪽 커스텀 확장자에는 표현되지 않으니 유의해주세요.)

2-1. 확장자 최대 입력 길이는 20자리
2-2. 추가버튼 클릭시 db 저장되며, 아래쪽 영역에 표현됩니다.

3-1. 커스텀 확장자는 최대 200개까지 추가가 가능
3-2. 확장자 옆 X를 클릭시 db에서 삭제

위 요건 이외에 어떤 점을 고려했는지 적어주세요.
ex) 커스텀 확장자 중복 체크
커스텀 확장자 sh를 추가한 후 다시 sh를 추가했을 때 고려하여 개발...
```

---

## 추가 고려사항 및 구현 기능

### 1. 커스텀 확장자 중복 체크

커스텀 확장자 `sh`를 추가한 후 다시 `sh`를 추가하면 "이미 등록된 확장자입니다: sh" 메시지를 반환합니다. 애플리케이션 레벨에서 `existsByExtension`으로 빠르게 거부하고, DB Unique Constraint로 최종 방어합니다.

### 2. 고정 확장자와 커스텀 확장자 이름 충돌 방지

커스텀 확장자로 `exe`, `bat` 등 고정 확장자와 동일한 이름을 등록하려 하면 "고정 확장자입니다. 상단의 체크박스를 이용하세요." 메시지로 거부합니다.

### 3. 입력값 검증

- 영문 소문자, 숫자만 허용 (한글, 특수문자 입력 불가 — 프론트에서 실시간 필터링)
- 대문자 입력 시 소문자 자동 변환
- 빈 입력, 공백만 있는 입력 거부
- 확장자 최대 20자 제한
- 쉼표(,)로 다중 입력 지원, 입력 내 중복 자동 제거

### 4. 동시성 제어

두 사용자가 동시에 확장자를 추가/삭제할 때 발생할 수 있는 문제를 3가지 수단으로 방어합니다.

- **200개 초과 방지**: `synchronized` + `TransactionTemplate`으로 count 조회부터 커밋까지 원자적으로 실행
- **중복 등록 방지**: DB Unique Constraint + `DataIntegrityViolationException` 예외 변환
- **고정 확장자 갱신 손실 방지**: `@Version` 낙관적 락으로 동시 UPDATE 충돌 감지 (409 Conflict 응답)

`@Transactional` + `synchronized`를 같은 메서드에 쓰면 AOP 프록시 순서 때문에 락이 트랜잭션보다 먼저 풀리는 문제가 있어, `TransactionTemplate`을 사용하여 커밋 시점을 락 내부로 강제했습니다.

### 5. 파일 업로드 이중 검증 (확장자 위변조 탐지)

- **1차 검증**: 파일 확장자 문자열 비교
- **2차 검증**: Apache Tika로 파일 바이너리(Magic Number) 분석하여 실제 MIME Type 판별
- exe 파일을 jpg로 이름만 바꿔서 업로드해도 Tika가 "application/x-msdownload"를 감지하여 차단
- All or Nothing 정책: 여러 파일 중 하나라도 차단이면 전체 업로드 거부

### 6. 파일 선택 UX 개선

- 파일 누적 선택 (여러 번 선택해도 이전 파일 유지, 덮어쓰지 않음)
- 같은 파일명 중복 추가 방지
- 개별 파일 제거(X 버튼) 지원
- 드래그 앤 드롭 지원
- 파일 크기 제한: 1개당 최대 50MB, 전체 최대 100MB

### 7. 전체 선택 / 전체 해제

고정 확장자 7개를 한 번에 체크하거나 해제할 수 있는 버튼을 제공합니다. JPQL Bulk Update로 처리하며, `@Version`을 수동 증가시켜 이후 개별 토글 시 낙관적 락이 정상 동작하도록 했습니다.

### 8. 설정 초기화

커스텀 확장자 전체 삭제 + 고정 확장자 전체 해제를 한 번에 수행합니다.

### 9. 테스트 데이터 일괄 생성

test1~test200 더미 데이터를 생성하여 200개 제한 동작을 확인할 수 있습니다.

### 10. 예외 처리

- `BlockedExtensionException` → 400 Bad Request (비즈니스 예외)
- `FileBlockedException` → 403 Forbidden (파일 차단)
- `OptimisticLockingFailureException` → 409 Conflict (동시 수정 충돌)
- `MaxUploadSizeExceededException` → 400 Bad Request (파일 크기 초과)
- `Exception` → 500 Internal Server Error (예상치 못한 오류)

### 11. Docker 배포

Docker Compose로 PostgreSQL + Spring Boot를 한 번에 실행할 수 있습니다. EC2에 Docker만 설치하면 JDK, PostgreSQL 별도 설치 없이 배포 가능합니다.

---

## 기술 스택

- Java 17, Spring Boot 3.5.10, Spring Data JPA
- PostgreSQL 16, Thymeleaf, Apache Tika
- Docker, Docker Compose
- JUnit 5, MockMvc, H2 (테스트)

---

## 실행 방법

```bash
docker-compose up -d --build
```

`http://localhost:8080` 접속

---

## 테스트

```bash
./gradlew test
```

| 테스트 파일 | 개수 | 내용 |
|------------|------|------|
| ExtensionServiceTest | 22개 | 고정/커스텀 CRUD, 200개 제한, 검증, 초기화 |
| ExtensionApiControllerTest | 12개 | API 엔드포인트 정상/에러 응답, 파일 업로드 |
| ConcurrencyTest | 4개 | 멀티스레드 동시 추가/삭제, 200개 미초과 검증 |

테스트는 H2 인메모리 DB로 실행되므로 PostgreSQL 불필요.

---

## API 명세

| Method | URL | 설명 |
|--------|-----|------|
| GET | /api/extensions/fixed | 고정 확장자 조회 |
| PATCH | /api/extensions/fixed | 고정 확장자 토글 |
| PATCH | /api/extensions/fixed/bulk | 고정 확장자 전체 선택/해제 |
| GET | /api/extensions/custom | 커스텀 확장자 조회 |
| POST | /api/extensions/custom | 커스텀 확장자 추가 |
| DELETE | /api/extensions/custom/{id} | 커스텀 확장자 개별 삭제 |
| DELETE | /api/extensions/custom | 커스텀 확장자 전체 삭제 |
| POST | /api/extensions/upload | 파일 업로드 검증 |
| POST | /api/extensions/reset | 전체 설정 초기화 |
| POST | /api/extensions/test-data | 테스트 데이터 생성 |

---

## 프로젝트 구조

```
src/main/java/com/khh/blocker/
├── config/
│   ├── JpaAuditingConfig.java       # JPA Auditing 활성화
│   └── WebConfig.java               # CORS 설정
├── controller/
│   ├── ExtensionApiController.java  # REST API 엔드포인트
│   └── PageController.java          # 메인 페이지 라우팅
├── domain/
│   ├── BaseTimeEntity.java          # 생성/수정 시간 자동 관리
│   ├── BlockedExtension.java        # 확장자 엔티티 (@Version 낙관적 락)
│   └── ExtensionType.java           # FIXED / CUSTOM 구분
├── dto/
│   ├── ApiResponse.java             # 공통 API 응답 (record)
│   ├── ExtensionDto.java            # 요청/응답 DTO (내부 클래스)
│   └── FileUploadDto.java           # 파일 업로드 응답 DTO
├── exception/
│   ├── BlockedExtensionException.java
│   ├── FileBlockedException.java
│   └── GlobalExceptionHandler.java  # 전역 예외 처리 (@Version 충돌 포함)
├── repository/
│   └── BlockedExtensionRepository.java  # JPA Repository + Bulk Update
└── service/
    ├── ExtensionService.java        # 핵심 비즈니스 로직 (동시성 제어)
    └── FileUploadService.java       # 파일 검증 (확장자 + Tika MIME)
```

---

## 배포

```bash
docker-compose up -d --build    # 빌드 + 실행
docker-compose logs -f app      # 로그 확인
docker-compose down             # 중지
docker-compose down -v          # 중지 + DB 데이터 삭제
```
