package com.cloudhandson.vpdbackoffice.domain.user;

public record AppUser(
    long userId,
    String username,
    String empNo,
    String deptCode,
    String activeYn
) {

  public boolean active() {
    return "Y".equalsIgnoreCase(activeYn);
  }
}
