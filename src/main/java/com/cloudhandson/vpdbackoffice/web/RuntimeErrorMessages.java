package com.cloudhandson.vpdbackoffice.web;

import java.sql.SQLException;
import java.util.Locale;
import org.springframework.dao.DataAccessException;

final class RuntimeErrorMessages {

  private RuntimeErrorMessages() {
  }

  static RuntimeErrorMessage dataAccess(DataAccessException exception) {
    String detail = detail(exception);
    if (looksLikeConnectionProblem(exception, detail)) {
      return new RuntimeErrorMessage(
          "DB 연결 설정이 필요합니다.",
          "ADB에 연결할 수 없습니다. BACKOFFICE_DB_URL, BACKOFFICE_DB_USERNAME, "
              + "BACKOFFICE_DB_PASSWORD를 확인하고 ./run.sh backoffice-support를 다시 실행하세요. 상세: "
              + detail,
          true
      );
    }
    return new RuntimeErrorMessage(
        "데이터 처리 오류가 발생했습니다.",
        "요청을 처리하는 중 DB 데이터 타입, SQL, 또는 매핑 오류가 발생했습니다. "
            + "입력값과 백오피스 테이블 스키마를 확인하세요. 상세: " + detail,
        false
    );
  }

  static RuntimeErrorMessage unexpected(Exception exception) {
    return new RuntimeErrorMessage(
        "요청 처리 중 오류가 발생했습니다.",
        "예상하지 못한 오류가 발생했습니다. 입력값을 확인한 뒤 다시 시도하세요. 상세: "
            + safeMessage(exception),
        false
    );
  }

  private static boolean looksLikeConnectionProblem(DataAccessException exception, String detail) {
    String text = (exception.getMessage() + " " + detail).toLowerCase(Locale.ROOT);
    return text.contains("connection refused")
        || text.contains("the network adapter could not establish the connection")
        || text.contains("io error")
        || text.contains("ora-01017")
        || text.contains("ora-12154")
        || text.contains("ora-12514")
        || text.contains("ora-12541");
  }

  private static String detail(DataAccessException exception) {
    Throwable cause = exception.getMostSpecificCause();
    if (cause instanceof SQLException sqlException) {
      return trim(sqlException.getMessage());
    }
    return trim(cause == null ? exception.getMessage() : safeMessage(cause));
  }

  private static String safeMessage(Throwable throwable) {
    String message = throwable.getMessage();
    return trim(message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message);
  }

  private static String trim(String value) {
    if (value == null || value.isBlank()) {
      return "상세 메시지가 없습니다.";
    }
    String normalized = value.replaceAll("\\s+", " ").trim();
    return normalized.length() <= 300 ? normalized : normalized.substring(0, 300) + "...";
  }
}
